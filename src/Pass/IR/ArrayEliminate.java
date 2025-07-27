package Pass.IR;

import IR.IRModule;
import IR.Type.IntegerType;
import IR.Type.PointerType;
import IR.Type.VoidType;
import IR.Value.*;
import IR.Value.Instructions.*;
import Pass.IR.Utils.AccessAnalysis;
import Pass.IR.Utils.AliasAnalysis;
import Pass.IR.Utils.InterproceduralAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

class ArrayInfo {
    public ArrayList<Instruction> mems = new ArrayList<>();
    public LinkedHashMap<Value, StoreInst> memout = new LinkedHashMap<>();
    public LinkedHashMap<Value, LinkedHashSet<StoreInst>> memin = new LinkedHashMap<>();

    // 保存到达此处的func参数的来源块，代表这些value会在对应块被修改，可以认为是store
    public LinkedHashMap<Value, LinkedHashSet<BasicBlock>> memFuncsin =
            new LinkedHashMap<>();

    public LinkedHashSet<Value> memFuncsout = new LinkedHashSet<>();
}


public class ArrayEliminate implements Pass.IRPass {

    LinkedHashMap<BasicBlock, ArrayInfo> blockArrayInfos;
    LinkedHashMap<StoreInst, Integer> storeCount;
    LinkedHashMap<LoadInst, LinkedHashSet<StoreInst>> loadConflict;
    LinkedHashSet<Value> ignoredArray;
    public boolean needrepeat;

    @Override
    public String getName() {
        return "ArrayEliminate";
    }

    public void runAnalysis(IRModule module, boolean needEliminate){
        InterproceduralAnalysis.run(module);
        needrepeat = false;
        ignoredArray = new LinkedHashSet<>();
        blockArrayInfos = new LinkedHashMap<>();
        storeCount = new LinkedHashMap<>();
        loadConflict = new LinkedHashMap<>();
        AtomicBoolean canrun = new AtomicBoolean(true);
        module.functions().forEach(f -> {
            f.getBbs().forEach(bbnode -> {
                blockArrayInfos.put(bbnode.getValue(), new ArrayInfo());
//                bbnode.getValue().getInsts().forEach(instnode -> {
//                    if (instnode.getValue() instanceof PtrInst ptrinst) {
//                        if (ptrinst.getTarget() instanceof PtrInst) {
//                            canrun.set(false);
//                        }
//                    }
//                });
            });
            if (f.getBbs().getSize() > 500) {
                canrun.set(false);
            }
        });
        if (!canrun.get()) {
            return;
        }

//        InterproceduralAnalysis.run(module);
        ignoreArrayAnalysis(module); //识别跨函数数组交叉访问
        AccessAnalysis.run(module);

        module.functions().forEach(f->runForFunction(f, needEliminate));

        //这里的逻辑是如果替换了一些Load，需要重新分析Store们是否被用到。如果没有新替换的Load，则可以用StoreCount清除Store
        if (!needrepeat && needEliminate) {
            ArrayList<Instruction> needRemove = new ArrayList<>();
            storeCount.keySet().forEach(s -> {
                if (s.getPointer() instanceof PtrInst) {
                    if (storeCount.get(s) == 0 &&
                            !(((PtrInst) s.getPointer()).getTarget() instanceof Argument ||
                                    ((PtrInst) s.getPointer()).getTarget() instanceof GlobalVar)) {
                        needRemove.add(s);
                    }
                } else if (s.getPointer() instanceof AllocInst && storeCount.get(s) == 0) {
                    needRemove.add(s);
                }
            });
            for (Instruction instruction : needRemove) {
                instruction.removeSelf();
            }
            if (!needRemove.isEmpty()) {
                needrepeat = true;
            }
        }
        return;
    }

    @Override
    public void run(IRModule module) {
        new UniqueConst().run(module);
        runAnalysis(module, true);
    }

    private void ignoreArrayAnalysis(IRModule module) {
        LinkedHashSet<Value> visited = new LinkedHashSet<>();
        for (Function function : module.functions()) {
            LinkedHashSet<Value> visitedCur = new LinkedHashSet<>();
            for (IList.INode<BasicBlock, Function> node : function.getBbs()) {
                var bb = node.getValue();
                for (IList.INode<Instruction, BasicBlock> instnode : bb.getInsts()) {
                    var inst = instnode.getValue();
                    if (inst instanceof PtrInst ptrInst) {
                        visitedCur.add(ptrInst.getTarget());
                    } else if (inst instanceof CallInst call && !call.getFunction().isLibFunction()) {
                        for (var ope : call.getOperands()) {
                            if (ope instanceof PtrInst) {
                                ignoredArray.add(((PtrInst) ope).getTarget());
                            }
                        }
                    } else if(inst instanceof Phi phi){
                        for(var op : inst.getOperands()){
                            if(op.getType() instanceof PointerType){
                                visitedCur.add(op);
                            }
                        }
                    }
                }
            }
            for (var arr : visitedCur) {
                if (!visited.contains(arr)) {
                    visited.add(arr);
                } else {
                    ignoredArray.add(arr);
                }
            }
        }
    }

