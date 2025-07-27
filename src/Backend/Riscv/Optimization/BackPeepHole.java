package Backend.Riscv.Optimization;

import Backend.Arm.Operand.ArmOperand;
import Backend.Riscv.Component.RiscvBlock;
import Backend.Riscv.Component.RiscvFunction;
import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Component.RiscvModule;
import Backend.Riscv.Instruction.*;
import Backend.Riscv.Operand.*;
import Backend.Riscv.Process.RegAllocator;
import Utils.DataStruct.IList;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import static Backend.Riscv.Instruction.RiscvBranch.RiscvCmpType.*;

public class BackPeepHole {
    private RiscvModule riscvModule;
    private RiscvFunction riscvFunc;
    private LinkedHashMap<RiscvBlock, LiveInfo> liveInfoMap;
    private LinkedHashSet<RiscvReg> influencedReg;

    public BackPeepHole(RiscvModule riscvModule) {
        this.riscvModule = riscvModule;
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
        deleteUselesslabel();
        branchOptimize();
    }

    public boolean peephole() {
        // 任何优化都不会发生，那么才叫做 isFinished
        boolean isFinished = true;
        for (RiscvFunction riscvFunction : riscvModule.getFunctions().values()) {
            // 正序遍历blocks
            // TODO livInfoMap 和 influencedReg 处理
            liveInfoMap = LiveInfo.liveInfoAnalysis(riscvFunction);
            riscvFunc = riscvFunction;
            for (IList.INode<RiscvBlock, RiscvFunction>
                 blockNode = riscvFunction.getBlocks().getHead();
                 blockNode != null; blockNode = blockNode.getNext()) {
                RiscvBlock block = blockNode.getValue();
                for (IList.INode<RiscvInstruction, RiscvBlock>
                     insNode = block.getRiscvInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    isFinished &= moveSameRegOptimize(insNode);
                    isFinished &= addiOptimize(insNode);
                    isFinished &= storeLoadOptimize(insNode);
                    isFinished &= removeUseLessStackFixer(insNode);
                    isFinished &= mergeAddiSP(insNode, block);
                }
            }

            // 倒序遍历blocks
            for (IList.INode<RiscvBlock, RiscvFunction>
                 blockNode = riscvFunction.getBlocks().getTail();
                 blockNode != null; blockNode = blockNode.getPrev()) {
                RiscvBlock block = blockNode.getValue();
                for (IList.INode<RiscvInstruction, RiscvBlock>
                     insNode = block.getRiscvInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    isFinished &= moveUselessOptimize(insNode); // 必须和 moveSameRegOptimize 分开
                }
                isFinished &= jumpOptimize(riscvFunction, block);
            }
        }
        return isFinished;
    }

    public void finalPeepHole() {
        for (RiscvFunction riscvFunction : riscvModule.getFunctions().values()) {
            for (IList.INode<RiscvBlock, RiscvFunction>
                 blockNode = riscvFunction.getBlocks().getHead();
                 blockNode != null; blockNode = blockNode.getNext()) {
                RiscvBlock block = blockNode.getValue();
                IList.INode<RiscvInstruction, RiscvBlock> insNode = block.getRiscvInstructions().getTail();
                RiscvInstruction instr = insNode.getValue();
                if (instr instanceof RiscvJ && blockNode.getNext() != null
                        && blockNode.getNext().getValue() == instr.getOperands().get(0)) {
                    insNode.removeFromList();
                }
            }
        }
    }

    public boolean moveSameRegOptimize(IList.INode<RiscvInstruction, RiscvBlock> insNode) {
        boolean finished = true;
        if (insNode.getValue() instanceof RiscvMv || insNode.getValue() instanceof RiscvFmv) {
            // 将 mv/fmv rs, rs 删掉
            if (insNode.getValue().getDefReg() ==
                    (insNode.getValue().getOperands().get(0))) {
                insNode.removeFromList();
                finished = false;
            }
        }
        return finished;
    }

