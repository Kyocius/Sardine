package Backend.Arm.tools;


import Backend.Arm.Instruction.*;
import Backend.Arm.Operand.*;
import Backend.Arm.Structure.ArmBlock;
import Backend.Arm.Structure.ArmFunction;
import Backend.Arm.Structure.ArmModule;
import Utils.DataStruct.IList;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static Backend.Arm.Operand.ArmCPUReg.getArmSpReg;

public class BackendPeepHole {
    private final ArmModule armModule;
    private ArmFunction armFunc;
    private LinkedHashMap<ArmBlock, LiveInfo> liveInfoMap;
    private final LinkedHashSet<ArmReg> influencedReg;

    public BackendPeepHole(ArmModule armModule) {
        this.armModule = armModule;
        this.influencedReg = new LinkedHashSet<>();
    }

    public void run() {
        boolean isFinished = false;
        while (!isFinished) {
            isFinished = peephole();
        }
        finalPeepHole(); // 这个窥孔必须是在最后进行
    }

    public void DataFlowRun() {
        loopPeephole();
        deleteUselessDef();
        deleteUselessCmp();
        deleteUselesslabel();
    }

    public boolean peephole() {
        // 任何优化都不会发生，那么才叫做 isFinished
        boolean isFinished = true;
        for (ArmFunction armFunction : armModule.getFunctions().values()) {
            // 正序遍历blocks
            // TODO livInfoMap 和 influencedReg 处理
            liveInfoMap = LiveInfo.liveInfoAnalysis(armFunction);
            armFunc = armFunction;
            for (IList.INode<ArmBlock, ArmFunction>
                 blockNode = armFunction.getBlocks().getHead();
                 blockNode != null; blockNode = blockNode.getNext()) {
                ArmBlock block = blockNode.getValue();
                for (IList.INode<ArmInstruction, ArmBlock>
                     insNode = block.getArmInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    isFinished &= moveSameRegOptimize(insNode);
                    isFinished &= addiOptimize(insNode);
                    isFinished &= storeLoadOptimize(insNode);
                    isFinished &= removeUseLessStackFixer(insNode);
                    isFinished &= mergeAddiSP(insNode, block);
                    isFinished &= callSpImm(insNode, block);
                    isFinished &= floatToMemory(insNode);
                    isFinished &= r2s2r(insNode);
                    isFinished &= floatStoreLoad(insNode);
                }
            }
//             倒序遍历blocks
            for (IList.INode<ArmBlock, ArmFunction>
                 blockNode = armFunction.getBlocks().getTail();
                 blockNode != null; blockNode = blockNode.getPrev()) {
                ArmBlock block = blockNode.getValue();
                for (IList.INode<ArmInstruction, ArmBlock>
                     insNode = block.getArmInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    isFinished &= moveUselessOptimize(insNode); // 必须和 moveSameRegOptimize 分开
                }
                isFinished &= jumpOptimize(armFunction, block);
            }
        }
        return isFinished;
    }

    public void finalPeepHole() {
        for (ArmFunction armFunction : armModule.getFunctions().values()) {
            for (IList.INode<ArmBlock, ArmFunction>
                 blockNode = armFunction.getBlocks().getHead();
                 blockNode != null; blockNode = blockNode.getNext()) {
                ArmBlock block = blockNode.getValue();
                IList.INode<ArmInstruction, ArmBlock> insNode = block.getArmInstructions().getTail();
                ArmInstruction instr = insNode.getValue();
                if (instr instanceof ArmJump && blockNode.getNext() != null
                        && blockNode.getNext().getValue() == instr.getOperands().get(0)) {
                    insNode.removeFromList();
                }
            }
        }
    }

    public boolean moveSameRegOptimize(IList.INode<ArmInstruction, ArmBlock> insNode) {
        boolean finished = true;
        if (insNode.getValue() instanceof ArmMv) {
            // 将 mv/fmv rs, rs 删掉
            if (insNode.getValue().getDefReg() ==
                    (insNode.getValue().getOperands().get(0))) {
                insNode.removeFromList();
                finished = false;
            }
        }
        return finished;
    }

    public boolean addiOptimize(IList.INode<ArmInstruction, ArmBlock> insNode) {
        boolean finished = true;
        if (insNode.getValue() instanceof ArmBinary) {
            ArmBinary ins = (ArmBinary) insNode.getValue();
            if (ins.getInstType().equals(ArmBinary.ArmBinaryType.add)) {
                if (ins.getOperands().get(1) instanceof ArmImm &&
                        ((ArmImm) ins.getOperands().get(1)).getValue() == 0) {
                    // 将 addi rs, rs, 0 删掉
                    if (ins.getDefReg().equals(ins.getOperands().get(0))) {
                        insNode.removeFromList();
//                        addInfluencedReg(insNode.getValue());
                        finished = false;
                    } else { // 将 addi rd, rs, 0 改成 mv rd, rs
                        IList.INode<ArmInstruction, ArmBlock> mvNode = new IList.INode<>
                                (new ArmMv((ArmReg) ins.getOperands().get(0),
                                        ins.getDefReg()));
                        mvNode.insertBefore(insNode);
                        updateBeUsed(mvNode);
                        insNode.removeFromList();
//                        addInfluencedReg(newNode.getValue());
//                        addInfluencedReg(insNode.getValue());
                        finished = false;
                    }
                }
            }
        }
        return finished;
    }


