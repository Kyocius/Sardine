package Pass.IR;

import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.Instruction;
import IR.Value.Instructions.Move;
import IR.Value.Instructions.Phi;
import IR.Value.Value;
import Pass.Pass;
import Utils.DataStruct.IList;
import Utils.DataStruct.Pair;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class RemovePhi implements Pass.IRPass {
    @Override
    public String getName() {
        return "RemovePhi";
    }

    @Override
    public void run(IRModule module) {
        for (Function func : module.functions()) {
            runForFunction(func);
        }
    }

    private void runForFunction(Function function) {
        ArrayList<Phi> allPhis = new ArrayList<>();
        LinkedHashMap<BasicBlock, ArrayList<Move>> waitAddedMove = new LinkedHashMap<>();
        //将所有Phi加入表中
        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof Phi) {
                    allPhis.add((Phi) inst);
                }
            }
        }
        //建立waitAddedMove
        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            waitAddedMove.put(bbNode.getValue(), new ArrayList<>());
        }
        for (Phi phi : allPhis) {
            for (Pair<Value, BasicBlock> pair : phi.getPhiValues()) {
                waitAddedMove.get(pair.getSecond()).add(new Move(phi, pair.getFirst()));
            }
        }
        allPhis.forEach(phi -> phi.getNode().removeFromList());
        var allbb = waitAddedMove.keySet();
        for (BasicBlock bb : allbb) {
            var allmove = waitAddedMove.get(bb);
            LinkedHashMap<Value, Integer> outDegree = new LinkedHashMap<>();
            allmove.forEach(move -> {
                assert move.getDestination() instanceof Phi;
                if (move.getDestination() instanceof Phi) {
                    outDegree.put(move.getDestination(), 0);
                }
            });
            allmove.forEach(move -> {
                if (outDegree.containsKey(move.getSource())) {
                    outDegree.put(move.getSource(), outDegree.get(move.getSource()) + 1);
                }
            });
            var needBreak = false;//是否需要破环
            while (!allmove.isEmpty()) {
                needBreak = true;
                ArrayList<Move> waitDeleted = new ArrayList<>();
                for (var move : allmove) {
                    if ((!outDegree.containsKey(move.getDestination())) ||
                            (outDegree.containsKey(move.getDestination()) && outDegree.get(move.getDestination()) == 0)) {
                        needBreak = false;
                        move.getNode().insertBefore(bb.getLastInst().getNode());
                        waitDeleted.add(move);
                        if(outDegree.containsKey(move.getSource())){
                            outDegree.put(move.getSource(), outDegree.get(move.getSource()) - 1);
                        }
                    }
                    if(move.getDestination() == move){
                        needBreak = false;
                        waitDeleted.add(move);
                        if(outDegree.containsKey(move.getSource())){
                            outDegree.put(move.getSource(), outDegree.get(move.getSource()) - 1);
                        }
                    }
                }
                allmove.removeAll(waitDeleted);
                if (needBreak) {
                    var choiceOneMove = allmove.get(0);
                    var tmpPhi = new Phi(choiceOneMove.getDestination().getType(), new ArrayList<>());
                    var newMove = new Move(tmpPhi, choiceOneMove.getDestination());
                    outDegree.put(choiceOneMove.getDestination(),0);
                    newMove.getNode().insertBefore(bb.getLastInst().getNode());
                    for (var move : allmove) {
                        if (move.getSource() == choiceOneMove.getDestination()) {
                            move.replaceOperand(choiceOneMove.getDestination(), tmpPhi);
                        }
                    }
                }
            }
        }
    }
}