    private boolean mustPass(Function f, BasicBlock from, BasicBlock to, BasicBlock mid) {
        assert !mid.equals(to);
        var dom = f.getDomer();
        return (dom.get(mid).contains(from) && dom.get(to).contains(from) &&
                dom.get(to).contains(mid));
    }


    private void updatememInFrom(BasicBlock accblock, BasicBlock bb, Function f) {
        var bbinfo = blockArrayInfos.get(bb);
        var accinfo = blockArrayInfos.get(accblock);
        //遍历这个块的可达块
        for (var ptr : bbinfo.memout.keySet()) {
            //对每个来源块的memout，检查能否添加到accblock的in中
            var store = bbinfo.memout.get(ptr);

            boolean hasPhi = AliasAnalysis.isPhiRelated(ptr);

            if (hasPhi) {
                if (!accinfo.memin.containsKey(ptr)) {
                    accinfo.memin.put(ptr, new LinkedHashSet<>());
                }
                accinfo.memin.get(ptr).add(store);
                continue;
            }


            //如果包含了相同的ptr，检查只配关系，如果互相有遮蔽关系，则进行遮蔽。

            ArrayList<StoreInst> needDelete = new ArrayList<>();
            boolean needAdd = true;
            if (accinfo.memin.containsKey(ptr)) {
                var accInPtr = ptr;
                // 遍历accblock的到达ptr数据流

                // 判断这个完全一致的已经加入的memin中的ptr是否需要被这个新加入的ptr删除，或者是否遮蔽了这个新加入的ptr。
                for (var accOtherPtr : accinfo.memin.get(ptr)) {
                    if (accOtherPtr == accInPtr) continue;
                    var otherfromblock = accOtherPtr.getParentbb();
                    if (bb.equals(accblock) || otherfromblock.equals(accblock)) continue;
                    // bb->accblock, otherFromBlock->accblock 这种到达存在，且不是到达自身。

                    if (otherfromblock.equals(bb)) {
                        accOtherPtr.getParentbb().getInsts().updateIndex();
                        if (store.getNode().getIndexInList() > accOtherPtr.getNode().getIndexInList()) {
                            needDelete.add(accOtherPtr); // bb中，目前检查的store比已经加入的更晚，删除已经加入的
                            continue;
                        } else {
                            needAdd = false; // bb中，目前检查的store比已经加入的更早，被遮蔽无需加入。
                            break;
                        }
                    }
                    // 三个均不等

                    //fromblock到达目标一定会经过bb
                    if (mustPass(f, otherfromblock, accblock, bb)) {
                        needDelete.add(accOtherPtr); //已经加入的ptr被遮蔽，删除
                    } else if (mustPass(f, bb, accblock, otherfromblock)) {
                        needAdd = false; //将要加入的ptr被遮蔽，无需加入
                        break;
                    }
                }
            }
            if (needAdd) {
                if (!accinfo.memin.containsKey(ptr)) {
                    accinfo.memin.put(ptr, new LinkedHashSet<>());
                }
                accinfo.memin.get(ptr).add(store);
            }
            needDelete.forEach(accinfo.memin.get(ptr)::remove);
        }
        for (var funcptr : bbinfo.memFuncsout) {
            if (!accinfo.memFuncsin.containsKey(funcptr)) {
                accinfo.memFuncsin.put(funcptr, new LinkedHashSet<>());
            }
            accinfo.memFuncsin.get(funcptr).add(bb);
        }
    }