    public boolean storeLoadOptimize(IList.INode<ArmInstruction, ArmBlock> insNode) {
        boolean finished = true;
        // 将 store a, memory; load b, sameMemory 改成 move b, a
        IList.INode<ArmInstruction, ArmBlock> preNode = insNode.getPrev();
        if (preNode != null) {
            if ((insNode.getValue() instanceof ArmLoad
                    && preNode.getValue() instanceof ArmSw
                    && insNode.getValue().getOperands().get(0)
                    .equals(preNode.getValue().getOperands().get(1))
                    && insNode.getValue().getOperands().get(1)
                    .equals(preNode.getValue().getOperands().get(2)))) {
                if (((ArmImm) preNode.getValue().getOperands().get(2)).getValue() == -904) {
                    int a = 1;
                }
                IList.INode<ArmInstruction, ArmBlock> newNode = new IList.INode<>
                        (new ArmMv((ArmReg) preNode.getValue().getOperands().get(0),
                                insNode.getValue().getDefReg()));
                newNode.insertBefore(insNode);
                updateBeUsed(newNode);
//                addInfluencedReg(newNode.getValue());
//                preNode.removeFromList();
                insNode.removeFromList();
//                addInfluencedReg(preNode.getValue());
//                addInfluencedReg(insNode.getValue());
                finished = false;
            } else if ((insNode.getValue() instanceof ArmVLoad
                    && preNode.getValue() instanceof ArmFSw
                    && insNode.getValue().getOperands().get(0)
                    .equals(preNode.getValue().getOperands().get(1))
                    && insNode.getValue().getOperands().get(1)
                    .equals(preNode.getValue().getOperands().get(2)))) {
                IList.INode<ArmInstruction, ArmBlock> newNode = new IList.INode<>
                        (new ArmFMv((ArmReg) preNode.getValue().getOperands().get(0),
                                insNode.getValue().getDefReg()));
                newNode.insertBefore(insNode);
                updateBeUsed(newNode);
//                addInfluencedReg(newNode.getValue());
//                preNode.removeFromList();
                insNode.removeFromList();
//                addInfluencedReg(preNode.getValue());
//                addInfluencedReg(insNode.getValue());
                finished = false;
            }
        }
        return finished;
    }

    public boolean removeUseLessStackFixer(IList.INode<ArmInstruction, ArmBlock> insNode) {
        boolean finished = true;
        if (insNode.getValue() instanceof ArmLi && insNode.getNext() != null
                && insNode.getValue().getOperands().get(0) instanceof ArmStackFixer) {
            IList.INode<ArmInstruction, ArmBlock> afterNode = insNode.getNext();
            int load = ((ArmStackFixer) insNode.getValue().getOperands().get(0)).getIntValue();
            if (afterNode.getValue() instanceof ArmBinary) {
                if (afterNode.getValue().getDefReg() == ArmCPUReg.getArmSpReg()) {
                    if (load >= 4095 || load <= -4095 || !ArmTools.isArmImmCanBeEncoded(-1 * load)
                            || !ArmTools.isArmImmCanBeEncoded(load)) {
                        return true;
                    }
                    assert ((ArmBinary) afterNode.getValue()).getInstType() == ArmBinary.ArmBinaryType.sub;
                    IList.INode<ArmInstruction, ArmBlock> node = afterNode.getNext();
                    assert afterNode.getNext().getValue() instanceof ArmCall;
                    while (node != null && !(node.getValue() instanceof ArmBinary
                            && node.getValue().getDefReg() == ArmCPUReg.getArmSpReg())) { //find add
                        node = node.getNext();
                    }
                    assert node != null && node.getValue() instanceof ArmBinary
                            && node.getValue().getDefReg() == ArmCPUReg.getArmSpReg()
                            && ((ArmBinary) node.getValue()).getInstType() == ArmBinary.ArmBinaryType.add;
                    IList.INode<ArmInstruction, ArmBlock> newNode1 = new IList.INode<>(
                            new ArmBinary(new ArrayList<>(Arrays.asList(
                                    ArmCPUReg.getArmSpReg(), new ArmImm(-1 * load))),
                                    ArmCPUReg.getArmSpReg(), ArmBinary.ArmBinaryType.add));
                    IList.INode<ArmInstruction, ArmBlock> newNode2 = new IList.INode<>(
                            new ArmBinary(new ArrayList<>(Arrays.asList(
                                    ArmCPUReg.getArmSpReg(), new ArmImm(load))),
                                    ArmCPUReg.getArmSpReg(), ArmBinary.ArmBinaryType.add));
                    newNode1.insertBefore(insNode);
                    updateBeUsed(newNode1);
//                    addInfluencedReg(newNode1.getValue());
                    insNode.removeFromList();
//                    addInfluencedReg(insNode.getValue());
                    afterNode.removeFromList();
//                    addInfluencedReg(afterNode.getValue());
                    newNode2.insertBefore(node);
//                    addInfluencedReg(newNode2.getValue());
                    node.removeFromList();
//                    addInfluencedReg(node.getValue());
                    return false;
                }
                switch (((ArmBinary) afterNode.getValue()).getInstType()) {
                    case add -> {
                        if (afterNode.getValue().getOperands().get(0) == (insNode.getValue()).getDefReg()
                                && load < 4095 && load >= -4095 && ArmTools.isArmImmCanBeEncoded(load)) {
                            finished = false;
                            IList.INode<ArmInstruction, ArmBlock> newNode = new IList.INode<>(
                                    new ArmBinary(new ArrayList<>(Arrays.asList(
                                            afterNode.getValue().getOperands().get(1), new ArmImm(load))),
                                            (afterNode.getValue()).getDefReg(), ArmBinary.ArmBinaryType.add));
                            newNode.insertBefore(insNode);
                            updateBeUsed(newNode);
//                            addInfluencedReg(newNode.getValue());
                            insNode.removeFromList();
//                            addInfluencedReg(insNode.getValue());
                            afterNode.removeFromList();
//                            addInfluencedReg(afterNode.getValue());
                        } else if (afterNode.getValue().getOperands().get(1) == (insNode.getValue()).getDefReg()
                                && load < 4095 && load >= -4095 && ArmTools.isArmImmCanBeEncoded(load)) {
                            finished = false;
                            IList.INode<ArmInstruction, ArmBlock> newNode = new IList.INode<>(
                                    new ArmBinary(new ArrayList<>(Arrays.asList(
                                            afterNode.getValue().getOperands().get(0), new ArmImm(load))),
                                            (afterNode.getValue()).getDefReg(), ArmBinary.ArmBinaryType.add));
                            newNode.insertBefore(insNode);
//                            addInfluencedReg(newNode.getValue());
                            insNode.removeFromList();
//                            addInfluencedReg(insNode.getValue());
                            afterNode.removeFromList();
//                            addInfluencedReg(afterNode.getValue());
                        }
                    }
                    case sub -> {
                        if (afterNode.getValue().getOperands().get(1) == (insNode.getValue()).getDefReg()
                                && load <= 4095 && load > -4095 && ArmTools.isArmImmCanBeEncoded(-1 * load)) {
                            finished = false;
                            IList.INode<ArmInstruction, ArmBlock> newNode = new IList.INode<>(
                                    new ArmBinary(new ArrayList<>(Arrays.asList(
                                            afterNode.getValue().getOperands().get(0), new ArmImm(-1 * load))),
                                            (afterNode.getValue()).getDefReg(), ArmBinary.ArmBinaryType.add));
                            newNode.insertBefore(insNode);
//                            addInfluencedReg(newNode.getValue());
                            insNode.removeFromList();
//                            addInfluencedReg(insNode.getValue());
                            afterNode.removeFromList();
//                            addInfluencedReg(afterNode.getValue());
                        }
                    }
                }
            }
        }
        return finished;
    }

