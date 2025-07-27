package Pass.IR;

import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.Instruction;
import IR.Value.User;
import Pass.IR.Utils.IRLoop;
import Pass.IR.Utils.LoopAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class RemoveUselessLoop implements Pass.IRPass {
    LinkedHashSet<BasicBlock> deleteBbs = new LinkedHashSet<>();

    @Override
    public void run(IRModule module) {
        for(Function function : module.functions()){
            LoopAnalysis.runLoopInfo(function);
            LoopAnalysis.runLoopIndvarInfo(function);
            ArrayList<IRLoop> dfsOrderLoops = new ArrayList<>();
            for(IRLoop loop : function.getTopLoops()){
                dfsOrderLoops.addAll(getDFSLoops(loop));
            }

            for(IRLoop loop : dfsOrderLoops){
                if (isUselessLoop(loop)){
                    removeUselessLoop(loop);
                }
            }
        }
        for(BasicBlock bb : deleteBbs){
            bb.removeSelf();
        }
    }

    private void removeUselessLoop(IRLoop loop){
        BasicBlock head = loop.getHead();
        BasicBlock exit = null;
        for(BasicBlock exitBb : loop.getExitBlocks()){
            exit = exitBb;
        }
        BasicBlock entering = head.getPreBlocks().get(0);
        entering.turnBrBlock(head, exit);
        entering.removeNxtBlock(head);
        entering.setNxtBlock(exit);
        assert exit != null;
        int idx = exit.getPreBlocks().indexOf(head);
        exit.getPreBlocks().set(idx, entering);

        deleteBbs.addAll(loop.getBbs());
    }


    private boolean isUselessLoop(IRLoop loop){
        if(!loop.getSubLoops().isEmpty()){
            return false;
        }
        if(!loop.isSetIndVar()){
            return false;
        }

        BasicBlock head = loop.getHead();
        BasicBlock latch = null;

        for(BasicBlock latchBb : loop.getLatchBlocks()){
            latch = latchBb;
        }
        assert latch != null;

        LinkedHashSet<Instruction> allUselessInsts = new LinkedHashSet<>();
        Instruction idcPhi = (Instruction) loop.getItVar();
        Instruction idcCmp = loop.getHeadBrCond();
        Instruction headBr = loop.getHead().getLastInst();
        Instruction idcAlu = (Instruction) loop.getItAlu();
        Instruction backedge = latch.getLastInst();

        allUselessInsts.add(idcPhi);
        allUselessInsts.add(idcCmp);
        allUselessInsts.add(headBr);
        allUselessInsts.add(idcAlu);
        allUselessInsts.add(backedge);

        for(BasicBlock bb : loop.getBbs()){
            for(IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()){
                Instruction inst = instNode.getValue();
                if(!allUselessInsts.contains(inst)){
                    return false;
                }
            }
        }

        for(User user : idcPhi.getUserList()){
            Instruction userInst = (Instruction) user;
            if(!allUselessInsts.contains(userInst)){
                return false;
            }
        }

        return true;
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
        return "RemoveUselessLoop";
    }
}
