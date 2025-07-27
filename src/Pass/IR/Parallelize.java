package Pass.IR;

import Driver.Config;
import IR.IRBuildFactory;
import IR.IRModule;
import IR.Type.IntegerType;
import IR.Use;
import IR.Value.*;
import IR.Value.Instructions.*;
import Pass.IR.Utils.*;
import Pass.Pass;
import Utils.DataStruct.IList;
import Utils.DataStruct.Pair;
import jdk.jshell.execution.Util;

import java.lang.reflect.Array;
import java.util.*;

//  Loop Invariant Code Motion
public class Parallelize implements Pass.IRPass {

    IRBuildFactory f = IRBuildFactory.getInstance();
    LoadStoreDataflowAnalysis loadStoreAnalyze;
    LinkedHashSet<IRLoop> visitedLoop;
    LinkedHashMap<IRLoop, LinkedHashSet<IRLoop>> loopChildren;

    //  1. load: TODO
    //  2. call: TODO
    //  3. store: TODO
    @Override
    public void run(IRModule module) {
//        var orilines = Config.loopUnrollMaxLines;
//        Config.loopUnrollMaxLines = 500;
//        new LoopUnroll().run(module);
//        Config.loopUnrollMaxLines = orilines;
        loadStoreAnalyze = new LoadStoreDataflowAnalysis();
        loadStoreAnalyze.runAnalysis(module, false);
        visitedLoop = new LinkedHashSet<>();
        loopChildren = new LinkedHashMap<>();

        InterproceduralAnalysis.run(module);
        for (Function function : module.functions()) {
            if (function.isLibFunction()) continue;
            LoopAnalysis.runLoopInfo(function);
            ArrayList<IRLoop> dfsOrderLoops = new ArrayList<>();
            for (IRLoop loop : function.getTopLoops()) {
                dfsOrderLoops.addAll(getDFSLoops(loop));
            }
//            Collections.reverse(dfsOrderLoops);

            for (IRLoop loop : dfsOrderLoops) {
                boolean success = ParallelizeForLoop(loop, module, Config.parallelNum);
//                if(success) break;
            }
        }
    }

