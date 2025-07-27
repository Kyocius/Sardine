package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Type.IntegerType;
import IR.Value.BasicBlock;
import IR.Value.ConstInteger;
import IR.Value.Function;
import IR.Value.Instructions.*;
import IR.Value.Value;
import Pass.IR.Utils.DomAnalysis;
import Pass.IR.Utils.IRLoop;
import Pass.IR.Utils.LoopAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class LoopStrengthReduction implements Pass.IRPass {

    IRBuildFactory f = IRBuildFactory.getInstance();

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
                runStrengthReduction(loop);
            }
        }
    }

    private void runStrengthReduction(IRLoop loop) {
        if (!loop.isSetIndVar()) return ;
        BasicBlock head = loop.getHead();
        if (head.getPreBlocks().size() != 2) return;
        if (loop.getLatchBlocks().size() != 1) return ;
        BasicBlock latch = loop.getLatchBlocks().get(0);
        int latchIdx = head.getPreBlocks().indexOf(latch);
        BasicBlock preHead = head.getPreBlocks().get(1 - latchIdx);
        BrInst preHeadBr = (BrInst) preHead.getLastInst();
        Value step = loop.getItStep();
        if (!(step instanceof ConstInteger)) return ;
        int val = ((ConstInteger) step).getValue();

        Value itVar = loop.getItVar(), itInit = loop.getItInit(), itEnd = loop.getItEnd();
        BinaryInst itAlu = (BinaryInst) loop.getItAlu();

        ArrayList<BinaryInst> todoInsts = new ArrayList<>();
        for (BasicBlock bb : loop.getBbs()) {
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof BinaryInst binInst && binInst.getOp() == OP.Mul && binInst.getLeftVal() == itVar) {
                    todoInsts.add(binInst);
                }
            }
        }

        for (BinaryInst inst : todoInsts) {
            BasicBlock bb = inst.getParentbb();
            boolean ok1 = true, ok2 = false;
            BasicBlock itBb = latch;
            while (itBb != bb) {
                if (itBb.getPreBlocks().size() > 1) {
                    ok1 = false; break;
                }
                itBb = itBb.getPreBlocks().get(0);
            }
            if (head.getNxtBlocks().size() == 2) {
                for (BasicBlock nxtBb : head.getNxtBlocks()) {
                    if (!loop.getExitBlocks().contains(nxtBb)) {
                        itBb = nxtBb;
                    }
                }
                while (itBb != latch) {
                    if (itBb == bb) {
                        ok2 = true; break;
                    }
                    if (itBb.getNxtBlocks().size() > 1) break;
                    itBb = itBb.getNxtBlocks().get(0);
                }
            }

            if (!ok1 && !ok2) continue;

            if (!(inst.getRightVal() instanceof ConstInteger) && val != 1) continue;
            if (!(inst.getRightVal() instanceof ConstInteger)) {
                if (loop.getBbs().contains(bb)) continue;
            }
            BinaryInst mulInst = f.getBinaryInst(itInit, inst.getRightVal(), OP.Mul, itInit.getType());
            BinaryInst initInst;
            if (itAlu.getOp() == OP.Add) {
                initInst = f.getBinaryInst(mulInst, inst.getRightVal(), OP.Sub, itInit.getType());
            }
            else {
                initInst = f.getBinaryInst(mulInst, inst.getRightVal(), OP.Add, itInit.getType());
            }

            initInst.insertBefore(preHeadBr);
            mulInst.insertBefore(initInst);
            ArrayList<Value> values = new ArrayList<>();
            values.add(null); values.add(null);
            values.set(1 - latchIdx, initInst);
            Phi phi = f.getPhi(inst.getType(), values);
            phi.insertBefore(head.getFirstInst());
            BinaryInst addInst = null;
            if (inst.getRightVal() instanceof ConstInteger constInt) {
                addInst = f.getBinaryInst(phi, new ConstInteger(val * constInt.getValue(), IntegerType.I32), itAlu.getOp(), inst.getType());
            }
            else {
                addInst = f.getBinaryInst(phi, inst.getRightVal(), itAlu.getOp(), inst.getType());
            }
            phi.replaceOperand(latchIdx, addInst);
            addInst.insertAfter(inst);
            inst.replaceUsedWith(addInst);
//            inst.removeSelf();
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
        return "LoopIndVars";
    }
}