    private void runForFunction(Function f, boolean needEliminate) {
        if (f.isLibFunction()) return;
        var pastcount = storeCount;
        storeCount = new LinkedHashMap<>();
        boolean hasRecursive = false;
        for (IList.INode<BasicBlock, Function> bbnode : f.getBbs()) {
            hasRecursive = hasRecursive || analysisForBlockReturnRecur(bbnode.getValue());
        }
        if (hasRecursive) {
            var stores = new ArrayList<>(storeCount.keySet());
            for (StoreInst store : stores) {
                storeCount.put(store, 1);
            }
            // 认为所有store都被用过。
        }
        storeCount.putAll(pastcount);
        // 更新memin
        for (IList.INode<BasicBlock, Function> bbnode : f.getBbs()) {
            var bb = bbnode.getValue();
            //遍历每个块
            for (var accblock : bb.accessible) {
                updatememInFrom(accblock, bb, f);
            }
        }
        //所有可到达自身的store和memfunc都设置到对应的accinfo了

        for (var bbnode : f.getBbs()) {
            var bb = bbnode.getValue();
            bb.getInsts().updateIndex();
            var info = blockArrayInfos.get(bb);
            var visitedStores = new ArrayList<StoreInst>();
            for (var inst : info.mems) {
                if (inst instanceof LoadInst load) {
                    LinkedHashSet<StoreInst> conflictStores = new LinkedHashSet<>();
                    loadConflict.put(load, conflictStores);
                    Value ptr = load.getPointer();
                    for (Value ptrInst : info.memin.keySet()) {
                        if (AliasAnalysis.checkAlias(ptr, ptrInst) != AliasAnalysis.Alias.NO) {
                            conflictStores.addAll(info.memin.get(ptrInst));
                        }
                    }
                    //插入到达这个load的所有可能与ptrInst冲突的store
                    //如果值可能来源于被函数修改的数组，直接认为所有冲突store都可能被用到，不再判断，跳过
                    boolean conflictWithFunc = false;
                    for (var funcptr : info.memFuncsin.keySet()) {
                        if (!(funcptr.getType() instanceof PointerType)) continue;
                        var ghostPtradd = new PtrInst(funcptr, new Function("ghost", IntegerType.I32));
                        conflictWithFunc = conflictWithFunc ||
                                (AliasAnalysis.checkAlias(ghostPtradd, ptr) != AliasAnalysis.Alias.NO);
                        ghostPtradd.removeUseFromOperands();
                    }
                    if (conflictWithFunc) {
                        conflictStores.forEach(s -> storeCount.put(
                                s, storeCount.get(s) + 1
                        ));
                        continue;
                    }
                    //保存块中从前往后遍历已经遇到的store，用来判断load替换使用。
                    //判断是否有store使用了同索引，并判断这个store是否必到达load、是否不会被其他store覆盖。
                    // 如果store来源于自身块，则需要判断是否在当前inst之前。
                    ArrayList<Value> aliasMeminPtr = new ArrayList<>();
                    boolean onlyInputValue = true;
                    Value onlyValue = null;
                    StoreInst keyStore = null;
                    for (var inPtr : info.memin.keySet()) {
                        if (AliasAnalysis.checkAlias(inPtr, ptr) == AliasAnalysis.Alias.YES) {
                            for (var store : info.memin.get(inPtr)) {
                                if(store.getParentbb().equals(bb) && !visitedStores.contains(store))continue;
                                if (f.getDomer().get(bb).contains(store.getParentbb()) ||
                                        (store.getParentbb().equals(bb) && !visitedStores.contains(store))) {
                                    boolean mayBeMasked = false;
                                    for (var s : conflictStores) {
                                        if (visitedStores.contains(store) && !visitedStores.contains(s)) {
                                            continue;
                                        }
                                        BasicBlock fromb = s.getParentbb();
                                        if (!store.getParentbb().equals(fromb)) {
                                            //这个冲突store是否被可能替换store拦截，没拦截则无法替换
                                            if (!mustPass(f, fromb, bb, store.getParentbb())) {
                                                mayBeMasked = true;
                                            }
                                        } else {
                                            //在同一个块中，则判断这个冲突store是否在可能替换store前面，如果在后面则无法替换。
                                            store.getParentbb().getInsts().updateIndex();
                                            if (store.getNode().getIndexInList() < s.getNode().getIndexInList()) {
                                                mayBeMasked = true;
                                            }
                                        }

                                    }
                                    if (mayBeMasked) continue;
                                    keyStore = store;
                                    if (onlyValue == null) {
                                        onlyValue = store.getValue();
                                    } else {
                                        onlyInputValue = false;
                                    }
                                }
                            }
                            aliasMeminPtr.add(inPtr);
                        }
                        if (!onlyInputValue) break;
                    }
                    if (onlyInputValue && onlyValue != null && needEliminate) {
                        load.replaceUsedWith(keyStore.getValue());
                        load.removeSelf();
                        needrepeat = true;
                        continue;
                    }

                    if (!conflictStores.isEmpty()) {
                        conflictStores.forEach(s -> storeCount.put(
                                s, storeCount.get(s) + 1
                        ));
                    }
                } else if (inst instanceof StoreInst s0) {
                    visitedStores.add(s0);
                    //如果是重复ptr，覆盖所有原值，直接替换。
                    Value ptr = s0.getPointer();
                    boolean staticAddress = AliasAnalysis.isStaticSpace(ptr);
                    if (staticAddress) {
                        if (info.memin.containsKey(ptr)) {
                            info.memin.get(ptr).clear();
                        }
                    }
                    if (!info.memin.containsKey(ptr)) {
                        info.memin.put(ptr, new LinkedHashSet<>());
                    }
                    info.memin.get(ptr).add(s0);
                } else if (inst instanceof CallInst call) {
                    //如果是call，加入memFuncsin即可，需要在Load时检查是否被覆盖，再做出替换决定。
                    //同时，涉及到的有冲突的store认为被用到了
                    boolean safefunc = false;
                    if (call.getFunction().getName().equals("@memset") ||
                            call.getFunction().getName().equals("@putarray") ||
                            call.getFunction().getName().equals("@putfarray") ||
                            !call.getFunction().isMayHasSideEffect()
                    ) {
                        safefunc = true;
                    }
                    if (!safefunc) {
                        for (var ope : call.getOperands()) {
                            if (ope.getType() instanceof PointerType) {
                                if (!info.memFuncsin.containsKey(ope)) {
                                    info.memFuncsin.put(ope, new LinkedHashSet<>());
                                }
                                info.memFuncsin.get(ope).add(bb);
                            }
                        }
                    }
                    for (var ope : call.getOperands()) {
                        if (ope.getType() instanceof PointerType) {
                            var ghostPtradd = new PtrInst(ope, new Function("ghost", IntegerType.I32));
                            for (var key : info.memin.keySet()) {
                                if (AliasAnalysis.checkAlias(key, ghostPtradd) != AliasAnalysis.Alias.NO) {
                                    info.memin.get(key).forEach(e -> storeCount.put(e, storeCount.get(e) + 1));
                                }
                            }
                            ghostPtradd.removeUseFromOperands();
                        }
                    }
                }
            }
        }

    }

