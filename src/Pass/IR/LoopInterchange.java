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
import java.util.HashSet;

public class LoopInterchange implements Pass.IRPass{

    HashMap<Value, Value> itEndMap = new HashMap<>();
    HashMap<Value, HashSet<IRLoop>> itEndToLoopMap = new HashMap<>();

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

            for (IRLoop loop : dfsOrderLoops){
                addStartToEnd(loop);
            }

            for(IRLoop loop : dfsOrderLoops){
                doLoopInterchange(loop);
            }
        }
    }

    private void addStartToEnd(IRLoop loop) {
        if (!loop.isSetIndVar()) return ;

        Value itVar = loop.getItVar(), itEnd = loop.getItEnd();
        itEndMap.put(itVar, itEnd);
        if (itEndToLoopMap.get(itEnd) == null) {
            itEndToLoopMap.put(itEnd, new HashSet<>());
        }
        itEndToLoopMap.get(itEnd).add(loop);
    }

    private void doLoopInterchange(IRLoop loop) {
        if (!loop.isSetIndVar()) return;
        BasicBlock head = loop.getHead();
        if (head.getPreBlocks().size() != 2) return;
        if (loop.getLatchBlocks().size() != 1) return;
        BasicBlock latch = loop.getLatchBlocks().get(0);
        int latchIdx = head.getPreBlocks().indexOf(latch);
        BasicBlock preHead = head.getPreBlocks().get(1 - latchIdx);

        if (!(loop.getItVar() instanceof Phi)) return ;
        Phi itVar = (Phi) loop.getItVar();
        Value itInit = loop.getItInit();
        if (itInit instanceof ConstInteger) return;
        if (!itEndMap.containsKey(itInit)) return;
        for (IRLoop preLoop : itEndToLoopMap.get(itInit)) {
            if (preLoop.getExitBlocks().size() > 1) continue;
            BasicBlock preLoopExitBb = null;
            for (BasicBlock exitBb : preLoop.getExitBlocks()) {
                preLoopExitBb = exitBb;
            }
            if (preLoopExitBb != preHead || !(preHead.getFirstInst() instanceof BrInst)) {
                continue;
            }
            Value preLoopStep = preLoop.getItStep(), step = loop.getItStep();
            if (preLoopStep instanceof ConstInteger constInt1) {
                if (!(step instanceof ConstInteger constInt2) || constInt1.getValue() != constInt2.getValue()) continue;
            } else {
                if (preLoopStep != step) continue;
            }


            BinaryInst preLoopCond = preLoop.getHeadBrCond(), itCond = loop.getHeadBrCond();
            if (preLoopCond.getOp() != itCond.getOp() || itCond.getOp() != OP.Lt) continue;
            if (preLoopCond.getLeftVal() != preLoop.getItVar() || itCond.getLeftVal() != loop.getItVar()) continue;
            //  head latch exit exiting mid
            if (loop.getBbs().size() != 5 || preLoop.getBbs().size() != 5) continue;


            BasicBlock midBb = head.getNxtBlocks().get(0);
            if (midBb.getNxtBlocks().size() > 1) continue;
            BasicBlock nxtBb = midBb.getNxtBlocks().get(0);
            ArrayList<Phi> nxtPhis = new ArrayList<>();
            for (IList.INode<Instruction, BasicBlock> instNode : nxtBb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof Phi phi) nxtPhis.add(phi);
                else break;
            }
            if (nxtPhis.size() != 2) continue;

            Value iVar = preLoopCond.getRightVal(), nVar = itCond.getRightVal();
            itVar.replaceOperand(0, new ConstInteger(0, IntegerType.I32));
            itCond.replaceOperand(1, iVar);


            Value iMulN = ((BinaryInst)midBb.getFirstInst()).getLeftVal();
            Value aij = midBb.getLastInst().getNode().getPrev().getValue();
            BinaryInst jMulN = f.getBinaryInst(itVar, nVar, OP.Mul, itVar.getType());
            jMulN.insertBefore(midBb.getLastInst());

            Phi kVar = nxtPhis.get(1);
            kVar.replaceOperand(0, iVar);
            BinaryInst cmpInst = (BinaryInst) kVar.getNode().getNext().getValue();
            cmpInst.replaceOperand(1, nVar);

            BasicBlock coreBb = nxtBb.getNxtBlocks().get(0);
            BinaryInst addInst = (BinaryInst) coreBb.getFirstInst();
            addInst.replaceOperand(0, jMulN);
            Instruction ajk = addInst.getNode().getNext().getNext().getValue();

            BinaryInst addInst2 = (BinaryInst) ajk.getNode().getNext().getNext().getValue();
            addInst2.replaceOperand(0, iMulN);
            addInst2.replaceOperand(1, kVar);
            Value aikPointer = addInst2.getNode().getNext().getValue();
            Value aik = addInst2.getNode().getNext().getNext().getValue();

            BinaryInst mulInst = (BinaryInst) addInst2.getNode().getNext().getNext().getNext().getValue();
            mulInst.replaceOperand(0, aij);
            mulInst.replaceOperand(1, ajk);
            BinaryInst subInst = (BinaryInst) mulInst.getNode().getNext().getValue();
            subInst.replaceOperand(0, aik);

            StoreInst storeInst = f.getStoreInst(subInst, aikPointer);
            storeInst.insertAfter(subInst);

            BrInst nxtBr = (BrInst) nxtBb.getLastInst();
            BasicBlock exitBb = nxtBr.getFalseBlock();
            for (IList.INode<Instruction, BasicBlock> instNode : exitBb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof StoreInst s) {
                    s.removeSelf();
                    break;
                }
            }
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
        return "LoopInterchange";
    }

}
