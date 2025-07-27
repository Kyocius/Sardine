package Backend.Riscv.Process;
import Backend.Riscv.Component.RiscvBlock;
import Backend.Riscv.Component.RiscvFunction;
import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Component.RiscvModule;
import Backend.Riscv.Instruction.*;
import Backend.Riscv.Operand.*;
import Backend.Riscv.Optimization.LiveInfo;
import IR.Value.Argument;
import Utils.DataStruct.IList;
import Utils.DataStruct.Pair;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RegAllocator {
    private RiscvModule riscvModule;
    private RiscvVirReg.RegType currentType;
    private LinkedHashMap<RiscvBlock, LiveInfo> liveInfoMap;

    private LinkedHashSet<RiscvOperand> simplifyWorklist; // 低度数的传送无关的节点表
    private LinkedHashSet<RiscvOperand> freezeWorklist; // 低度数的传送有关的节点表
    private LinkedHashSet<RiscvOperand> spillWorklist; // 高度数的结点表
    private LinkedHashSet<RiscvOperand> spilledNodes; // 本轮要溢出的结点，初始为空
    private LinkedHashSet<RiscvOperand> coalescedNodes; // 已合并的寄存器集合
    private LinkedHashSet<RiscvReg> coloredNodes; // 已成功着色的节点集合
    private Stack<RiscvOperand> selectStack; // 一个包含从图中删除的临时变量的栈

    private LinkedHashSet<RiscvInstruction> coalescedMoves; // 已经合并的传送指令集合
    private LinkedHashSet<RiscvInstruction> constrainedMoves; // 源操作数和目标操作数冲突的传送指令集合
    private LinkedHashSet<RiscvInstruction> frozenMoves; // 不再考虑合并的传送指令集合
    private LinkedHashSet<RiscvInstruction> worklistMoves; // 有可能合并的传送指令集合
    private LinkedHashSet<RiscvInstruction> activeMoves; // 还未做好合并准备的传送指令集合

    private LinkedHashSet<Pair<RiscvReg, RiscvReg>> adjSet; // 图中冲突边（u，v）的集合
    private LinkedHashMap<RiscvOperand, LinkedHashSet<RiscvOperand>> adjList; // 图的领接表表示
    private LinkedHashMap<RiscvOperand, Integer> degree; // 包含每个结点当前度数的数组
    private LinkedHashMap<RiscvOperand, LinkedHashSet<RiscvMv>> moveList; // 从一个结点到与该结点相关的传送指令表的映射
    private LinkedHashMap<RiscvOperand, RiscvOperand> alias; // 传送指令（u，v）合并，且v放入coalescedNodes后，alias(v) = u
    private LinkedHashMap<RiscvReg, Integer> color; // 算法为结点选择的颜色
    private LinkedHashMap<RiscvVirReg, LinkedHashSet<RiscvReg>> crossConflicts;

    private final int INF = 0x7fffffff;
    private final int K = 27; // 不动zero,ra,sp,gp,tp,因此27种

    public RegAllocator(RiscvModule riscvModule) {
        this.riscvModule = riscvModule;
    }

    public void run() {
        for (RiscvFunction riscvFunction : riscvModule.getFunctions().values()) {
            if (!riscvFunction.getBlocks().isEmpty()) {
                currentType = null;
                while (true) {
//                    System.out.println("while1");
                    if (currentType == null) {
                        currentType = RiscvVirReg.RegType.floatType;
                    } else if (currentType == RiscvVirReg.RegType.floatType) {
                        currentType = RiscvVirReg.RegType.intType;
                    } else {
                        break;
                    }
                    if (!riscvFunction.getName().equals("main")) {
                        IList.INode<RiscvInstruction, RiscvBlock> firstNode = riscvFunction.getBlocks().getHead().getValue()
                                .getRiscvInstructions().getHead();
                        ArrayList<IList.INode<RiscvInstruction, RiscvBlock>> tailNodes = new ArrayList<>();
                        for (RiscvBlock tailBlock : riscvFunction.getRetBlocks()) {
                            for (IList.INode<RiscvInstruction, RiscvBlock>
                                 insNode = tailBlock.getRiscvInstructions().getTail();
                                 insNode != null; insNode = insNode.getPrev()) {
                                if (insNode.getValue() instanceof RiscvJr) {
                                    tailNodes.add(insNode);
                                }
                            }
                        }
                        genProtectionMove(firstNode, tailNodes, riscvFunction);
                    }
                    boolean hasTailCall =true;
                    while (hasTailCall) {
                        initialize(riscvFunction);
                        hasTailCall = false;
                        buildGraph(riscvFunction);
                        makeWorkList(riscvFunction);
                        // System.out.printf("节点数%d\n", adjSet.size() / 2);
                        do {
//                            System.out.println("while2");
//                            System.out.println(simplifyWorklist);
//                            System.out.println(worklistMoves);
//                            System.out.println(freezeWorklist);
//                            System.out.println(spillWorklist);
                            if (!simplifyWorklist.isEmpty()) {
                                simplify();
                            } else if (!worklistMoves.isEmpty()) {
                                coalesce();
                            } else if (!freezeWorklist.isEmpty()) {
                                freeze();
                            } else if (!spillWorklist.isEmpty()) {
                                selectSpill();
                            }
                        } while(!(simplifyWorklist.isEmpty() && worklistMoves.isEmpty()
                                && freezeWorklist.isEmpty() && spillWorklist.isEmpty()));
                        assignColor();
//                        System.out.println("Before rewrite " + spilledNodes);
                        if (!spilledNodes.isEmpty()) {
//                            System.out.println("Before rewrite " + riscvFunction.dump());
                            rewriteProgram(riscvFunction);
//                            System.out.println("After rewrite " + riscvFunction.dump());
                            hasTailCall = true;
                        }
//                        System.out.println("After rewrite " +spilledNodes);
                    }

                    for (IList.INode<RiscvBlock, RiscvFunction>
                         blockNode = riscvFunction.getBlocks().getHead();
                         blockNode != null; blockNode = blockNode.getNext()) {
                        RiscvBlock riscvBlock = blockNode.getValue();
                        for (IList.INode<RiscvInstruction, RiscvBlock>
                             riscvInstructionNode = riscvBlock.getRiscvInstructions().getTail();
                             riscvInstructionNode != null;
                             riscvInstructionNode = riscvInstructionNode.getPrev()) {
                            RiscvInstruction riscvInstruction = riscvInstructionNode.getValue();
                            if (riscvInstruction.getDefReg() instanceof RiscvVirReg
                                    && color.containsKey(riscvInstruction.getDefReg()) ) {
//                                    && currentType == ((RiscvVirReg) riscvInstruction.getDefReg()).regType) {
                                if (((RiscvVirReg) riscvInstruction.getDefReg()).regType == RiscvVirReg.RegType.intType
                                    && currentType == RiscvVirReg.RegType.intType) {
                                    riscvInstruction.replaceDefReg(
                                            RiscvCPUReg.getRiscvCPUReg(color.get(riscvInstruction.getDefReg()))
                                            , riscvInstructionNode);
                                } else if (((RiscvVirReg) riscvInstruction.getDefReg()).regType == RiscvVirReg.RegType.floatType
                                    && currentType == RiscvVirReg.RegType.floatType) {
                                    riscvInstruction.replaceDefReg(
                                            RiscvFPUReg.getRiscvFPUReg(color.get(riscvInstruction.getDefReg()))
                                            , riscvInstructionNode);
                                }
                            }

//                            System.out.println(riscvFunction.getName());
//                            System.out.println(riscvBlock.getName());
//                            System.out.println(riscvInstruction);

                            LinkedHashMap<RiscvOperand, RiscvOperand> replace = new LinkedHashMap<>();
                            for (RiscvOperand riscvOperand: riscvInstruction.getOperands()) {
                                if (riscvOperand instanceof RiscvVirReg && color.containsKey(riscvOperand) ) {
//                                        && currentType == ((RiscvVirReg) riscvOperand).regType) {
                                    if (((RiscvVirReg) riscvOperand).regType == RiscvVirReg.RegType.intType
                                        && currentType == RiscvVirReg.RegType.intType) {
//                                        System.out.println(riscvOperand);
                                        replace.put(riscvOperand, RiscvCPUReg.getRiscvCPUReg(color.get(riscvOperand)));
                                    } else if (((RiscvVirReg) riscvOperand).regType == RiscvVirReg.RegType.floatType
                                        && currentType == RiscvVirReg.RegType.floatType) {
                                        replace.put(riscvOperand, RiscvFPUReg.getRiscvFPUReg(color.get(riscvOperand)));
                                    }
                                }
                            }
                            for (RiscvOperand key : replace.keySet()) {
                                riscvInstruction.replaceOperands((RiscvReg) key,
                                        (RiscvReg) replace.get(key), riscvInstructionNode);
                            }
                        }
                    }
                }
            }
        }
    }

    public void genProtectionMove(IList.INode<RiscvInstruction, RiscvBlock> headNode,
                                  ArrayList<IList.INode<RiscvInstruction, RiscvBlock>> tailNodes,
                                  RiscvFunction riscvFunction) {
        ArrayList<Integer> list = new ArrayList<>(Arrays.asList(8, 9, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27));
        for (int index: list) {
            IList.INode<RiscvInstruction, RiscvBlock> newInstrNode;
            if (currentType == RiscvVirReg.RegType.intType) {
                RiscvVirReg virReg = new RiscvVirReg(riscvFunction.updateVirRegIndex(),
                        RiscvVirReg.RegType.intType, riscvFunction);
                newInstrNode = new IList.INode<>(new RiscvMv(RiscvCPUReg.getRiscvCPUReg(index), virReg));
                newInstrNode.insertBefore(headNode);
                updateBeUsed(newInstrNode);
                for (IList.INode<RiscvInstruction, RiscvBlock> tailNode: tailNodes) {
                    newInstrNode = new IList.INode<>(new RiscvMv(virReg, RiscvCPUReg.getRiscvCPUReg(index)));
                    newInstrNode.insertBefore(tailNode);
                    updateBeUsed(newInstrNode);
                }
            } else {
                RiscvVirReg virReg = new RiscvVirReg(riscvFunction.updateVirRegIndex(),
                        RiscvVirReg.RegType.floatType, riscvFunction);
                newInstrNode = new IList.INode<>(new RiscvFmv(RiscvFPUReg.getRiscvFPUReg(index), virReg));
                newInstrNode.insertBefore(headNode);
                updateBeUsed(newInstrNode);
                for (IList.INode<RiscvInstruction, RiscvBlock> tailNode: tailNodes) {
                    newInstrNode = new IList.INode<>(new RiscvFmv(virReg, RiscvFPUReg.getRiscvFPUReg(index)));
                    newInstrNode.insertBefore(tailNode);
                    updateBeUsed(newInstrNode);
                }
            }
        }

    }

    public void initialize(RiscvFunction riscvFunction) {
        liveInfoMap = LiveInfo.liveInfoAnalysis(riscvFunction);

        simplifyWorklist = new LinkedHashSet<>();
        freezeWorklist = new LinkedHashSet<>();
        spillWorklist = new LinkedHashSet<>();
        spilledNodes = new LinkedHashSet<>();
        coalescedNodes = new LinkedHashSet<>();
        coloredNodes = new LinkedHashSet<>();
        selectStack = new Stack<>();

        coalescedMoves = new LinkedHashSet<>();
        constrainedMoves = new LinkedHashSet<>();
        frozenMoves = new LinkedHashSet<>();
        worklistMoves = new LinkedHashSet<>();
        activeMoves = new LinkedHashSet<>();

        adjSet = new LinkedHashSet<>();
        adjList = new LinkedHashMap<>();
        degree = new LinkedHashMap<>();
        moveList = new LinkedHashMap<>();
        alias = new LinkedHashMap<>();
        color = new LinkedHashMap<>();

        coloredNodes.addAll(RiscvCPUReg.getAllCPURegs().values());
        coloredNodes.addAll(RiscvFPUReg.getAllFPURegs().values());
        RiscvCPUReg.getRiscvCPUReg(0);
        RiscvFPUReg.getRiscvFPUReg(0);
        RiscvCPUReg.getAllCPURegs().values().forEach(reg -> {
            color.put(reg, reg.getIndex());
            degree.put(reg, INF);
        });
        RiscvFPUReg.getAllFPURegs().values().forEach(reg -> {
            color.put(reg, reg.getIndex());
            degree.put(reg, INF);
        });
    }

    public void buildGraph(RiscvFunction riscvFunction) {
        boolean isFirstBlock = true;
        for (IList.INode<RiscvBlock, RiscvFunction> riscvBlockNode: riscvFunction.getBlocks()) {
            RiscvBlock riscvBlock = riscvBlockNode.getValue();
            LinkedHashSet<RiscvReg> liveOut = liveInfoMap.get(riscvBlock).getLiveOut();
            // 由于在逆序遍历指令的过程中，从块的LiveOut，逐条指令计算该指令的 LiveOut， 遍历到该指令时，里面装的就是该指令的 LiveOut
            for (IList.INode<RiscvInstruction, RiscvBlock>
                 riscvInstructionNode = riscvBlock.getRiscvInstructions().getTail();
                 riscvInstructionNode != null;
                 riscvInstructionNode = riscvInstructionNode.getPrev()) {
                RiscvInstruction riscvInstruction = riscvInstructionNode.getValue();
                if (riscvInstruction instanceof RiscvMv) {
                    liveOut.remove(riscvInstruction.getOperands().get(0));
                    if (!moveList.containsKey(riscvInstruction.getOperands().get(0))) {
                        moveList.put(riscvInstruction.getOperands().get(0), new LinkedHashSet<>());
                    }
                    if (!moveList.containsKey(riscvInstruction.getDefReg())) {
                        moveList.put(riscvInstruction.getDefReg(), new LinkedHashSet<>());
                    }
                    moveList.get(riscvInstruction.getOperands().get(0)).add((RiscvMv) riscvInstruction);
                    moveList.get(riscvInstruction.getDefReg()).add((RiscvMv) riscvInstruction);
//                    System.out.println("build-worklist" + riscvInstruction);
                    worklistMoves.add(riscvInstruction);
                }

//                System.out.println(riscvFunction.getName());
//                System.out.println(riscvBlock.dump());
//                System.out.println(riscvInstruction);
                //TODO 如果defReg能有多个 这里需要修改
                ArrayList<RiscvReg> defRegs = new ArrayList<>();
                if (riscvInstruction.getDefReg() != null) {
                    defRegs.add(riscvInstruction.getDefReg());
                    if (riscvInstruction instanceof RiscvJal) {
                        defRegs.addAll(getCallDefs());
                    } // 由于没有定义Call，所以这里用Jal代替
                    liveOut.addAll(defRegs);
                    for (RiscvReg defReg: defRegs) {
                        for (RiscvReg liveReg: liveOut) {
                            //System.out.println("conflict " + defReg + " " + liveReg);
                            addEdge(liveReg, defReg);
                        }
                    }
//                    if (!(riscvInstruction instanceof RiscvMv)) {
//                        liveOut.removeAll(defRegs);
//                    } else if (!(isFirstBlock
//                            && riscvInstruction.getDefReg() instanceof RiscvPhyReg)) {
//                        liveOut.removeAll(defRegs);
//                    }
                    liveOut.removeAll(defRegs);
                }
                for (RiscvOperand riscvOperand : riscvInstruction.getOperands()) {
                    if (riscvOperand instanceof RiscvReg) {
                        liveOut.add((RiscvReg) riscvOperand);
                    }
                }
                if (riscvInstruction instanceof RiscvJal) {
                    liveOut.addAll(((RiscvJal) riscvInstruction).getUsedRegs());
                } // 由于没有定义Call，所以这里用Jal代替
            }
            isFirstBlock = false;
        }
    }

    public void makeWorkList(RiscvFunction riscvFunction) {
        for (IList.INode<RiscvBlock, RiscvFunction> riscvBlockNode: riscvFunction.getBlocks()) {
            RiscvBlock riscvBlock = riscvBlockNode.getValue();
            for (IList.INode<RiscvInstruction, RiscvBlock>
                 riscvInstructionNode = riscvBlock.getRiscvInstructions().getTail();
                 riscvInstructionNode != null;
                 riscvInstructionNode = riscvInstructionNode.getPrev()) {
                RiscvInstruction riscvInstruction = riscvInstructionNode.getValue();
                if (riscvInstruction instanceof RiscvMv) {
                    if (!adjList.containsKey(riscvInstruction.getDefReg())) {
                        adjList.put(riscvInstruction.getDefReg(), new LinkedHashSet<>());
                    }
                    if (adjList.get(riscvInstruction.getDefReg()).contains(riscvInstruction.getOperands().get(0))) {
                        worklistMoves.remove(riscvInstruction);
                        constrainedMoves.add(riscvInstruction);
                    }
                }
            }
        }

        for (RiscvOperand n: riscvFunction.getAllVirRegUsed()) {
//            System.out.println("----------------------------------");
            assert n instanceof RiscvVirReg;
            if (((RiscvVirReg) n).regType == currentType) {
                if (degree.getOrDefault(n, 0) >= K) {
                    spillWorklist.add(n);
                } else if (moveRelated(n)) {
                    freezeWorklist.add(n);
                } else {
                    simplifyWorklist.add(n);
                }
            }
        }
    }

    public void simplify() {
        RiscvOperand n = simplifyWorklist.iterator().next();
        simplifyWorklist.remove(n);
//        System.out.println("selectStack: " + n);
        selectStack.push(n);
        for (RiscvOperand m : adjacent((RiscvReg) n)) {
            decrementDegree(m);
        }
    }

    public void coalesce() {
        RiscvInstruction m = worklistMoves.iterator().next();
        var x = getAlias(m.getDefReg());
        var y = getAlias((RiscvReg) m.getOperands().get(0));
        Pair<RiscvReg, RiscvReg> u_v;
        if (y.isPreColored()) {
            u_v = new Pair<>(y, x);
        } else {
            u_v = new Pair<>(x, y);
        }
        worklistMoves.remove(m);
        if (u_v.getFirst() == u_v.getSecond()) {
            coalescedMoves.add(m);
            addWorkList(u_v.getFirst());
        } else if (u_v.getSecond().isPreColored() || adjSet.contains(u_v)) {
            constrainedMoves.add(m);
            addWorkList(u_v.getFirst());
            addWorkList(u_v.getSecond());
        } else {
            boolean flag = true;
            if (u_v.getFirst().isPreColored()) {
                for (RiscvOperand t : adjacent(u_v.getSecond())) {
                    flag = flag && OK((RiscvReg) t, u_v.getFirst());
                }
            } else {
                LinkedHashSet<RiscvOperand> computedNodes = new LinkedHashSet<>();
                computedNodes.addAll(adjacent(u_v.getFirst()));
                computedNodes.addAll(adjacent(u_v.getSecond()));
                flag = conservative(computedNodes);
            }
            if (flag) {
                coalescedMoves.add(m);
                combine(u_v.getFirst(), u_v.getSecond());
                addWorkList(u_v.getFirst());
            } else {
                activeMoves.add(m);
            }
        }
    }

    public void freeze() {
        RiscvOperand u = freezeWorklist.iterator().next();
        freezeWorklist.remove(u);
        simplifyWorklist.add(u);
        freezeMoves((RiscvReg) u);
    }

    public void selectSpill() {
        RiscvReg m = null;//启发式算法，o（n）内得到选择的溢出寄存器
        boolean first = true;
        for (RiscvOperand waitSpill : spillWorklist) {
            RiscvReg vcmp = (RiscvReg) waitSpill;
            if (first) {
                m = vcmp;
                first = false;
            } else if (getprioForSpill(vcmp) < getprioForSpill(m)) {
                m = vcmp;
            }
        }
        spillWorklist.remove(m);
        simplifyWorklist.add(m);
        freezeMoves(m);
    }

    public void assignColor() {
//        System.out.println(selectStack);
        while (!selectStack.isEmpty()) {
            RiscvOperand n = selectStack.pop();
            LinkedHashSet<Integer> okColors = initOkColors();
            if (!adjList.containsKey(n)) {
                adjList.put(n, new LinkedHashSet<>());
            }
            for (RiscvOperand w : adjList.get(n)) {
                if (getAlias((RiscvReg) w).isPreColored() ||
                        coloredNodes.contains(getAlias((RiscvReg) w))) {
                    okColors.remove(color.get(getAlias((RiscvReg) w)));
                }
            }
            if (okColors.isEmpty()) {
                spilledNodes.add(n);
            } else {
                coloredNodes.add((RiscvReg) n);
                Integer c = okColors.iterator().next();
                // System.out.println("Color1: " + n + ' ' + c);
                color.put((RiscvReg) n, c);
            }
        }
        for (RiscvOperand n : coalescedNodes) {
//            System.out.println("Color2: " + n + ' ' + color.get(getAlias((RiscvReg) n)));
//            if (color.get(getAlias((RiscvReg) n)) == null) {
//                System.out.println(n);
//                System.out.println(getAlias((RiscvReg) n));
//            }
            color.put((RiscvReg) n, color.get(getAlias((RiscvReg) n)));
        }
    }

    public void rewriteProgram(RiscvFunction riscvFunction) {
        for (RiscvOperand spilledNode: spilledNodes) {
            assert spilledNode instanceof RiscvVirReg;
            riscvFunction.alloc((RiscvVirReg) spilledNode);
            ArrayList<IList.INode<RiscvInstruction, RiscvBlock>> users =
                    new ArrayList<>(spilledNode.getUsers());
            for (IList.INode<RiscvInstruction, RiscvBlock> user : users) {
                RiscvInstruction riscvInstruction = user.getValue();
                if (riscvInstruction.getDefReg() != null && riscvInstruction.getDefReg().equals(spilledNode)) {
                    RiscvVirReg newDefReg =
                            new RiscvVirReg(riscvFunction.updateVirRegIndex(),
                                    ((RiscvVirReg) spilledNode).regType, riscvFunction);
                    riscvFunction.reMap(newDefReg, (RiscvVirReg) spilledNode);
                    int offset = riscvFunction.getOffset(newDefReg);
                    if (offset > -2048 && offset <= 2048) {
                        IList.INode<RiscvInstruction, RiscvBlock> sdNode;
                        if (((RiscvVirReg) spilledNode).regType == RiscvVirReg.RegType.floatType) {
                            sdNode = new IList.INode<>
                                    (new RiscvFsd(newDefReg, new RiscvImm(-1*offset), RiscvCPUReg.getRiscvSpReg()));
                        } else {
                            sdNode = new IList.INode<>
                                    (new RiscvSd(newDefReg, new RiscvImm(-1*offset), RiscvCPUReg.getRiscvSpReg()));
                        }
                        newDefReg.beUsed(sdNode);
                        sdNode.insertAfter(user);
                        updateBeUsed(sdNode);
                        riscvInstruction.replaceDefReg(newDefReg, user);
                        riscvFunction.replaceVirReg(newDefReg, (RiscvVirReg) spilledNode);
                    } else {
                        RiscvVirReg assistReg =
                                new RiscvVirReg(riscvFunction.updateVirRegIndex(),
                                        RiscvVirReg.RegType.intType, riscvFunction);
                        IList.INode<RiscvInstruction, RiscvBlock> biNode = new IList.INode<>
                                (new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                                        RiscvCPUReg.getRiscvSpReg())), assistReg,
                                        RiscvBinary.RiscvBinaryType.add));
                        IList.INode<RiscvInstruction, RiscvBlock> sdNode;
                        if (((RiscvVirReg) spilledNode).regType == RiscvVirReg.RegType.floatType) {
                            sdNode = new IList.INode<>(new RiscvFsd(newDefReg, new RiscvImm(0), assistReg));
                        } else {
                            sdNode = new IList.INode<>(new RiscvSd(newDefReg, new RiscvImm(0), assistReg));
                        }
                        IList.INode<RiscvInstruction, RiscvBlock> liNode = new IList.INode<>
                                (new RiscvLi(new RiscvImm(offset * (-1)), assistReg));
                        assistReg.beUsed(biNode);
                        assistReg.beUsed(sdNode);
                        sdNode.insertAfter(user);
                        updateBeUsed(sdNode);
                        biNode.insertAfter(user);
                        updateBeUsed(biNode);
                        liNode.insertAfter(user);
                        updateBeUsed(liNode);
                        riscvInstruction.replaceDefReg(newDefReg, user);
                        riscvFunction.replaceVirReg(newDefReg, (RiscvVirReg) spilledNode);
                    }
                }

                // 用于处理连续用到两次
                for (RiscvOperand riscvOperand: riscvInstruction.getOperands()) {
                    if (riscvOperand.equals(spilledNode)) {
                        RiscvVirReg newDefReg =
                                new RiscvVirReg(riscvFunction.updateVirRegIndex(),
                                        ((RiscvVirReg) spilledNode).regType, riscvFunction);
                        riscvFunction.reMap(newDefReg, (RiscvVirReg) spilledNode);
                        int offset = riscvFunction.getOffset(newDefReg);
                        if (((RiscvVirReg) spilledNode).regType == RiscvVirReg.RegType.intType) {
                            if (offset > -2048 && offset <= 2048) {
                                IList.INode<RiscvInstruction, RiscvBlock> ldNode =
                                        new IList.INode<>(new RiscvLd(new RiscvImm(-1*offset),
                                                RiscvCPUReg.getRiscvSpReg(), newDefReg));
                                newDefReg.beUsed(ldNode);
                                ldNode.insertBefore(user);
                                updateBeUsed(ldNode);
                                riscvInstruction.replaceOperands((RiscvReg)spilledNode, newDefReg, ldNode);
                            } else {
                                IList.INode<RiscvInstruction, RiscvBlock> liNode = new IList.INode<>
                                        (new RiscvLi(new RiscvImm(offset * (-1)), newDefReg));
                                IList.INode<RiscvInstruction, RiscvBlock> biNode = new IList.INode<>
                                        (new RiscvBinary(new ArrayList<>(Arrays.asList(newDefReg,
                                                RiscvCPUReg.getRiscvSpReg())), newDefReg,
                                                RiscvBinary.RiscvBinaryType.add));
                                IList.INode<RiscvInstruction, RiscvBlock> ldNode =
                                        new IList.INode<>(new RiscvLd(new RiscvImm(0), newDefReg, newDefReg));
                                newDefReg.beUsed(biNode);
                                newDefReg.beUsed(ldNode);
                                liNode.insertBefore(user);
                                updateBeUsed(liNode);
                                biNode.insertBefore(user);
                                updateBeUsed(biNode);
                                ldNode.insertBefore(user);
                                updateBeUsed(ldNode);
                                riscvInstruction.replaceOperands((RiscvReg)spilledNode, newDefReg, user);
                            }
                        } else {
                            if (offset > -2048 && offset <= 2048) {
                                IList.INode<RiscvInstruction, RiscvBlock> FldNode =
                                        new IList.INode<>(new RiscvFld(new RiscvImm(-1*offset),
                                                RiscvCPUReg.getRiscvSpReg(), newDefReg));
                                newDefReg.beUsed(FldNode);
//                                System.out.println(spilledNode);
//                                System.out.println(user.getValue());
                                FldNode.insertBefore(user);
                                updateBeUsed(FldNode);
                                riscvInstruction.replaceOperands((RiscvReg)spilledNode, newDefReg, FldNode);
                            } else {
                                RiscvVirReg assistReg =
                                        new RiscvVirReg(riscvFunction.updateVirRegIndex(),
                                                RiscvVirReg.RegType.intType, riscvFunction);
                                IList.INode<RiscvInstruction, RiscvBlock> liNode = new IList.INode<>
                                        (new RiscvLi(new RiscvImm(offset * (-1)), assistReg));
                                IList.INode<RiscvInstruction, RiscvBlock> biNode = new IList.INode<>
                                        (new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                                                RiscvCPUReg.getRiscvSpReg())), assistReg,
                                                RiscvBinary.RiscvBinaryType.add));
                                IList.INode<RiscvInstruction, RiscvBlock> ldNode =
                                        new IList.INode<>(new RiscvFld(new RiscvImm(0), assistReg, newDefReg));
                                assistReg.beUsed(biNode);
                                assistReg.beUsed(ldNode);
                                liNode.insertBefore(user);
                                updateBeUsed(liNode);
                                biNode.insertBefore(user);
                                updateBeUsed(biNode);
                                ldNode.insertBefore(user);
                                updateBeUsed(ldNode);
                                riscvInstruction.replaceOperands((RiscvReg)spilledNode, newDefReg, user);
                            }
                        }
                        riscvInstruction.replaceOperands((RiscvReg) spilledNode, newDefReg, user);
                    }
                }
            }
            spilledNode.getUsers().clear();
        }
    }

    public ArrayList<RiscvPhyReg> getCallDefs() {
        if (currentType == RiscvVirReg.RegType.intType) {
            ArrayList<Integer> list_int = new ArrayList<>(
                    Arrays.asList(1, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 28, 29, 30, 31));
            ArrayList<Integer> list_float = new ArrayList<>(
                    Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 28, 29, 30, 31));
            ArrayList<RiscvPhyReg> ret = new ArrayList<>();
            list_float.forEach(i -> ret.add(RiscvFPUReg.getRiscvFPUReg(i)));
            list_int.forEach(i -> ret.add(RiscvCPUReg.getRiscvCPUReg(i)));
            return ret;
        } else {
            ArrayList<Integer> list_float = new ArrayList<>(
                    Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 28, 29, 30, 31));
            ArrayList<RiscvPhyReg> ret = new ArrayList<>();
            list_float.forEach(i -> ret.add(RiscvFPUReg.getRiscvFPUReg(i)));
            return ret;
        }
    }

    public void addEdge(RiscvReg u, RiscvReg v) {
        // 确保u v是同一类型的寄存器（float或者int）
        boolean uIsIntType = (u instanceof RiscvCPUReg)
                || (u instanceof RiscvVirReg && ((RiscvVirReg) u).regType == RiscvVirReg.RegType.intType);
        boolean vIsIntType = (v instanceof RiscvCPUReg)
                || (v instanceof RiscvVirReg && ((RiscvVirReg) v).regType == RiscvVirReg.RegType.intType);
        boolean isSameType = uIsIntType == vIsIntType;
        boolean currentIsIntType = currentType == RiscvVirReg.RegType.intType;
        if (isSameType && !adjSet.contains(new Pair<>(u, v)) && u != v) {
            if (!((uIsIntType && currentType == RiscvVirReg.RegType.intType) ||
                    (!uIsIntType) && currentType == RiscvVirReg.RegType.floatType)) {
                return;
            }
            adjSet.add(new Pair<>(u, v));
            adjSet.add(new Pair<>(v, u));
            if (!u.isPreColored()) {
                if (!adjList.containsKey(u)) {
                    adjList.put(u, new LinkedHashSet<>());
                }
                adjList.get(u).add(v);
                if (!degree.containsKey(u)) {
                    degree.put(u, 1);
                } else {
                    degree.put(u, degree.get(u) + 1);
                }
            }
            if (!v.isPreColored()) {
                if (!adjList.containsKey(v)) {
                    adjList.put(v, new LinkedHashSet<>());
                }
                adjList.get(v).add(u);
                if (!degree.containsKey(v)) {
                    degree.put(v, 1);
                } else {
                    degree.put(v, degree.get(v) + 1);
                }
            }
        }
//        else if (!isSameType && currentIsIntType) {
//            // v是int，u不是，且v是虚拟寄存器，则记录
//            if (vIsIntType && v instanceof RiscvVirReg) {
//                assert u instanceof RiscvFPUReg;
//                if (!crossConflicts.containsKey(v)) {
//                    crossConflicts.put((RiscvVirReg) v, new LinkedHashSet<>());
//                }
//                crossConflicts.get(v).add(u);
//            } else if (uIsIntType && u instanceof RiscvVirReg) {
//                assert v instanceof RiscvFPUReg;
//                if (!crossConflicts.containsKey(u)) {
//                    crossConflicts.put((RiscvVirReg) u, new LinkedHashSet<>());
//                }
//                crossConflicts.get(u).add(v);
//            }
//        }
    }

    public LinkedHashSet<RiscvInstruction> nodeMoves(RiscvOperand reg) {
        LinkedHashSet<RiscvInstruction> result;
        if (moveList.containsKey(reg)) {
            result = new LinkedHashSet<>(moveList.get(reg));
        } else {
            result = new LinkedHashSet<>();
        }
        LinkedHashSet<RiscvInstruction> tmpset = new LinkedHashSet<>(activeMoves);
        tmpset.addAll(worklistMoves);
        result.retainAll(tmpset);
        return result;
    }
    public boolean moveRelated(RiscvOperand riscvOperand) {
        return !nodeMoves(riscvOperand).isEmpty();
    }

    public LinkedHashSet<RiscvOperand> adjacent(RiscvOperand riscvOperand) {
        LinkedHashSet<RiscvOperand> result = new LinkedHashSet<>();
        if (adjList.containsKey(riscvOperand)) {
            result.addAll(adjList.get(riscvOperand));
        }
        selectStack.forEach(result::remove);
        result.removeAll(coalescedNodes);
        return result;
    }

    public void decrementDegree(RiscvOperand m) {
        degree.put(m, degree.get(m)-1);
        if (degree.get(m) == K - 1) {
            adjacent(m).add(m);
            spillWorklist.remove(m);
            if (moveRelated(m)) {
                freezeWorklist.add(m);
            } else {
                simplifyWorklist.add(m);
            }
        }
    }
    public RiscvReg getAlias(RiscvReg n) {
        if (coalescedNodes.contains(n)) {
            return getAlias((RiscvReg) alias.get(n));
        } else {
            return n;
        }
    }

    public void addWorkList(RiscvReg u) {
//        System.out.println(u);
//        System.out.println("precolor: " + !u.isPreColored());
//        System.out.println("move: " + !moveRelated(u));
//        System.out.println("degree: " + (degree.getOrDefault(u, 0) < K));
        if (!u.isPreColored() && !moveRelated(u) && (degree.getOrDefault(u, 0) < K)) {
            freezeWorklist.remove(u);
            simplifyWorklist.add(u);
        }
    }

    public boolean OK(RiscvReg t, RiscvReg r) {
        return degree.getOrDefault(t, 0) < K || t.isPreColored() ||
                adjSet.contains(new Pair<>(t, r));
    }

    public boolean conservative(LinkedHashSet<RiscvOperand> nodes) {
        int k = 0;
        for (RiscvOperand n : nodes) {
            if (degree.getOrDefault(n, 0) >= K) {
                k += 1;
            }
            if (k == K) {
                return false;
            }
        }
        return true;
    }

    public void combine(RiscvReg u, RiscvReg v) {
        if (freezeWorklist.contains(v)) {
            freezeWorklist.remove(v);
        } else {
            spillWorklist.remove(v);
        }
        coalescedNodes.add(v);
        // System.out.println("alias" + v + ' ' + u);
        alias.put(v, u);
        moveList.get(u).addAll(moveList.get(v));
        enableMoves(new LinkedHashSet<>(Collections.singletonList(v)));
        for (RiscvOperand t : adjList.getOrDefault(v, new LinkedHashSet<>())) {
            addEdge((RiscvReg) t, u);
            decrementDegree(t);
        }
        if (degree.getOrDefault(u, 0) >= K && freezeWorklist.contains(u)) {
            freezeWorklist.remove(u);
            spillWorklist.add(u);
        }
    }

    public void freezeMoves(RiscvReg u) {
        for (RiscvInstruction m : nodeMoves(u)) {
            RiscvReg x = m.getDefReg();
            RiscvReg y = (RiscvReg) m.getOperands().get(0);
            RiscvReg v;
            if (getAlias(y) == getAlias(u)) {
                v = getAlias(x);
            } else {
                v = getAlias(y);
            }
            activeMoves.remove(m);
            frozenMoves.add(m);
            if (nodeMoves(v).isEmpty() && degree.getOrDefault(v, 0) < K) {
                freezeWorklist.remove(v);
                simplifyWorklist.add(v);
            }
        }
    }

    public double getprioForSpill(RiscvReg r) {
//        return (double) r.loopFactor / (double) degree.getOrDefault(r, 0); // todo 这里目前还没有计算loopFactor，先注释一下
        return 1; //todo 为了通过编译，先这样写了
    }

    public LinkedHashSet<Integer> initOkColors() {
        LinkedHashSet<Integer> okColors = new LinkedHashSet<>();
        if (currentType == RiscvVirReg.RegType.intType) {
            for (int i = 0; i < 27; i++) {
                okColors.add(5 + i);
            }
        } else {
            for (int i = 0; i < 32; i++) {
                okColors.add(i);
            }
        }
        return okColors;
    }

    public void enableMoves(LinkedHashSet<RiscvOperand> nodes) {
        for (var n : nodes) {
            for (var m : nodeMoves(n)) {
                if (activeMoves.contains(m)) {
                    activeMoves.remove(m);
                    worklistMoves.add(m);
                }
            }
        }
    }

    public void dump() {
        try {
            var out = new BufferedWriter(new FileWriter("rv_backend_afterRegAllocator.s"));
            out.write(riscvModule.toString());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateBeUsed(IList.INode<RiscvInstruction, RiscvBlock> instrNode) {
        RiscvInstruction instr = instrNode.getValue();
        for (RiscvOperand operand : instr.getOperands()) {
            operand.beUsed(instrNode);
//            if (operand instanceof RiscvReg) {
//                ((RiscvReg) operand).addDefOrUse(loopdepth, loop);
//            }
        }
        if (instr.getDefReg() != null) {
            instr.getDefReg().beUsed(instrNode);
//            var operand = instrnode.getContent().getDefReg();
//            if (operand != null) {
//                operand.addDefOrUse(loopdepth, loop);
//            }
        }
    }
}