    private boolean isIgnorePtr(Value ptr){
        if(!ptr.getType().isPointerType()){
            return false;
        }
        LinkedHashSet<Value> visitedList = new LinkedHashSet<>();
        Stack<Value> waitinglist = new Stack<>();
        waitinglist.add(ptr);
        while(!waitinglist.isEmpty()){
            var x = waitinglist.pop();
            visitedList.add(x);
            if(ignoredArray.contains(AliasAnalysis.getRoot(x))){
                return true;
            }
            if(! (x instanceof User)){
                continue;
            }
            for(var ope : ((User) x).getOperands()){
                if(ope.getType() instanceof PointerType && !visitedList.contains(ope)){
                    waitinglist.add(ope);
                }
            }
        }
        return false;
    }

    private boolean analysisForBlockReturnRecur(BasicBlock block) {
        boolean recur = false;
        var info = blockArrayInfos.get(block);
        for (IList.INode<Instruction, BasicBlock> instnode : block.getInsts()) {
            var inst = instnode.getValue();
            if (inst instanceof StoreInst store) {
                if (!isIgnorePtr(store.getPointer())) {
                    info.memout.put(store.getPointer(), store);
                    storeCount.put(store, 0);
                    info.mems.add(store);
                }
            } else if (inst instanceof CallInst call) {
                info.mems.add(call);
                if (call.getFunction().getName().equals("@memset") ||
                        call.getFunction().getName().equals("@putarray") ||
                        call.getFunction().getName().equals("@putfarray") || !call.getFunction().isMayHasSideEffect()) {
                    continue;
                }
                for (var ope : call.getOperands()) {
                    if (ope.getType() instanceof PointerType) {
                        info.memFuncsout.add(ope);
                    }
                }
                if (((CallInst) inst).getFunction().getName().equals(block.getParentFunc().getName())) {
                    recur = true;
                }
            } else if (inst instanceof LoadInst load) {
                if (!(load.getPointer() instanceof PtrInst || load.getPointer() instanceof GlobalVar ||
                        load.getPointer() instanceof AllocInst || load.getPointer() instanceof Phi)) {
                    continue;
                }
                if (!isIgnorePtr(load.getPointer())) {
                    info.mems.add(load);
                }
            }
        }
        return recur;
    }
}
