package Backend.Arm.tools;

import Backend.Arm.Instruction.*;
import Backend.Arm.Operand.*;
import Backend.Arm.Structure.ArmBlock;
import Backend.Arm.Structure.ArmFunction;

import Backend.Arm.Structure.ArmModule;
import Driver.Config;
import Utils.DataStruct.IList;
import Utils.DataStruct.Pair;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RegisterAllocator {
    private ArmModule armModule;
    private ArmVirReg.RegType currentType;
    private LinkedHashMap<ArmBlock, LiveInfo> liveInfoMap;

    private LinkedHashSet<ArmOperand> simplifyWorklist; // 低度数的传送无关的节点表
    private LinkedHashSet<ArmOperand> freezeWorklist; // 低度数的传送有关的节点表
    private LinkedHashSet<ArmOperand> spillWorklist; // 高度数的结点表
    private LinkedHashSet<ArmOperand> spilledNodes; // 本轮要溢出的结点，初始为空
    private LinkedHashSet<ArmOperand> coalescedNodes; // 已合并的寄存器集合
    private LinkedHashSet<ArmReg> coloredNodes; // 已成功着色的节点集合
    private Stack<ArmOperand> selectStack; // 一个包含从图中删除的临时变量的栈

    private LinkedHashSet<ArmInstruction> coalescedMoves; // 已经合并的传送指令集合
    private LinkedHashSet<ArmInstruction> constrainedMoves; // 源操作数和目标操作数冲突的传送指令集合
    private LinkedHashSet<ArmInstruction> frozenMoves; // 不再考虑合并的传送指令集合
    private LinkedHashSet<ArmInstruction> worklistMoves; // 有可能合并的传送指令集合
    private LinkedHashSet<ArmInstruction> activeMoves; // 还未做好合并准备的传送指令集合

    private LinkedHashSet<Pair<ArmReg, ArmReg>> adjSet; // 图中冲突边（u，v）的集合
    private LinkedHashMap<ArmOperand, LinkedHashSet<ArmOperand>> adjList; // 图的领接表表示
    private LinkedHashMap<ArmOperand, Integer> degree; // 包含每个结点当前度数的数组
    private LinkedHashMap<ArmOperand, LinkedHashSet<ArmMv>> moveList; // 从一个结点到与该结点相关的传送指令表的映射
    private LinkedHashMap<ArmOperand, ArmOperand> alias; // 传送指令（u，v）合并，且v放入coalescedNodes后，alias(v) = u
    private LinkedHashMap<ArmReg, Integer> color; // 算法为结点选择的颜色
    private LinkedHashMap<ArmVirReg, LinkedHashSet<ArmReg>> crossConflicts;
    private boolean canSpillToFPUReg;

    private final int INF = 0x7fffffff;
    private final int K_int = 12;
    private final int K_float = 31;
    private int K = 0;

    public RegisterAllocator(ArmModule armModule) {
        this.armModule = armModule;
    }

    public void run() {
        for (ArmFunction armFunction : armModule.getFunctions().values()) {
            if (!armFunction.getBlocks().isEmpty()) {
//                System.out.println("Registering function " + armFunction.getName());
                if (!armFunction.getName().equals("main")) {
                    IList.INode<ArmInstruction, ArmBlock> firstNode = armFunction.getBlocks().getHead().getValue()
                            .getArmInstructions().getHead();
                    ArrayList<IList.INode<ArmInstruction, ArmBlock>> tailNodes = new ArrayList<>();
                    for (ArmBlock tailBlock : armFunction.getRetBlocks()) {
                        for (IList.INode<ArmInstruction, ArmBlock>
                             insNode = tailBlock.getArmInstructions().getTail();
                             insNode != null; insNode = insNode.getPrev()) {
                            if (insNode.getValue() instanceof ArmRet && insNode.getPrev() != null
                                    && insNode.getPrev().getValue() instanceof ArmMv
                                    && insNode.getPrev().getValue().getDefReg().equals(ArmCPUReg.getArmCPUReg(14))) {
                                if (insNode.getPrev().getPrev() != null
                                        && (insNode.getPrev().getPrev().getValue().getDefReg().equals(ArmCPUReg.getArmCPUReg(0)) ||
                                        insNode.getPrev().getPrev().getValue().getDefReg().equals(ArmFPUReg.getArmFloatReg(0)))) {
                                    tailNodes.add(insNode.getPrev().getPrev());
                                } else {
                                    tailNodes.add(insNode.getPrev());
                                }

                            }
                        }
                    }

                    genProtectionMove(firstNode, tailNodes, armFunction);
                }
                goingToSpillToFPUReg(armFunction);
                System.out.println(armFunction.getName() + " canSpillToFPUReg: " + canSpillToFPUReg);
                while (true) {
                    if (Config.spill2FloatOpen && canSpillToFPUReg) {
                        if (currentType == null) {
                            currentType = ArmVirReg.RegType.intType;
                            K = K_int;
                        } else if (currentType == ArmVirReg.RegType.intType) {
                            currentType = ArmVirReg.RegType.floatType;
                            K = K_float;
                        } else {
                            break;
                        }
                    } else {
                        if (currentType == null) {
                            currentType = ArmVirReg.RegType.floatType;
                            K = K_float;
                        } else if (currentType == ArmVirReg.RegType.floatType) {
                            currentType = ArmVirReg.RegType.intType;
                            K = K_int;
                        } else {
                            break;
                        }
                    }


                    boolean hasTailCall =true;
                    boolean helpSpillToFPUReg = canSpillToFPUReg;
                    while (hasTailCall) {
                        initialize(armFunction);
                        hasTailCall = false;
                        buildGraph(armFunction);
                        makeWorkList(armFunction);
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
//                        System.out.println("Begin assignColor");
                        assignColor();
//                        System.out.println("Before rewrite " + spilledNodes);
                        if (!spilledNodes.isEmpty()) {
//                            System.out.println("Before rewrite " + armFunction.dump());
                            rewriteProgram(armFunction);
//                            System.out.println("After rewrite " + armFunction.dump());
                            goingToSpillToFPUReg(armFunction);
                            if (Config.spill2FloatOpen && helpSpillToFPUReg && !canSpillToFPUReg) {
                                break;
                            } else {
                                hasTailCall = true;
                            }
                        }
//                        System.out.println("After rewrite " +spilledNodes);
                    }
                    if (Config.spill2FloatOpen && helpSpillToFPUReg && !canSpillToFPUReg) {
                        currentType = null;
                        continue;
                    }

                    for (IList.INode<ArmBlock, ArmFunction>
                         blockNode = armFunction.getBlocks().getHead();
                         blockNode != null; blockNode = blockNode.getNext()) {
                        ArmBlock armBlock = blockNode.getValue();
//                        System.out.println("Begin: ");armBlock.dump();
                        for (IList.INode<ArmInstruction, ArmBlock>
                             armInstructionNode = armBlock.getArmInstructions().getTail();
                             armInstructionNode != null;
                             armInstructionNode = armInstructionNode.getPrev()) {
                            ArmInstruction armInstruction = armInstructionNode.getValue();
//                            System.out.println(armInstruction);
                            if (armInstruction.getDefReg() instanceof ArmVirReg
                                    && color.containsKey(armInstruction.getDefReg()) ) {
//                                    && currentType == ((ArmVirReg) armInstruction.getDefReg()).regType) {
                                if (((ArmVirReg) armInstruction.getDefReg()).regType == ArmVirReg.RegType.intType
                                        && currentType == ArmVirReg.RegType.intType) {
//                                    System.out.println(armInstruction.getDefReg());
//                                    System.out.println(armInstruction);
//                                    System.out.println(color.get(armInstruction.getDefReg()));
                                    armInstruction.replaceDefReg(
                                            ArmCPUReg.getArmCPUReg(color.get(armInstruction.getDefReg()))
                                            , armInstructionNode);
                                } else if (((ArmVirReg) armInstruction.getDefReg()).regType == ArmVirReg.RegType.floatType
                                        && currentType == ArmVirReg.RegType.floatType) {
                                    armInstruction.replaceDefReg(
                                            ArmFPUReg.getArmFloatReg(color.get(armInstruction.getDefReg()))
                                            , armInstructionNode);
                                }
                            }
//                            System.out.println(armFunction.getName());
//                            System.out.println(armBlock.getName());
//                            System.out.println(armInstruction);

                            LinkedHashMap<ArmOperand, ArmOperand> replace = new LinkedHashMap<>();
                            for (ArmOperand armOperand: armInstruction.getOperands()) {
                                if (armOperand instanceof ArmVirReg && color.containsKey(armOperand) ) {
//                                        && currentType == ((ArmVirReg) armOperand).regType) {
                                    if (((ArmVirReg) armOperand).regType == ArmVirReg.RegType.intType
                                            && currentType == ArmVirReg.RegType.intType) {
//                                        System.out.println(armOperand);
                                        replace.put(armOperand, ArmCPUReg.getArmCPUReg(color.get(armOperand)));
                                    } else if (((ArmVirReg) armOperand).regType == ArmVirReg.RegType.floatType
                                            && currentType == ArmVirReg.RegType.floatType) {
                                        replace.put(armOperand, ArmFPUReg.getArmFloatReg(color.get(armOperand)));
                                    }
                                }
                            }
                            for (ArmOperand key : replace.keySet()) {
                                armInstruction.replaceOperands((ArmReg) key,
                                        (ArmReg) replace.get(key), armInstructionNode);
                            }
//                            System.out.println(armInstruction);
                        }
//                        System.out.println("End: ");armBlock.dump();
                    }
                }
            }
        }
    }

    public void genProtectionMove(IList.INode<ArmInstruction, ArmBlock> headNode,
                                  ArrayList<IList.INode<ArmInstruction, ArmBlock>> tailNodes,
                                  ArmFunction armFunction) {
        IList.INode<ArmInstruction, ArmBlock> newInstrNode;
        ArrayList<Integer> list = new ArrayList<>(Arrays.asList(4,5,6,7,8,9,10,11));
        for (int index : list) {
//                ArmVirReg virReg = new ArmVirReg(allocator.getId(), currentType, armFunction);
            ArmVirReg virReg = armFunction.getNewReg(ArmVirReg.RegType.intType);
//                System.out.println(virReg);
            newInstrNode = new IList.INode<>(new ArmMv(ArmCPUReg.getArmCPUReg(index), virReg));
            newInstrNode.insertBefore(headNode);
            updateBeUsed(newInstrNode);
            for (IList.INode<ArmInstruction, ArmBlock> tailNode: tailNodes) {
                newInstrNode = new IList.INode<>(new ArmMv(virReg, ArmCPUReg.getArmCPUReg(index)));
                newInstrNode.insertBefore(tailNode);
                updateBeUsed(newInstrNode);
            }
        }
        for (int index = 16; index <= 31; index = index + 1) {
            ArmVirReg virReg = armFunction.getNewReg(ArmVirReg.RegType.floatType);
            newInstrNode = new IList.INode<>(new ArmFMv(ArmFPUReg.getArmFloatReg(index), virReg));
            newInstrNode.insertBefore(headNode);
            updateBeUsed(newInstrNode);
            for (IList.INode<ArmInstruction, ArmBlock> tailNode: tailNodes) {
                newInstrNode = new IList.INode<>(new ArmFMv(virReg, ArmFPUReg.getArmFloatReg(index)));
                newInstrNode.insertBefore(tailNode);
                updateBeUsed(newInstrNode);
            }
        }
    }

    public void initialize(ArmFunction armFunction) {
        liveInfoMap = LiveInfo.liveInfoAnalysis(armFunction);

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

        coloredNodes.addAll(ArmCPUReg.getAllCPURegs().values());
        coloredNodes.addAll(ArmFPUReg.getAllFPURegs().values());
        ArmCPUReg.getArmCPUReg(0);
        ArmFPUReg.getArmFloatReg(0);
        ArmCPUReg.getAllCPURegs().values().forEach(reg -> {
            color.put(reg, reg.getIndex());
            degree.put(reg, INF);
        });
        ArmFPUReg.getAllFPURegs().values().forEach(reg -> {
            color.put(reg, reg.getIndex());
            degree.put(reg, INF);
        });
    }

    public void buildGraph(ArmFunction armFunction) {
        boolean isFirstBlock = true;
        for (IList.INode<ArmBlock, ArmFunction> armBlockNode: armFunction.getBlocks()) {
            ArmBlock armBlock = armBlockNode.getValue();
            LinkedHashSet<ArmReg> liveOut = liveInfoMap.get(armBlock).getLiveOut();
//            System.out.println(liveOut.size());
            // 由于在逆序遍历指令的过程中，从块的LiveOut，逐条指令计算该指令的 LiveOut， 遍历到该指令时，里面装的就是该指令的 LiveOut
            for (IList.INode<ArmInstruction, ArmBlock>
                 armInstructionNode = armBlock.getArmInstructions().getTail();
                 armInstructionNode != null;
                 armInstructionNode = armInstructionNode.getPrev()) {
                ArmInstruction armInstruction = armInstructionNode.getValue();
                if (armInstruction instanceof ArmMv) {
                    liveOut.remove(armInstruction.getOperands().get(0));
                    if (!moveList.containsKey(armInstruction.getOperands().get(0))) {
                        moveList.put(armInstruction.getOperands().get(0), new LinkedHashSet<>());
                    }
                    if (!moveList.containsKey(armInstruction.getDefReg())) {
                        moveList.put(armInstruction.getDefReg(), new LinkedHashSet<>());
                    }
                    moveList.get(armInstruction.getOperands().get(0)).add((ArmMv) armInstruction);
                    moveList.get(armInstruction.getDefReg()).add((ArmMv) armInstruction);
//                    System.out.println("build-worklist" + armInstruction);
                    worklistMoves.add(armInstruction);
                }

//                System.out.println(armFunction.getName());
//                System.out.println(armBlock.dump());
//                System.out.println(armInstruction);
//                System.out.println(degree);
                //TODO 如果defReg能有多个 这里需要修改
                ArrayList<ArmReg> defRegs = new ArrayList<>();
                if (armInstruction.getDefReg() != null) {
//                    System.out.println(armInstruction.getDefReg());
                    if (armInstruction.getDefReg().toString().equals("%int47")) {
                        int a = 0;
                    }
                    defRegs.add(armInstruction.getDefReg());
                    if (armInstruction instanceof ArmCall) {
                        defRegs.addAll(getCallDefs());
                    }
                    liveOut.addAll(defRegs);
//                    System.out.println(liveOut);
                    for (ArmReg defReg: defRegs) {
                        for (ArmReg liveReg: liveOut) {
                            //System.out.println("conflict " + defReg + " " + liveReg);
                            addEdge(liveReg, defReg);
                        }
                    }
//                    if (!(armInstruction instanceof ArmMv)) {
//                        liveOut.removeAll(defRegs);
//                    } else if (!(isFirstBlock
//                            && armInstruction.getDefReg() instanceof ArmPhyReg)) {
//                        liveOut.removeAll(defRegs);
//                    }
                    liveOut.removeAll(defRegs);
                }
                for (ArmOperand armOperand : armInstruction.getOperands()) {
                    if (armOperand instanceof ArmReg) {
                        liveOut.add((ArmReg) armOperand);
                    }
                }
                if (armInstruction instanceof ArmCall) {
                    liveOut.addAll(((ArmCall) armInstruction).getUsedRegs());
                }
//                System.out.println(armInstruction + " " + liveOut);
            }
            isFirstBlock = false;
        }
    }

    public void makeWorkList(ArmFunction armFunction) {
        for (IList.INode<ArmBlock, ArmFunction> armBlockNode: armFunction.getBlocks()) {
            ArmBlock armBlock = armBlockNode.getValue();
            for (IList.INode<ArmInstruction, ArmBlock>
                 armInstructionNode = armBlock.getArmInstructions().getTail();
                 armInstructionNode != null;
                 armInstructionNode = armInstructionNode.getPrev()) {
                ArmInstruction armInstruction = armInstructionNode.getValue();
                if (armInstruction instanceof ArmMv) {
                    if (!adjList.containsKey(armInstruction.getDefReg())) {
                        adjList.put(armInstruction.getDefReg(), new LinkedHashSet<>());
                    }
                    if (adjList.get(armInstruction.getDefReg()).contains(armInstruction.getOperands().get(0))) {
                        worklistMoves.remove(armInstruction);
                        constrainedMoves.add(armInstruction);
                    }
                }
            }
        }

        for (ArmOperand n: armFunction.getAllVirRegUsed()) {
//            System.out.println("----------------------------------");
            assert n instanceof ArmVirReg;
            if (((ArmVirReg) n).regType == currentType) {
//                System.out.println(degree.getOrDefault(n,0));
                if (degree.getOrDefault(n, 0) >= K) {
                    spillWorklist.add(n);
                } else if (moveRelated(n)) {
                    freezeWorklist.add(n);
                } else {
//                    System.out.println("makeWorklist" + n);
                    simplifyWorklist.add(n);
                }
            }
        }
    }

    public void simplify() {
        ArmOperand n = simplifyWorklist.iterator().next();
        simplifyWorklist.remove(n);
//        System.out.println("selectStack: " + n);
        selectStack.push(n);
        for (ArmOperand m : adjacent((ArmReg) n)) {
            decrementDegree(m);
        }
    }

    public void coalesce() {
        ArmInstruction m = worklistMoves.iterator().next();
        var x = getAlias(m.getDefReg());
        var y = getAlias((ArmReg) m.getOperands().get(0));
        Pair<ArmReg, ArmReg> u_v;
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
                for (ArmOperand t : adjacent(u_v.getSecond())) {
                    flag = flag && OK((ArmReg) t, u_v.getFirst());
                }
            } else {
                LinkedHashSet<ArmOperand> computedNodes = new LinkedHashSet<>();
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
        ArmOperand u = freezeWorklist.iterator().next();
        freezeWorklist.remove(u);
//        System.out.println("freeze" + u);
        simplifyWorklist.add(u);
        freezeMoves((ArmReg) u);
    }

    public void selectSpill() {
        ArmReg m = null;//启发式算法，o（n）内得到选择的溢出寄存器
        boolean first = true;
        for (ArmOperand waitSpill : spillWorklist) {
            ArmReg vcmp = (ArmReg) waitSpill;
            if (first) {
                m = vcmp;
                first = false;
            } else if (getprioForSpill(vcmp) < getprioForSpill(m)) {
                m = vcmp;
            }
        }
//        System.out.println("selectSpill remove spillWorkList" + m);
        spillWorklist.remove(m);
//        System.out.println("selectSpill" + m);
        simplifyWorklist.add(m);
        freezeMoves(m);
    }

    public void assignColor() {
//        System.out.println(selectStack);
        while (!selectStack.isEmpty()) {
            ArmOperand n = selectStack.pop();
            LinkedHashSet<Integer> okColors = initOkColors();
            if (!adjList.containsKey(n)) {
                adjList.put(n, new LinkedHashSet<>());
            }
            for (ArmOperand w : adjList.get(n)) {
//                System.out.println(w + " " + getAlias((ArmReg) w));
//                System.out.println(getAlias((ArmReg) w).isPreColored());
//                System.out.println(coloredNodes.contains(getAlias((ArmReg) w)));
                if (getAlias((ArmReg) w).isPreColored() ||
                        coloredNodes.contains(getAlias((ArmReg) w))) {
//                    System.out.println(color.get(getAlias((ArmReg) w)));
                    okColors.remove(color.get(getAlias((ArmReg) w)));
                }
            }
            if (okColors.isEmpty()) {
                spilledNodes.add(n);
            } else {
                coloredNodes.add((ArmReg) n);
                Integer c = okColors.iterator().next();
//                 System.out.println("Color1: " + n + ' ' + c);
                color.put((ArmReg) n, c);
            }
        }
        for (ArmOperand n : coalescedNodes) {
//            System.out.println("Color2: " + n + ' ' + color.get(getAlias((ArmReg) n)));
//            if (color.get(getAlias((ArmReg) n)) == null) {
//                System.out.println(n);
//                System.out.println(getAlias((ArmReg) n));
//            }
            color.put((ArmReg) n, color.get(getAlias((ArmReg) n)));
        }
    }

    public void rewriteProgram(ArmFunction armFunction) {
        for (ArmOperand spilledNode: spilledNodes) {
            assert spilledNode instanceof ArmVirReg;
            if (Config.spill2FloatOpen && canSpillToFPUReg && currentType == ArmVirReg.RegType.intType) {
                spillToFPUReg(spilledNode, armFunction);
                continue;
            }

            armFunction.alloc((ArmVirReg) spilledNode);
            ArrayList<IList.INode<ArmInstruction, ArmBlock>> users =
                    new ArrayList<>(spilledNode.getUsers());
            for (IList.INode<ArmInstruction, ArmBlock> user : users) {
                ArmInstruction armInstruction = user.getValue();
                if (armInstruction.getDefReg() != null && armInstruction.getDefReg().equals(spilledNode)) {
                    ArmVirReg newDefReg = armFunction.getNewReg(((ArmVirReg) spilledNode).regType);
                    armFunction.reMap(newDefReg, (ArmVirReg) spilledNode);
                    int offset = armFunction.getOffset(newDefReg);
                    if (ArmTools.isLegalVLoadStoreImm(offset)
                            && ((ArmVirReg) spilledNode).regType == ArmVirReg.RegType.floatType) {
                        IList.INode<ArmInstruction, ArmBlock> sdNode = new IList.INode<>
                                (new ArmFSw(newDefReg, ArmCPUReg.getArmSpReg(), new ArmImm(-1*offset)));
                        newDefReg.beUsed(sdNode);
                        sdNode.insertAfter(user);
                        updateBeUsed(sdNode);
                        armInstruction.replaceDefReg(newDefReg, user);
                        armFunction.replaceVirReg(newDefReg, (ArmVirReg) spilledNode);
                    } else if (offset >= -4095 && offset <= 4095
                            && ((ArmVirReg) spilledNode).regType != ArmVirReg.RegType.floatType) {
                        IList.INode<ArmInstruction, ArmBlock> sdNode =
                                new IList.INode<>(new ArmSw(newDefReg, ArmCPUReg.getArmSpReg(), new ArmImm(-1*offset)));
                        newDefReg.beUsed(sdNode);
                        sdNode.insertAfter(user);
                        updateBeUsed(sdNode);
                        armInstruction.replaceDefReg(newDefReg, user);
                        armFunction.replaceVirReg(newDefReg, (ArmVirReg) spilledNode);
                    } else {
                        ArmVirReg assistReg = armFunction.getNewReg(ArmVirReg.RegType.intType);
                        IList.INode<ArmInstruction, ArmBlock> biNode = new IList.INode<>
                                (new ArmBinary(new ArrayList<>(Arrays.asList(assistReg,
                                        ArmCPUReg.getArmSpReg())), assistReg,
                                        ArmBinary.ArmBinaryType.add));
                        IList.INode<ArmInstruction, ArmBlock> sdNode;
                        if (((ArmVirReg) spilledNode).regType == ArmVirReg.RegType.floatType) {
                            sdNode = new IList.INode<>(new ArmFSw(newDefReg, assistReg, new ArmImm(0)));
                        } else {
                            sdNode = new IList.INode<>(new ArmSw(newDefReg, assistReg, new ArmImm(0)));
                        }
                        IList.INode<ArmInstruction, ArmBlock> liNode = new IList.INode<>
                                (new ArmLi(new ArmImm(offset * (-1)), assistReg));
                        assistReg.beUsed(biNode);
                        assistReg.beUsed(sdNode);
                        sdNode.insertAfter(user);
                        updateBeUsed(sdNode);
                        biNode.insertAfter(user);
                        updateBeUsed(biNode);
                        liNode.insertAfter(user);
                        updateBeUsed(liNode);
                        armInstruction.replaceDefReg(newDefReg, user);
                        armFunction.replaceVirReg(newDefReg, (ArmVirReg) spilledNode);
                    }
                }

                // 用于处理连续用到两次
                for (ArmOperand armOperand: armInstruction.getOperands()) {
                    if (armOperand.equals(spilledNode)) {
                        ArmVirReg newDefReg = armFunction.getNewReg(((ArmVirReg) spilledNode).regType);
                        armFunction.reMap(newDefReg, (ArmVirReg) spilledNode);
                        int offset = armFunction.getOffset(newDefReg);
                        if (((ArmVirReg) spilledNode).regType == ArmVirReg.RegType.intType) {
                            if (offset >= -4095 && offset <= 4095) {
                                IList.INode<ArmInstruction, ArmBlock> ldNode =
                                        new IList.INode<>(new ArmLoad(ArmCPUReg.getArmSpReg(),
                                                new ArmImm(-1*offset), newDefReg));
                                if (offset == 12) {
//                                    System.out.println("1: " + ldNode.getValue());
                                }
                                newDefReg.beUsed(ldNode);
                                ldNode.insertBefore(user);
                                updateBeUsed(ldNode);
                                armInstruction.replaceOperands((ArmReg)spilledNode, newDefReg, ldNode);
                            } else {
                                IList.INode<ArmInstruction, ArmBlock> liNode = new IList.INode<>
                                        (new ArmLi(new ArmImm(offset * (-1)), newDefReg));
                                IList.INode<ArmInstruction, ArmBlock> biNode = new IList.INode<>
                                        (new ArmBinary(new ArrayList<>(Arrays.asList(newDefReg,
                                                ArmCPUReg.getArmSpReg())), newDefReg,
                                                ArmBinary.ArmBinaryType.add));
                                IList.INode<ArmInstruction, ArmBlock> ldNode =
                                        new IList.INode<>(new ArmLoad(newDefReg, new ArmImm(0), newDefReg));
//                                System.out.println("2: " + ldNode.getValue());
                                newDefReg.beUsed(biNode);
                                newDefReg.beUsed(ldNode);
                                liNode.insertBefore(user);
                                updateBeUsed(liNode);
                                biNode.insertBefore(user);
                                updateBeUsed(biNode);
                                ldNode.insertBefore(user);
                                updateBeUsed(ldNode);
                                armInstruction.replaceOperands((ArmReg)spilledNode, newDefReg, user);
                            }
                        } else {
                            if (ArmTools.isLegalVLoadStoreImm(offset)) {
                                IList.INode<ArmInstruction, ArmBlock> FldNode =
                                        new IList.INode<>(new ArmVLoad(ArmCPUReg.getArmSpReg(),
                                                new ArmImm(-1*offset),newDefReg));
                                newDefReg.beUsed(FldNode);
                                FldNode.insertBefore(user);
                                updateBeUsed(FldNode);
                                armInstruction.replaceOperands((ArmReg)spilledNode, newDefReg, FldNode);
                            } else {
                                ArmVirReg assistReg = armFunction.getNewReg(ArmVirReg.RegType.intType);
                                IList.INode<ArmInstruction, ArmBlock> liNode = new IList.INode<>
                                        (new ArmLi(new ArmImm(offset * (-1)), assistReg));
                                IList.INode<ArmInstruction, ArmBlock> biNode = new IList.INode<>
                                        (new ArmBinary(new ArrayList<>(Arrays.asList(assistReg,
                                                ArmCPUReg.getArmSpReg())), assistReg,
                                                ArmBinary.ArmBinaryType.add));
                                IList.INode<ArmInstruction, ArmBlock> ldNode =
                                        new IList.INode<>(new ArmVLoad(assistReg, new ArmImm(0), newDefReg));
                                assistReg.beUsed(biNode);
                                assistReg.beUsed(ldNode);
                                liNode.insertBefore(user);
                                updateBeUsed(liNode);
                                biNode.insertBefore(user);
                                updateBeUsed(biNode);
                                ldNode.insertBefore(user);
                                updateBeUsed(ldNode);
                                armInstruction.replaceOperands((ArmReg)spilledNode, newDefReg, user);
                            }
                        }
                        armInstruction.replaceOperands((ArmReg) spilledNode, newDefReg, user);
                    }
                }
            }
            spilledNode.getUsers().clear();
        }
    }

    public ArrayList<ArmPhyReg> getCallDefs() {
        if (currentType == ArmVirReg.RegType.intType) {
            ArrayList<Integer> list_int = new ArrayList<>(
                    Arrays.asList(0, 1, 2, 3, 12));
            ArrayList<Integer> list_float = new ArrayList<>(
                    Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
            ArrayList<ArmPhyReg> ret = new ArrayList<>();
            list_float.forEach(i -> ret.add(ArmFPUReg.getArmFloatReg(i)));
            list_int.forEach(i -> ret.add(ArmCPUReg.getArmCPUReg(i)));
            return ret;
        } else {
            ArrayList<Integer> list_float = new ArrayList<>(
                    Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
            ArrayList<ArmPhyReg> ret = new ArrayList<>();
            list_float.forEach(i -> ret.add(ArmFPUReg.getArmFloatReg(i)));
            return ret;
        }
    }

    public void addEdge(ArmReg u, ArmReg v) {
        // 确保u v是同一类型的寄存器（float或者int）
        boolean uIsIntType = (u instanceof ArmCPUReg)
                || (u instanceof ArmVirReg && ((ArmVirReg) u).regType == ArmVirReg.RegType.intType);
        boolean vIsIntType = (v instanceof ArmCPUReg)
                || (v instanceof ArmVirReg && ((ArmVirReg) v).regType == ArmVirReg.RegType.intType);
        boolean isSameType = uIsIntType == vIsIntType;
        boolean currentIsIntType = currentType == ArmVirReg.RegType.intType;
        if (isSameType && !adjSet.contains(new Pair<>(u, v)) && u != v) {
            if (!((uIsIntType && currentType == ArmVirReg.RegType.intType) ||
                    (!uIsIntType) && currentType == ArmVirReg.RegType.floatType)) {
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
//            if (vIsIntType && v instanceof ArmVirReg) {
//                assert u instanceof ArmFPUReg;
//                if (!crossConflicts.containsKey(v)) {
//                    crossConflicts.put((ArmVirReg) v, new LinkedHashSet<>());
//                }
//                crossConflicts.get(v).add(u);
//            } else if (uIsIntType && u instanceof ArmVirReg) {
//                assert v instanceof ArmFPUReg;
//                if (!crossConflicts.containsKey(u)) {
//                    crossConflicts.put((ArmVirReg) u, new LinkedHashSet<>());
//                }
//                crossConflicts.get(u).add(v);
//            }
//        }
    }

    public LinkedHashSet<ArmInstruction> nodeMoves(ArmOperand reg) {
        LinkedHashSet<ArmInstruction> result;
        if (moveList.containsKey(reg)) {
            result = new LinkedHashSet<>(moveList.get(reg));
        } else {
            result = new LinkedHashSet<>();
        }
        LinkedHashSet<ArmInstruction> tmpset = new LinkedHashSet<>(activeMoves);
        tmpset.addAll(worklistMoves);
        result.retainAll(tmpset);
        return result;
    }
    public boolean moveRelated(ArmOperand armOperand) {
        return !nodeMoves(armOperand).isEmpty();
    }

    public LinkedHashSet<ArmOperand> adjacent(ArmOperand armOperand) {
        LinkedHashSet<ArmOperand> result = new LinkedHashSet<>();
        if (adjList.containsKey(armOperand)) {
            result.addAll(adjList.get(armOperand));
        }
        selectStack.forEach(result::remove);
        result.removeAll(coalescedNodes);
        return result;
    }

    public void decrementDegree(ArmOperand m) {
        degree.put(m, degree.get(m)-1);
        if (degree.get(m) == K - 1) {
            adjacent(m).add(m);
//            System.out.println("decrement remove spillWorkList" + m);
            spillWorklist.remove(m);
            if (moveRelated(m)) {
                freezeWorklist.add(m);
            } else {
                simplifyWorklist.add(m);
            }
        }
    }
    public ArmReg getAlias(ArmReg n) {
        if (coalescedNodes.contains(n)) {
            return getAlias((ArmReg) alias.get(n));
        } else {
            return n;
        }
    }

    public void addWorkList(ArmReg u) {
//        System.out.println(u);
//        System.out.println("precolor: " + !u.isPreColored());
//        System.out.println("move: " + !moveRelated(u));
//        System.out.println("degree: " + (degree.getOrDefault(u, 0) < K));
        if (!u.isPreColored() && !moveRelated(u) && (degree.getOrDefault(u, 0) < K)) {
            freezeWorklist.remove(u);
            simplifyWorklist.add(u);
        }
    }

    public boolean OK(ArmReg t, ArmReg r) {
        return degree.getOrDefault(t, 0) < K || t.isPreColored() ||
                adjSet.contains(new Pair<>(t, r));
    }

    public boolean conservative(LinkedHashSet<ArmOperand> nodes) {
        int k = 0;
        for (ArmOperand n : nodes) {
            if (degree.getOrDefault(n, 0) >= K) {
                k += 1;
            }
            if (k == K) {
                return false;
            }
        }
        return true;
    }

    public void combine(ArmReg u, ArmReg v) {
        if (freezeWorklist.contains(v)) {
            freezeWorklist.remove(v);
        } else {
//            System.out.println("combine remove spillWorkList" + v);
            spillWorklist.remove(v);
        }
        coalescedNodes.add(v);
        // System.out.println("alias" + v + ' ' + u);
        alias.put(v, u);
        moveList.get(u).addAll(moveList.get(v));
        enableMoves(new LinkedHashSet<>(Collections.singletonList(v)));
        for (ArmOperand t : adjList.getOrDefault(v, new LinkedHashSet<>())) {
            addEdge((ArmReg) t, u);
            decrementDegree(t);
        }
        if (degree.getOrDefault(u, 0) >= K && freezeWorklist.contains(u)) {
            freezeWorklist.remove(u);
            spillWorklist.add(u);
        }
    }

    public void freezeMoves(ArmReg u) {
        for (ArmInstruction m : nodeMoves(u)) {
            ArmReg x = m.getDefReg();
            ArmReg y = (ArmReg) m.getOperands().get(0);
            ArmReg v;
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

    public double getprioForSpill(ArmReg r) {
//        return (double) r.loopFactor / (double) degree.getOrDefault(r, 0); // todo 这里目前还没有计算loopFactor，先注释一下
        return 1; //todo 为了通过编译，先这样写了
    }

    public LinkedHashSet<Integer> initOkColors() {
        LinkedHashSet<Integer> okColors = new LinkedHashSet<>();
        if (currentType == ArmVirReg.RegType.intType) {
            for (int i = 0; i <= K_int; i++) {
                okColors.add(i);
            }
        } else {
            for (int i = 0; i <= K_float; i++) {
                okColors.add(i);
            }
        }
        return okColors;
    }

    public void enableMoves(LinkedHashSet<ArmOperand> nodes) {
        for (var n : nodes) {
            for (var m : nodeMoves(n)) {
                if (activeMoves.contains(m)) {
                    activeMoves.remove(m);
                    worklistMoves.add(m);
                }
            }
        }
    }

    private void goingToSpillToFPUReg(ArmFunction armFunction) {
        currentType = ArmVirReg.RegType.floatType;
        initialize(armFunction);
        buildGraph(armFunction);
        makeWorkList(armFunction);
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
//        System.out.println("Begin assignColor");
        assignColor();
        if (spilledNodes.isEmpty()) {
            canSpillToFPUReg = true;
        }
        currentType = null;
    }


    private void spillToFPUReg(ArmOperand spilledNode, ArmFunction armFunction) {
        ArrayList<IList.INode<ArmInstruction, ArmBlock>> users = new ArrayList<>(spilledNode.getUsers());
        ArmVirReg floatReg = armFunction.getNewReg(ArmVirReg.RegType.floatType);
//        System.out.println("spillToFPUReg" + floatReg);
        for (IList.INode<ArmInstruction, ArmBlock> userNode : users) {
            ArmInstruction user = userNode.getValue();
            if (user.getDefReg() != null && user.getDefReg().equals(spilledNode)) {
                ArmVirReg newDefReg = armFunction.getNewReg(((ArmVirReg) spilledNode).regType);
                IList.INode<ArmInstruction, ArmBlock> vmovNode = new IList.INode<>(new ArmConvMv(newDefReg, floatReg));
                floatReg.beUsed(vmovNode);
                newDefReg.beUsed(vmovNode);
                vmovNode.insertAfter(userNode);
                updateBeUsed(vmovNode);
                user.replaceDefReg(newDefReg, userNode);
                armFunction.replaceVirReg(newDefReg, (ArmVirReg) spilledNode);
            }

            // 用于处理连续用到两次
            for (ArmOperand armOperand: user.getOperands()) {
                if (armOperand.equals(spilledNode)) {
                    ArmVirReg newDefReg = armFunction.getNewReg(((ArmVirReg) spilledNode).regType);
                    IList.INode<ArmInstruction, ArmBlock> vmovNode = new IList.INode<>(new ArmConvMv(floatReg, newDefReg));
                    floatReg.beUsed(vmovNode);
                    newDefReg.beUsed(vmovNode);
                    vmovNode.insertBefore(userNode);
                    updateBeUsed(vmovNode);
                    user.replaceOperands((ArmReg) spilledNode, newDefReg, userNode);
                }
            }
        }
        spilledNode.getUsers().clear();
    }

    public void dump() {
        try {
            var out = new BufferedWriter(new FileWriter("arm_backend_afterRegAllocator.s"));
            out.write(armModule.toString());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateBeUsed(IList.INode<ArmInstruction, ArmBlock> instrNode) {
        ArmInstruction instr = instrNode.getValue();
        for (ArmOperand operand : instr.getOperands()) {
            operand.beUsed(instrNode);
//            if (operand instanceof ArmReg) {
//                ((ArmReg) operand).addDefOrUse(loopdepth, loop);
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
