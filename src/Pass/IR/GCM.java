package Pass.IR;

import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.*;
import IR.Value.User;
import IR.Value.Value;
import Pass.IR.Utils.DomAnalysis;
import Pass.IR.Utils.LoopAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;

public class GCM implements Pass.IRPass {
    private final LinkedHashSet<Instruction> visited = new LinkedHashSet<>();

    @Override
    public void run(IRModule module) {
        for (Function function : module.functions()) {
            if(function.getBbs().getSize() > 1) {
                runGCMForFunc(function);
            }
        }
    }

    private void  runGCMForFunc(Function function){
        visited.clear();
        LoopAnalysis.runLoopInfo(function);
        ArrayList<BasicBlock> postOrder = DomAnalysis.getDomPostOrder(function);
        Collections.reverse(postOrder);

        ArrayList<Instruction> instructions = new ArrayList<>();

        for (BasicBlock bb : postOrder) {
            for(IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()){
                instructions.add(instNode.getValue());
            }
        }

        for (Instruction instruction : instructions) {
            scheduleEarly(instruction, function);
        }


        visited.clear();
        Collections.reverse(instructions);
        for (Instruction instruction : instructions) {
            scheduleLate(instruction);
        }
    }

    private void scheduleEarly(Instruction inst, Function function){
        if (visited.contains(inst) || isPinned(inst)) {
            return;
        }
        visited.add(inst);

        //  初始移动到入口块的最后一条指令前 入口块的domLV最小
        BasicBlock entry = function.getBbEntry();
        inst.removeFromBb();
        inst.insertBefore(entry.getLastInst());

        //  确保inst的domLV大于等于它的所有输入
        for (Value input : inst.getUseValues()) {
            if (input instanceof Instruction inputInst) {
                scheduleEarly(inputInst, function);
                if (inst.getParentbb().getDomLV() < inputInst.getParentbb().getDomLV()) {
                    // 将指令插在输入指令的基本块的最后一条指令的前面
                    inst.removeFromBb();
                    inst.insertBefore(inputInst.getParentbb().getLastInst());
                }
            }
        }
    }

    private void scheduleLate(Instruction inst){
        if (visited.contains(inst) || isPinned(inst)) {
            return;
        }

        visited.add(inst);
        // lca 是所有的 user 的共同祖先
        BasicBlock lca = null;

        for (User user : inst.getUserList()) {
            if (user instanceof Instruction userInst) {
                scheduleLate(userInst);
                BasicBlock useBB;
                if (userInst instanceof Phi phi) {
                    for (int j = 0; j < phi.getPhiValues().size(); j++) {
                        Value value = userInst.getUseValues().get(j);
                        if (value instanceof Instruction && value.equals(inst)) {
                            useBB = phi.getParentbb().getPreBlocks().get(j);
                            if(useBB == null){
                                System.out.println("UseBB is NULL!");
                            }
                            lca = findLCA(lca, useBB);
                        }
                    }
                }
                else {
                    useBB = userInst.getParentbb();
                    lca = findLCA(lca, useBB);
                }
            }
        }

        // Pick a final position
        if (!inst.getUserList().isEmpty()) {
            BasicBlock best = lca;
            while (!lca.equals(inst.getParentbb())) {
                lca = lca.getIdominator();
                if (lca == null) {
                    System.out.println(inst);
                }
                if ((lca.getLoopDepth() < best.getLoopDepth()) ||
                        (lca.getNxtBlocks().size()==1 && lca.getNxtBlocks().contains(best))) {
                    best = lca;
                }
            }

            inst.removeFromBb();
            inst.insertBefore(best.getLastInst());
        }

        BasicBlock best = inst.getParentbb();
        for (IList.INode<Instruction, BasicBlock> instNode : best.getInsts()) {
            Instruction instInBestBb = instNode.getValue();
            if(!instInBestBb.equals(inst)){
                if (!(instInBestBb instanceof Phi) && instInBestBb.getUseValues().contains(inst)) {
                    inst.removeFromBb();
                    inst.insertBefore(instInBestBb);
                    break;
                }
            }
        }
    }

    private BasicBlock findLCA(BasicBlock bb1, BasicBlock bb2) {
        if (bb1 == null) {
            return bb2;
        }
        while (bb1.getDomLV() < bb2.getDomLV()) {
            bb2 = bb2.getIdominator();
        }
        while (bb2.getDomLV() < bb1.getDomLV()) {
            bb1 = bb1.getIdominator();
        }
        while (!(bb1.equals(bb2))) {
            bb1 = bb1.getIdominator();
            bb2 = bb2.getIdominator();
        }
        return bb1;
    }

    private boolean isPinned(Instruction inst){
        return inst instanceof BrInst || inst instanceof Phi
                || inst instanceof RetInst || inst instanceof StoreInst
                || inst instanceof LoadInst || inst instanceof CallInst;
    }

    @Override
    public String getName() {
        return "GCM";
    }
}
