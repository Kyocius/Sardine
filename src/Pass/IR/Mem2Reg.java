package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Value.*;
import IR.Value.Instructions.*;
import Pass.IR.Utils.DomAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.*;

public class Mem2Reg implements Pass.IRPass {
    private final IRBuildFactory f = IRBuildFactory.getInstance();
    private LinkedHashMap<BasicBlock, Boolean> visited;
    private LinkedHashMap<AllocInst, ArrayList<BasicBlock>> defMap;
    private ArrayList<AllocInst> defs;
    private LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> domTree;
    private LinkedHashSet<Instruction> deletedInst;
    private LinkedHashMap<Phi, AllocInst> phiAllocMap;

    @Override
    public String getName() {
        return "Mem2Reg";
    }

    @Override
    public void run(IRModule module) {
        for(Function function : module.functions()){
            if (function.isLibFunction()) {
                continue;
            }
            IList<BasicBlock, Function> basicBlocks = function.getBbs();
            visited = new LinkedHashMap<>();
            //  df为该函数的支配边界信息，记录了每个节点的支配边界
            DomAnalysis.run(function);
            LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> df = function.getDF();
            domTree = function.getIdoms();
            //  defMap记录一个allocInst的值被哪些基本块使用
            defMap = new LinkedHashMap<>();
            //  defs记录需要mem2reg的所有alloc指令
            defs = new ArrayList<>();
            //  phiAllocMap为Phi和allocInst之间建立映射
            phiAllocMap = new LinkedHashMap<>();
            //  deletedInst用于记录要删除的指令
            deletedInst = new LinkedHashSet<>();

            //  初始化alloca
            for(IList.INode<BasicBlock, Function> bbNode : basicBlocks){
                BasicBlock bb = bbNode.getValue();
                for(IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()){
                    Instruction inst = instNode.getValue();
                    if(inst instanceof AllocInst allocInst){
                        if(allocInst.getAllocType().isIntegerTy()
                                || allocInst.getAllocType().isFloatTy()) {
                            defMap.put(allocInst, new ArrayList<>());
                            defs.add(allocInst);
                        }
                    }
                }
            }


            //  初始化store
            for(IList.INode<BasicBlock, Function> bbNode : basicBlocks){
                BasicBlock bb = bbNode.getValue();
                for(IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()){
                    Instruction inst = instNode.getValue();
                    if(inst instanceof StoreInst storeInst){
                        Value pointer = storeInst.getPointer();
                        if(pointer instanceof AllocInst && defMap.containsKey(pointer)) {
                            defMap.get(pointer).add(bb);
                        }
                    }
                }
            }

            //  删除无用的alloca
            LinkedHashMap<AllocInst, ArrayList<BasicBlock>> tmpDefMap = new LinkedHashMap<>(defMap);
            for (AllocInst allocaInst : defMap.keySet()) {
                if (defMap.get(allocaInst).isEmpty()) {
                    tmpDefMap.remove(allocaInst);
                    defs.remove(allocaInst);
                }
            }
            defMap = tmpDefMap;

            //  插入Phi指令
            for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                visited.put(bbNode.getValue(), false);
            }
            for (AllocInst allocInst : defs) {
                Queue<BasicBlock> workList = new LinkedList<>(defMap.get(allocInst));
                //  F存储需要插入phi的基本块
                LinkedHashSet<BasicBlock> F = new LinkedHashSet<>();

                while (!workList.isEmpty()) {
                    BasicBlock X = workList.remove();
                    ArrayList<BasicBlock> DF_X = df.get(X);
                    for (BasicBlock Y : DF_X) {
                        if (!F.contains(Y)) {
                            //  插入Phi
                            ArrayList<Value> tmpValues = new ArrayList<>();
                            if (allocInst.getAllocType().isIntegerTy()) {
                                tmpValues = new ArrayList<>(Collections.nCopies(Y.getPreBlocks().size(), f.buildNumber(0)));
                            }
                            else if(allocInst.getAllocType().isFloatTy()){
                                tmpValues = new ArrayList<>(Collections.nCopies(Y.getPreBlocks().size(), f.buildNumber((float) 0.0)));
                            }
                            Phi phiInst = f.buildPhi(Y, allocInst.getAllocType(), tmpValues);
                            phiAllocMap.put(phiInst, allocInst);

                            F.add(Y);
                            if(!defMap.get(allocInst).contains(Y)){
                                workList.add(Y);
                            }
                        }
                    }
                }
            }

            //  开始变量重命名
            //  1. 为每一个def设置一个初始值并初始化visited
            ArrayList<Value> values = new ArrayList<>();
            for (AllocInst def : defs) {
                if(def.getAllocType().isIntegerTy()){
                    values.add(f.buildNumber(0));
                }
                else{
                    values.add(f.buildNumber((float) 0.0));
                }
            }
            for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                visited.put(bbNode.getValue(), false);
            }