    private boolean ParallelizeForLoop(IRLoop loop, IRModule module, int parallelNum) {
        var func = loop.getHead().getParentFunc();
        if (visitedLoop.contains(loop)) return false ;
        visitedLoop.add(loop);

        int depth = 0;
        for(var key : loopChildren.keySet()){
            if(loopChildren.get(key).contains(loop)){
                depth += 1;
            }
        }
        if(depth >= 3){
            return false;
        }
        //if (loop.getBbs().size() > 10) {
        //    return false;
        //}
//        if(!loop.getSubLoops().isEmpty()){
//            return;
//        }
        if ((loop.getExitingBlocks().size() != 1)) return false;
        var head = loop.getHead();
        ArrayList<Phi> philist = new ArrayList<>();
        for (var instnode : head.getInsts()) {
            if (instnode.getValue() instanceof Phi phiinst)
                philist.add(phiinst);
        }
        if (philist.size() != 1) {
            return false;
        }
        Phi onlyPhi = philist.get(0);
        if (onlyPhi.getPhiValues().size() != 2) {
            return false;
        }
        var latches = loop.getLatchBlocks();
        if (latches.size() != 1) {
            return false;
        }
        var latch = latches.get(0);
        BinaryInst phiOperate = null;
        Value startIndex = null;
        Value step = null;
        BasicBlock preEntry = null;
        for (var pair : onlyPhi.getPhiValues()) {
            if (pair.getSecond().equals(latch)) {
                if (!(pair.getFirst() instanceof BinaryInst binaryInst &&
                        binaryInst.getOperands().contains(onlyPhi))) {
                    return false;
                }
                phiOperate = (BinaryInst) pair.getFirst();
                if (!phiOperate.getOperand(0).equals(onlyPhi)) {
                    return false;
                }
                step = phiOperate.getOperand(1);
            } else {
                startIndex = pair.getFirst();
                preEntry = pair.getSecond();
            }
        }
        if (startIndex == null || step == null) {
            return false;
        }
        if (step.getType() != IntegerType.I32) {
            return false;
        }
        var cond = head.getBrCond();
        if (cond == null) return false; // 过滤要求必须是cond br。

        // todo 扩充binaryInst的范围，目前只有add

        if (!(phiOperate.getOp() == OP.Add)) {
            return false;
        }

        // some->head, latch->head

        // 运行load-store分析，判断是否能够并行化

        LinkedHashSet<Instruction> allInsts = new LinkedHashSet<>();
        int memCount = 0;
        for (var bb : loop.getBbs()) {
            for (var instnode : bb.getInsts()) {
                allInsts.add(instnode.getValue());
                if(instnode.getValue() instanceof LoadInst || instnode.getValue() instanceof StoreInst){
                    memCount++;
                }
            }
        }
//        if((double)memCount/(double)allInsts.size() > 0.06){
//            return false;
//        }
        if (allInsts.contains(step)) return false;//step 不能循环相关
        for (var user : onlyPhi.getUserList()) {
            if (!allInsts.contains(user)) {
                return false;
            }
        }
        boolean canParallelize = true;
        for (var inst : allInsts) {
            if (inst instanceof LoadInst) {
                var conflictstores = loadStoreAnalyze.loadConflict.get(inst);
                if (conflictstores == null) {
                    return false;
                }
                var ptr = ((LoadInst) inst).getPointer();
                boolean phiRelated = checkUseTreeContain(ptr, onlyPhi);
                for (var store : conflictstores) {
                    if (allInsts.contains(store)) {
                        if (!store.getPointer().equals(ptr) || !phiRelated) {
                            canParallelize = false;
                            break;
                        }
                        if(store.getParentbb().getLoop() != null &&
                                store.getParentbb().getLoop()!= inst.getParentbb().getLoop() ){
                            canParallelize = false;
                            break;
                        }
                    }
                }
            } else if (inst instanceof CallInst) {
//                if(!((CallInst) inst).getFunction().getName().equals("@putint")){
//                    canParallelize = false;
//                    break;
//                }
                canParallelize = false;
                break;
            } else if (inst instanceof RetInst) {
                canParallelize = false;
                break;
            }
        }
        if (!canParallelize) {
            return false;
        }

        var entryBlock = new BasicBlock(loop.getHead().getParentFunc());
        var endBlock = new BasicBlock(loop.getHead().getParentFunc());

        var startCall = new CallInst(Objects.requireNonNull(module.getLibFunction("parallelStart")),
                new ArrayList<>());
        var newStep = new BinaryInst(OP.Mul, step, new ConstInteger(parallelNum, IntegerType.I32), IntegerType.I32);
        entryBlock.addInst(newStep);
        var newStartIndexes = new ArrayList<Value>();
        for(int i = 0;i<parallelNum;i++){
            var startIndexOffset = new BinaryInst(OP.Mul, new ConstInteger(i, IntegerType.I32), step, IntegerType.I32);
            var newStartIndex = new BinaryInst(OP.Add, startIndex, startIndexOffset, IntegerType.I32);
            entryBlock.addInst(startIndexOffset);
            entryBlock.addInst(newStartIndex);
            newStartIndexes.add(newStartIndex);
        }
        entryBlock.addInst(startCall);
        var brToHead = new BrInst(head);
        entryBlock.addInst(brToHead);

        head.getPreBlocks().set(head.getPreBlocks().indexOf(preEntry), entryBlock); // 为了让phi能够fix

        onlyPhi.replaceOperand(startIndex, newStartIndexes.get(0));

        // phiOperate.replaceOperand(step, newStep);
        var newphiOperate = new BinaryInst(phiOperate.getOp(), phiOperate.getLeftVal(), phiOperate.getRightVal(), phiOperate.getType());
        newphiOperate.replaceOperand(step, newStep);
        newphiOperate.insertBefore(phiOperate);
        onlyPhi.replaceOperand(phiOperate, newphiOperate);

        assert preEntry.getLastInst() instanceof BrInst;
        var preBr = (BrInst) preEntry.getLastInst();
        preBr.replaceBlock(head, entryBlock);

        BasicBlock exitBlock = null;
        for (var bb : head.getNxtBlocks()) {
            if (!loop.getBbs().contains(bb)) {
                exitBlock = bb;
                break;
            }
        }

        var brToExit = new BrInst(exitBlock);
        exitBlock.getPreBlocks().set(exitBlock.getPreBlocks().indexOf(head), endBlock);
        var endPhi = new Phi(IntegerType.I32,new ArrayList<>(){{add(new ConstInteger(0, IntegerType.I32));}});
        var endCall = new CallInst(Objects.requireNonNull(module.getLibFunction("parallelEnd")),
                new ArrayList<>(){{
                    add(endPhi);
                }});
        endBlock.addInst(endPhi);
        endBlock.addInst(endCall);
        endBlock.addInst(brToExit);
        endBlock.getPreBlocks().add(head);

        var headbr = (BrInst) head.getLastInst();
        headbr.replaceBlock(exitBlock, endBlock);


        endBlock.insertBefore(exitBlock);
        entryBlock.insertAfter(preEntry);
        entryBlock.getPreBlocks().add(preEntry);

        visitedLoop.addAll(loopChildren.get(loop));
        copyLoopForParallel(entryBlock, endBlock, loop, startCall, endCall, parallelNum, newStartIndexes);
        UtilFunc.makeCFG(func);
        return true;
    }

