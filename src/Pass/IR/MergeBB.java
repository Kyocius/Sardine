package Pass.IR;

import Frontend.AST;
import IR.IRModule;
import IR.Value.*;
import IR.Value.Instructions.BrInst;
import IR.Value.Instructions.CallInst;
import IR.Value.Instructions.Instruction;
import IR.Value.Instructions.Phi;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import static Pass.IR.Utils.UtilFunc.*;

/*
*   MergeBB将只有一条跳转指令的基本块删除
*   并修改与之相关的pre,nxt信息
*
* */
public class MergeBB implements Pass.IRPass {

    @Override
    public String getName() {
        return "MergeBB";
    }

    @Override
    public void run(IRModule module) {
        mergeSimpleBb(module);
        mergeBb(module);
        mergeBb2(module);
    }

    private void mergeBb2(IRModule module) {
        boolean change = true;
        for (Function function : module.functions()) {
            while (change) {
                change = false;

                ArrayList<BasicBlock> deleteBbs = new ArrayList<>();
                for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                    BasicBlock bb = bbNode.getValue();
                    if (bb.getFirstInst() == bb.getLastInst()
                            && bb.getFirstInst() instanceof BrInst brInst
                            && bb.getPreBlocks().size() == 1
                            && (bb.getPreBlocks().get(0).getLastInst() instanceof BrInst brInst2 && (brInst2.isJump() || brInst2.getJudVal() instanceof ConstInteger))
                            && (brInst.isJump() || brInst.getJudVal() instanceof ConstInteger constInt)) {
                        deleteBbs.add(bb);
                    }
                }
                for (BasicBlock bb : deleteBbs) {
                    change = true;
                    BrInst brInst = (BrInst) bb.getFirstInst();
                    BasicBlock target = getTargetBb(brInst);
                    BasicBlock preBb = bb.getPreBlocks().get(0);
                    preBb.turnBrBlock(bb, target);
                    preBb.removeNxtBlock(bb);
                    preBb.setNxtBlock(target);
                    int idx = target.getPreBlocks().indexOf(bb);
                    target.getPreBlocks().set(idx, preBb);
                }
                for (BasicBlock bb : deleteBbs) {
                    bb.getNode().removeFromList();
                }
            }
        }
    }

    private void mergeBb(IRModule module){
        LinkedHashSet<BasicBlock>  parallelStartBB = new LinkedHashSet<>();
        for(var func : module.functions()){
            for(var bbnode : func.getBbs()){
                for(var instnode : bbnode.getValue().getInsts()){
                    if(instnode.getValue() instanceof CallInst checkcall &&
                            checkcall.getFunction().equals(module.getLibFunction("parallelStart"))){
                        parallelStartBB.add(bbnode.getValue());
                        break;
                    }
                }
            }
        }
        for(Function function : module.functions()){
            boolean change = true;
            while (change) {
                change = false;
                for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                    BasicBlock bb = bbNode.getValue();
                    if (!(bb.getLastInst() instanceof BrInst brInst)) {
                        continue;
                    }
                    if (!brInst.isJump()) {
                        continue;
                    }

                    BasicBlock nxtBlock = brInst.getJumpBlock();
                    if (nxtBlock.getPreBlocks().size() != 1) {
                        continue;
                    }
                    if (nxtBlock.getFirstInst() instanceof Phi) {
                        continue;
                    }
                    if(parallelStartBB.contains(nxtBlock)){
                        continue;
                    }

                    ArrayList<Instruction> nxtInsts = new ArrayList<>();
                    for (IList.INode<Instruction, BasicBlock> nxtInstNode : nxtBlock.getInsts()) {
                        nxtInsts.add(nxtInstNode.getValue());
                    }

                    for (Instruction nxtInst : nxtInsts) {
                        nxtInst.removeFromBb();
                        nxtInst.insertAfter(bb.getLastInst());
                    }

                    brInst.removeSelf();

                    bb.removeNxtBlock(nxtBlock);
                    nxtBlock.removePreBlock(bb);

                    for(BasicBlock nxtNxtBlock : nxtBlock.getNxtBlocks()){
                        int idx = nxtNxtBlock.getPreBlocks().indexOf(nxtBlock);
                        nxtNxtBlock.getPreBlocks().set(idx, bb);
                        bb.setNxtBlock(nxtNxtBlock);
                    }

                    nxtBlock.getNode().removeFromList();

                    change = true;
                }
            }
            makeCFG(function);
        }
    }


    private void mergeSimpleBb(IRModule module){
        boolean change = true;
        for (Function function : module.functions()) {
            if (function.isLibFunction()) continue;
            while (change) {
                change = false;

                ArrayList<BasicBlock> deleteBbs = new ArrayList<>();
                for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                    BasicBlock bb = bbNode.getValue();
                    //  entry特殊判断
                    if (bb.equals(function.getBbEntry())) {
                        continue;
                    }
                    if(canMerge(bb)){
                        change = true;
                        deleteBbs.add(bb);
                    }
                }

                for (BasicBlock deleteBb : deleteBbs) {
                    BasicBlock targetBb = getTargetBb((BrInst) deleteBb.getLastInst());
                    for (BasicBlock preBb : deleteBb.getPreBlocks()) {
                        preBb.turnBrBlock(deleteBb, targetBb);

                        preBb.removeNxtBlock(deleteBb);
                        preBb.setNxtBlock(targetBb);
                        targetBb.setPreBlock(preBb);
                    }
                    deleteBb.removeSelf();
                }

                //  entry特殊判断
                BasicBlock entry = function.getBbEntry();
                if(canMerge(entry)) {
                    BasicBlock newEntry = getTargetBb((BrInst) entry.getLastInst());
                    if(newEntry.getPreBlocks().isEmpty()) {
                        entry.removeSelf();
                        IList.INode<BasicBlock, Function> newEntryNode = newEntry.getNode();
                        newEntryNode.removeFromList();
                        function.getBbs().addToHead(newEntryNode);
                    }
                }
            }
            makeCFG(function);
        }
    }

    private boolean canMerge(BasicBlock bb){
        if (involvePhi(bb)) {
            return false;
        }

        Instruction firstInst = bb.getFirstInst();
        Instruction lastInst = bb.getLastInst();
        if (firstInst.equals(lastInst)
                && lastInst instanceof BrInst brInst) {
            if(brInst.isJump()) {
                return true;
            }
            else{
                Value value = brInst.getJudVal();
                return value instanceof ConstInteger;
            }
        }
        return false;
    }

    private BasicBlock getTargetBb(BrInst brInst){
        BasicBlock targetBb;
        if(brInst.isJump()){
            targetBb = brInst.getJumpBlock();
        }
        else{
            ConstInteger constInt = (ConstInteger) brInst.getJudVal();
            if(constInt.getValue() == 1){
                targetBb = brInst.getTrueBlock();
            }
            else{
                targetBb = brInst.getFalseBlock();
            }
        }
        return targetBb;
    }
}