    public boolean mergeAddiSP(IList.INode<ArmInstruction, ArmBlock> insNode, ArmBlock block) {
        boolean finished = true;
        IList.INode<ArmInstruction, ArmBlock> afterNode = insNode.getNext();
        IList.INode<ArmInstruction, ArmBlock> newNode = null;
        if (insNode.getValue() instanceof ArmBinary &&
                (((ArmBinary) insNode.getValue()).getInstType() == ArmBinary.ArmBinaryType.add)
                && insNode.getValue().getDefReg() != ArmCPUReg.getArmSpReg()) {
            if (afterNode != null && afterNode.getValue() instanceof ArmSw
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(2)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())) {
                finished = false;
                if (((ArmImm) afterNode.getValue().getOperands().get(2)).getValue() == -904) {
                    int a = 1;
                }
                newNode = new IList.INode<>(
                        new ArmSw((ArmReg) afterNode.getValue().getOperands().get(0),
                                (ArmReg) insNode.getValue().getOperands().get(0),
                                insNode.getValue().getOperands().get(1)));
            } else if (afterNode != null && afterNode.getValue() instanceof ArmFSw
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(2)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())
                    && ((ArmImm) insNode.getValue().getOperands().get(1)).getValue() >= -1020
                    && ((ArmImm) insNode.getValue().getOperands().get(1)).getValue() <= 1020
                    && ((ArmImm) insNode.getValue().getOperands().get(1)).getValue() % 4 == 0) {
                finished = false;
                newNode = new IList.INode<>(
                        new ArmFSw((ArmReg) afterNode.getValue().getOperands().get(0),
                                (ArmReg) insNode.getValue().getOperands().get(0),
                                insNode.getValue().getOperands().get(1)));
            } else if (afterNode != null && afterNode.getValue() instanceof ArmLoad
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(1)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())) {
                finished = false;
                newNode = new IList.INode<>(
                        new ArmLoad((ArmReg) insNode.getValue().getOperands().get(0),
                                insNode.getValue().getOperands().get(1),
                                afterNode.getValue().getDefReg()));
            } else if (afterNode != null && afterNode.getValue() instanceof ArmVLoad
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(1)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())
                    && ((ArmImm) insNode.getValue().getOperands().get(1)).getValue() >= -1020
                    && ((ArmImm) insNode.getValue().getOperands().get(1)).getValue() <= 1020
                    && ((ArmImm) insNode.getValue().getOperands().get(1)).getValue() % 4 == 0) {
                finished = false;
                newNode = new IList.INode<>(
                        new ArmVLoad((ArmReg) insNode.getValue().getOperands().get(0),
                                insNode.getValue().getOperands().get(1), afterNode.getValue().getDefReg()));
            }
        }
        if (newNode != null) {
            newNode.insertBefore(insNode);
//            addInfluencedReg(newNode.getValue());
            insNode.removeFromList();
//            addInfluencedReg(insNode.getValue());
            afterNode.removeFromList();
//            addInfluencedReg(afterNode.getValue());
//            insNode.setSucc(afterNode.getNext());
        }
        return finished;
    }

    public boolean callSpImm(IList.INode<ArmInstruction, ArmBlock> insNode, ArmBlock block) {
        boolean finished = true;
        // mv  rs, imm
        // sub sp, sp, rs
        // call
        // add sp, sp, rs
        // 改成
        // sub sp, sp, imm
        // call
        // add sp, sp, imm
        if (insNode.getValue() instanceof ArmCall) {
            if (insNode.getPrev() != null && insNode.getNext() != null && insNode.getPrev().getPrev() != null
                    && insNode.getPrev().getValue() instanceof ArmBinary
                    && insNode.getNext().getValue() instanceof ArmBinary
                    && insNode.getPrev().getPrev().getValue() instanceof ArmMv) {
                IList.INode<ArmInstruction, ArmBlock> beforeNode = insNode.getPrev();
                IList.INode<ArmInstruction, ArmBlock> afterNode = insNode.getNext();
                if (((ArmBinary) beforeNode.getValue()).getInstType() == ArmBinary.ArmBinaryType.sub
                        && ((ArmBinary) afterNode.getValue()).getInstType() == ArmBinary.ArmBinaryType.add) {
                    IList.INode<ArmInstruction, ArmBlock> mvNode = beforeNode.getPrev();
                    ArmBinary sub = (ArmBinary) beforeNode.getValue();
                    ArmBinary add = (ArmBinary) afterNode.getValue();
                    if (sub.getDefReg().equals(getArmSpReg()) && sub.getOperands().get(0).equals(getArmSpReg())
                            && add.getDefReg().equals(getArmSpReg()) && add.getOperands().get(0).equals(getArmSpReg())
                            && sub.getOperands().get(1).equals(add.getOperands().get(1))
                            && mvNode.getValue().getDefReg().equals(sub.getOperands().get(1))
                            && mvNode.getValue().getOperands().get(0) instanceof ArmImm) {
                        ArmReg reg = mvNode.getValue().getDefReg();
                        ArmImm imm = (ArmImm) mvNode.getValue().getOperands().get(0);
                        boolean canRemoveMv = true;
                        boolean haveDefBelowcall = false;
                        for (IList.INode<ArmInstruction, ArmBlock> insNode1 = afterNode.getNext();
                             insNode1 != null; insNode1 = insNode1.getNext()) {
                            for (ArmOperand operand : insNode1.getValue().getOperands()) {
                                if (operand.equals(reg)) {
                                    canRemoveMv = false;
                                    break;
                                }
                            }
                            if (!canRemoveMv) {
                                break;
                            }
                            if (insNode1.getValue().getDefReg().equals(reg)) {
                                haveDefBelowcall = true;
                                break;
                            }
                        }
//                        if (!haveDefBelowcall) {
//                            for (IList.INode<ArmInstruction, ArmBlock> user:reg.getUsers()) {
//                                if (!user.getParent().equals(insNode.getParent())) {
//                                    canRemoveMv = false;
//                                    break;
//                                }
//                                if (user.equals(mvNode.getValue()) || user.equals(beforeNode) || user.equals(afterNode)) {
//                                    continue;
//                                }
//                                for (IList.INode<ArmInstruction, ArmBlock> insNode1 = afterNode.getNext();
//                                     insNode1 != null; insNode1 = insNode1.getNext()) {
//
//                                }
//                            }
//                        }
                        if (canRemoveMv) {
                            sub.replaceOperands(reg, imm, beforeNode);
                            add.replaceOperands(reg, imm, afterNode);
                            mvNode.removeFromList();
                            deleteBeUsed(mvNode);
                            finished = false;
                        }
                    }
                }
            }
        }
        return finished;
    }

    public boolean floatToMemory(IList.INode<ArmInstruction, ArmBlock> insNode) {
        boolean finished = true;
        IList.INode<ArmInstruction, ArmBlock> helpNode = insNode.getPrev();
        if (insNode.getValue() instanceof ArmConvMv && insNode.getNext() != null
                && insNode.getNext().getValue() instanceof ArmFSw
                && insNode.getNext().getValue().getOperands().get(0).equals(insNode.getValue().getDefReg())) {
            ArmConvMv convMv = (ArmConvMv) insNode.getValue();
            ArmFSw fsw = (ArmFSw) insNode.getNext().getValue();
            IList.INode<ArmInstruction, ArmBlock> swNode = new IList.INode<>
                    (new ArmSw((ArmReg) convMv.getOperands().get(0), (ArmReg) fsw.getOperands().get(1), fsw.getOperands().get(2)));

            if (((ArmImm) fsw.getOperands().get(2)).getValue() == -904) {
                int a = 1;
            }
            ArrayList<IList.INode<ArmInstruction, ArmBlock>> loadNodeList = new ArrayList<>();
            ArrayList<IList.INode<ArmInstruction, ArmBlock>> deleteUserList = new ArrayList<>();
            for (IList.INode<ArmInstruction, ArmBlock> user : convMv.getOperands().get(0).getUsers()) {
                if (user.getValue() instanceof ArmConvMv && user.getValue().getDefReg().equals(convMv.getOperands().get(0))
                        && user.getPrev() != null && user.getPrev().getValue() instanceof ArmVLoad
                        && user.getPrev().getValue().getDefReg().equals(user.getValue().getOperands().get(0))
                        && ((ArmImm) user.getPrev().getValue().getOperands().get(1)).getValue() == ((ArmImm) fsw.getOperands().get(2)).getValue()
                        && user.getPrev().getValue().getOperands().get(0).equals(fsw.getOperands().get(1))) {
                    IList.INode<ArmInstruction, ArmBlock> loadNode = new IList.INode<>
                            (new ArmLoad((ArmReg) fsw.getOperands().get(1), fsw.getOperands().get(2), (ArmReg) convMv.getOperands().get(0)));
                    loadNode.insertAfter(user);
                    loadNodeList.add(loadNode);
                    deleteUserList.add(user);
                }
            }
            for (IList.INode<ArmInstruction, ArmBlock> loadNode : loadNodeList) {
                updateBeUsed(loadNode);
            }
            for (IList.INode<ArmInstruction, ArmBlock> deleteUser : deleteUserList) {
                deleteUser.getPrev().removeFromList();
//                deleteBeUsed(deleteUser.getPrev());
                deleteUser.removeFromList();
//                deleteBeUsed(deleteUser);
            }
            swNode.insertBefore(insNode);
            updateBeUsed(swNode);
            insNode.getNext().removeFromList();
//            deleteBeUsed(insNode.getNext());
            insNode.removeFromList();
//            deleteBeUsed(insNode);
            finished = false;

//            System.out.println(helpNode.getValue());
//            System.out.println(helpNode.getNext().getValue());
//            System.out.println(' ');
        }

        return finished;
    }

    public boolean r2s2r(IList.INode<ArmInstruction, ArmBlock> insNode) {
        boolean finished = true;
        if (insNode.getValue() instanceof ArmConvMv && insNode.getNext() != null
                && insNode.getNext().getValue() instanceof ArmConvMv
                && insNode.getValue().getDefReg().equals(insNode.getNext().getValue().getOperands().get(0))
                && insNode.getNext().getValue().getDefReg().equals(insNode.getValue().getOperands().get(0))) {
            insNode.getNext().removeFromList();
            finished = false;
        }
        return finished;
    }

    public boolean floatStoreLoad(IList.INode<ArmInstruction, ArmBlock> insNode) {
        boolean finished = true;
        if (insNode.getValue() instanceof ArmFSw && insNode.getNext() != null && insNode.getNext().getValue() instanceof ArmVLoad) {
            ArmFSw fsw = (ArmFSw) insNode.getValue();
            ArmVLoad vload = (ArmVLoad) insNode.getNext().getValue();
            if (fsw.getOperands().get(0).equals(vload.getDefReg())
                    && fsw.getOperands().get(1).equals(vload.getOperands().get(0))
                    && fsw.getOperands().get(2).equals(vload.getOperands().get(1))) {
                insNode.getNext().removeFromList();
                finished = false;
//                deleteBeUsed(insNode.getNext());
            }
        } else if (insNode.getValue() instanceof ArmSw && insNode.getNext() != null && insNode.getNext().getValue() instanceof ArmLoad){
            ArmSw sw = (ArmSw) insNode.getValue();
            ArmLoad load = (ArmLoad) insNode.getNext().getValue();
            if (sw.getOperands().get(0).equals(load.getDefReg())
                    && sw.getOperands().get(1).equals(load.getOperands().get(0))
                    && sw.getOperands().get(2).equals(load.getOperands().get(1))) {
                insNode.getNext().removeFromList();
                finished = false;
//                deleteBeUsed(insNode.getNext());
            }
        }
        return finished;
    }

    public boolean moveUselessOptimize(IList.INode<ArmInstruction, ArmBlock> insNode) {
        boolean finished = true;
        if (insNode.getValue() instanceof ArmMv) {
//            System.out.println(insNode.getValue());
            // 将 mv rs, rs 删掉
            if (insNode.getValue().getDefReg() ==
                    (insNode.getValue().getOperands().get(0))) {
                insNode.removeFromList();
                finished = false;
            }

            // 连续两条指令 mv a1, a2 和 mv a1, a3 则删掉第一条指令
            IList.INode<ArmInstruction, ArmBlock> preNode = insNode.getPrev();
            if (preNode != null && preNode.getValue() instanceof ArmMv
                    && insNode.getValue().getDefReg().equals(preNode.getValue().getDefReg())) {
                preNode.removeFromList();
                finished = false;
            }
        }
        return finished;
    }

    public boolean jumpOptimize(ArmFunction armFunction, ArmBlock block) {
        boolean finished = true;
        // 例如：在 blocklable1 的最后 j blocklable2， 那么合并两个block
        // 【前提：全局没有其他 j blocklable2 和 br blocklable2 了】
        IList.INode<ArmInstruction, ArmBlock> insNode = block.getArmInstructions().getTail();
        if (insNode.getValue() instanceof ArmJump) {
            ArmBlock defBlock = (ArmBlock) insNode.getValue().getOperands().get(0);
            for (IList.INode<ArmBlock, ArmFunction> assitBlockNode : armFunction.getBlocks()) {
                if (assitBlockNode.getValue().equals(defBlock) && assitBlockNode.getPrev() != null
                        && assitBlockNode.getPrev().getValue().equals(block)) { // defBlock 是紧接着 block 的
                    boolean signal = true;
                    for (IList.INode<ArmBlock, ArmFunction> blockNode : armFunction.getBlocks()) {
                        if (!signal) break;
                        ArmBlock armBlock = blockNode.getValue();
                        if (armBlock.equals(block)) {
                            continue;
                        }
                        IList.INode<ArmInstruction, ArmBlock> ins = armBlock.getArmInstructions().getTail();
                        while (ins != null && (ins.getValue() instanceof ArmJump
                                || ins.getValue() instanceof ArmBranch)) {
                            if (ins.getValue() instanceof ArmJump
                                    && ins.getValue().getOperands().get(0).equals(defBlock)) {
                                signal = false;
                                break;
                            } else if (ins.getValue() instanceof ArmBranch
                                    && ins.getValue().getOperands().get(0).equals(defBlock)) {
                                signal = false;
                                break;
                            }
                            ins = ins.getPrev();
                        }
                    }
                    if (signal) {
                        var thisInsts = insNode.getParent();
                        for (ArmBlock block0 : defBlock.getSuccs()) {
                            block.addSuccs(block0);
                        }
                        block.getSuccs().remove(defBlock);
                        insNode.removeFromList();
                        thisInsts.addListBefore(defBlock.getArmInstructions());
                        for (IList.INode<ArmBlock, ArmFunction> blockNode : armFunction.getBlocks()) {
                            if (blockNode.getValue().equals(defBlock)) {
                                blockNode.removeFromList();
                                break;
                            }
                        }
                        finished = false;
                    }
                    break;
                }
            }
        }
        return finished;
    }

    private void updateBeUsed(IList.INode<ArmInstruction, ArmBlock> instrNode) {
        ArmInstruction instr = instrNode.getValue();
        for (ArmOperand operand : instr.getOperands()) {
            operand.beUsed(instrNode);
//            if (operand instanceof ArmReg) {
//                ((ArmReg) operand).addDefOrUse(loopdepth, loop);
//            }
        }
        if (instr.getDefReg() != null) {
            instr.getDefReg().beUsed(instrNode);
//            var operand = instrnode.getValue().getDefReg();
//            if (operand != null) {
//                operand.addDefOrUse(loopdepth, loop);
//            }
        }
    }


    private void deleteBeUsed(IList.INode<ArmInstruction, ArmBlock> instrNode) {
        ArmInstruction instr = instrNode.getValue();
        for (ArmOperand operand : instr.getOperands()) {
            operand.getUsers().remove(instrNode);
        }
    }

    //使用数据流分析以判断该addi是否可以删除
    //利用局部性原理剪枝
    private boolean canDeleteAddi(ArmBlock block, IList.INode<ArmInstruction, ArmBlock> insNode, ArmReg reg) {
        //优先对当前块进行遍历
        if (insNode.getValue() instanceof ArmLoad || insNode.getValue() instanceof ArmVLoad) {
            if (insNode.getValue().getDefReg() == reg) {
                return true;
            }
        }
        IList.INode<ArmInstruction, ArmBlock> afterNode = insNode.getNext();
        while (afterNode != null) {
            for (var op : afterNode.getValue().getOperands()) {
                if (op == reg) {
                    return false;
                }
            }
            if (getDefReg(afterNode.getValue()).contains(reg)) {
                return true;
            }
            afterNode = afterNode.getNext();
        }
        if (!influencedReg.contains(reg)) {
            return !liveInfoMap.get(block).getLiveOut().contains(reg);
        }
        //对一级子孙进行遍历
        for (ArmBlock block1 : block.getSuccs()) {
            afterNode = block1.getArmInstructions().getHead();
            while (afterNode != null) {
                for (var op : afterNode.getValue().getOperands()) {
                    if (op == reg) {
                        return false;
                    }
                }
                if (getDefReg(afterNode.getValue()).contains(reg)) {
                    break;
                }
                afterNode = afterNode.getNext();
            }
        }
        liveInfoMap = Backend.Arm.tools.LiveInfo.liveInfoAnalysis(armFunc);
        // TODO influencedReg.clear();
        return !liveInfoMap.get(block).getLiveOut().contains(reg);
        //迫不得已 重新调用活跃性分析
    }

    private LinkedHashSet<ArmPhyReg> getDefReg(ArmInstruction ins) {
        if (!(ins instanceof ArmCall)) {
            LinkedHashSet<ArmPhyReg> ret = new LinkedHashSet<>();
            ret.add((ArmPhyReg) ins.getDefReg());
            return ret;
        } else {
            LinkedHashSet<ArmPhyReg> ret = new LinkedHashSet<>();
            ArrayList<Integer> list =
                    new ArrayList<>(
                            Arrays.asList(0, 1, 2, 3, 12));
            list.forEach(i -> ret.add(ArmCPUReg.getArmCPUReg(i)));
            ArrayList<Integer> list1 =
                    new ArrayList<>(
                            Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
            list1.forEach(i -> ret.add(ArmFPUReg.getArmFloatReg(i)));
            return ret;
        }
    }

    private void loopPeephole() {
        for (ArmFunction armFunction : armModule.getFunctions().values()) {
            // 正序遍历blocks
            // TODO livInfoMap 和 influencedReg 处理
            liveInfoMap = Backend.Arm.tools.LiveInfo.liveInfoAnalysis(armFunction);
            armFunc = armFunction;
            for (IList.INode<ArmBlock, ArmFunction>
                 blockNode = armFunction.getBlocks().getHead();
                 blockNode != null; blockNode = blockNode.getNext()) {
                // 消除多余li
                ArmBlock block = blockNode.getValue();
                for (IList.INode<ArmInstruction, ArmBlock>
                     insNode = block.getArmInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    ArmInstruction instr = insNode.getValue();
                    if (instr instanceof ArmLi && instr.getDefReg() instanceof ArmVirReg &&
                            !(instr.getOperands().get(0) instanceof ArmStackFixer)) {
                        ArmLi li = (ArmLi) instr;
                        ArmImm imm = (ArmImm) li.getOperands().get(0);
                        if (insNode.getNext() != null) {
                            for (IList.INode<ArmInstruction, ArmBlock> insNode1 = insNode.getNext();
                                 insNode1 != null; insNode1 = insNode1.getNext()) {
                                instr = insNode1.getValue();
                                if (instr instanceof ArmLi && instr.getDefReg() instanceof ArmVirReg
                                        && (instr.getOperands().get(0)).equals(imm)) {
                                    int sumOperand = 0;
                                    for (IList.INode<ArmInstruction, ArmBlock> useNode : instr.getDefReg().getUsers()) {
                                        for (ArmOperand operand : useNode.getValue().getOperands()) {
                                            if (instr.getDefReg().equals(operand)) {
                                                sumOperand++;
                                            }
                                        }
                                    }
                                    if (sumOperand > 0) {
                                        continue;
                                    }
//                                    block.dump();
                                    instr.getDefReg().replaceAllUser(insNode1, insNode);
                                    insNode1.removeFromList();
                                    deleteBeUsed(insNode1);
//                                    block.dump();
                                }
                            }
                        }
                    }
                }

                // 消除重复的二元运算指令
                for (IList.INode<ArmInstruction, ArmBlock> insNode = block.getArmInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    ArmInstruction instr = insNode.getValue();
                    if (instr instanceof ArmBinary && instr.getDefReg() instanceof ArmVirReg) {
                        ArmBinary binary = (ArmBinary) instr;
                        if (insNode.getNext() != null) {
                            for (IList.INode<ArmInstruction, ArmBlock> insNode1 = insNode.getNext();
                                 insNode1 != null; insNode1 = insNode1.getNext()) {
                                instr = insNode1.getValue();
                                if (instr instanceof ArmBinary
                                        && instr.getDefReg() instanceof ArmVirReg
                                        && (((ArmBinary) instr).getInstType()).equals(binary.getInstType())) {
                                    if ((instr.getOperands().get(0).equals(binary.getOperands().get(0))
                                            && instr.getOperands().get(1).equals(binary.getOperands().get(1)))
                                            || (instr.getOperands().get(0).equals(binary.getOperands().get(1))
                                            && instr.getOperands().get(1).equals(binary.getOperands().get(0)))) {
                                        int sumDef = 0;
                                        for (IList.INode<ArmInstruction, ArmBlock> useNode : instr.getDefReg().getUsers()) {
                                            if (instr.getDefReg().equals(useNode.getValue().getDefReg())) {
                                                sumDef++;
                                            }
                                        }
                                        if (sumDef > 1) {
                                            continue;
                                        }
                                        instr.getDefReg().replaceAllUser(insNode1, insNode);
                                        insNode1.removeFromList();
                                        deleteBeUsed(insNode1);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void deleteUselessDef() {
        for (ArmFunction armFunction : armModule.getFunctions().values()) {
            for (IList.INode<ArmBlock, ArmFunction>
                 blockNode = armFunction.getBlocks().getHead();
                 blockNode != null; blockNode = blockNode.getNext()) {
                // 消除多余li
                ArmBlock block = blockNode.getValue();
                for (IList.INode<ArmInstruction, ArmBlock>
                     insNode = block.getArmInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    ArmInstruction instr = insNode.getValue();
                    if (instr.getDefReg() != null && instr.getDefReg() instanceof ArmVirReg
                            && instr.getDefReg().getUsers().size() == 1) {
                        insNode.removeFromList();
                    }
                }
            }
        }
    }

    private boolean canDelete(IList.INode<ArmInstruction, ArmBlock> after_li, IList.INode<ArmInstruction, ArmBlock> cmp2,
                              ArmOperand op1, ArmOperand op2) {
        IList.INode<ArmInstruction, ArmBlock> insNode = after_li;
        while (insNode.getNext() != null) {
            if (insNode == cmp2) {
                return true;
            } else if (!(insNode.getValue() instanceof ArmLi || insNode.getValue() instanceof ArmMv ||
                    insNode.getValue() instanceof ArmFLi || insNode.getValue() instanceof ArmFMv)) {
                return false;
            } else if (insNode.getValue().getDefReg() == op1 || insNode.getValue().getDefReg() == op2) {
                return false;
            }
            insNode = insNode.getNext();
        }
        return false;
    }

    private void deleteUselessCmp() {
        for (ArmFunction armFunction : armModule.getFunctions().values()) {
            for (IList.INode<ArmBlock, ArmFunction>
                 blockNode = armFunction.getBlocks().getHead();
                 blockNode != null; blockNode = blockNode.getNext()) {
                ArmBlock block = blockNode.getValue();
                for (IList.INode<ArmInstruction, ArmBlock>
                     insNode = block.getArmInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    ArmInstruction instr = insNode.getValue();
                    if (instr instanceof ArmLi) {
                        IList.INode<ArmInstruction, ArmBlock> liNode1 = insNode;
                        if (liNode1.getNext() != null && liNode1.getNext().getValue() instanceof ArmLi) {
                            IList.INode<ArmInstruction, ArmBlock> liNode2 = liNode1.getNext();
                            IList.INode<ArmInstruction, ArmBlock> cmpNode1 = liNode1.getPrev();
                            if (cmpNode1 == null || !(liNode2.getValue() instanceof ArmLi && (cmpNode1.getValue() instanceof ArmCompare
                                    || cmpNode1.getValue() instanceof ArmVCompare))) {
                                continue;
                            }
                            LinkedHashSet<IList.INode<ArmInstruction, ArmBlock>> users = liNode2.getValue().getDefReg().getUsers();
                            IList.INode<ArmInstruction, ArmBlock> cmpNode2 = null;
                            if (users.size() == 3 && users.contains(liNode1) && users.contains(liNode2)) {
                                boolean flag = true;
                                for (var user : users) {
                                    if (user == liNode1 || user == liNode2) {
                                        continue;
                                    }
                                    if (!(user.getValue() instanceof ArmCompare) ||
                                            !canDelete(liNode2, user, cmpNode1.getValue().getOperands().get(0),
                                            cmpNode1.getValue().getOperands().get(1))) {
                                        flag = false;
                                    } else {
                                        cmpNode2 = user;
                                    }
                                }
                                if (!flag) {
                                    continue;
                                }
                                if (cmpNode2 != null && cmpNode2.getNext() != null
                                        && cmpNode2.getNext().getValue() instanceof ArmBranch) {
                                    IList.INode<ArmInstruction, ArmBlock> branchNode = cmpNode2.getNext();
                                    if (((ArmBranch) branchNode.getValue()).getType().equals(ArmTools.CondType.ne)) {
                                        ((ArmBranch) branchNode.getValue()).setType(((ArmLi) liNode1.getValue()).getCondType());
                                    } else if (((ArmBranch) branchNode.getValue()).getType().equals(ArmTools.CondType.eq)) {
                                        ((ArmBranch) branchNode.getValue()).setType(((ArmLi) liNode2.getValue()).getCondType());
                                    }
                                    if (branchNode.getNext() != null
                                            && branchNode.getNext().getValue() instanceof ArmBranch) {
                                        IList.INode<ArmInstruction, ArmBlock> branchNode2 = branchNode.getNext();
                                        if (((ArmBranch) branchNode2.getValue()).getType().equals(ArmTools.CondType.ne)) {
                                            ((ArmBranch) branchNode2.getValue()).setType(((ArmLi) liNode1.getValue()).getCondType());
                                        } else if (((ArmBranch) branchNode2.getValue()).getType().equals(ArmTools.CondType.eq)) {
                                            ((ArmBranch) branchNode2.getValue()).setType(((ArmLi) liNode2.getValue()).getCondType());
                                        }
                                    }
                                }
                                liNode1.removeFromList();
                                liNode2.removeFromList();
                                cmpNode2.removeFromList();
//                                for (IList.INode<ArmInstruction, ArmBlock> user : users) {
//                                    if (!user.equals(liNode1) && !user.equals(liNode2) && user.getValue() instanceof ArmCompare) {
//                                        IList.INode<ArmInstruction, ArmBlock> cmpNode = user;
//                                        if (cmpNode.getNext() != null && cmpNode.getNext().getValue() instanceof ArmBranch) {
//                                            IList.INode<ArmInstruction, ArmBlock> branchNode = cmpNode.getNext();
//                                            if (liNode2.getValue().getDefReg().getUsers().size() == 3
//                                                    && liNode2.getValue().getDefReg().equals(cmpNode.getValue().getOperands().get(0))
//                                                    && liNode2.getValue().getDefReg().equals(liNode1.getValue().getDefReg())) {
//                                                if (((ArmBranch) branchNode.getValue()).getType().equals(ArmTools.CondType.ne)) {
//                                                    ((ArmBranch) branchNode.getValue()).setType(((ArmLi) liNode1.getValue()).getCondType());
//                                                } else if (((ArmBranch) branchNode.getValue()).getType().equals(ArmTools.CondType.eq)) {
//                                                    ((ArmBranch) branchNode.getValue()).setType(((ArmLi) liNode2.getValue()).getCondType());
//                                                }
//                                                if (branchNode.getNext() != null && branchNode.getNext().getValue() instanceof ArmBranch) {
//                                                    IList.INode<ArmInstruction, ArmBlock> branchNode2 = branchNode.getNext();
//                                                    if (((ArmBranch) branchNode2.getValue()).getType().equals(ArmTools.CondType.ne)) {
//                                                        ((ArmBranch) branchNode2.getValue()).setType(((ArmLi) liNode1.getValue()).getCondType());
//                                                    } else if (((ArmBranch) branchNode2.getValue()).getType().equals(ArmTools.CondType.eq)) {
//                                                        ((ArmBranch) branchNode2.getValue()).setType(((ArmLi) liNode2.getValue()).getCondType());
//                                                    }
//                                                }
//                                                liNode1.removeFromList();
//                                                liNode2.removeFromList();
//                                                cmpNode.removeFromList();
//                                            }
//                                        }
//                                    }
//                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private void deleteUselesslabel() {
        for (ArmFunction armFunction : armModule.getFunctions().values()) {
            for (IList.INode<ArmBlock, ArmFunction>
                 blockNode = armFunction.getBlocks().getHead();
                 blockNode != null; blockNode = blockNode.getNext()) {
                ArmBlock block = blockNode.getValue();
                for (IList.INode<ArmInstruction, ArmBlock>
                     insNode = block.getArmInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    ArmInstruction instr = insNode.getValue();
                    if (instr instanceof ArmLi && instr.getDefReg() instanceof ArmVirReg &&
                            instr.getOperands().get(0) instanceof ArmLabel) {
                        boolean beUsedInDifferentBlocks = false;
                        LinkedHashSet<IList.INode<ArmInstruction, ArmBlock>> labelUsers = instr.getDefReg().getUsers();
                        for (IList.INode<ArmInstruction, ArmBlock> user : labelUsers) {
                            if (!user.getParent().equals(block)) {
                                beUsedInDifferentBlocks = true;
                                break;
                            }
                        }
                        if (beUsedInDifferentBlocks) {
                            continue;
                        }
                        ArmLi li = (ArmLi) instr;
                        ArmLabel label = (ArmLabel) li.getOperands().get(0);
                        if (insNode.getNext() != null) {
                            IList.INode<ArmInstruction, ArmBlock> usefulLabelNode = null;
                            for (IList.INode<ArmInstruction, ArmBlock> insNode1 = block.getArmInstructions().getTail();
                                 insNode1 != insNode; insNode1 = insNode1.getPrev()) {
                                if (insNode1.getValue() instanceof ArmLi && insNode1.getValue().getDefReg() instanceof ArmVirReg
                                        && (insNode1.getValue().getOperands().get(0)).equals(label)) {
                                    usefulLabelNode = insNode1;
                                    break;
                                }
                            }
                            if (usefulLabelNode != null) {
                                ArmReg reg = li.getDefReg();
                                if (reg.getUsers().size() >1) {
                                    boolean flag = false;
                                    for (IList.INode<ArmInstruction, ArmBlock> insNode1 = insNode.getNext();
                                         insNode1 != null; insNode1 = insNode1.getNext()) {
                                        ArmInstruction instr1 = insNode1.getValue();
                                        if (instr1.getDefReg()!= null && instr1.getDefReg().equals(reg)) {
                                            flag = true;
                                        }
                                        instr1.replaceOperands(reg, usefulLabelNode.getValue().getDefReg(), insNode1);
                                        if (flag) {
                                            break;
                                        }
                                    }
                                }
                                li.replaceDefReg(usefulLabelNode.getValue().getDefReg(), insNode);
                                usefulLabelNode.removeFromList();
                                deleteBeUsed(usefulLabelNode);

                                // 非常关键的一步！！！！
                                li.getDefReg().getUsers().remove(usefulLabelNode);
                                li.getDefReg().getUsers().add(insNode);

                                ArrayList<IList.INode<ArmInstruction, ArmBlock>> removeGetLabels = new ArrayList<>();
                                for (IList.INode<ArmInstruction, ArmBlock> insNode1 = insNode.getNext();
                                     insNode1 != null; insNode1 = insNode1.getNext()) {
                                    if (insNode1.getValue() instanceof ArmLi && insNode1.getValue().getDefReg() instanceof ArmVirReg &&
                                            insNode1.getValue().getOperands().get(0).equals(label)) {
                                        reg = insNode1.getValue().getDefReg();
                                        if (reg.getUsers().size() >1) {
                                            boolean flag = false;
                                            for (IList.INode<ArmInstruction, ArmBlock> insNode2 = insNode1.getNext();
                                                 insNode2 != null; insNode2 = insNode2.getNext()) {
                                                ArmInstruction instr2 = insNode2.getValue();
                                                if (instr2.getDefReg()!= null && instr2.getDefReg().equals(reg)) {
                                                    flag = true;
                                                }
                                                instr2.replaceOperands(reg, usefulLabelNode.getValue().getDefReg(), insNode2);
                                                if (flag) {
                                                    break;
                                                }
                                            }
                                        }
                                        removeGetLabels.add(insNode1);
                                    }
                                }
                                for (IList.INode<ArmInstruction, ArmBlock> remove: removeGetLabels) {
                                    remove.removeFromList();
                                    deleteBeUsed(remove);

                                    // 又是非常关键的一步！！！！
                                    remove.getValue().getDefReg().getUsers().remove(remove);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void dump() {
        try {
            var out = new BufferedWriter(new FileWriter("arm_peephole.s"));
            out.write(armModule.toString());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void DataFlowDump() {
        try {
            var out = new BufferedWriter(new FileWriter("arm_DateFLowPeephole.s"));
            out.write(armModule.toString());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