    private void copyLoopForParallel(BasicBlock paraentry, BasicBlock paraexit, IRLoop loop, CallInst paraStartCall,
                                     CallInst paraEndCall,
                                     int parallelNum, ArrayList<Value> startIndexes) {
        // 目的：复制整个循环，添加三个中间BB，使得四个并行进程进入不同的循环。
        ArrayList<ArrayList<BasicBlock>> cl = new ArrayList<>(); //copied loops
        ArrayList<BasicBlock> dfsOrder = new ArrayList<>();
        LinkedHashSet<BasicBlock> visited = new LinkedHashSet<>();
        LinkedHashSet<BasicBlock> range = new LinkedHashSet<>(loop.getBbs());
        dfsAndAdd(loop.getHead(), visited, dfsOrder, range);

        BrInst lastBr = (BrInst) paraentry.getLastInst();



        for (int i = 0; i < parallelNum-1; i++) {

            var copyhelper = new CloneHelper();
            var newBBs = new ArrayList<BasicBlock>();
            cl.add(newBBs);
            for (var bb : dfsOrder) {
                var copybb = new BasicBlock();
                newBBs.add(copybb);
                copyhelper.addValueMapping(bb, copybb);
            }
            //第一遍克隆，主要用来生成指令和valuemap
            for (var bb : dfsOrder) {
                var copybb = (BasicBlock) copyhelper.findValue(bb);
                for (var instnode : bb.getInsts()) {
                    var inst = instnode.getValue();
                    if (inst instanceof Phi) {
                        var phi = new Phi(inst.getType(), inst.getOperands());
                        copybb.addInst(phi);
                        copyhelper.addValueMapping(inst, phi);
                    } else {
                        var copyinst = copyhelper.copyInstruction(inst);
                        copybb.addInst(copyinst);
                        copyhelper.addValueMapping(inst, copyinst);
                    }

                }
            }
            //第二遍克隆，替换所有被克隆的value
            for (var bb : dfsOrder) {
                var copybb = (BasicBlock) copyhelper.findValue(bb);
                for (var instnode : copybb.getInsts()) {
                    freshValueByNew(instnode.getValue(), copyhelper);
                }
            }
            //第三遍克隆，设置所有克隆块的前继后继
            for (var bb : dfsOrder) {
                freshPredNextByOld(bb, copyhelper);
            }
        }
        for (int i = 0; i < parallelNum-1; i++) {

            var cmp = new BinaryInst(OP.Eq, paraStartCall, new ConstInteger(i, IntegerType.I32), IntegerType.I1);
            cmp.insertBefore(lastBr);
            lastBr.removeUseFromOperands();
            lastBr.getOperands().clear();
            lastBr.addOperand(cmp);
            lastBr.setTrueBlock(lastBr.getJumpBlock());

            var lastbb = lastBr.getParentbb();
            var thisParaBbs = cl.get(i);
            var newbb = new BasicBlock();
            newbb.getPreBlocks().add(lastbb);
            lastBr.setFalseBlock(newbb);

            var newbr = new BrInst(thisParaBbs.get(0));
            newbb.addInst(newbr);
            newbr.getJumpBlock().getPreBlocks().set(
                    newbr.getJumpBlock().getPreBlocks().indexOf(paraentry),
                    newbb
            );

            newbb.insertAfter(paraentry);
            var tmpbb = newbb;
            for(var bb : thisParaBbs){
                bb.insertAfter(tmpbb);
                tmpbb = bb;
            }
            lastBr = newbr;
            paraexit.getFirstInst().addOperand(new ConstInteger(i+1, IntegerType.I32));
            paraexit.getPreBlocks().add(thisParaBbs.get(0));
            thisParaBbs.get(0).getFirstInst().replaceOperand(startIndexes.get(0), startIndexes.get(i+1));
        }

    }