    public boolean addiOptimize(IList.INode<RiscvInstruction, RiscvBlock> insNode) {
        boolean finished = true;
        if (insNode.getValue() instanceof RiscvBinary) {
            RiscvBinary ins = (RiscvBinary) insNode.getValue();
            if (ins.getInstType().equals(RiscvBinary.RiscvBinaryType.addi)) {
                if (((RiscvImm) ins.getOperands().get(1)).getValue() == 0) {
                    // 将 addi rs, rs, 0 删掉
                    if (ins.getDefReg().equals(ins.getOperands().get(0))) {
                        insNode.removeFromList();
//                        addInfluencedReg(insNode.getValue());
                        finished = false;
                    } else { // 将 addi rd, rs, 0 改成 mv rd, rs
                        IList.INode<RiscvInstruction, RiscvBlock> mvNode = new IList.INode<>
                                (new RiscvMv((RiscvReg) ins.getOperands().get(0),
                                        ins.getDefReg()));
                        mvNode.insertBefore(insNode);
                        updateBeUsed(mvNode);
                        insNode.removeFromList();
//                        addInfluencedReg(newNode.getValue());
//                        addInfluencedReg(insNode.getValue());
                        finished = false;
                    }
                }
            } else if (ins.getInstType().equals(RiscvBinary.RiscvBinaryType.addiw)) {
                if (((RiscvImm) ins.getOperands().get(1)).getValue() == 0) {
                    // 将 addi rs, rs, 0 删掉
                    if (ins.getDefReg().equals(ins.getOperands().get(0))) {
                        insNode.removeFromList();
//                        addInfluencedReg(insNode.getValue());
                        finished = false;
                    } else { // 将 addi rd, rs, 0 改成 mv rd, rs
                        IList.INode<RiscvInstruction, RiscvBlock> mvNode = new IList.INode<>
                                (new RiscvMv((RiscvReg) ins.getOperands().get(0),
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



    public boolean storeLoadOptimize(IList.INode<RiscvInstruction, RiscvBlock> insNode) {
        boolean finished = true;
        // 将 store a, memory; load b, sameMemory 改成 move b, a
        IList.INode<RiscvInstruction, RiscvBlock> preNode = insNode.getPrev();
        if (preNode != null) {
            if ((insNode.getValue() instanceof RiscvLd
                    && preNode.getValue() instanceof RiscvSd
                    && insNode.getValue().getOperands().get(0)
                    .equals(preNode.getValue().getOperands().get(1))
                    && insNode.getValue().getOperands().get(1)
                    .equals(preNode.getValue().getOperands().get(2)))
                    || (insNode.getValue() instanceof RiscvLw
                    && preNode.getValue() instanceof RiscvSw
                    && insNode.getValue().getOperands().get(0)
                    .equals(preNode.getValue().getOperands().get(1))
                    && insNode.getValue().getOperands().get(1)
                    .equals(preNode.getValue().getOperands().get(2)))) {
                IList.INode<RiscvInstruction, RiscvBlock> newNode = new IList.INode<>
                        (new RiscvMv((RiscvReg) preNode.getValue().getOperands().get(0),
                                insNode.getValue().getDefReg()));
                newNode.insertBefore(insNode);
                updateBeUsed(newNode);
//                addInfluencedReg(newNode.getValue());
                preNode.removeFromList();
                insNode.removeFromList();
//                addInfluencedReg(preNode.getValue());
//                addInfluencedReg(insNode.getValue());
                finished = false;
            } else if ((insNode.getValue() instanceof RiscvFld
                    && preNode.getValue() instanceof RiscvFsd
                    && insNode.getValue().getOperands().get(0)
                    .equals(preNode.getValue().getOperands().get(1))
                    && insNode.getValue().getOperands().get(1)
                    .equals(preNode.getValue().getOperands().get(2))) ||
                    (insNode.getValue() instanceof RiscvFlw
                            && preNode.getValue() instanceof RiscvFsw
                            && insNode.getValue().getOperands().get(0)
                            .equals(preNode.getValue().getOperands().get(1))
                            && insNode.getValue().getOperands().get(1)
                            .equals(preNode.getValue().getOperands().get(2)))) {
                IList.INode<RiscvInstruction, RiscvBlock> newNode = new IList.INode<>
                        (new RiscvFmv((RiscvReg) preNode.getValue().getOperands().get(0),
                                insNode.getValue().getDefReg()));
                newNode.insertBefore(insNode);
                updateBeUsed(newNode);
//                addInfluencedReg(newNode.getValue());
                preNode.removeFromList();
                insNode.removeFromList();
//                addInfluencedReg(preNode.getValue());
//                addInfluencedReg(insNode.getValue());
                finished = false;
            }
        }
        return finished;
    }

    public boolean removeUseLessStackFixer(IList.INode<RiscvInstruction, RiscvBlock> insNode) {
        boolean finished = true;
        if (insNode.getValue() instanceof RiscvLi && insNode.getNext() != null
                && insNode.getValue().getOperands().get(0) instanceof RiscvStackFixer) {
            IList.INode<RiscvInstruction, RiscvBlock> afterNode = insNode.getNext();
            int load = Integer.parseInt(insNode.getValue().getOperands().get(0).toString());
            if (afterNode.getValue() instanceof RiscvBinary) {
                if (afterNode.getValue().getDefReg() == RiscvCPUReg.getRiscvCPUReg(2)) {
                    if (load >= 2048 || load <= -2048) {
                        return true;
                    }
                    assert ((RiscvBinary) afterNode.getValue()).getInstType() == RiscvBinary.RiscvBinaryType.sub;
                    IList.INode<RiscvInstruction, RiscvBlock> node = afterNode.getNext();
                    assert afterNode.getNext().getValue() instanceof RiscvJal;
                    while (node != null && !(node.getValue() instanceof RiscvBinary
                            && node.getValue().getDefReg() == RiscvCPUReg.getRiscvCPUReg(2))) { //find add
                        node = node.getNext();
                    }
                    assert node != null && node.getValue() instanceof RiscvBinary
                            && node.getValue().getDefReg() == RiscvCPUReg.getRiscvCPUReg(2)
                            && ((RiscvBinary) node.getValue()).getInstType() == RiscvBinary.RiscvBinaryType.add;
                    IList.INode<RiscvInstruction, RiscvBlock> newNode1 = new IList.INode<>(
                            new RiscvBinary(new ArrayList<>(Arrays.asList(
                                    RiscvCPUReg.getRiscvCPUReg(2), new RiscvImm(-1 * load))),
                                    RiscvCPUReg.getRiscvCPUReg(2), RiscvBinary.RiscvBinaryType.addi));
                    IList.INode<RiscvInstruction, RiscvBlock> newNode2 = new IList.INode<>(
                            new RiscvBinary(new ArrayList<>(Arrays.asList(
                                    RiscvCPUReg.getRiscvCPUReg(2), new RiscvImm(load))),
                                    RiscvCPUReg.getRiscvCPUReg(2), RiscvBinary.RiscvBinaryType.addi));
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
                switch (((RiscvBinary) afterNode.getValue()).getInstType()) {
                    case add -> {
                        if (afterNode.getValue().getOperands().get(0) == (insNode.getValue()).getDefReg()
                                && load < 2048 && load >= -2048) {
                            finished = false;
                            IList.INode<RiscvInstruction, RiscvBlock> newNode = new IList.INode<>(
                                    new RiscvBinary(new ArrayList<>(Arrays.asList(
                                            afterNode.getValue().getOperands().get(1), new RiscvImm(load))),
                                            (afterNode.getValue()).getDefReg(), RiscvBinary.RiscvBinaryType.addi));
                            newNode.insertBefore(insNode);
                            updateBeUsed(newNode);
//                            addInfluencedReg(newNode.getValue());
                            insNode.removeFromList();
//                            addInfluencedReg(insNode.getValue());
                            afterNode.removeFromList();
//                            addInfluencedReg(afterNode.getValue());
                        } else if (afterNode.getValue().getOperands().get(1) == (insNode.getValue()).getDefReg()
                                && load < 2048 && load >= -2048) {
                            finished = false;
                            IList.INode<RiscvInstruction, RiscvBlock> newNode = new IList.INode<>(
                                    new RiscvBinary(new ArrayList<>(Arrays.asList(
                                            afterNode.getValue().getOperands().get(0), new RiscvImm(load))),
                                            (afterNode.getValue()).getDefReg(), RiscvBinary.RiscvBinaryType.addi));
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
                                && load <= 2048 && load > -2048) {
                            finished = false;
                            IList.INode<RiscvInstruction, RiscvBlock> newNode = new IList.INode<>(
                                    new RiscvBinary(new ArrayList<>(Arrays.asList(
                                            afterNode.getValue().getOperands().get(0), new RiscvImm(-1 * load))),
                                            (afterNode.getValue()).getDefReg(), RiscvBinary.RiscvBinaryType.addi));
                            newNode.insertBefore(insNode);
//                            addInfluencedReg(newNode.getValue());
                            insNode.removeFromList();
//                            addInfluencedReg(insNode.getValue());
                            afterNode.removeFromList();
//                            addInfluencedReg(afterNode.getValue());
                        }
                    }
                    case subw -> {
                        if (afterNode.getValue().getOperands().get(1) == (insNode.getValue()).getDefReg()
                                && load <= 2048 && load > -2048) {
                            finished = false;
                            IList.INode<RiscvInstruction, RiscvBlock> newNode = new IList.INode<>(
                                    new RiscvBinary(new ArrayList<>(Arrays.asList(
                                            afterNode.getValue().getOperands().get(0), new RiscvImm(-1 * load))),
                                            (afterNode.getValue()).getDefReg(), RiscvBinary.RiscvBinaryType.addiw));
                            newNode.insertBefore(insNode);
//                            addInfluencedReg(newNode.getValue());
                            insNode.removeFromList();
//                            addInfluencedReg(insNode.getValue());
                            afterNode.removeFromList();
//                            addInfluencedReg(afterNode.getValue());
                        }
                    }
                    case addw -> {
                        if (afterNode.getValue().getOperands().get(0) == (insNode.getValue()).getDefReg()
                                && load < 2048 && load >= -2048) {
                            finished = false;
                            IList.INode<RiscvInstruction, RiscvBlock> newNode = new IList.INode<>(
                                    new RiscvBinary(new ArrayList<>(Arrays.asList(
                                            afterNode.getValue().getOperands().get(1), new RiscvImm(load))),
                                            (afterNode.getValue()).getDefReg(), RiscvBinary.RiscvBinaryType.addiw));
                            newNode.insertBefore(insNode);
//                            addInfluencedReg(newNode.getValue());
                            insNode.removeFromList();
//                            addInfluencedReg(insNode.getValue());
                            afterNode.removeFromList();
//                            addInfluencedReg(afterNode.getValue());
                        } else if (afterNode.getValue().getOperands().get(1) == (insNode.getValue()).getDefReg()
                                && load < 2048 && load >= -2048) {
                            finished = false;
                            IList.INode<RiscvInstruction, RiscvBlock> newNode = new IList.INode<>(
                                    new RiscvBinary(new ArrayList<>(Arrays.asList(
                                            afterNode.getValue().getOperands().get(0), new RiscvImm(load))),
                                            (afterNode.getValue()).getDefReg(), RiscvBinary.RiscvBinaryType.addiw));
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

    public boolean mergeAddiSP(IList.INode<RiscvInstruction, RiscvBlock> insNode, RiscvBlock block) {
        boolean finished = true;
        IList.INode<RiscvInstruction, RiscvBlock> afterNode = insNode.getNext();
        IList.INode<RiscvInstruction, RiscvBlock> newNode = null;
        if (insNode.getValue() instanceof RiscvBinary &&
                (((RiscvBinary) insNode.getValue()).getInstType() == RiscvBinary.RiscvBinaryType.addi
                        || ((RiscvBinary) insNode.getValue()).getInstType() == RiscvBinary.RiscvBinaryType.addiw)
                && insNode.getValue().getDefReg() != RiscvCPUReg.getRiscvCPUReg(2)) {
            if (afterNode != null && afterNode.getValue() instanceof RiscvSw
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(2)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())) {
                finished = false;
                newNode = new IList.INode<>(
                        new RiscvSw((RiscvReg) afterNode.getValue().getOperands().get(0),
                                (RiscvImm) insNode.getValue().getOperands().get(1),
                                (RiscvReg) insNode.getValue().getOperands().get(0)));
            } else if (afterNode != null && afterNode.getValue() instanceof RiscvSd
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(2)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())) {
                finished = false;
                newNode = new IList.INode<>(
                        new RiscvSd((RiscvReg) afterNode.getValue().getOperands().get(0),
                                (RiscvImm) insNode.getValue().getOperands().get(1),
                                (RiscvReg) insNode.getValue().getOperands().get(0)));
            } else if (afterNode != null && afterNode.getValue() instanceof RiscvFsw
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(2)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())) {
                finished = false;
                newNode = new IList.INode<>(
                        new RiscvFsw((RiscvReg) afterNode.getValue().getOperands().get(0),
                                (RiscvImm) insNode.getValue().getOperands().get(1),
                                (RiscvReg) insNode.getValue().getOperands().get(0)));
            } else if (afterNode != null && afterNode.getValue() instanceof RiscvFsd
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(2)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())) {
                finished = false;
                newNode = new IList.INode<>(
                        new RiscvFsd((RiscvReg) afterNode.getValue().getOperands().get(0),
                                (RiscvImm) insNode.getValue().getOperands().get(1),
                                (RiscvReg) insNode.getValue().getOperands().get(0)));
            } else if (afterNode != null && afterNode.getValue() instanceof RiscvLw
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(1)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())) {
                finished = false;
                newNode = new IList.INode<>(
                        new RiscvLw((RiscvImm) insNode.getValue().getOperands().get(1),
                                (RiscvReg) insNode.getValue().getOperands().get(0),
                                afterNode.getValue().getDefReg()));
            } else if (afterNode != null && afterNode.getValue() instanceof RiscvLd
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(1)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())) {
                finished = false;
                newNode = new IList.INode<>(
                        new RiscvLd((RiscvImm) insNode.getValue().getOperands().get(1),
                                (RiscvReg) insNode.getValue().getOperands().get(0),
                                afterNode.getValue().getDefReg()));
            } else if (afterNode != null && afterNode.getValue() instanceof RiscvFlw
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(1)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())) {
                finished = false;
                newNode = new IList.INode<>(
                        new RiscvFlw((RiscvImm) insNode.getValue().getOperands().get(1),
                                (RiscvReg) insNode.getValue().getOperands().get(0), afterNode.getValue().getDefReg()));
            } else if (afterNode != null && afterNode.getValue() instanceof RiscvFld
                    && insNode.getValue().getDefReg() == afterNode.getValue().getOperands().get(1)
                    && insNode.getValue().getDefReg() != insNode.getValue().getOperands().get(0)
                    && canDeleteAddi(block, afterNode, insNode.getValue().getDefReg())) {
                finished = false;
                newNode = new IList.INode<>(
                        new RiscvFld((RiscvImm) insNode.getValue().getOperands().get(1),
                                (RiscvReg) insNode.getValue().getOperands().get(0), afterNode.getValue().getDefReg()));
            }
        }
        if(newNode != null) {
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

    public boolean moveUselessOptimize(IList.INode<RiscvInstruction, RiscvBlock> insNode) {
        boolean finished = true;
        if (insNode.getValue() instanceof RiscvMv) {
//            System.out.println(insNode.getValue());
            // 将 mv rs, rs 删掉
            if (insNode.getValue().getDefReg() ==
                    (insNode.getValue().getOperands().get(0))) {
                insNode.removeFromList();
                finished = false;
            }

            // 连续两条指令 mv a1, a2 和 mv a1, a3 则删掉第一条指令
            IList.INode<RiscvInstruction, RiscvBlock> preNode = insNode.getPrev();
            if (preNode != null && preNode.getValue() instanceof RiscvMv
                    && insNode.getValue().getDefReg().equals(preNode.getValue().getDefReg())) {
                preNode.removeFromList();
                finished = false;
            }
        }
        return finished;
    }

    public boolean jumpOptimize(RiscvFunction riscvFunction, RiscvBlock block) {
        boolean finished = true;
        // 例如：在 blocklable1 的最后 j blocklable2， 那么合并两个block
        // 【前提：全局没有其他 j blocklable2 和 br blocklable2 了】
        IList.INode<RiscvInstruction, RiscvBlock> insNode = block.getRiscvInstructions().getTail();
        if (insNode.getValue() instanceof RiscvJ) {
            RiscvBlock defBlock = (RiscvBlock) insNode.getValue().getOperands().get(0);
            for (IList.INode<RiscvBlock, RiscvFunction> assitBlockNode : riscvFunction.getBlocks()) {
                if (assitBlockNode.getValue().equals(defBlock) && assitBlockNode.getPrev() != null
                        && assitBlockNode.getPrev().getValue().equals(block)) { // defBlock 是紧接着 block 的
                    boolean signal = true;
                    for (IList.INode<RiscvBlock, RiscvFunction> blockNode: riscvFunction.getBlocks()) {
                        if (!signal) break;
                        RiscvBlock riscvBlock = blockNode.getValue();
                        if (riscvBlock.equals(block)) {
                            continue;
                        }
                        IList.INode<RiscvInstruction, RiscvBlock> ins = riscvBlock.getRiscvInstructions().getTail();
                        while(ins != null && (ins.getValue() instanceof RiscvJ
                                || ins.getValue() instanceof RiscvBranch)) {
                            if (ins.getValue() instanceof RiscvJ
                                    && ins.getValue().getOperands().get(0).equals(defBlock)) {
                                signal = false;
                                break;
                            } else if (ins.getValue() instanceof RiscvBranch
                                    && ins.getValue().getOperands().get(2).equals(defBlock)) {
                                signal = false;
                                break;
                            }
                            ins = ins.getPrev();
                        }
                    }
                    if (signal) {
                        var thisInsts = insNode.getParent();
                        for(RiscvBlock block0: defBlock.getSuccs()) {
                            block.addSuccs(block0);
                        }
                        block.getSuccs().remove(defBlock);
                        insNode.removeFromList();
                        thisInsts.addListBefore(defBlock.getRiscvInstructions());
                        for (IList.INode<RiscvBlock, RiscvFunction> blockNode: riscvFunction.getBlocks()) {
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

    private void updateBeUsed(IList.INode<RiscvInstruction, RiscvBlock> instrNode) {
        RiscvInstruction instr = instrNode.getValue();
        for (RiscvOperand operand : instr.getOperands()) {
            operand.beUsed(instrNode);
//            if (operand instanceof RiscvReg) {
//                ((RiscvReg) operand).addDefOrUse(loopdepth, loop);
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

    private void deleteBeUsed(IList.INode<RiscvInstruction, RiscvBlock> instrNode) {
        RiscvInstruction instr = instrNode.getValue();
        for (RiscvOperand operand : instr.getOperands()) {
            operand.getUsers().remove(instrNode);
        }
    }

    //使用数据流分析以判断该addi是否可以删除
    //利用局部性原理剪枝
    private boolean canDeleteAddi(RiscvBlock block, IList.INode<RiscvInstruction, RiscvBlock> insNode, RiscvReg reg) {
        //优先对当前块进行遍历
        if(insNode.getValue() instanceof RiscvLw || insNode.getValue() instanceof RiscvLd
                || insNode.getValue() instanceof RiscvFlw || insNode.getValue() instanceof RiscvFld) {
            if(insNode.getValue().getDefReg() == reg) {
                return true;
            }
        }
        IList.INode<RiscvInstruction, RiscvBlock> afterNode = insNode.getNext();
        while(afterNode != null) {
            for(var op:afterNode.getValue().getOperands()) {
                if(op == reg) {
                    return false;
                }
            }
            if(getDefReg(afterNode.getValue()).contains(reg)) {
                return true;
            }
            afterNode = afterNode.getNext();
        }
        if(!influencedReg.contains(reg)) {
            return !liveInfoMap.get(block).getLiveOut().contains(reg);
        }
        //对一级子孙进行遍历
        for(RiscvBlock block1:block.getSuccs()) {
            afterNode = block1.getRiscvInstructions().getHead();
            while(afterNode != null) {
                for(var op:afterNode.getValue().getOperands()) {
                    if(op == reg) {
                        return false;
                    }
                }
                if(getDefReg(afterNode.getValue()).contains(reg)) {
                    break;
                }
                afterNode = afterNode.getNext();
            }
        }
        liveInfoMap = LiveInfo.liveInfoAnalysis(riscvFunc);
        // TODO influencedReg.clear();
        return !liveInfoMap.get(block).getLiveOut().contains(reg);
        //迫不得已 重新调用活跃性分析
    }

    private LinkedHashSet<RiscvPhyReg> getDefReg(RiscvInstruction ins) {
        if(!(ins instanceof RiscvJal)) {
            LinkedHashSet<RiscvPhyReg> ret = new LinkedHashSet<>();
            ret.add((RiscvPhyReg) ins.getDefReg());
            return ret;
        } else {
            LinkedHashSet<RiscvPhyReg> ret = new LinkedHashSet<>();
            ArrayList<Integer> list =
                    new ArrayList<>(
                            Arrays.asList(1, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 28, 29, 30, 31));
            list.forEach(i -> ret.add(RiscvCPUReg.getRiscvCPUReg(i)));
            ArrayList<Integer> list1 =
                    new ArrayList<>(
                            Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 28, 29,
                                    30, 31));
            list1.forEach(i -> ret.add(RiscvFPUReg.getRiscvFPUReg(i)));
            return ret;
        }
    }

    private void loopPeephole() {
        for (RiscvFunction riscvFunction : riscvModule.getFunctions().values()) {
            // 正序遍历blocks
            // TODO livInfoMap 和 influencedReg 处理
            liveInfoMap = LiveInfo.liveInfoAnalysis(riscvFunction);
            riscvFunc = riscvFunction;
            for (IList.INode<RiscvBlock, RiscvFunction>
                 blockNode = riscvFunction.getBlocks().getHead();
                 blockNode != null; blockNode = blockNode.getNext()) {
                // 消除多余li
                RiscvBlock block = blockNode.getValue();
                for (IList.INode<RiscvInstruction, RiscvBlock>
                     insNode = block.getRiscvInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    RiscvInstruction instr = insNode.getValue();
                    if (instr instanceof RiscvLi && instr.getDefReg() instanceof RiscvVirReg &&
                            !(instr.getOperands().get(0) instanceof RiscvStackFixer)) {
                        RiscvLi li = (RiscvLi) instr;
                        RiscvImm imm = (RiscvImm) li.getOperands().get(0);
                        if (insNode.getNext() != null) {
                            for (IList.INode<RiscvInstruction, RiscvBlock> insNode1 = insNode.getNext();
                                 insNode1 != null; insNode1 = insNode1.getNext()) {
                                instr = insNode1.getValue();
                                if (instr instanceof RiscvLi && instr.getDefReg() instanceof RiscvVirReg
                                        && ((RiscvImm) instr.getOperands().get(0)).getValue() == imm.getValue()) {
                                    int sumOperand = 0;
                                    for (IList.INode<RiscvInstruction, RiscvBlock> useNode : instr.getDefReg().getUsers()) {
                                        for (RiscvOperand rOperand : useNode.getValue().getOperands()) {
                                            if (instr.getDefReg().equals(rOperand)) {
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
//                                    block.dump();
                                }
                            }
                        }
                    }
                }

                // 消除重复的二元运算指令
                for (IList.INode<RiscvInstruction, RiscvBlock> insNode = block.getRiscvInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    RiscvInstruction instr = insNode.getValue();
                    if (instr instanceof RiscvBinary && instr.getDefReg() instanceof RiscvVirReg) {
                        RiscvBinary binary = (RiscvBinary) instr;
                        if (insNode.getNext() != null) {
                            for (IList.INode<RiscvInstruction, RiscvBlock> insNode1 = insNode.getNext();
                                 insNode1 != null; insNode1 = insNode1.getNext()) {
                                instr = insNode1.getValue();
                                if (instr instanceof RiscvBinary
                                        && instr.getDefReg() instanceof RiscvVirReg
                                        && (((RiscvBinary) instr).getInstType()).equals(binary.getInstType())) {
                                    if ((instr.getOperands().get(0).equals(binary.getOperands().get(0))
                                            && instr.getOperands().get(1).equals(binary.getOperands().get(1)))
                                            || (instr.getOperands().get(0).equals(binary.getOperands().get(1))
                                            && instr.getOperands().get(1).equals(binary.getOperands().get(0)))) {
                                        int sumOperand = 0;
                                        for (IList.INode<RiscvInstruction, RiscvBlock> useNode : instr.getDefReg().getUsers()) {
                                            for (RiscvOperand rOperand : useNode.getValue().getOperands()) {
                                                if (instr.getDefReg().equals(rOperand)) {
                                                    sumOperand++;
                                                }
                                            }
                                        }
                                        if (sumOperand > 0) {
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

//                // 乘除法优化
//                for (IList.INode<RiscvInstruction, RiscvBlock> insNode = block.getRiscvInstructions().getHead();
//                     insNode != null; insNode = insNode.getNext()) {
//                    RiscvInstruction instr = insNode.getValue();
//                    if (instr instanceof RiscvBinary
//                            && ((RiscvBinary) instr).getInstType() == RiscvBinary.RiscvBinaryType.mulw) {
//                        RiscvReg defReg = instr.getDefReg();
//                        RiscvOperand operand1 = instr.getOperands().get(0);
//                        RiscvOperand operand2 = instr.getOperands().get(1);
//                        // 不会出现operand都是立即数的情况
//                        // 如果两个operand有且仅有一个常数, 要将常数放进寄存器里相乘
//                        // 这里相当于要做一个简单的乘法优化, 是针对于移位操作的乘法优化: 对于乘一个和2的幂次相关的数, 可以优化为移位操作
//                        // 这算是机器相关优化了
//                        int opConst = 0;
//                        RiscvOperand operand;
//                        if (operand1 instanceof RiscvImm) {
//                            opConst = ((RiscvImm) operand1).getValue();
//                            operand = operand2;
//                        } else if (operand2 instanceof RiscvImm) {
//                            opConst = ((RiscvImm) operand2).getValue();
//                            operand = operand1;
//                        }
//                        RiscvOperand assistReg = null; // TODO ArmOperand opReg = getArmOperand(opValue, armBlock, 0);
//                        // opConst 是0/二进制有且仅有1位1的数
//
//                        // TODO : 关于 '1000_0000' 这种最大负数的处理, 以及乘法越界处理
//                        if ((opConst & (opConst - 1)) == 0) {
//                            mulPowOptimize(armBlock, dst, opReg, opConst);
//                        } else if (((opConst - 1) & (opConst - 2)) == 0) { // opConst为 (2的幂次方 + 1) ---> opConst - 1 为 2的幂次方
//                            // opReg先乘以2的幂次方, 再加1
//                            // -128 + 1 == -127
//                            mulPowOptimize(armBlock, defReg, opReg, opConst - 1);
//                            new ArmBinary(armBlock, ArmBinary.BinaryType.add, defReg, defReg, opReg);
//                        } else if (((opConst + 1) & opConst) == 0) {
//                            // opConst 是 2的幂次方 - 1
//                            mulPowOptimize(armBlock, defReg, opReg, opConst + 1);
//                            new ArmBinary(armBlock, ArmBinary.BinaryType.sub, defReg, defReg, opReg);
//                        } else {
//                            ArmOperand rhs = movImme(opConst, armBlock, null);
//                            new ArmBinary(armBlock, ArmBinary.BinaryType.mul, defReg, opReg, rhs);
//                        }
//
//                    }
//                }
            }
        }
    }

    private void deleteUselessDef() {
        for (RiscvFunction riscvFunction : riscvModule.getFunctions().values()) {
            for (IList.INode<RiscvBlock, RiscvFunction>
                 blockNode = riscvFunction.getBlocks().getHead();
                 blockNode != null; blockNode = blockNode.getNext()) {
                // 消除多余li
                RiscvBlock block = blockNode.getValue();
                for (IList.INode<RiscvInstruction, RiscvBlock>
                     insNode = block.getRiscvInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    RiscvInstruction instr = insNode.getValue();
                    if (instr.getDefReg() != null && instr.getDefReg() instanceof RiscvVirReg
                            && instr.getDefReg().getUsers().size() == 0) {
                        insNode.removeFromList();
                    }
                }
            }
        }
    }

    private void deleteUselesslabel() {
        // TODO 对于Global变量的处理 a[1] = a[0] + 1 改成仅需要取一次全局变量数组a的label
    }

    public void dump() {
        try {
            var out = new BufferedWriter(new FileWriter("rv_peephole.s"));
            out.write(riscvModule.toString());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void DataFlowDump() {
        try {
            var out = new BufferedWriter(new FileWriter("rv_DateFLowPeephole.s"));
            out.write(riscvModule.toString());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean ok2Move(IList.INode<RiscvInstruction, RiscvBlock>
                                   cmpNode, IList.INode<RiscvInstruction, RiscvBlock> brNode,
        RiscvOperand op1, RiscvOperand op2) {
        //在同一个块内，且中间无其他节点 defReg 为cmpNode的Op
        IList.INode<RiscvInstruction, RiscvBlock> insNode = cmpNode;
        while(insNode.getNext() != null) {
            insNode = insNode.getNext();
            if (insNode == brNode) {
                return true;
            } else if (op1 == insNode.getValue().getDefReg()
                    || op2 == insNode.getValue().getDefReg()){
                return false;
            }
        }
        return false;
    }

    public void branchOptimize() {
        for (RiscvFunction riscvFunction : riscvModule.getFunctions().values()) {
            for (IList.INode<RiscvBlock, RiscvFunction>
                 blockNode = riscvFunction.getBlocks().getHead();
                 blockNode != null; blockNode = blockNode.getNext()) {
                // 消除多余li
                RiscvBlock block = blockNode.getValue();
                for (IList.INode<RiscvInstruction, RiscvBlock>
                     insNode = block.getRiscvInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    RiscvInstruction instr = insNode.getValue();
                    if (instr instanceof RiscvBinary &&
                            ((RiscvBinary) instr).getInstType() == RiscvBinary.RiscvBinaryType.slt) {
                        boolean flag = false;
                        for(var userNode: instr.getDefReg().getUsers()) {
                            if (!(userNode.getValue() instanceof RiscvBranch && ok2Move(insNode, userNode,
                                    insNode.getValue().getOperands().get(0), insNode.getValue().getOperands().get(1)))
                                    && userNode!= insNode) {
                                flag = true;
                            }
                        }
                        if (!flag) {
                            ArrayList<IList.INode<RiscvInstruction, RiscvBlock>> add_nodes = new ArrayList<>();
                            ArrayList<IList.INode<RiscvInstruction, RiscvBlock>> del_nodes = new ArrayList<>();
                            for(var userNode: instr.getDefReg().getUsers()) {
                                if (userNode == insNode) {
                                    continue;
                                }
                                RiscvInstruction ins = userNode.getValue();
                                assert ins instanceof RiscvBranch;
                                if (((RiscvBranch) ins).getCmpType() == RiscvBranch.RiscvCmpType.bne) {
                                    RiscvBranch new_branch = new RiscvBranch(instr.getOperands().get(0),
                                            instr.getOperands().get(1), (RiscvLabel) ins.getOperands().get(2), blt);
                                    IList.INode<RiscvInstruction, RiscvBlock> insNode_branch = new IList.INode<>(new_branch);
                                    insNode_branch.insertAfter(userNode);
                                    add_nodes.add(insNode_branch);
                                } else if (((RiscvBranch) ins).getCmpType() == RiscvBranch.RiscvCmpType.beq) {
                                    RiscvBranch new_branch = new RiscvBranch(instr.getOperands().get(0),
                                            instr.getOperands().get(1), (RiscvLabel) ins.getOperands().get(2), bge);
                                    IList.INode<RiscvInstruction, RiscvBlock> insNode_branch = new IList.INode<>(new_branch);
                                    insNode_branch.insertAfter(userNode);
                                    add_nodes.add(insNode_branch);
                                }
                                userNode.removeFromList();
                                del_nodes.add(userNode);
                            }
                            for (var node: add_nodes) {
                                updateBeUsed(node);
                            }
                            for (var node: del_nodes) {
                                deleteBeUsed(node);
                            }
                            deleteBeUsed(insNode);
                            insNode.removeFromList();
                        }
                    } else if (instr instanceof RiscvBinary &&
                            ((RiscvBinary) instr).getInstType() == RiscvBinary.RiscvBinaryType.slti) {
                        assert insNode.getPrev() != null;
                        IList.INode<RiscvInstruction, RiscvBlock> subNode = insNode.getPrev();
                        RiscvOperand op1 = subNode.getValue().getOperands().get(0);
                        RiscvOperand op2 = subNode.getValue().getOperands().get(1);
                        boolean flag = false;
                        for(var userNode: instr.getDefReg().getUsers()) {
                            if (userNode == insNode || userNode == subNode) {
                                continue;
                            }
                            if (!(userNode.getValue() instanceof RiscvBranch
                                    && ok2Move(insNode, userNode, op1, op2))) {
                                System.out.println(userNode.getValue());
                                flag = true;
                            }
                        }
                        if (!flag) {
                            ArrayList<IList.INode<RiscvInstruction, RiscvBlock>> add_nodes = new ArrayList<>();
                            ArrayList<IList.INode<RiscvInstruction, RiscvBlock>> del_nodes = new ArrayList<>();
                            for(var userNode: instr.getDefReg().getUsers()) {
                                if (userNode == insNode || userNode == subNode) {
                                    continue;
                                }
                                RiscvInstruction ins = userNode.getValue();
                                assert ins instanceof RiscvBranch;
                                if (((RiscvBranch) ins).getCmpType() == RiscvBranch.RiscvCmpType.bne) {
                                    RiscvBranch new_branch = new RiscvBranch(op2,
                                            op1, (RiscvLabel) ins.getOperands().get(2), bge);
                                    IList.INode<RiscvInstruction, RiscvBlock> insNode_branch = new IList.INode<>(new_branch);
                                    insNode_branch.insertAfter(userNode);
                                    add_nodes.add(insNode_branch);
                                } else if (((RiscvBranch) ins).getCmpType() == RiscvBranch.RiscvCmpType.beq) {
                                    RiscvBranch new_branch = new RiscvBranch(op2,
                                            op1, (RiscvLabel) ins.getOperands().get(2), blt);
                                    IList.INode<RiscvInstruction, RiscvBlock> insNode_branch = new IList.INode<>(new_branch);
                                    insNode_branch.insertAfter(userNode);
                                    add_nodes.add(insNode_branch);
                                }
                                userNode.removeFromList();
                                del_nodes.add(userNode);
                            }
                            for (var node: add_nodes) {
                                updateBeUsed(node);
                            }
                            for (var node: del_nodes) {
                                deleteBeUsed(node);
                            }
                            deleteBeUsed(insNode);
                            insNode.removeFromList();
                            deleteBeUsed(subNode);
                            subNode.removeFromList();
                        }
                    } else if (instr instanceof RiscvBinary &&
                            ((RiscvBinary) instr).getInstType() == RiscvBinary.RiscvBinaryType.sltiu) {
                        assert insNode.getPrev() != null;
                        IList.INode<RiscvInstruction, RiscvBlock> subNode = insNode.getPrev();
                        RiscvOperand op1 = subNode.getValue().getOperands().get(0);
                        RiscvOperand op2 = subNode.getValue().getOperands().get(1);
                        boolean flag = false;
                        for(var userNode: instr.getDefReg().getUsers()) {
                            if (userNode == insNode || userNode == subNode) {
                                continue;
                            }
                            if (!(userNode.getValue() instanceof RiscvBranch
                                    && ok2Move(insNode, userNode, op1, op2))) {
                                System.out.println(userNode.getValue());
                                flag = true;
                            }
                        }
                        if (!flag) {
                            ArrayList<IList.INode<RiscvInstruction, RiscvBlock>> add_nodes = new ArrayList<>();
                            ArrayList<IList.INode<RiscvInstruction, RiscvBlock>> del_nodes = new ArrayList<>();
                            for(var userNode: instr.getDefReg().getUsers()) {
                                if (userNode == insNode || userNode == subNode) {
                                    continue;
                                }
                                RiscvInstruction ins = userNode.getValue();
                                assert ins instanceof RiscvBranch;
                                if (((RiscvBranch) ins).getCmpType() == RiscvBranch.RiscvCmpType.bne) {
                                    RiscvBranch new_branch = new RiscvBranch(op2,
                                            op1, (RiscvLabel) ins.getOperands().get(2), beq);
                                    IList.INode<RiscvInstruction, RiscvBlock> insNode_branch = new IList.INode<>(new_branch);
                                    insNode_branch.insertAfter(userNode);
                                    add_nodes.add(insNode_branch);
                                } else if (((RiscvBranch) ins).getCmpType() == RiscvBranch.RiscvCmpType.beq) {
                                    RiscvBranch new_branch = new RiscvBranch(op2,
                                            op1, (RiscvLabel) ins.getOperands().get(2), bne);
                                    IList.INode<RiscvInstruction, RiscvBlock> insNode_branch = new IList.INode<>(new_branch);
                                    insNode_branch.insertAfter(userNode);
                                    add_nodes.add(insNode_branch);
                                }
                                userNode.removeFromList();
                                del_nodes.add(userNode);
                            }
                            for (var node: add_nodes) {
                                updateBeUsed(node);
                            }
                            for (var node: del_nodes) {
                                deleteBeUsed(node);
                            }
                            deleteBeUsed(insNode);
                            insNode.removeFromList();
                            deleteBeUsed(subNode);
                            subNode.removeFromList();
                        }
                    } else if (instr instanceof RiscvBinary &&
                            ((RiscvBinary) instr).getInstType() == RiscvBinary.RiscvBinaryType.sltu) {
                        assert insNode.getPrev() != null;
                        IList.INode<RiscvInstruction, RiscvBlock> subNode = insNode.getPrev();
                        RiscvOperand op1 = subNode.getValue().getOperands().get(0);
                        RiscvOperand op2 = subNode.getValue().getOperands().get(1);
                        boolean flag = false;
                        for(var userNode: instr.getDefReg().getUsers()) {
                            if (userNode == insNode || userNode == subNode) {
                                continue;
                            }
                            if (!(userNode.getValue() instanceof RiscvBranch
                                    && ok2Move(insNode, userNode, op1, op2))) {
                                System.out.println(userNode.getValue());
                                flag = true;
                            }
                        }
                        if (!flag) {
                            ArrayList<IList.INode<RiscvInstruction, RiscvBlock>> add_nodes = new ArrayList<>();
                            ArrayList<IList.INode<RiscvInstruction, RiscvBlock>> del_nodes = new ArrayList<>();
                            for(var userNode: instr.getDefReg().getUsers()) {
                                if (userNode == insNode || userNode == subNode) {
                                    continue;
                                }
                                RiscvInstruction ins = userNode.getValue();
                                assert ins instanceof RiscvBranch;
                                if (((RiscvBranch) ins).getCmpType() == RiscvBranch.RiscvCmpType.bne) {
                                    RiscvBranch new_branch = new RiscvBranch(op2,
                                            op1, (RiscvLabel) ins.getOperands().get(2), bne);
                                    IList.INode<RiscvInstruction, RiscvBlock> insNode_branch = new IList.INode<>(new_branch);
                                    insNode_branch.insertAfter(userNode);
                                    add_nodes.add(insNode_branch);
                                } else if (((RiscvBranch) ins).getCmpType() == RiscvBranch.RiscvCmpType.beq) {
                                    RiscvBranch new_branch = new RiscvBranch(op2,
                                            op1, (RiscvLabel) ins.getOperands().get(2), beq);
                                    IList.INode<RiscvInstruction, RiscvBlock> insNode_branch = new IList.INode<>(new_branch);
                                    insNode_branch.insertAfter(userNode);
                                    add_nodes.add(insNode_branch);
                                }
                                userNode.removeFromList();
                                del_nodes.add(userNode);
                            }
                            for (var node: add_nodes) {
                                updateBeUsed(node);
                            }
                            for (var node: del_nodes) {
                                deleteBeUsed(node);
                            }
                            deleteBeUsed(insNode);
                            insNode.removeFromList();
                            deleteBeUsed(subNode);
                            subNode.removeFromList();
                        }
                    }
                }
            }
        }
    }
}
