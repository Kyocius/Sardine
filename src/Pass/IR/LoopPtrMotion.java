package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.*;
import IR.Value.Value;
import Pass.IR.Utils.IRLoop;
import Pass.IR.Utils.LoopAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.HashSet;

public class LoopPtrMotion implements Pass.IRPass {

    IRBuildFactory f = IRBuildFactory.getInstance();

    @Override
    public void run(IRModule module) {
        for (Function function : module.functions()) {
            LoopAnalysis.runLoopIndvarInfo(function);
            LoopAnalysis.runLoopIndvarInfo(function);
            ArrayList<IRLoop> dfsOrderLoops = new ArrayList<>();
            for(IRLoop loop : function.getTopLoops()){
                dfsOrderLoops.addAll(getDFSLoops(loop));
            }

            for(IRLoop loop : dfsOrderLoops){
                runPtrMotion(loop);
            }
        }
    }

    private void runPtrMotion(IRLoop loop) {
        if (!loop.isSetIndVar()) return ;
        BasicBlock head = loop.getHead();
        if (head.getPreBlocks().size() != 2) return;
        if (loop.getLatchBlocks().size() != 1) return ;
        BasicBlock latch = loop.getLatchBlocks().get(0);
        int latchIdx = head.getPreBlocks().indexOf(latch);
        BasicBlock preHead = head.getPreBlocks().get(1 - latchIdx);
        BrInst preHeadBr = (BrInst) preHead.getLastInst();
        Value itVar = loop.getItVar(), itInit = loop.getItInit(), itStep = loop.getItStep();
        BinaryInst itAlu = (BinaryInst) loop.getItAlu();
        if (itAlu.getOp() != OP.Add && itAlu.getOp() != OP.Sub) return ;

        HashSet<Instruction> allInsts = new HashSet<>();
        ArrayList<PtrInst> todoInsts = new ArrayList<>();
        for (BasicBlock bb : loop.getBbs()) {
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                allInsts.add(inst);
                if (inst instanceof PtrInst ptrInst && ptrInst.getOffset() instanceof BinaryInst) {
                    todoInsts.add(ptrInst);
                }
            }
        }

        for (PtrInst ptrInst : todoInsts) {
            BinaryInst binInst = (BinaryInst) ptrInst.getOffset();
            Value left = binInst.getLeftVal(), right = binInst.getRightVal();
            if (binInst.getOp() != OP.Add) continue;
            if (right == itVar) {
                binInst.replaceOperand(0, right);
                binInst.replaceOperand(1, left);
            }
            left = binInst.getLeftVal(); right = binInst.getRightVal();

            Value pointer = ptrInst.getTarget();
            if (left != itVar || allInsts.contains(right)) continue;
            BinaryInst addInst = f.getBinaryInst(right, itInit, OP.Add, itInit.getType());
            PtrInst initPtr = f.getPtrInst(pointer, addInst);

            initPtr.insertBefore(preHeadBr);
            addInst.insertBefore(initPtr);

            ArrayList<Value> values = new ArrayList<>();
            values.add(null); values.add(null);
            values.set(1 - latchIdx, initPtr);
            Phi phi = f.getPhi(ptrInst.getType(), values);
            phi.insertBefore(head.getFirstInst());

            Instruction itPtrInst;
            if (itAlu.getOp() == OP.Add) itPtrInst = f.getPtrInst(phi, itStep);
            else itPtrInst = f.getPtrSubInst(phi, itStep);
            phi.replaceOperand(latchIdx, itPtrInst);
            itPtrInst.insertBefore(latch.getLastInst());
            ptrInst.replaceUsedWith(phi);
        }
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
        return "LoopPtrMotion";
    }
}
