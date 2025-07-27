package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Type.IntegerType;
import IR.Value.BasicBlock;
import IR.Value.ConstInteger;
import IR.Value.Function;
import IR.Value.Instructions.*;
import IR.Value.Value;
import Pass.IR.Utils.IRLoop;
import Pass.IR.Utils.LoopAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.HashMap;

public class LoopRotate implements Pass.IRPass {

    IRBuildFactory f = IRBuildFactory.getInstance();

    @Override
    public void run(IRModule module) {
        for (Function function : module.functions()) {
            if (function.isLibFunction()) continue;
            LoopAnalysis.runLoopInfo(function);
            LoopAnalysis.runLoopIndvarInfo(function);
            ArrayList<IRLoop> dfsOrderLoops = new ArrayList<>();
            for(IRLoop loop : function.getTopLoops()){
                dfsOrderLoops.addAll(getDFSLoops(loop));
            }

            for(IRLoop loop : dfsOrderLoops){
                LoopRotateForLoop(loop);
            }
        }
    }

    private void LoopRotateForLoop(IRLoop loop) {
        if (!loop.isSetIndVar()) return ;
        if (loop.getExitBlocks().size() != 1) return ;
        if (loop.getLatchBlocks().size() != 1) return ;

        boolean unrolled = loop.isUnrolled();
        BasicBlock head = loop.getHead(), latch = loop.getLatchBlocks().get(0), exit = null;
        if (head.getPreBlocks().size() != 2) return ;
        Value init = loop.getItInit();
        if (!(init instanceof ConstInteger constInt) || constInt.getValue() != 0) return ;
        BinaryInst brCond = loop.getHeadBrCond();
        if (brCond.getOp() != OP.Lt) return ;
        for (BasicBlock bb : loop.getExitBlocks()) {
            exit = bb;
        }

        Value itInit = loop.getItInit(), itStep = loop.getItStep(), itEnd = loop.getItEnd();
        int latchIdx = head.getPreBlocks().indexOf(latch);
        HashMap<Phi, Value> headPhiToInitValues = new HashMap<>();
        HashMap<Phi, Value> headPhiToLatchValues = new HashMap<>();
        for (IList.INode<Instruction, BasicBlock> instNode : head.getInsts()) {
            Instruction inst = instNode.getValue();
            if (inst instanceof Phi phi) {
                headPhiToInitValues.put(phi, phi.getOperand(1 - latchIdx));
                headPhiToLatchValues.put(phi, phi.getOperand(latchIdx));
            }
            else break;
        }

        //  1. build preHead
        if (!unrolled) {
            BasicBlock preHead = f.getBasicBlock(head.getParentFunc());
            BinaryInst cmpInst = null;
            if (itEnd instanceof LoadInst loadInst) {
                Value ptr = loadInst.getPointer();
                LoadInst load = f.buildLoadInst(ptr, preHead);
                cmpInst = f.buildBinaryInst(load, new ConstInteger(0, IntegerType.I32), OP.Gt, preHead);
            }
            else cmpInst = f.buildBinaryInst(headPhiToInitValues.getOrDefault(itEnd, itEnd), new ConstInteger(0, IntegerType.I32), OP.Gt, preHead);
            f.buildBrInst(cmpInst, head, exit, preHead);
            for (BasicBlock preBb : head.getPreBlocks()) {
                if (preBb == latch) continue;
                preBb.removeNxtBlock(head);
                preBb.setNxtBlock(preHead);
                BrInst brInst = (BrInst) preBb.getLastInst();
                brInst.replaceBlock(head, preHead);
                head.getPreBlocks().set(1 - latchIdx, preHead);
                preHead.setPreBlock(preBb);
                preHead.setNxtBlock(head);
            }
            preHead.insertBefore(head);
            assert exit != null;
            exit.setPreBlock(preHead);
        }

        //  2. Fix Exit Phis

        int headIdx = exit.getPreBlocks().indexOf(head);
        for (IList.INode<Instruction, BasicBlock> instNode : exit.getInsts()) {
            Instruction inst = instNode.getValue();
            if (inst instanceof Phi phi) {
                Value value = phi.getOperand(headIdx);
                if (!unrolled) phi.addOperand(headPhiToInitValues.getOrDefault(value, value));
                phi.replaceOperand(headIdx, headPhiToLatchValues.getOrDefault(value, value));
            }
            else break;
        }

        //  3. move brCond to latch
        BrInst headBr = (BrInst) head.getLastInst();
        BasicBlock nxtBb = latch;
        for (BasicBlock bb : head.getNxtBlocks()) {
            if (bb != exit) {
                nxtBb = bb;
            }
        }
        brCond.removeFromBb();
        headBr.replaceBlock(nxtBb, head);
        headBr.removeFromBb();
        f.buildBrInst(nxtBb, head);
        exit.getPreBlocks().set(headIdx, latch);
        head.removeNxtBlock(exit);
        BrInst latchBr = (BrInst) latch.getLastInst();
        brCond.replaceOperand(0, loop.getItAlu());
        if (headPhiToLatchValues.containsKey(itEnd)) {
            brCond.replaceOperand(1, headPhiToLatchValues.get(itEnd));
        }
        brCond.insertBefore(latchBr);
        headBr.insertBefore(latchBr);
        latchBr.removeSelf();
    }

    private ArrayList<IRLoop> getDFSLoops(IRLoop loop){
        ArrayList<IRLoop> allLoops = new ArrayList<>();
        for(IRLoop subLoop : loop.getSubLoops()){
            allLoops.addAll(getDFSLoops(subLoop));
        }
        allLoops.add(loop);
        return allLoops;
    }

    @Override
    public String getName() {
        return "LoopRotate";
    }
}