            //  2. DFS支配树
            BasicBlock bbEntry = function.getBbEntry();
            dfsDF(bbEntry, values);
        }
    }

    private void dfsDF(BasicBlock nowBb, ArrayList<Value> values){
        ArrayList<Value> tmpValues = new ArrayList<>(values);
        visited.put(nowBb, true);
        deletedInst.clear();
        //  下面主要就是判断指令能不能删的事
        //  顺便赋值了value，然后构建phi指令
        for(IList.INode<Instruction, BasicBlock> instNode : nowBb.getInsts()){
            //  alloc: 删除
            Instruction inst = instNode.getValue();
            if(inst instanceof AllocInst){
                if(defMap.containsKey((AllocInst) inst)){
                    deletedInst.add(inst);
                }
            }
            //  load: 更新一下到达定义(就是values)
            else if(inst instanceof LoadInst loadInst){
                if(!(loadInst.getPointer() instanceof AllocInst allocInst)){
                    continue;
                }
                if(!allocInst.getAllocType().isIntegerTy() && !allocInst.getAllocType().isFloatTy()){
                    continue;
                }

                Value value = tmpValues.get(defs.indexOf(allocInst));
                loadInst.replaceUsedWith(value);
                deletedInst.add(inst);
            }
            else if(inst instanceof StoreInst storeInst){
                if(!(storeInst.getPointer() instanceof AllocInst allocInst)){
                    continue;
                }
                if(!allocInst.getAllocType().isIntegerTy() && !allocInst.getAllocType().isFloatTy()){
                    continue;
                }

                int index = defs.indexOf(allocInst);
                tmpValues.set(index, storeInst.getValue());
                deletedInst.add(storeInst);
            }
            else if(inst instanceof Phi phi){
                AllocInst allocInst = phiAllocMap.get(phi);
                //  源程序中可能已经有phi
                if(allocInst != null) {
                    int index = defs.indexOf(allocInst);
                    tmpValues.set(index, phi);
                }
            }
        }
        //  维护后继基本块里的phi指令
        for(BasicBlock nxtBb : nowBb.getNxtBlocks()){
            for(IList.INode<Instruction, BasicBlock> instNode : nxtBb.getInsts()){
                Instruction inst = instNode.getValue();
                if(inst instanceof Phi phi){
                    AllocInst allocInst = phiAllocMap.get(phi);
                    //  Mem2reg可能做多遍，源程序里可能已经有phi，通过phiAllocMap判断是否是此次新加入的phi
                    if(allocInst != null) {
                        //  这部分是确保phi的index和value对应正确
                        int preBbIndex = nxtBb.getPreBlocks().indexOf(nowBb);
                        //  values和defs中元素的顺序是一一对应的
                        Value value = tmpValues.get(defs.indexOf(allocInst));
                        phi.replaceOperand(preBbIndex, value);
                    }
                }
            }
        }

        for(Instruction inst : deletedInst){
            inst.removeSelf();
        }

        for (BasicBlock bb : domTree.get(nowBb)) {
            if(!visited.get(bb)) {
                dfsDF(bb, tmpValues);
            }
        }
    }

}