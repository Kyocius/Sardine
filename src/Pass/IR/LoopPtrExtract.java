package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Type.IntegerType;
import IR.Type.PointerType;
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

public class LoopPtrExtract implements Pass.IRPass {

    IRBuildFactory f = IRBuildFactory.getInstance();

    /*
     */
    @Override
    public void run(IRModule module) {
        for (Function function : module.functions()) {
            LoopAnalysis.runLoopInfo(function);
            LoopAnalysis.runLoopIndvarInfo(function);
            ArrayList<IRLoop> dfsOrderLoops = new ArrayList<>();
            for(IRLoop loop : function.getTopLoops()){
                dfsOrderLoops.addAll(getDFSLoops(loop));
            }

            for(IRLoop loop : dfsOrderLoops){
                runPtrExtractForLoop(loop);
            }
        }
    }

    private void runPtrExtractForLoop(IRLoop loop) {
        if (!loop.isSetIndVar()) return;

        BasicBlock head = loop.getHead();
        if (head.getPreBlocks().size() != 2) return;
        if (loop.getLatchBlocks().size() != 1) return ;
        BasicBlock latch = loop.getLatchBlocks().get(0);
        int latchIdx = head.getPreBlocks().indexOf(latch);
        BasicBlock preHead = head.getPreBlocks().get(1 - latchIdx);
        BinaryInst itAlu = (BinaryInst) loop.getItAlu();
        Value itInit = loop.getItInit(), itEnd = loop.getItEnd();
        //  先做单变量的试试
        Value itVar = loop.getItVar();
        PtrInst ptrInst = null;
        for (BasicBlock bb : loop.getBbs()) {
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof PtrInst ptr) {
                    Value value = ptr.getOffset();
                    if (value == itVar) {
                        ptrInst = ptr;
                        break;
                    }
                }
            }
        }
        if (ptrInst == null) return ;

        Value pointer = ptrInst.getTarget();
        BrInst brInst = (BrInst) preHead.getLastInst();
        PtrInst ptrInit = f.getPtrInst(pointer, itInit);
        ptrInit.insertBefore(brInst);

        // build ptr phi
        ArrayList<Value> values = new ArrayList<>();
        values.add(null); values.add(null);
        values.set(latchIdx, null);
        values.set(1 - latchIdx, ptrInit);
        Phi phi = f.getPhi(pointer.getType(), values);
        phi.insertBefore(head.getFirstInst());


        Value step = loop.getItStep();
        PtrInst itPtrInst = null;
        BinaryInst binInst = null;
        if (itAlu.getOp() == OP.Add) {
             itPtrInst = f.getPtrInst(phi, step);
        }
        else {
            if (step instanceof ConstInteger constInt) {
                itPtrInst = f.getPtrInst(phi, new ConstInteger(-constInt.getValue(), IntegerType.I32));
            }
            else {
                binInst = f.getBinaryInst(step, new ConstInteger(-1, IntegerType.I32), OP.Mul, IntegerType.I32);
                itPtrInst = f.getPtrInst(phi, binInst);
            }
        }

        itPtrInst.insertAfter(itAlu);
        if (binInst != null) binInst.insertBefore(itPtrInst);
        ptrInst.replaceUsedWith(phi);
        ptrInst.removeSelf();
        phi.getOperands().set(latchIdx, itPtrInst);
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
        return "LoopPtrExtract";
    }
}