    private void freshValueByNew(Instruction copied, CloneHelper helper) {
        LinkedHashMap<Value, Value> mapping = new LinkedHashMap<>();
        for (var ope : copied.getOperands()) {
            if (helper.findValue(ope) != ope) {
                mapping.put(ope, helper.findValue(ope));
            }
        }
        for (var k : mapping.keySet()) {
            copied.replaceOperand(k, mapping.get(k));
        }
        if(copied instanceof BrInst br){
            if(br.isJump()){
                br.setJumpBlock((BasicBlock) helper.findValue(br.getJumpBlock()));
            }else{
                br.setTrueBlock((BasicBlock) helper.findValue(br.getTrueBlock()));
                br.setFalseBlock((BasicBlock) helper.findValue(br.getFalseBlock()));
            }
        }
    }

    private void freshPredNextByOld(BasicBlock old, CloneHelper helper) {
        BasicBlock newbb = (BasicBlock) helper.findValue(old);
        ArrayList<BasicBlock> preds = new ArrayList<>();
        ArrayList<BasicBlock> nxts = new ArrayList<>();
        for (var ori : old.getPreBlocks()) {
            preds.add((BasicBlock) helper.findValue(ori));
        }
        for (var ori : old.getNxtBlocks()) {
            nxts.add((BasicBlock) helper.findValue(ori));
        }
        newbb.setNxtBlocks(nxts);
        newbb.setPreBlocks(preds);
    }

    private void dfsAndAdd(BasicBlock start, LinkedHashSet<BasicBlock> visited, ArrayList<BasicBlock> res,
                           LinkedHashSet<BasicBlock> range) {
        if (visited.contains(start)) {
            return;
        }
        visited.add(start);
        res.add(start);
        for (var bb : start.getNxtBlocks()) {
            if (visited.contains(bb) || !range.contains(bb)) continue;
            dfsAndAdd(bb, visited, res, range);
        }
    }

    private boolean checkUseTreeContain(Value val, Value target) {
        if (!(val instanceof User)) {
            return false;
        }
        var user = (User) val;
        Stack<User> checklist = new Stack<>();
        LinkedHashSet<User> visited = new LinkedHashSet<>();
        checklist.add(user);
        visited.add(user);
        while (!checklist.isEmpty()) {
            var u = checklist.pop();
            for (var ope : u.getOperands()) {
                if (ope.equals(target)) {
                    return true;
                }
                if (ope instanceof User && !visited.contains(ope)) {
                    checklist.add((User) ope);
                    visited.add((User) ope);
                }
            }
        }
        return false;
    }

    private ArrayList<IRLoop> getDFSLoops(IRLoop loop) {
        ArrayList<IRLoop> allLoops = new ArrayList<>();
        allLoops.add(loop);
        for (IRLoop subLoop : loop.getSubLoops()) {
            allLoops.addAll(getDFSLoops(subLoop));
        }
        loopChildren.put(loop, new LinkedHashSet<>(allLoops));
        return allLoops;
    }

    @Override
    public String getName() {
        return "Parallelize";
    }
}
