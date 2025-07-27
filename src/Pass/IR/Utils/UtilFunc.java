package Pass.IR.Utils;

import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.BrInst;
import IR.Value.Instructions.CallInst;
import IR.Value.Instructions.Instruction;
import IR.Value.Instructions.Phi;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class UtilFunc {
    public static void makeCFG(Function function){
        LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> preMap = new LinkedHashMap<>();
        LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> nxtMap = new LinkedHashMap<>();

        //  初始化前驱后继图
        for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            preMap.put(bb, new ArrayList<>());
            nxtMap.put(bb, new ArrayList<>());
        }

        for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            Instruction lastInst = bb.getLastInst();
            if (lastInst instanceof BrInst brInst) {
                if(brInst.isJump()){
                    BasicBlock jumpBb = brInst.getJumpBlock();
                    nxtMap.get(bb).add(jumpBb);
                    preMap.get(jumpBb).add(bb);
                }
                else {
                    BasicBlock TrueBlock = brInst.getTrueBlock();
                    BasicBlock FalseBlock = brInst.getFalseBlock();
                    nxtMap.get(bb).add(TrueBlock);
                    nxtMap.get(bb).add(FalseBlock);
                    preMap.get(TrueBlock).add(bb);
                    preMap.get(FalseBlock).add(bb);
                }
            }
        }

        //  回写基本块和函数
        for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            bb.setPreBlocks(preMap.get(bb));
            bb.setNxtBlocks(nxtMap.get(bb));
        }
    }

    public static void makeCFG(ArrayList<BasicBlock> bbs){
        LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> preMap = new LinkedHashMap<>();
        LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> nxtMap = new LinkedHashMap<>();

        //  初始化前驱后继图
        for(BasicBlock bb : bbs) {
            preMap.put(bb, new ArrayList<>());
            nxtMap.put(bb, new ArrayList<>());
        }

        for(BasicBlock bb : bbs) {
            if(bb.getInsts().getTail() == null){
                continue;
            }
            Instruction lastInst = bb.getLastInst();
            if (lastInst instanceof BrInst brInst) {
                if(brInst.isJump()){
                    BasicBlock jumpBb = brInst.getJumpBlock();
                    nxtMap.get(bb).add(jumpBb);
                    preMap.get(jumpBb).add(bb);
                }
                else {
                    BasicBlock TrueBlock = brInst.getTrueBlock();
                    BasicBlock FalseBlock = brInst.getFalseBlock();
                    nxtMap.get(bb).add(TrueBlock);
                    nxtMap.get(bb).add(FalseBlock);
                    preMap.get(TrueBlock).add(bb);
                    preMap.get(FalseBlock).add(bb);
                }
            }
        }

        //  回写基本块和函数
        for(BasicBlock bb : bbs) {
            bb.setPreBlocks(preMap.get(bb));
            bb.setNxtBlocks(nxtMap.get(bb));
        }
    }


    public static void buildCallRelation(IRModule module){
        for(Function function : module.functions()){
            function.getCallerList().clear();
            function.getCalleeList().clear();
        }
        for(Function function : module.functions()){
            for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()){
                BasicBlock bb = bbNode.getValue();
                for(IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()){
                    Instruction inst = instNode.getValue();
                    if(inst instanceof CallInst callInst && !callInst.getFunction().isLibFunction()){
                        Function targetFunc = callInst.getFunction();
                        function.addCallee(targetFunc);
                        targetFunc.addCaller(function);
                    }
                }
            }
        }
    }

    public static ArrayList<Phi> getAllPhiInBbs(ArrayList<BasicBlock> bbs){
        ArrayList<Phi> phiArrayList = new ArrayList<>();
        for (BasicBlock bb : bbs) {
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof Phi) {
                    phiArrayList.add((Phi) inst);
                }
                else break;
            }
        }
        return phiArrayList;
    }

    public static ArrayList<Phi> getPhiInBb(BasicBlock bb){
        ArrayList<Phi> phiArrayList = new ArrayList<>();
        for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
            Instruction inst = instNode.getValue();
            if (inst instanceof Phi) {
                phiArrayList.add((Phi) inst);
            }
            else break;
        }
        return phiArrayList;
    }

    public static boolean involvePhi(BasicBlock bb){
        for(BasicBlock nxtBlock : bb.getNxtBlocks()){
            if(!getPhiInBb(nxtBlock).isEmpty()){
                return true;
            }
        }
        return false;
    }
}
