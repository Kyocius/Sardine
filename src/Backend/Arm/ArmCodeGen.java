package Backend.Arm;

import Backend.Arm.Instruction.*;
import Backend.Arm.Operand.*;
import Backend.Arm.Structure.*;
import Backend.Arm.tools.ArmTools;
import Backend.Riscv.Operand.RiscvReg;
import Driver.Config;
import IR.IRModule;
import IR.Type.IntegerType;
import IR.Type.PointerType;
import IR.Value.*;
import IR.Value.Instructions.*;
import Utils.DataStruct.IList;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

import static Backend.Arm.tools.ArmTools.getOnlyRevBigSmallType;

public class ArmCodeGen {
    public IRModule irModule;
    public ArmModule armModule = new ArmModule();
    private final LinkedHashMap<Value, ArmLabel> value2Label = new LinkedHashMap<>();
    private final LinkedHashMap<Value, ArmReg> value2Reg = new LinkedHashMap<>();
    private final LinkedHashMap<Value, Integer> ptr2Offset = new LinkedHashMap<>();
    private ArmBlock curArmBlock = null;
    private ArmFunction curArmFunction = null;
    private ArmFunction ldivmod = new ArmFunction("__divti3"); // AArch64 128-bit division function
    private final LinkedHashMap<Instruction, ArrayList<ArmInstruction>> predefines = new LinkedHashMap<>();

    public ArmCodeGen(IRModule irModule) {
        this.irModule = irModule;
    }

    public String removeLeadingAt(String name) {
        if (name.startsWith("@")) {
            return name.substring(1);
        }
        return name;
    }

    public void run() {
        System.out.println("start gen code for armv8-a 64-bit");
        ArmCPUReg.getArmCPUReg(0);
        ArmFPUReg.getArmFloatReg(0);
        for (var globalVariable : irModule.globalVars()) {
            parseGlobalVar(globalVariable);
        }
        String parallelStartName = "@parallelStart";
        String parallelEndName = "@parallelEnd";

        for (Function function : irModule.libFunctions()) {
            if(Objects.equals(function.getName(), parallelStartName) && Config.parallelOpen) {
                ArmFunction parallelStart = new ArmFunction(removeLeadingAt(parallelStartName));
                buildParallelBegin(parallelStart);
                parallelStart.parseArgs(function.getArgs(), value2Reg);
                armModule.addFunction(parallelStartName, parallelStart);
                value2Label.put(function, parallelStart);
            } else if (Objects.equals(function.getName(), parallelEndName) && Config.parallelOpen) {
                ArmFunction parallelEnd = new ArmFunction(removeLeadingAt(parallelEndName));
                buildParallelEnd(parallelEnd);
                parallelEnd.parseArgs(function.getArgs(), value2Reg);
                armModule.addFunction(function.getName(), parallelEnd);
                value2Label.put(function, parallelEnd);
            } else {
                ArmFunction armFunction = new ArmFunction(removeLeadingAt(function.getName()));
                armModule.addFunction(function.getName(), armFunction);
                value2Label.put(function, armFunction);
                armFunction.parseArgs(function.getArgs(), value2Reg);
            }
        }

        for (Function function : irModule.functions()) {
            ArmFunction armFunction = new ArmFunction(removeLeadingAt(function.getName()));
            armModule.addFunction(removeLeadingAt(function.getName()), armFunction);
            value2Label.put(function, armFunction);
            armFunction.parseArgs(function.getArgs(), value2Reg);
        }
        for (var function : irModule.functions()) {
            parseFunction(function);
            for (IList.INode<ArmBlock, ArmFunction> bb : ((ArmFunction) value2Label.get(function)).getBlocks()) {
                if (bb.getPrev() != null) {
                    if (!(bb.getPrev().getValue().getArmInstructions().getTail().getValue() instanceof ArmJump ||
                            bb.getPrev().getValue().getArmInstructions().getTail().getValue() instanceof ArmRet)) {
                        bb.getValue().addPreds(bb.getPrev().getValue());
                    }
                }
                if (bb.getNext() != null) {
                    if (!(bb.getValue().getArmInstructions().getTail().getValue() instanceof ArmJump ||
                            bb.getValue().getArmInstructions().getTail().getValue() instanceof ArmRet)) {
                        bb.getValue().addSuccs(bb.getNext().getValue());
                    }
                }
            }
            //TODO: 是否需要调整位数
        }
    }

    public void buildParallelBegin(ArmFunction parallelStart) {
        curArmFunction = parallelStart;
        ArmBlock armBlock0 = new ArmBlock("parallel_start0");
        ArmBlock armBlock1 = new ArmBlock("parallel_start1");
        ArmBlock armBlock2 = new ArmBlock("parallel_start2");
        curArmBlock = armBlock0;
        curArmFunction.addBlock(new IList.INode<>(curArmBlock));

        ArmGlobalVariable stoR7 = new ArmGlobalVariable("parallell_stoR7",
                false, 8, new ArrayList<>(List.of(new ArmGlobalZero(8))));
        armModule.addBssVar(stoR7);
        ArmGlobalVariable stoR5 = new ArmGlobalVariable("parallell_stoR5",
                false, 8, new ArrayList<>(List.of(new ArmGlobalZero(8))));
        armModule.addBssVar(stoR5);
        ArmGlobalVariable stoLr = new ArmGlobalVariable("parallell_stoLR",
                false, 8, new ArrayList<>(List.of(new ArmGlobalZero(8))));
        armModule.addBssVar(stoLr);

        addInstr(new ArmLi(stoR7, ArmCPUReg.getArmCPUReg(2)),
                null, false);
        addInstr(new ArmSw(ArmCPUReg.getArmCPUReg(7), ArmCPUReg.getArmCPUReg(2), new ArmImm(0)),
                null, false);
        addInstr(new ArmLi(stoR5, ArmCPUReg.getArmCPUReg(2)),
                null, false);
        addInstr(new ArmSw(ArmCPUReg.getArmCPUReg(5), ArmCPUReg.getArmCPUReg(2), new ArmImm(0)),
                null, false);
        addInstr(new ArmLi(stoLr, ArmCPUReg.getArmCPUReg(2)),
                null, false);
        addInstr(new ArmSw(ArmCPUReg.getArmRetReg(), ArmCPUReg.getArmCPUReg(2), new ArmImm(0)),
                null, false);

        ArmLi li4 = new ArmLi(new ArmImm(Config.parallelNum), ArmCPUReg.getArmCPUReg(5));
        addInstr(li4, null, false);

        curArmBlock = armBlock1;
        curArmFunction.addBlock(new IList.INode<>(curArmBlock));
        addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmCPUReg(5),
                new ArmImm(1))), ArmCPUReg.getArmCPUReg(5), ArmBinary.ArmBinaryType.sub), null, false);
        addInstr(new ArmCompare(ArmCPUReg.getArmCPUReg(5), new ArmImm(0),
                ArmCompare.CmpType.cmp), null, false);
        addInstr(new ArmBranch(armBlock2, ArmTools.CondType.eq), null, false);

        // TODO: Update syscall numbers for ARMv8-A (AArch64)
        // ARMv7: clone = 120, ARMv8-A: clone = 220
        addInstr(new ArmLi(new ArmImm(220), ArmCPUReg.getArmCPUReg(8)), null, false); // x8 holds syscall number in AArch64
        addInstr(new ArmLi(new ArmImm(273), ArmCPUReg.getArmArgReg(0)), null, false);
        addInstr(new ArmMv(ArmCPUReg.getArmSpReg(), ArmCPUReg.getArmArgReg(1)), null, false);

        addInstr(new ArmSyscall(new ArmImm(0)), null, false);
        addInstr(new ArmCompare(ArmCPUReg.getArmCPUReg(0),
                new ArmImm(0), ArmCompare.CmpType.cmp), null, false);
        addInstr(new ArmBranch(armBlock1, ArmTools.CondType.ne), null, false);

        curArmBlock = armBlock2;
        curArmFunction.addBlock(new IList.INode<>(curArmBlock));

        addInstr(new ArmMv(ArmCPUReg.getArmCPUReg(5), ArmCPUReg.getArmCPUReg(0)), null, false);
        addInstr(new ArmLi(stoR7,
                ArmCPUReg.getArmCPUReg(2)), null, false);
        addInstr(new ArmLoad(ArmCPUReg.getArmCPUReg(2), new ArmImm(0),
                ArmCPUReg.getArmCPUReg(7)), null, false);
        addInstr(new ArmLi(stoR5,
                ArmCPUReg.getArmCPUReg(2)), null, false);
        addInstr(new ArmLoad(ArmCPUReg.getArmCPUReg(2), new ArmImm(0),
                ArmCPUReg.getArmCPUReg(5)), null, false);
        addInstr(new ArmLi(stoLr,
                ArmCPUReg.getArmCPUReg(2)), null, false);
        addInstr(new ArmLoad(ArmCPUReg.getArmCPUReg(2), new ArmImm(0),
                ArmCPUReg.getArmRetReg()), null, false);
        addInstr(new ArmRet(ArmCPUReg.getArmRetReg(), null), null, false);
    }

    // 构建并结束并行区块，符合 AArch64 标准
        public void buildParallelEnd(ArmFunction parallelEnd) {
            curArmFunction = parallelEnd;
            ArmBlock armBlock0 = new ArmBlock("parallel_end0");
            ArmBlock armBlock1 = new ArmBlock("parallel_end1");
            ArmBlock armBlock2 = new ArmBlock("parallel_end2");
            ArmBlock armBlock3 = new ArmBlock("parallel_end3");
            ArmBlock armBlock4 = new ArmBlock("parallel_end4");

            // 并行结束时保存 R7 和 LR 的全局变量
            ArmGlobalVariable stoR7 = new ArmGlobalVariable("parallell_endR7",
                    false, 8, new ArrayList<>(List.of(new ArmGlobalZero(8))));
            armModule.addBssVar(stoR7);
            ArmGlobalVariable stoLr = new ArmGlobalVariable("parallell_endLR",
                    false, 8, new ArrayList<>(List.of(new ArmGlobalZero(8))));
            armModule.addBssVar(stoLr);

            curArmBlock = armBlock0;
            curArmFunction.addBlock(new IList.INode<>(curArmBlock));
            // 判断 x0 是否为 0，决定是否退出
            addInstr(new ArmCompare(ArmCPUReg.getArmCPUReg(0), new ArmImm(0), ArmCompare.CmpType.cmp),
                    null, false);
            addInstr(new ArmBranch(armBlock2, ArmTools.CondType.eq), null, false);

            curArmBlock = armBlock1;
            curArmFunction.addBlock(new IList.INode<>(curArmBlock));
            // AArch64 标准：exit 系统调用号为 93，x8 作为 syscall 号寄存器
            addInstr(new ArmLi(new ArmImm(93), ArmCPUReg.getArmCPUReg(8)), null, false);
            addInstr(new ArmSyscall(new ArmImm(0)), null, false);
            System.out.println("AArch64: exit syscall, x8=93, x0=返回值");

            curArmBlock = armBlock2;
            curArmFunction.addBlock(new IList.INode<>(curArmBlock));
            // 保存 R7 和 LR 到全局变量
            addInstr(new ArmLi(stoR7, ArmCPUReg.getArmCPUReg(2)), null, false);
            addInstr(new ArmSw(ArmCPUReg.getArmCPUReg(7),
                    ArmCPUReg.getArmCPUReg(2), new ArmImm(0)), null, false);
            addInstr(new ArmLi(stoLr, ArmCPUReg.getArmCPUReg(2)), null, false);
            addInstr(new ArmSw(ArmCPUReg.getArmRetReg(),
                    ArmCPUReg.getArmCPUReg(2), new ArmImm(0)), null, false);

            // 初始化并行计数器
            addInstr(new ArmLi(new ArmImm(Config.parallelNum), ArmCPUReg.getArmCPUReg(7)), null, false);
            System.out.println("AArch64: 初始化并行计数器 x7=" + Config.parallelNum);

            curArmBlock = armBlock3;
            curArmFunction.addBlock(new IList.INode<>(curArmBlock));
            // x7 = x7 - 1
            addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmCPUReg(7),
                    new ArmImm(1))), ArmCPUReg.getArmCPUReg(7), ArmBinary.ArmBinaryType.sub), null, false);
            // 判断 x7 是否为 0
            addInstr(new ArmCompare(ArmCPUReg.getArmCPUReg(7), new ArmImm(0),
                    ArmCompare.CmpType.cmp), null, false);
            addInstr(new ArmBranch(armBlock4, ArmTools.CondType.eq), null, false);
            // x0 = SP - 4，栈指针下移
            addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), new ArmImm(4))),
                    ArmCPUReg.getArmCPUReg(0), ArmBinary.ArmBinaryType.sub), null, false);
            addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), new ArmImm(4))),
                    ArmCPUReg.getArmSpReg(), ArmBinary.ArmBinaryType.sub), null, false);
            // 等待其他线程
            addInstr(new ArmWait(), null, false);
            // SP 恢复
            addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), new ArmImm(4))),
                    ArmCPUReg.getArmSpReg(), ArmBinary.ArmBinaryType.add), null, false);
            // 跳转回 armBlock3，继续等待
            addInstr(new ArmJump(armBlock3, curArmBlock), null, false);
            System.out.println("AArch64: 并行等待区块循环");

            curArmBlock = armBlock4;
            curArmFunction.addBlock(new IList.INode<>(curArmBlock));
            // 恢复 R7 和 LR
            addInstr(new ArmLi(stoR7, ArmCPUReg.getArmCPUReg(2)), null, false);
            addInstr(new ArmLoad(ArmCPUReg.getArmCPUReg(2),
                    new ArmImm(0), ArmCPUReg.getArmCPUReg(7)), null, false);
            addInstr(new ArmLi(stoLr, ArmCPUReg.getArmCPUReg(2)), null, false);
            addInstr(new ArmLoad(ArmCPUReg.getArmCPUReg(2),
                    new ArmImm(0), ArmCPUReg.getArmRetReg()), null, false);
            addInstr(new ArmRet(ArmCPUReg.getArmRetReg(), null), null, false);
            System.out.println("AArch64: 并行结束，恢复寄存器并返回");
        }

    /**
                 * 解析全局变量，生成 ARMv8-A (AArch64) 标准的汇编数据段或 bss 段
                 * @param var IR 全局变量
                 */
                public void parseGlobalVar(GlobalVar var) {
                    boolean flag = true;
                    // 判断是否为数组类型
                    if (var.isArray()) {
                        // 判断数组元素类型是否为整数
                        boolean isIntType = ((PointerType) var.getType()).getEleType() instanceof IntegerType;
                        int zeros = 0;
                        ArrayList<ArmGlobalValue> values = new ArrayList<>();
                        // 非零初始化时，遍历所有元素
                        if (!var.isZeroInit()) {
                            for (Value value : var.getValues()) {
                                if (isIntType) {
                                    assert value instanceof ConstInteger;
                                    if (((ConstInteger) value).getValue() == 0) {
                                        zeros += 4;
                                    } else {
                                        flag = false;
                                        if (zeros > 0) {
                                            values.add(new ArmGlobalZero(zeros));
                                            zeros = 0;
                                        }
                                        values.add(new ArmGlobalInt(((ConstInteger) value).getValue()));
                                        System.out.println("AArch64: 数组元素为整数，值=" + ((ConstInteger) value).getValue());
                                    }
                                } else {
                                    assert value instanceof ConstFloat || value instanceof ConstInteger;
                                    float val = (value instanceof ConstInteger) ? ((ConstInteger) value).getValue() :
                                            ((ConstFloat) value).getValue();
                                    if (val == 0) {
                                        zeros += 4;
                                    } else {
                                        flag = false;
                                        if (zeros > 0) {
                                            values.add(new ArmGlobalZero(zeros));
                                            zeros = 0;
                                        }
                                        values.add(new ArmGlobalFloat(val));
                                        System.out.println("AArch64: 数组元素为浮点数，值=" + val);
                                    }
                                }
                            }
                            if (zeros > 0) {
                                values.add(new ArmGlobalZero(zeros));
                                System.out.println("AArch64: 数组结尾补零，zeros=" + zeros);
                            }
                        }
                        // 构造 ARMv8-A 标准的全局变量
                        ArmGlobalVariable globalVariable = new ArmGlobalVariable(removeLeadingAt(var.getName()),
                                !var.isZeroInit(), 4 * var.getSize(), values);
                        if (!flag) {
                            armModule.addDataVar(globalVariable);
                            System.out.println("AArch64: 添加到 .data 段，全局变量=" + var.getName());
                        } else {
                            armModule.addBssVar(globalVariable);
                            System.out.println("AArch64: 添加到 .bss 段，全局变量=" + var.getName());
                        }
                        value2Label.put(var, globalVariable);
                    } else {
                        // 非数组类型，判断是否为整数类型
                        boolean isIntType = !var.getType().isFloatTy();
                        if (isIntType) {
                            assert var.getValue() instanceof ConstInteger;
                            ArmGlobalValue riscvGlobalInt = new ArmGlobalInt(((ConstInteger) var.getValue()).getValue());
                            ArrayList<ArmGlobalValue> values = new ArrayList<>();
                            values.add(riscvGlobalInt);
                            ArmGlobalVariable globalVariable = new ArmGlobalVariable(removeLeadingAt(var.getName()),
                                    true, 4, values);
                            if (((ConstInteger) var.getValue()).getValue() == 0) {
                                armModule.addBssVar(globalVariable);
                                System.out.println("AArch64: 单值整数为零，添加到 .bss 段，全局变量=" + var.getName());
                            } else {
                                armModule.addDataVar(globalVariable);
                                System.out.println("AArch64: 单值整数非零，添加到 .data 段，全局变量=" + var.getName());
                            }
                            value2Label.put(var, globalVariable);
                        } else {
                            assert var.getValue() instanceof ConstFloat || var.getValue() instanceof ConstInteger;
                            float val = (var.getValue() instanceof ConstInteger) ?
                                    ((ConstInteger) var.getValue()).getValue() :
                                    ((ConstFloat) var.getValue()).getValue();
                            ArmGlobalValue armGlobalFloat = new ArmGlobalFloat(val);
                            ArrayList<ArmGlobalValue> values = new ArrayList<>();
                            values.add(armGlobalFloat);
                            ArmGlobalVariable globalVariable = new ArmGlobalVariable(removeLeadingAt(var.getName()),
                                    true, 4, values);
                            armModule.addDataVar(globalVariable);
                            System.out.println("AArch64: 单值浮点数，添加到 .data 段，全局变量=" + var.getName() + "，值=" + val);
                            value2Label.put(var, globalVariable);
                        }
                    }
                }

    public void parseFunction(Function function) {
        curArmBlock = null;
        curArmFunction = (ArmFunction) value2Label.get(function);
        System.out.println("[parseFunction] 正在解析函数: " + function.getName() + "，基本块数量: " + function.getBbs().getSize());
        for (IList.INode<BasicBlock, Function> basicBlockNode : function.getBbs()) {
            BasicBlock bb = basicBlockNode.getValue();
            ArmBlock temp_block = new ArmBlock(curArmFunction.getName() +
                    "_block" + curArmFunction.allocBlockIndex());
            value2Label.put(bb, temp_block);
        }
        boolean flag = false;
        for (IList.INode<BasicBlock, Function> basicBlockNode : function.getBbs()) {
            if (curArmBlock != null) {
                curArmFunction.addBlock(new IList.INode<>(curArmBlock));
            }
            BasicBlock bb = basicBlockNode.getValue();
            curArmBlock = (ArmBlock) value2Label.get(bb);
            System.out.println("[parseFunction] 处理基本块: " + bb.getName() + "，指令数量: " + bb.getInsts().getSize());
            if (!flag) {
                // AArch64 函数序言: 保存帧指针和链接寄存器
                System.out.println("[parseFunction] 插入函数序言，保存 x29(帧指针) 和 x30(链接寄存器)");
                ArmStp prologue = new ArmStp(ArmCPUReg.getArmCPUReg(29), ArmCPUReg.getArmRetReg(),
                                           ArmCPUReg.getArmSpReg(), new ArmImm(-16), true);
                addInstr(prologue, null, false);

                // 设置帧指针
                System.out.println("[parseFunction] 设置帧指针 x29");
                ArmMv setFP = new ArmMv(ArmCPUReg.getArmSpReg(), ArmCPUReg.getArmCPUReg(29));
                addInstr(setFP, null, false);

                // ARM64: 在函数开始分配栈空间
                int stackSize = curArmFunction.getStackPosition();
                if (stackSize > 0) {
                    // 16 字节对齐
                    stackSize = (stackSize + 15) & ~15;
                    System.out.println("[parseFunction] 分配栈空间，大小: " + stackSize);
                    if (stackSize <= 4095) {
                        // 立即数方式
                        ArmBinary stackAlloc = new ArmBinary(
                            new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), new ArmImm(stackSize))),
                            ArmCPUReg.getArmSpReg(),
                            ArmBinary.ArmBinaryType.sub
                        );
                        addInstr(stackAlloc, null, false);
                    } else {
                        // 使用寄存器辅助分配大栈空间
                        System.out.println("[parseFunction] 使用辅助寄存器分配大栈空间");
                        ArmReg assistReg = getNewIntReg();
                        ArmLi li = new ArmLi(new ArmImm(stackSize), assistReg);
                        addInstr(li, null, false);
                        ArmBinary stackAlloc = new ArmBinary(
                            new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), assistReg)),
                            ArmCPUReg.getArmSpReg(),
                            ArmBinary.ArmBinaryType.sub
                        );
                        addInstr(stackAlloc, null, false);
                    }
                }

                // 将所有参数相关的 mv 指令加入 Block
                System.out.println("[parseFunction] 插入参数传递相关的寄存器移动指令");
                for (ArmMv armMv : curArmFunction.getMvs()) {
                    addInstr(armMv, null, false);
                }
                // 保存返回地址寄存器 (x30 -> 虚拟寄存器)
                System.out.println("[parseFunction] 保存返回地址寄存器 x30 到虚拟寄存器");
                ArmMv mv = new ArmMv(curArmFunction.getRetReg(), ArmCPUReg.getArmRetReg());
                addInstr(mv, null, false);
                flag = true;
            }
            parseBasicBlock(bb);
        }
        if (function.getBbs().getSize() != 0) {
            curArmFunction.addBlock(new IList.INode<>(curArmBlock));
            System.out.println("[parseFunction] 已添加最后一个基本块到函数: " + function.getName() + "，总块数: " + curArmFunction.getBlocks().getSize());
        } else {
            System.out.println("[parseFunction] 函数无基本块可添加: " + function.getName());
        }
    }

    // 解析一个 IR 基本块，将其中每条 IR 指令转换为 ARM 指令
    // 注意：AArch64 要求每条指令都应符合 64 位 ARM 指令集规范
    public void parseBasicBlock(BasicBlock block) {
        // 遍历基本块中的所有 IR 指令节点
        System.out.println("[parseBasicBlock] 处理基本块: " + block.getName() + "，指令数量: " + block.getInsts().getSize());
        for (IList.INode<Instruction, BasicBlock> insNode : block.getInsts()) {
            Instruction ins = insNode.getValue();
            // 转换为 ARM 指令，false 表示不是预定义指令
            System.out.println("[parseBasicBlock] 转换 IR 指令: " + ins);
            parseInstruction(ins, false);
        }
    }

    // 解析一条 IR 指令并转换为 ARM 指令
    public void parseInstruction(Instruction ins, boolean predefine) {
        if (ins instanceof AllocInst) {
            System.out.println("[parseInstruction] 分配内存指令: " + ins);
            parseAlloc((AllocInst) ins, predefine);
        } else if (ins instanceof BinaryInst) {
            System.out.println("[parseInstruction] 二元运算指令: " + ins);
            parseBinaryInst((BinaryInst) ins, predefine);
        } else if (ins instanceof BrInst) {
            System.out.println("[parseInstruction] 分支指令: " + ins);
            parseBrInst((BrInst) ins, predefine);
        } else if (ins instanceof CallInst) {
            System.out.println("[parseInstruction] 函数调用指令: " + ins);
            parseCallInst((CallInst) ins, predefine);
        } else if (ins instanceof ConversionInst) {
            System.out.println("[parseInstruction] 类型转换指令: " + ins);
            parseConversionInst((ConversionInst) ins, predefine);
        } else if (ins instanceof LoadInst) {
            System.out.println("[parseInstruction] 加载指令: " + ins);
            parseLoad((LoadInst) ins, predefine);
        } else if (ins instanceof Move) {
            System.out.println("[parseInstruction] 移动指令: " + ins);
            parseMove((Move) ins, predefine);
        } else if (ins instanceof PtrInst) {
            System.out.println("[parseInstruction] 指针操作指令: " + ins);
            parsePtrInst((PtrInst) ins, predefine);
        } else if (ins instanceof PtrSubInst) {
            System.out.println("[parseInstruction] 指针减法指令: " + ins);
            parsePtrSubInst((PtrSubInst) ins, predefine);
        } else if (ins instanceof RetInst) {
            System.out.println("[parseInstruction] 返回指令: " + ins);
            parseRetInst((RetInst) ins, predefine);
        } else if (ins instanceof StoreInst) {
            System.out.println("[parseInstruction] 存储指令: " + ins);
            parseStore((StoreInst) ins, predefine);
        } else {
            System.err.println("[parseInstruction] 未知指令类型: " + ins);
        }
    }

    public void parseBinaryInst(BinaryInst binaryInst, boolean predefine) {
        // 处理 IR 二元运算指令，转换为 ARM 指令
        if (binaryInst.getOp() == OP.Add) {
            System.out.println("[parseBinaryInst] 二元运算: 加法 " + binaryInst);
            parseAdd(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Sub) {
            System.out.println("[parseBinaryInst] 二元运算: 减法 " + binaryInst);
            parseSub(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Mul) {
            System.out.println("[parseBinaryInst] 二元运算: 乘法 " + binaryInst);
            parseMul(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Div) {
            System.out.println("[parseBinaryInst] 二元运算: 除法 " + binaryInst);
            parseDiv(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Mod) {
            System.out.println("[parseBinaryInst] 二元运算: 取模 " + binaryInst);
            parseMod(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.And) {
            System.out.println("[parseBinaryInst] 二元运算: 按位与 " + binaryInst);
            parseAnd(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Or) {
            System.out.println("[parseBinaryInst] 二元运算: 按位或 " + binaryInst);
            parseOr(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Xor) {
            System.out.println("[parseBinaryInst] 二元运算: 按位异或 " + binaryInst);
            parseXor(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Fsub || binaryInst.getOp() == OP.Fadd
                || binaryInst.getOp() == OP.Fmul || binaryInst.getOp() == OP.Fdiv) {
            System.out.println("[parseBinaryInst] 浮点二元运算: " + binaryInst.getOp() + " " + binaryInst);
            parseFbin(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Fmod) {
            System.out.println("[parseBinaryInst] 浮点取模暂不支持 " + binaryInst);
            assert false;
        } else if (binaryInst.getOp() == OP.Lt || binaryInst.getOp() == OP.Le
                || binaryInst.getOp() == OP.Gt || binaryInst.getOp() == OP.Ge
                || binaryInst.getOp() == OP.Eq || binaryInst.getOp() == OP.Ne) {
            System.out.println("[parseBinaryInst] 整型比较运算: " + binaryInst.getOp() + " " + binaryInst);
            parseIcmp(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.FLt || binaryInst.getOp() == OP.FLe
                || binaryInst.getOp() == OP.FGt || binaryInst.getOp() == OP.FGe
                || binaryInst.getOp() == OP.FEq || binaryInst.getOp() == OP.FNe) {
            System.out.println("[parseBinaryInst] 浮点比较运算: " + binaryInst.getOp() + " " + binaryInst);
            parseFcmp(binaryInst, predefine);
        } else {
            System.err.println("[parseBinaryInst] 未知二元运算类型: " + binaryInst.getOp() + " " + binaryInst);
            assert false;
        }
    }

    public void parseMod(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(binaryInst, insList);
        ArmVirReg resReg = getResReg(binaryInst, ArmVirReg.RegType.intType);

        // 处理64位整数取模操作
        if(binaryInst.I64) {
            // 在AArch64中，使用内置的除法指令并计算余数
            System.out.println("[parseMod] 处理64位整数取模操作");

            // 获取被除数和除数
            ArmReg dividend = getRegOnlyFromValue(binaryInst.getLeftVal(), insList, predefine);
            ArmReg divisor = getRegOnlyFromValue(binaryInst.getRightVal(), insList, predefine);

            // 临时寄存器用于存储商
            ArmReg quotientReg = getNewIntReg();

            // 执行有符号除法
            addInstr(new ArmBinary(
                    new ArrayList<>(Arrays.asList(dividend, divisor)),
                    quotientReg,
                    ArmBinary.ArmBinaryType.sdiv),
                    insList, predefine);

            // 计算余数：remainder = dividend - (quotient * divisor)
            // 先计算 quotient * divisor
            ArmReg tempReg = getNewIntReg();
            addInstr(new ArmBinary(
                    new ArrayList<>(Arrays.asList(quotientReg, divisor)),
                    tempReg,
                    ArmBinary.ArmBinaryType.mul),
                    insList, predefine);

            // 然后计算 dividend - (quotient * divisor)
            addInstr(new ArmBinary(
                    new ArrayList<>(Arrays.asList(dividend, tempReg)),
                    resReg,
                    ArmBinary.ArmBinaryType.sub),
                    insList, predefine);

            value2Reg.put(binaryInst, resReg);
            return;
        }

        // 处理32位整数取模操作
        ArmOperand leftOperand = getRegOnlyFromValue(binaryInst.getLeftVal(), insList, predefine);

        // 优化：如果除数是2的幂，可以使用位操作优化
        if (binaryInst.getRightVal() instanceof ConstInteger) {
            int val = ((ConstInteger) binaryInst.getRightVal()).getValue();
            int temp = Math.abs(val);

            if ((temp & (temp - 1)) == 0 && temp > 0) {  // 是否为2的幂
                System.out.println("[parseMod] 使用2的幂优化取模: " + temp);

                // 对于2的幂取模，可以使用 x & (power-1) 计算
                // 但需要处理负数情况
                int mask = temp - 1;

                // 获取结果的符号（与被除数相同）
                ArmReg signReg = getNewIntReg();
                addInstr(new ArmBinary(
                        new ArrayList<>(Arrays.asList(leftOperand, new ArmImm(31))),
                        signReg, ArmBinary.ArmBinaryType.asr),
                        insList, predefine);

                // 计算 x & (power-1)
                ArmReg maskReg = getNewIntReg();
                addInstr(new ArmLi(new ArmImm(mask), maskReg), insList, predefine);

                ArmReg andReg = getNewIntReg();
                addInstr(new ArmBinary(
                        new ArrayList<>(Arrays.asList(leftOperand, maskReg)),
                        andReg, ArmBinary.ArmBinaryType.and),
                        insList, predefine);

                // 如果被除数为负数，需要修正结果
                // 负数需要额外处理：如果结果不为0，需要补充 divisor
                ArmReg tempReg = getNewIntReg();

                // 检查结果是否为0
                addInstr(new ArmCompare(andReg, new ArmImm(0), ArmCompare.CmpType.cmp),
                        insList, predefine);

                // 生成条件移动：如果结果不为0且原数为负数，需要从除数中减去结果
                addInstr(new ArmLi(new ArmImm(0), tempReg), insList, predefine);
                addInstr(new ArmLi(new ArmImm(temp), resReg), insList, predefine);

                // 条件选择：如果andReg为0或signReg为0（非负数），则选择andReg；否则选择temp-andReg
                addInstr(new ArmCsel(andReg, tempReg, andReg, ArmTools.CondType.eq, ArmCsel.CselType.csel),
                        insList, predefine);

                addInstr(new ArmBinary(
                        new ArrayList<>(Arrays.asList(resReg, andReg)),
                        resReg, ArmBinary.ArmBinaryType.sub),
                        insList, predefine);

                // 基于符号的条件选择最终结果
                addInstr(new ArmCompare(signReg, new ArmImm(0), ArmCompare.CmpType.cmp),
                        insList, predefine);
                addInstr(new ArmCsel(andReg, resReg, resReg, ArmTools.CondType.lt, ArmCsel.CselType.csel),
                        insList, predefine);

                value2Reg.put(binaryInst, resReg);
                return;
            }
        }

        // 一般情况：使用标准除法和乘法计算余数
        ArmOperand rightOperand = getRegOnlyFromValue(binaryInst.getRightVal(), insList, predefine);

        // 计算商：dividend / divisor
        ArmReg quotientReg = getNewIntReg();
        addInstr(new ArmBinary(
                new ArrayList<>(Arrays.asList(leftOperand, rightOperand)),
                quotientReg,
                ArmBinary.ArmBinaryType.sdiv),
                insList, predefine);

        // 计算商×除数：(dividend / divisor) * divisor
        ArmReg productReg = getNewIntReg();
        addInstr(new ArmBinary(
                new ArrayList<>(Arrays.asList(quotientReg, rightOperand)),
                productReg,
                ArmBinary.ArmBinaryType.mul),
                insList, predefine);

        // 计算余数：dividend - ((dividend / divisor) * divisor)
        addInstr(new ArmBinary(
                new ArrayList<>(Arrays.asList(leftOperand, productReg)),
                resReg,
                ArmBinary.ArmBinaryType.sub),
                insList, predefine);

        value2Reg.put(binaryInst, resReg);
    }

    // 判断是否为 AArch64 标准支持的整型比较操作符
    // AArch64 支持的整型比较包括：==, !=, >=, >, <=, <
    public boolean isIntCmpType(OP op) {
        boolean result = op == OP.Eq || op == OP.Ne || op == OP.Ge || op == OP.Gt
                || op == OP.Le || op == OP.Lt;
        System.out.println("[isIntCmpType] 检查操作符 " + op + " 是否为 AArch64 整型比较: " + result);
        return result;
    }

    // 检查是否为 AArch64 标准支持的浮点比较操作符
    // AArch64 支持的浮点比较包括：==, !=, >=, >, <=, <
    public boolean isFloatCmpType(OP op) {
        boolean result = op == OP.FEq || op == OP.FNe || op == OP.FGe || op == OP.FGt
                || op == OP.FLe || op == OP.FLt;
        System.out.println("[isFloatCmpType] 检查操作符 " + op + " 是否为 AArch64 浮点比较: " + result);
        return result;
    }

    public void parseBrInst(BrInst brInst, boolean predefine) {
            // 检查是否已预处理（预定义指令），AArch64标准分支指令处理入口
            if (preProcess(brInst, predefine)) {
                return;
            }
            ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
            predefines.put(brInst, insList);
            assert value2Label.containsKey(brInst.getParentbb())
                    && value2Label.get(brInst.getParentbb()) instanceof ArmBlock;
            ArmBlock block = (ArmBlock) value2Label.get(brInst.getParentbb());

            // AArch64: 跳转指令或真假分支相同，直接生成跳转
            if (brInst.isJump() || brInst.getTrueBlock() == brInst.getFalseBlock()) {
                assert value2Label.containsKey(brInst.getJumpBlock());
                System.out.println("[parseBrInst] 生成无条件跳转指令到块: " + brInst.getJumpBlock().getName());
                addInstr(new ArmJump(value2Label.get(brInst.getJumpBlock()), block), insList, predefine);
            } else {
                // 处理条件分支，先确保判断值已生成
                if (!value2Reg.containsKey(brInst.getJudVal())) {
                    assert brInst.getJudVal() instanceof Instruction;
                    System.out.println("[parseBrInst] 生成条件判断值: " + brInst.getJudVal());
                    parseInstruction((Instruction) brInst.getJudVal(), true);
                }
                BasicBlock parentBlock = brInst.getParentbb();
                Function func = parentBlock.getParentFunc();
                BasicBlock nextBlock = null;
                for (IList.INode<BasicBlock, Function> bb : func.getBbs()) {
                    if (bb.getValue() == parentBlock && bb.getNext() != null) {
                        nextBlock = bb.getNext().getValue();
                    }
                }
                // AArch64: 使用cmp指令比较判断值与1
                System.out.println("[parseBrInst] 生成AArch64条件比较指令 cmp 判断值与1");
                addInstr(new ArmCompare(value2Reg.get(brInst.getJudVal()), new ArmImm(1),
                        ArmCompare.CmpType.cmp), insList, predefine);

                // 生成条件分支指令，ne为假分支，eq为真分支
                if (nextBlock != brInst.getFalseBlock()) {
                    System.out.println("[parseBrInst] 生成条件分支(ne)跳转到块: " + brInst.getFalseBlock().getName());
                    ArmBranch br = new ArmBranch((ArmBlock) value2Label.get(brInst.getFalseBlock()),
                            ArmTools.CondType.ne);
                    addInstr(br, insList, predefine);
                    br.setPredSucc(curArmBlock);
                }
                if (nextBlock != brInst.getTrueBlock()) {
                    System.out.println("[parseBrInst] 生成条件分支(eq)跳转到块: " + brInst.getTrueBlock().getName());
                    ArmBranch br = new ArmBranch((ArmBlock) value2Label.get(brInst.getTrueBlock()),
                            ArmTools.CondType.eq);
                    addInstr(br, insList, predefine);
                    br.setPredSucc(curArmBlock);
                }
            }
        }

    /**
         * 解析整型比较指令，生成符合 AArch64 标准的比较和条件赋值指令
         * 支持 ==, !=, >=, >, <=, < 操作
         */
        public void parseIcmp(BinaryInst binaryInst, boolean predefine) {
            // 检查是否已预处理（预定义指令）
            if (preProcess(binaryInst, predefine)) {
                System.out.println("[parseIcmp] 已预处理，跳过: " + binaryInst);
                return;
            }
            ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
            predefines.put(binaryInst, insList);

            // 获取左右操作数，可能为寄存器或立即数
            ArmOperand leftOp = getRegOrImmFromValue(binaryInst.getOperands().get(0), insList, predefine);
            ArmOperand rightOp = getRegOrImmFromValue(binaryInst.getOperands().get(1), insList, predefine);

            // 匹配 AArch64 支持的比较类型
            ArmTools.CondType type = null;
            switch (binaryInst.getOp()) {
                case Eq -> type = ArmTools.CondType.eq;
                case Ne -> type = ArmTools.CondType.ne;
                case Ge -> type = ArmTools.CondType.ge;
                case Gt -> type = ArmTools.CondType.gt;
                case Le -> type = ArmTools.CondType.le;
                case Lt -> type = ArmTools.CondType.lt;
                default -> {
                    System.err.println("[parseIcmp] 不支持的比较操作类型: " + binaryInst.getOp());
                    assert false;
                }
            }

            // AArch64 标准：如果左操作数为立即数，交换左右并反转比较类型
            if (leftOp instanceof ArmImm) {
                ArmOperand op = leftOp;
                leftOp = rightOp;
                rightOp = op;
                type = getOnlyRevBigSmallType(type);
                System.out.println("[parseIcmp] 左操作数为立即数，交换并反转比较类型: " + type);
            }

            // 生成 cmp 指令，AArch64 要求立即数可编码，否则先加载到寄存器
            if (rightOp instanceof ArmImm) {
                if (ArmTools.isArmImmCanBeEncoded(((ArmImm) rightOp).getIntValue())) {
                    addInstr(new ArmCompare(leftOp, rightOp, ArmCompare.CmpType.cmp), insList, predefine);
                    System.out.println("[parseIcmp] 生成 cmp 指令: " + leftOp + " 与 " + rightOp);
                } else {
                    ArmReg reg = getNewIntReg();
                    addInstr(new ArmLi(rightOp, reg), insList, predefine);
                    addInstr(new ArmCompare(leftOp, reg, ArmCompare.CmpType.cmp), insList, predefine);
                    System.out.println("[parseIcmp] 立即数不可编码，先加载到寄存器: " + reg);
                }
            } else {
                addInstr(new ArmCompare(leftOp, rightOp, ArmCompare.CmpType.cmp), insList, predefine);
                System.out.println("[parseIcmp] 生成 cmp 指令: " + leftOp + " 与 " + rightOp);
            }

            // 条件赋值，AArch64 标准：先赋0（反条件），再赋1（条件成立）
            ArmReg reg = getNewIntReg();
            assert type != null;
            addInstr(new ArmLi(new ArmImm(0), reg, ArmTools.getRevCondType(type)), insList, predefine);
            addInstr(new ArmLi(new ArmImm(1), reg, type), insList, predefine);
            System.out.println("[parseIcmp] 条件赋值: " + reg + "，条件类型: " + type);

            // 保存结果寄存器
            value2Reg.put(binaryInst, reg);
        }

    public void parseFcmp(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(binaryInst, insList);
        ArmReg leftReg = getRegOnlyFromValue(binaryInst.getOperands().get(0), insList, predefine);
        ArmReg rightReg = getRegOnlyFromValue(binaryInst.getOperands().get(1), insList, predefine);
        ArmTools.CondType type;
        switch (binaryInst.getOp()) {
            case FEq -> type = ArmTools.CondType.eq;
            case FNe -> type = ArmTools.CondType.ne;
            case FGe -> type = ArmTools.CondType.ge;
            case FGt -> type = ArmTools.CondType.gt;
            case FLe -> type = ArmTools.CondType.le;
            case FLt -> type = ArmTools.CondType.lt;
            default -> type = null;
        }
        assert type != null;
        addInstr(new ArmVCompare(leftReg, rightReg), insList, predefine);
        ArmReg reg = getNewIntReg();
        addInstr(new ArmLi(new ArmImm(0), reg, ArmTools.getRevCondType(type))
                , insList, predefine);
        addInstr(new ArmLi(new ArmImm(1), reg, type)
                , insList, predefine);
        value2Reg.put(binaryInst, reg);
    }

    public void parseAdd(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        ArmVirReg resReg = getResReg(binaryInst, ArmVirReg.RegType.intType);
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        if (leftVal instanceof ConstInteger) {
            Value tempVal = leftVal;
            leftVal = rightVal;
            rightVal = tempVal;
        }
        ArmReg left = getRegOnlyFromValue(leftVal, insList, predefine);
        ArmOperand right = getRegOrImmFromValue(rightVal, insList, predefine);
        if (right instanceof ArmImm) {
            if (((ArmImm) right).getValue() == 0) {
                ArmMv mv = new ArmMv(left, resReg);
                addInstr(mv, insList, predefine);
            } else {
                //TODO: 是否能换成减法呢
                if (ArmTools.isArmImmCanBeEncoded(((ArmImm) right).getIntValue())) {
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left,
                            new ArmImm(((ArmImm) right).getValue()))), resReg,
                            ArmBinary.ArmBinaryType.add);
                    addInstr(binary, insList, predefine);
                } else {
                    ArmReg assistReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(((ArmImm) right).getValue()), assistReg);
                    addInstr(li, insList, predefine);
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left, assistReg)), resReg,
                            ArmBinary.ArmBinaryType.add);
                    addInstr(binary, insList, predefine);
                }
            }
        } else {
            ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                    ArmBinary.ArmBinaryType.add);
            addInstr(binary, insList, predefine);
        }
        value2Reg.put(binaryInst, resReg);
        predefines.put(binaryInst, insList);
    }

    public void parseAnd(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        ArmVirReg resReg = getResReg(binaryInst, ArmVirReg.RegType.intType);
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        if (leftVal instanceof ConstInteger) {
            Value tempVal = leftVal;
            leftVal = rightVal;
            rightVal = tempVal;
        }
        ArmReg left = getRegOnlyFromValue(leftVal, insList, predefine);
        ArmOperand right = getRegOrImmFromValue(rightVal, insList, predefine);
        if (right instanceof ArmImm) {
            if (((ArmImm) right).getValue() == 0) {
                ArmMv mv = new ArmMv(left, resReg);
                addInstr(mv, insList, predefine);
            } else {
                // ARMv8-A: Check if it's a valid logical immediate for AND instruction
                if (ArmTools.isLogicalImmediate(((ArmImm) right).getValue())) {
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left,
                            new ArmImm(((ArmImm) right).getValue()))), resReg,
                            ArmBinary.ArmBinaryType.and);
                    addInstr(binary, insList, predefine);
                } else {
                    ArmReg assistReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(((ArmImm) right).getValue()), assistReg);
                    addInstr(li, insList, predefine);
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left, assistReg)), resReg,
                            ArmBinary.ArmBinaryType.and);
                    addInstr(binary, insList, predefine);
                }
            }
        } else {
            ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                    ArmBinary.ArmBinaryType.and);
            addInstr(binary, insList, predefine);
        }
        value2Reg.put(binaryInst, resReg);
        predefines.put(binaryInst, insList);
    }

    public void parseOr(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        ArmVirReg resReg = getResReg(binaryInst, ArmVirReg.RegType.intType);
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        if (leftVal instanceof ConstInteger) {
            Value tempVal = leftVal;
            leftVal = rightVal;
            rightVal = tempVal;
        }
        ArmReg left = getRegOnlyFromValue(leftVal, insList, predefine);
        ArmOperand right = getRegOrImmFromValue(rightVal, insList, predefine);
        if (right instanceof ArmImm) {
            if (((ArmImm) right).getValue() == 0) {
                ArmMv mv = new ArmMv(left, resReg);
                addInstr(mv, insList, predefine);
            } else {
                //TODO: 是否能换成减法呢
                if (ArmTools.isLogicalImmediate(((ArmImm) right).getValue())) {
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left,
                            new ArmImm(((ArmImm) right).getValue()))), resReg,
                            ArmBinary.ArmBinaryType.orr);
                    addInstr(binary, insList, predefine);
                } else {
                    ArmReg assistReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(((ArmImm) right).getValue()), assistReg);
                    addInstr(li, insList, predefine);
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left, assistReg)), resReg,
                            ArmBinary.ArmBinaryType.orr);
                    addInstr(binary, insList, predefine);
                }
            }
        } else {
            ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                    ArmBinary.ArmBinaryType.orr);
            addInstr(binary, insList, predefine);
        }
        value2Reg.put(binaryInst, resReg);
        predefines.put(binaryInst, insList);
    }

    public void parseXor(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        ArmVirReg resReg = getResReg(binaryInst, ArmVirReg.RegType.intType);
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        if (leftVal instanceof ConstInteger) {
            Value tempVal = leftVal;
            leftVal = rightVal;
            rightVal = tempVal;
        }
        ArmReg left = getRegOnlyFromValue(leftVal, insList, predefine);
        ArmOperand right = getRegOrImmFromValue(rightVal, insList, predefine);
        if (right instanceof ArmImm) {
            if (((ArmImm) right).getValue() == 0) {
                ArmMv mv = new ArmMv(left, resReg);
                addInstr(mv, insList, predefine);
            } else {
                //TODO: 是否能换成减法呢
                if (ArmTools.isLogicalImmediate(((ArmImm) right).getValue())) {
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left,
                            new ArmImm(((ArmImm) right).getValue()))), resReg,
                            ArmBinary.ArmBinaryType.eor);
                    addInstr(binary, insList, predefine);
                } else {
                    ArmReg assistReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(((ArmImm) right).getValue()), assistReg);
                    addInstr(li, insList, predefine);
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left, assistReg)), resReg,
                            ArmBinary.ArmBinaryType.eor);
                    addInstr(binary, insList, predefine);
                }
            }
        } else {
            ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                    ArmBinary.ArmBinaryType.eor);
            addInstr(binary, insList, predefine);
        }
        value2Reg.put(binaryInst, resReg);
        predefines.put(binaryInst, insList);
    }

    public void parseSub(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        ArmVirReg resReg = getResReg(binaryInst, ArmVirReg.RegType.intType);
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        ArmOperand left = getRegOrImmFromValue(leftVal, insList, predefine);
        ArmOperand right = getRegOrImmFromValue(rightVal, insList, predefine);
        if (left instanceof ArmImm) {
            assert !(right instanceof ArmImm);
            if (((ArmImm) left).getValue() == 0) {
                ArmRev rev = new ArmRev((ArmReg) right, resReg);
                addInstr(rev, insList, predefine);
            } else {
                if (ArmTools.isArmImmCanBeEncoded(((ArmImm) left).getIntValue())) {
                    // For rsb (reverse subtract), we need to compute left - right
                    // In ARM64, use sub with operands swapped: sub res, left_reg, right_reg
                    ArmReg assistReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(((ArmImm) left).getValue()), assistReg);
                    addInstr(li, insList, predefine);
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(assistReg, right)), resReg,
                            ArmBinary.ArmBinaryType.sub);
                    addInstr(binary, insList, predefine);
                } else {
                    ArmReg assistReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(((ArmImm) left).getValue()), assistReg);
                    addInstr(li, insList, predefine);
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(assistReg, right)), resReg,
                            ArmBinary.ArmBinaryType.sub);
                    addInstr(binary, insList, predefine);
                }
            }
        } else {
            assert left instanceof ArmReg;
            if (right instanceof ArmImm) {
                if (((ArmImm) right).getValue() == 0) {
                    ArmMv mv = new ArmMv((ArmReg) left, resReg);
                    addInstr(mv, insList, predefine);
                } else {
                    if (ArmTools.isArmImmCanBeEncoded(((ArmImm) right).getIntValue())) {
                        ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left,
                                new ArmImm(((ArmImm) right).getValue()))), resReg,
                                ArmBinary.ArmBinaryType.sub);
                        addInstr(binary, insList, predefine);
                    } else {
                        ArmReg assistReg = getNewIntReg();
                        ArmLi li = new ArmLi(new ArmImm(((ArmImm) right).getValue()), assistReg);
                        addInstr(li, insList, predefine);
                        ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left, assistReg)), resReg,
                                ArmBinary.ArmBinaryType.sub);
                        addInstr(binary, insList, predefine);
                    }
                }
            } else {
                ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                        ArmBinary.ArmBinaryType.sub);
                addInstr(binary, insList, predefine);
            }
        }
        value2Reg.put(binaryInst, resReg);
        predefines.put(binaryInst, insList);
    }

    private ArrayList<Integer> canOpt(int num) {
        ArrayList<Integer> ans = new ArrayList<>();
        int i = 1;
        while (i < num) {
            i *= 2;
        }
        if (i == num) {
            ans.add(i);
            return ans;
        }
        if (BigInteger.valueOf(Math.abs(num)).bitCount() == 2) {
            for (int j = 1; j < i; j *= 2) {
                if (((num - j) & (num - j - 1)) == 0) {
                    ans.add(j);
                    ans.add(num - j);
                    break;
                }
            }
        } else if (BigInteger.valueOf(Math.abs(i - num)).bitCount() == 1) {
            ans.add(i);
            ans.add(num - i);
        }
        return ans;
    }

    public int getShift(int temp) {
        int shift = 0;
        while (temp >= 2) {
            shift++;
            temp /= 2;
        }
        return shift;
    }

    public void parseMul(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        if (binaryInst.I64) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(binaryInst, insList);
        //TODO: ready to be optimized
        ArmReg resReg = getResReg(binaryInst, ArmVirReg.RegType.intType);
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        if (leftVal instanceof ConstInteger) {
            Value temp = leftVal;
            leftVal = rightVal;
            rightVal = temp;
        }
        ArmReg leftOperand = getRegOnlyFromValue(leftVal, insList, predefine);
        ArmOperand rightOperand;
        if (rightVal instanceof ConstInteger) {
            if (((ConstInteger) rightVal).getValue() == 1) {
                addInstr(new ArmMv(leftOperand, resReg), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else if (((ConstInteger) rightVal).getValue() == -1) {
                addInstr(new ArmRev(leftOperand, resReg), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else if (((ConstInteger) rightVal).getValue() == 0) {
                addInstr(new ArmLi(new ArmImm(0), resReg), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else {
                ArrayList<Integer> ans = canOpt(Math.abs(((ConstInteger) rightVal).getValue()));
                if (ans.size() > 0) {
                    if (((ConstInteger) rightVal).getValue() < 0) {
                        ArmVirReg reg = getNewIntReg();
                        addInstr(new ArmRev(leftOperand, reg), insList, predefine);
                        leftOperand = reg;
                    }
                    if (ans.size() == 1) {
                        int shift = getShift(Math.abs(ans.get(0)));
                        addInstr(new ArmBinary(
                                new ArrayList<>(Arrays.asList(leftOperand, new ArmImm(shift))),
                                resReg, ArmBinary.ArmBinaryType.lsl), insList, predefine);
                        value2Reg.put(binaryInst, resReg);
                        return;
                    } else if (ans.size() == 2) {
                        assert ans.get(0) > 0;
                        int shift = getShift(Math.abs(ans.get(0)));
                        if (shift == 0) {
                            addInstr(new ArmMv(leftOperand, resReg), insList, predefine);
                        } else {
                            addInstr(new ArmBinary(
                                    new ArrayList<>(Arrays.asList(leftOperand, new ArmImm(shift))),
                                    resReg, ArmBinary.ArmBinaryType.lsl), insList, predefine);
                        }
                        boolean flag = ans.get(1) > 0;
                        shift = getShift(Math.abs(ans.get(1)));
                        if (flag) {
                            addInstr(new ArmBinary(
                                    new ArrayList<>(Arrays.asList(resReg, leftOperand)),
                                    resReg, shift, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.add), insList, predefine);
                        } else {
                            if (shift == 0) {
                                addInstr(new ArmBinary(
                                        new ArrayList<>(Arrays.asList(resReg, leftOperand)),
                                        resReg, ArmBinary.ArmBinaryType.sub), insList, predefine);
                            } else {
                                addInstr(new ArmBinary(
                                        new ArrayList<>(Arrays.asList(resReg, leftOperand)),
                                        resReg, shift, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.sub), insList, predefine);
                            }
                        }
                        value2Reg.put(binaryInst, resReg);
                        return;
                    }
                }
            }
        }
        rightOperand = getRegOnlyFromValue(rightVal, insList, predefine);
        ArmBinary mul = new ArmBinary(new ArrayList<>(
                Arrays.asList(leftOperand, rightOperand)), resReg, ArmBinary.ArmBinaryType.mul);
        value2Reg.put(binaryInst, resReg);
        addInstr(mul, insList, predefine);
    }

    /**
     * 解析整型除法指令，生成符合 AArch64 标准的除法指令
     * 支持常数除法优化，包括除以1、-1、2的幂以及更复杂的常数优化
     * 其余情况使用标准 sdiv 指令
     */
    public void parseDiv(BinaryInst binaryInst, boolean predefine) {
        // 检查是否已预处理（预定义指令）
        if (preProcess(binaryInst, predefine)) {
            System.out.println("[parseDiv] 已预处理，跳过: " + binaryInst);
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(binaryInst, insList);
        // 获取结果寄存器
        ArmVirReg resReg = getResReg(binaryInst, ArmVirReg.RegType.intType);
        // 获取被除数寄存器
        ArmReg leftOperand = getRegOnlyFromValue(binaryInst.getLeftVal(), insList, predefine);
        ArmOperand rightOperand;
        // 常数除法优化
        if (binaryInst.getRightVal() instanceof ConstInteger) {
            int val = ((ConstInteger) binaryInst.getRightVal()).getValue();
            if (val == 1) {
                // 除以1，直接赋值
                System.out.println("[parseDiv] 除数为1，直接赋值: " + leftOperand);
                addInstr(new ArmMv(leftOperand, resReg), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else if (val == -1) {
                // 除以-1，取反
                System.out.println("[parseDiv] 除数为-1，取反: " + leftOperand);
                addInstr(new ArmRev(leftOperand, resReg), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else if ((Math.abs(val) & (Math.abs(val) - 1)) == 0) {
                // 除以2的幂，使用算术右移优化
                System.out.println("[parseDiv] 除数为2的幂，使用算术右移优化: " + val);
                boolean flag = val < 0;
                int temp = Math.abs(val);
                int shift = 0;
                while (temp >= 2) {
                    shift++;
                    temp /= 2;
                }
                if (flag) {
                    ArmVirReg reg1 = getNewIntReg();
                    addInstr(new ArmRev(leftOperand, reg1), insList, predefine);
                    leftOperand = reg1;
                }
                ArmReg reg = getNewIntReg();
                addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(leftOperand, new ArmImm(31))),
                        reg, ArmBinary.ArmBinaryType.asr), insList, predefine);
                addInstr(new ArmBinary(
                        new ArrayList<>(Arrays.asList(reg, new ArmImm(32 - shift))),
                        reg, ArmBinary.ArmBinaryType.lsr), insList, predefine);
                addInstr(new ArmBinary(
                        new ArrayList<>(Arrays.asList(leftOperand, reg)),
                        reg, ArmBinary.ArmBinaryType.add), insList, predefine);
                addInstr(new ArmBinary(
                        new ArrayList<>(Arrays.asList(reg, new ArmImm(shift))),
                        resReg, ArmBinary.ArmBinaryType.asr), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else if (Config.divOptOpen) {
                // 更复杂的常数除法优化（乘法+移位法）
                System.out.println("[parseDiv] 启用常数除法优化: " + val);
                boolean flag = ((ConstInteger) binaryInst.getRightVal()).getValue() < 0;
                int divNum = ((ConstInteger) binaryInst.getRightVal()).getValue();
                long nc = ((long) 1 << 31) - (((long) 1 << 31) % divNum) - 1;
                long p = 32;
                while (((long) 1 << p) <= nc * (divNum - ((long) 1 << p) % divNum)) {
                    p++;
                }
                long m = ((((long) 1 << p) + (long) divNum - ((long) 1 << p) % divNum) / (long) divNum);
                long n = (long) ((m << 32) >>> 32);
                int shift = (int) (p - 32);
                ArmReg reg1 = getNewIntReg();
                ArmReg reg2 = getNewIntReg();
                addInstr(new ArmLi(new ArmImm((int) n), reg1), insList, predefine);
                if (m >= 2147483648L) {
                    ArmFma fma = new ArmFma(leftOperand, reg1, leftOperand, reg2);
                    addInstr(fma, insList, predefine);
                    //fma.setSigned(true);
                } else {
                    addInstr(new ArmLongMul(reg2, leftOperand, reg1), insList, predefine);
                }
                ArmReg reg3 = getNewIntReg();
                addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(reg2, new ArmImm(shift))),
                        reg3, ArmBinary.ArmBinaryType.asr), insList, predefine);
                addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(reg3, leftOperand)),
                        resReg, 31, ArmBinary.ArmShiftType.LSR, ArmBinary.ArmBinaryType.add),
                        insList, predefine);
                if (flag) {
                    addInstr(new ArmRev(resReg, resReg), insList, predefine);
                }
                value2Reg.put(binaryInst, resReg);
                return;
            }
        }
        // 其余情况，使用标准 sdiv 指令
        System.out.println("[parseDiv] 使用标准 sdiv 指令: " + leftOperand + " / " + binaryInst.getRightVal());
        rightOperand = getRegOnlyFromValue(binaryInst.getRightVal(), insList, predefine);
        ArmBinary div = new ArmBinary(new ArrayList<>(
                Arrays.asList(leftOperand, rightOperand)), resReg, ArmBinary.ArmBinaryType.sdiv);
        value2Reg.put(binaryInst, resReg);
        addInstr(div, insList, predefine);
    }

    public void parseFbin(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(binaryInst, insList);
        ArmVirReg resReg = getResReg(binaryInst, ArmVirReg.RegType.floatType);
        ArmOperand left = getRegOnlyFromValue(binaryInst.getLeftVal(), insList, predefine);
        ArmOperand right = getRegOnlyFromValue(binaryInst.getRightVal(), insList, predefine);
        ArmBinary.ArmBinaryType type = null;
        switch (binaryInst.getOp()) {
            case Fadd -> type = ArmBinary.ArmBinaryType.vadd;
            case Fsub -> type = ArmBinary.ArmBinaryType.vsub;
            case Fmul -> type = ArmBinary.ArmBinaryType.vmul;
            case Fdiv -> type = ArmBinary.ArmBinaryType.vdiv;
        }
        ArmBinary binary = new ArmBinary(new ArrayList<>(
                Arrays.asList(left, right)), resReg, type);
        value2Reg.put(binaryInst, resReg);
        addInstr(binary, insList, predefine);
    }


    public void parseConversionInst(ConversionInst conversionInst, boolean predefine) {
        if (preProcess(conversionInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(conversionInst, insList);
        ArmReg srcReg = getRegOnlyFromValue(conversionInst.getValue(), insList, predefine);
        if (conversionInst.getOp() == OP.Ftoi) {
            assert srcReg instanceof ArmVirReg && ((ArmVirReg) srcReg).regType == ArmVirReg.RegType.floatType;
            ArmReg resReg = getResReg(conversionInst, ArmVirReg.RegType.intType);
            ArmVirReg assistReg = getNewFloatReg();
            ArmCvt cvt = new ArmCvt(srcReg, false, assistReg);
            ArmConvMv convMv = new ArmConvMv(assistReg, resReg);
            addInstr(cvt, insList, predefine);
            addInstr(convMv, insList, predefine);
            value2Reg.put(conversionInst, resReg);
        } else {
            assert conversionInst.getOp() == OP.Itof;
            assert srcReg instanceof ArmVirReg && ((ArmVirReg) srcReg).regType == ArmVirReg.RegType.intType;
            ArmReg resReg = getResReg(conversionInst, ArmVirReg.RegType.floatType);
            ArmConvMv convMv = new ArmConvMv(srcReg, resReg);
            ArmCvt cvt = new ArmCvt(resReg, true, resReg);
            addInstr(convMv, insList, predefine);
            addInstr(cvt, insList, predefine);
            value2Reg.put(conversionInst, resReg);
        }
    }

    public void parseAlloc(AllocInst allocInst, boolean predefine) {
        if (!predefines.containsKey(allocInst)) {
            curArmFunction.alloc(allocInst);
            predefines.put(allocInst, null);
        }
    }

    public void parseMove(Move mv, boolean predefine) {
        if (preProcess(mv, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(mv, insList);
        if (!value2Reg.containsKey(mv.getDestination()) && !(mv.getDestination() instanceof Argument)) {
            if (mv.getDestination().getType().isFloatTy()) {
                value2Reg.put(mv.getDestination(), getNewFloatReg());
            } else {
                value2Reg.put(mv.getDestination(), getNewIntReg());
            }
        }
        ArmReg src = getRegOnlyFromValue(mv.getSource(), insList, predefine);
        ArmReg dst = getRegOnlyFromValue(mv.getDestination(), insList, predefine);
        if (mv.getDestination().getType().isFloatTy()) {
            ArmFMv fmv = new ArmFMv(src, dst);
            addInstr(fmv, insList, predefine);
        } else {
            ArmMv move = new ArmMv(src, dst);
            addInstr(move, insList, predefine);
        }
    }

    public void parseStore(StoreInst storeInst, boolean predefine) {
        // 检查是否已预处理（预定义指令），AArch64标准存储指令处理入口
        if (preProcess(storeInst, predefine)) {
            System.out.println("[parseStore] 已预处理，跳过: " + storeInst);
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(storeInst, insList);
        ArmReg stoReg = getRegOnlyFromValue(storeInst.getValue(), insList, predefine);

        // AArch64: 指针类型存储（包括指针和指针偏移）
        if (storeInst.getPointer() instanceof PtrInst || storeInst.getPointer() instanceof PtrSubInst) {
            if (!(value2Reg.containsKey(storeInst.getPointer())
                    || ptr2Offset.containsKey(storeInst.getPointer()))) {
                if (storeInst.getPointer() instanceof PtrInst) {
                    System.out.println("[parseStore] 解析PtrInst指针: " + storeInst.getPointer());
                    parsePtrInst((PtrInst) storeInst.getPointer(), true);
                } else {
                    System.out.println("[parseStore] 解析PtrSubInst指针: " + storeInst.getPointer());
                    parsePtrSubInst((PtrSubInst) storeInst.getPointer(), true);
                }
            }
            if (ptr2Offset.containsKey(storeInst.getPointer())) {
                int offset = ptr2Offset.get(storeInst.getPointer());
                // ARM64: 合法立即数偏移存储
                if (ArmTools.isLegalLoadStoreImm(offset) &&
                        !((PointerType) storeInst.getPointer().getType()).getEleType().isFloatTy()) {
                    System.out.println("[parseStore] 使用ArmSw存储整型，偏移: " + offset);
                    ArmSw armSw = new ArmSw(stoReg, ArmCPUReg.getArmSpReg(), new ArmImm(offset));
                    addInstr(armSw, insList, predefine);
                } else if (ArmTools.isLegalVLoadStoreImm(offset)
                        && ((PointerType) storeInst.getPointer().getType()).getEleType().isFloatTy()) {
                    System.out.println("[parseStore] 使用ArmFSw存储浮点型，偏移: " + offset);
                    ArmFSw fsw = new ArmFSw(stoReg, ArmCPUReg.getArmSpReg(), new ArmImm(offset));
                    addInstr(fsw, insList, predefine);
                } else {
                    System.out.println("[parseStore] 偏移超出范围，先加载到寄存器: " + offset);
                    ArmReg assistReg = getNewIntReg();
                    addInstr(new ArmLi(new ArmImm(offset), assistReg), insList, predefine);
                    if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                        ArmSw armSw = new ArmSw(stoReg, ArmCPUReg.getArmSpReg(), assistReg);
                        addInstr(armSw, insList, predefine);
                    } else {
                        ArmFSw fsw = new ArmFSw(stoReg, ArmCPUReg.getArmSpReg(), assistReg);
                        addInstr(fsw, insList, predefine);
                    }
                }
            } else {
                // ARM64: 无偏移，直接存储
                if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                    System.out.println("[parseStore] 无偏移，存储整型到指针: " + storeInst.getPointer());
                    ArmSw sw = new ArmSw(stoReg, value2Reg.get(storeInst.getPointer()), new ArmImm(0));
                    addInstr(sw, insList, predefine);
                } else {
                    System.out.println("[parseStore] 无偏移，存储浮点型到指针: " + storeInst.getPointer());
                    ArmFSw fsw = new ArmFSw(stoReg, value2Reg.get(storeInst.getPointer()), new ArmImm(0));
                    addInstr(fsw, insList, predefine);
                }
            }
        }
        // AArch64: 全局变量存储
        else if (storeInst.getPointer() instanceof GlobalVar) {
            assert value2Label.containsKey(storeInst.getPointer());
            ArmGlobalVariable var = (ArmGlobalVariable) value2Label.get(storeInst.getPointer());
            ArmReg assistReg = getNewIntReg();
            ArmLi li = new ArmLi(var, assistReg);
            addInstr(li, insList, predefine);
            if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                System.out.println("[parseStore] 存储整型到全局变量: " + var);
                ArmSw sw = new ArmSw(stoReg, assistReg, new ArmImm(0));
                addInstr(sw, insList, predefine);
            } else {
                System.out.println("[parseStore] 存储浮点型到全局变量: " + var);
                ArmFSw fsw = new ArmFSw(stoReg, assistReg, new ArmImm(0));
                addInstr(fsw, insList, predefine);
            }
        }
        // AArch64: 局部变量分配存储
        else if (storeInst.getPointer() instanceof AllocInst) {
            if (!curArmFunction.containOffset(storeInst.getPointer())) {
                System.out.println("[parseStore] 分配局部变量空间: " + storeInst.getPointer());
                parseAlloc((AllocInst) storeInst.getPointer(), true);
            }
            int offset = curArmFunction.getOffset(storeInst.getPointer()) * -1;
            if (ArmTools.isLegalLoadStoreImm(offset) &&
                    !((PointerType) storeInst.getPointer().getType()).getEleType().isFloatTy()) {
                System.out.println("[parseStore] 存储整型到局部变量，偏移: " + offset);
                ArmSw armSw = new ArmSw(stoReg, ArmCPUReg.getArmSpReg(), new ArmImm(offset));
                addInstr(armSw, insList, predefine);
            } else if (ArmTools.isLegalVLoadStoreImm(offset)
                    && ((PointerType) storeInst.getPointer().getType()).getEleType().isFloatTy()) {
                System.out.println("[parseStore] 存储浮点型到局部变量，偏移: " + offset);
                ArmFSw fsw = new ArmFSw(stoReg, ArmCPUReg.getArmSpReg(), new ArmImm(offset));
                addInstr(fsw, insList, predefine);
            } else {
                System.out.println("[parseStore] 局部变量偏移超出范围，先加载到寄存器: " + offset);
                ArmReg assistReg = getNewIntReg();
                ArmLi li = new ArmLi(new ArmImm(offset), assistReg);
                addInstr(li, insList, predefine);
                if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                    ArmSw sw = new ArmSw(stoReg, ArmCPUReg.getArmSpReg(), assistReg);
                    addInstr(sw, insList, predefine);
                } else {
                    ArmFSw fsw = new ArmFSw(stoReg, ArmCPUReg.getArmSpReg(), assistReg);
                    addInstr(fsw, insList, predefine);
                }
            }
        }
        // AArch64: 参数或Phi节点存储
        else if (storeInst.getPointer() instanceof Argument || storeInst.getPointer() instanceof Phi) {
            ArmReg assistReg = getRegOnlyFromValue(storeInst.getPointer(), insList, predefine);
            assert storeInst.getPointer().getType() instanceof PointerType;
            if ((((PointerType) storeInst.getPointer().getType()).getEleType().isFloatTy())) {
                System.out.println("[parseStore] 存储浮点型到参数或Phi: " + storeInst.getPointer());
                ArmFSw fsw = new ArmFSw(stoReg, assistReg, new ArmImm(0));
                addInstr(fsw, insList, predefine);
            } else {
                System.out.println("[parseStore] 存储整型到参数或Phi: " + storeInst.getPointer());
                ArmSw sw = new ArmSw(stoReg, assistReg, new ArmImm(0));
                addInstr(sw, insList, predefine);
            }
        }
        // 非AArch64标准支持的存储类型
        else {
            System.err.println("[parseStore] 不支持的存储指针类型: " + storeInst.getPointer());
            assert false;
        }
    }

    public void parseLoad(LoadInst loadInst, boolean predefine) {
        if (preProcess(loadInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(loadInst, insList);
        ArmReg resReg = null;
        if (loadInst.getPointer() instanceof PtrInst || loadInst.getPointer() instanceof PtrSubInst) {
            if (!(value2Reg.containsKey(loadInst.getPointer())
                    || ptr2Offset.containsKey(loadInst.getPointer()))) {
                if (loadInst.getPointer() instanceof PtrInst) {
                    parsePtrInst((PtrInst) loadInst.getPointer(), true);
                } else {
                    parsePtrSubInst((PtrSubInst) loadInst.getPointer(), true);
                }
            }
            if (ptr2Offset.containsKey(loadInst.getPointer())) {
                int offset = ptr2Offset.get(loadInst.getPointer());
                if (ArmTools.isLegalLoadStoreScaledImm(offset, 4) &&
                        !((PointerType) loadInst.getPointer().getType()).getEleType().isFloatTy()) {
                    resReg = getResReg(loadInst, ArmVirReg.RegType.intType);
                    ArmLoad lw = new ArmLoad(ArmCPUReg.getArmSpReg(), new ArmImm(offset), resReg);
                    addInstr(lw, insList, predefine);
                } else if (((PointerType) loadInst.getPointer().getType()).getEleType().isFloatTy()
                        && ArmTools.isLegalVLoadStoreImm(offset)) {
                    resReg = getResReg(loadInst, ArmVirReg.RegType.floatType);
                    ArmVLoad flw = new ArmVLoad(ArmCPUReg.getArmSpReg(), new ArmImm(offset), resReg);
                    addInstr(flw, insList, predefine);
                } else {
                    ArmReg assistReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(offset), assistReg);
                    addInstr(li, insList, predefine);
                    if (!((PointerType) loadInst.getPointer().getType()).getEleType().isFloatTy()) {
                        resReg = getResReg(loadInst, ArmVirReg.RegType.intType);
                        ArmLoad lw = new ArmLoad(ArmCPUReg.getArmSpReg(), assistReg, resReg);
                        addInstr(lw, insList, predefine);
                    } else {
                        resReg = getResReg(loadInst, ArmVirReg.RegType.floatType);
                        ArmVLoad flw = new ArmVLoad(ArmCPUReg.getArmSpReg(), assistReg, resReg);
                        addInstr(flw, insList, predefine);
                    }
                }
            } else {
                if (!((PointerType) loadInst.getPointer().getType()).getEleType().isFloatTy()) {
                    resReg = getResReg(loadInst, ArmVirReg.RegType.intType);
                    ArmLoad lw = new ArmLoad(value2Reg.get(loadInst.getPointer()), new ArmImm(0), resReg);
                    addInstr(lw, insList, predefine);
                } else {
                    resReg = getResReg(loadInst, ArmVirReg.RegType.floatType);
                    ArmVLoad flw = new ArmVLoad(value2Reg.get(loadInst.getPointer()), new ArmImm(0), resReg);
                    addInstr(flw, insList, predefine);
                }
            }
        } else if (loadInst.getPointer() instanceof GlobalVar) {
            assert value2Label.containsKey(loadInst.getPointer());
            ArmGlobalVariable var = (ArmGlobalVariable) value2Label.get(loadInst.getPointer());
            ArmReg assistReg = getNewIntReg();
            ArmLi li = new ArmLi(var, assistReg);
            addInstr(li, insList, predefine);
            if (!((PointerType) loadInst.getPointer().getType()).getEleType().isFloatTy()) {
                resReg = getResReg(loadInst, ArmVirReg.RegType.intType);
                ArmLoad lw = new ArmLoad(assistReg, new ArmImm(0), resReg);
                addInstr(lw, insList, predefine);
            } else {
                resReg = getResReg(loadInst, ArmVirReg.RegType.floatType);
                ArmVLoad flw = new ArmVLoad(assistReg, new ArmImm(0), resReg);
                addInstr(flw, insList, predefine);
            }
        } else if (loadInst.getPointer() instanceof AllocInst) {
            if (!curArmFunction.containOffset(loadInst.getPointer())) {
                parseAlloc((AllocInst) loadInst.getPointer(), true);
            }
            int offset = curArmFunction.getOffset(loadInst.getPointer()) * -1;
            if (ArmTools.isLegalLoadStoreScaledImm(offset, 4) &&
                    !((PointerType) loadInst.getPointer().getType()).getEleType().isFloatTy()) {
                resReg = getResReg(loadInst, ArmVirReg.RegType.intType);
                ArmLoad lw = new ArmLoad(ArmCPUReg.getArmSpReg(), new ArmImm(offset), resReg);
                addInstr(lw, insList, predefine);
            } else if (ArmTools.isLegalVLoadStoreImm(offset)
                    && ((PointerType) loadInst.getPointer().getType()).getEleType().isFloatTy()) {
                resReg = getResReg(loadInst, ArmVirReg.RegType.floatType);
                ArmVLoad flw = new ArmVLoad(ArmCPUReg.getArmSpReg(), new ArmImm(offset), resReg);
                addInstr(flw, insList, predefine);
            } else {
                ArmReg assistReg = getNewIntReg();
                ArmLi li = new ArmLi(new ArmImm(offset), assistReg);
                addInstr(li, insList, predefine);
                if (!((PointerType) loadInst.getPointer().getType()).getEleType().isFloatTy()) {
                    resReg = getResReg(loadInst, ArmVirReg.RegType.intType);
                    ArmLoad lw = new ArmLoad(ArmCPUReg.getArmSpReg(), assistReg, resReg);
                    addInstr(lw, insList, predefine);
                } else {
                    resReg = getResReg(loadInst, ArmVirReg.RegType.floatType);
                    ArmVLoad flw = new ArmVLoad(ArmCPUReg.getArmSpReg(), assistReg, resReg);
                    addInstr(flw, insList, predefine);
                }
            }
        } else if (loadInst.getPointer() instanceof Argument || loadInst.getPointer() instanceof Phi) {
            ArmReg assistReg = getRegOnlyFromValue(loadInst.getPointer(), insList, predefine);
            assert loadInst.getPointer().getType() instanceof PointerType;
            if ((((PointerType) loadInst.getPointer().getType()).getEleType().isFloatTy())) {
                resReg = getResReg(loadInst, ArmVirReg.RegType.floatType);
                ArmVLoad flw = new ArmVLoad(assistReg, new ArmImm(0), resReg);
                addInstr(flw, insList, predefine);
            } else {
                resReg = getResReg(loadInst, ArmVirReg.RegType.intType);
                ArmLoad lw = new ArmLoad(assistReg, new ArmImm(0), resReg);
                addInstr(lw, insList, predefine);
            }
        } else {
            assert false;
        }
        value2Reg.put(loadInst, resReg);
    }

    public void parseRetInst(RetInst retInst, boolean predefine) {
        if (!predefine) {
            curArmFunction.getRetBlocks().add(curArmBlock);
        }
        if (preProcess(retInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(retInst, insList);
        ArmReg retUsedReg = null;
        if (!retInst.isVoid()) {
            if (retInst.getValue() instanceof ConstInteger) {
                ArmLi li = new ArmLi(new ArmImm(((ConstInteger) retInst.getValue()).getValue()),
                        ArmCPUReg.getArmCPURetValueReg());
                addInstr(li, insList, predefine);
                retUsedReg = ArmCPUReg.getArmCPURetValueReg();
            } else if (retInst.getValue() instanceof ConstFloat) {
                makeFli(((ConstFloat) retInst.getValue()).getValue(),
                        ArmFPUReg.getArmFPURetValueReg(), insList, predefine);
                retUsedReg = ArmFPUReg.getArmFPURetValueReg();
            } else {
                ArmReg reg = getRegOnlyFromValue(retInst.getValue(), insList, predefine);
                assert reg instanceof ArmVirReg;
                if (((ArmVirReg) reg).regType == ArmVirReg.RegType.intType) {
                    addInstr(new ArmMv(reg, ArmCPUReg.getArmCPURetValueReg()), insList, predefine);
                    retUsedReg = ArmCPUReg.getArmCPURetValueReg();
                } else {
                    addInstr(new ArmFMv(reg, ArmFPUReg.getArmFPURetValueReg()), insList, predefine);
                    retUsedReg = ArmFPUReg.getArmFPURetValueReg();
                }
            }
        }
        // AArch64 function epilogue: restore frame pointer and link register
        ArmLdp epilogue = new ArmLdp(ArmCPUReg.getArmCPUReg(29), ArmCPUReg.getArmRetReg(),
                                    ArmCPUReg.getArmSpReg(), new ArmImm(16), true);
        addInstr(epilogue, insList, predefine);
        
        // Return
        addInstr(new ArmRet(ArmCPUReg.getArmRetReg(), retUsedReg), insList, predefine);
    }

    public void parseCallInst(CallInst callInst, boolean predefine) {
        if (preProcess(callInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(callInst, insList);
        ArmLabel targetFunction = value2Label.get(callInst.getFunction());
        ArmCall call = new ArmCall(targetFunction);
        int argc = callInst.getParams().size();
        assert argc == callInst.getFunction().getArgs().size();
        assert targetFunction instanceof ArmFunction;
        int stackCur = 0;//表示调用此函数时jal时的栈顶参数栈位置
        int otherCur = 0, floatCur = 0;//表示当前参数保存的位置
        for (var arg : callInst.getOperands()) {
            if (arg.getType().isFloatTy()) {
                if (floatCur < 4) {
                    if (arg instanceof ConstFloat) {
                        makeFli(((ConstFloat) arg).getValue(),
                                ArmFPUReg.getArmFArgReg(floatCur), insList, predefine);
                        call.addUsedReg(ArmFPUReg.getArmFArgReg(floatCur));
                    } else if (arg instanceof ConstInteger) {
                        assert false;
                    } else {
                        ArmReg argReg = ArmFPUReg.getArmFArgReg(floatCur);
                        ArmReg reg = getRegOnlyFromValue(arg, insList, predefine);
                        assert reg instanceof ArmVirReg
                                && ((ArmVirReg) reg).regType == ArmVirReg.RegType.floatType;
                        ArmFMv fmv = new ArmFMv(reg, argReg);
                        addInstr(fmv, insList, predefine);
                        call.addUsedReg(argReg);
                    }
                } else {
                    stackCur++;
                    int offset = stackCur * 4;
                    if (arg instanceof ConstFloat) {
                        ArmReg reg = getNewFloatReg();
                        makeFli(((ConstFloat) arg).getValue(), reg, insList, predefine);
                        ArmReg regAssist = getNewIntReg();
                        ArmLi li = new ArmLi(new ArmStackFixer(curArmFunction, offset), regAssist);
                        addInstr(li, insList, predefine);
                        ArmBinary sub = new ArmBinary(
                                new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), regAssist)), regAssist,
                                ArmBinary.ArmBinaryType.sub);
                        addInstr(sub, insList, predefine);
                        ArmFSw fsw = new ArmFSw(reg, regAssist, new ArmImm(0));
                        addInstr(fsw, insList, predefine);
                    } else if (arg instanceof ConstInteger) {
                        assert false;
                    } else {
                        ArmReg reg = getRegOnlyFromValue(arg, insList, predefine);
                        assert reg instanceof ArmVirReg
                                && ((ArmVirReg) reg).regType == ArmVirReg.RegType.floatType;
                        ArmReg offReg = getNewIntReg();
                        ArmLi li = new ArmLi(new ArmStackFixer(curArmFunction, offset), offReg);
                        addInstr(li, insList, predefine);
                        ArmBinary sub = new ArmBinary(
                                new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), offReg)), offReg,
                                ArmBinary.ArmBinaryType.sub);
                        addInstr(sub, insList, predefine);
                        ArmFSw fsw = new ArmFSw(reg, offReg, new ArmImm(0));
                        addInstr(fsw, insList, predefine);
                    }
                }
                floatCur++;
            } else {
                /*整数类型*/
                if (otherCur < 4) {
                    ArmReg reg = ArmCPUReg.getArmArgReg(otherCur);
                    call.addUsedReg(reg);
                    if (arg instanceof ConstInteger) {
                        ArmLi li = new ArmLi(new ArmImm(((ConstInteger) arg).getValue()), reg);
                        addInstr(li, insList, predefine);
                    } else if (arg instanceof ConstFloat) {
                        assert false;
                    } else if (arg instanceof GlobalVar) {
                        ArmLabel label = value2Label.get(arg);
                        ArmLi li = new ArmLi(label, reg);
                        addInstr(li, insList, predefine);
                    } else if (arg instanceof AllocInst) {
                        ArmReg virReg = getRegOnlyFromValue(arg, insList, predefine);
                        ArmMv mv = new ArmMv(virReg, reg);
                        addInstr(mv, insList, predefine);
                    } else if (arg instanceof PtrInst || arg instanceof PtrSubInst) {
                        if (!(ptr2Offset.containsKey(arg) || value2Reg.containsKey(arg))) {
                            if (arg instanceof PtrInst) {
                                parsePtrInst((PtrInst) arg, true);
                            } else {
                                parsePtrSubInst((PtrSubInst) arg, true);
                            }
                        }
                        if (ptr2Offset.containsKey(arg)) {
                            int offset = ptr2Offset.get(arg);
                            if (ArmTools.isArmImmCanBeEncoded(offset)) {
                                ArmBinary binary = new ArmBinary(
                                        new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(),
                                                new ArmImm(offset))), reg, ArmBinary.ArmBinaryType.add);
                                addInstr(binary, insList, predefine);
                            } else {
                                ArmLi li = new ArmLi(new ArmImm(offset), reg);
                                ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), reg)),
                                        reg, ArmBinary.ArmBinaryType.add);
                                addInstr(li, insList, predefine);
                                addInstr(add, insList, predefine);
                            }
                        } else {
                            ArmMv mv = new ArmMv(value2Reg.get(arg), reg);
                            addInstr(mv, insList, predefine);
                        }
                    } else {
                        ArmMv mv = new ArmMv(getRegOnlyFromValue(arg, insList, predefine), reg);
                        addInstr(mv, insList, predefine);
                    }
                } else {
                    stackCur++;
                    int offset = stackCur * 4;
                    ArmReg virReg = getRegOnlyFromValue(arg, insList, predefine);
                    assert virReg instanceof ArmVirReg
                            && ((ArmVirReg) virReg).regType == ArmVirReg.RegType.intType;
                    ArmReg assistReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmStackFixer(curArmFunction, offset), assistReg);
                    addInstr(li, insList, predefine);
                    ArmBinary sub = new ArmBinary(
                            new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), assistReg)), assistReg,
                            ArmBinary.ArmBinaryType.sub);
                    addInstr(sub, insList, predefine);
                    ArmSw sw = new ArmSw(virReg, assistReg, new ArmImm(0));
                    addInstr(sw, insList, predefine);
                }
                otherCur++;
            }
        }
        //use a callee saved Register
        ArmReg regUp = ArmCPUReg.getArmCPUReg(4);
        ArmLi ins1 = new ArmLi(new ArmStackFixer(curArmFunction, 0), regUp);
        addInstr(ins1, insList, predefine);
        ArmBinary ins2 = new ArmBinary(new ArrayList<>(
                Arrays.asList(ArmCPUReg.getArmSpReg(), regUp)),
                ArmCPUReg.getArmSpReg(), ArmBinary.ArmBinaryType.sub);
        addInstr(ins2, insList, predefine);
        addInstr(call, insList, predefine);
        ArmBinary ins3 = new ArmBinary(new ArrayList<>(
                Arrays.asList(ArmCPUReg.getArmSpReg(), regUp)),
                ArmCPUReg.getArmSpReg(), ArmBinary.ArmBinaryType.add);
        addInstr(ins3, insList, predefine);

        if (callInst.getFunction().getType().isFloatTy()) {
            ArmVirReg resReg = getResReg(callInst, ArmVirReg.RegType.floatType);
            ArmFMv fmv = new ArmFMv(ArmFPUReg.getArmFPURetValueReg(), resReg);
            value2Reg.put(callInst, resReg);
            addInstr(fmv, insList, predefine);
        } else {
            ArmVirReg resReg = getResReg(callInst, ArmVirReg.RegType.intType);
            ArmMv mv = new ArmMv(ArmCPUReg.getArmCPURetValueReg(), resReg);
            value2Reg.put(callInst, resReg);
            addInstr(mv, insList, predefine);
        }
    }

    public void parsePtrInst(PtrInst ptrInst, boolean predefine) {
        // 考虑 target 来自 Alloca
        if (preProcess(ptrInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(ptrInst, insList);
        ArmOperand op2 = getRegOrImmFromValue(ptrInst.getOffset(), insList, predefine);
        if (ptrInst.getTarget() instanceof AllocInst) {
            if (!curArmFunction.containOffset(ptrInst.getTarget())) {
                parseAlloc((AllocInst) ptrInst.getTarget(), true);
            }
            int offset = curArmFunction.getOffset(ptrInst.getTarget()) * -1;
            if (op2 instanceof ArmImm) {
                offset = offset + ((ArmImm) op2).getIntValue() * 4;
                ptr2Offset.put(ptrInst, offset);
            } else {
                assert op2 instanceof ArmVirReg;
                ArmReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
                ArmBinary add1 = new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), op2)),
                        resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.add);
                addInstr(add1, insList, predefine);
                if (ArmTools.isArmImmCanBeEncoded(offset)) {
                    ArmBinary add2 = new ArmBinary(new ArrayList<>(Arrays.asList(resReg, new ArmImm(offset))),
                            resReg, ArmBinary.ArmBinaryType.add);
                    addInstr(add2, insList, predefine);
                } else {
                    ArmReg assistReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(offset), assistReg);
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                            assistReg)), resReg, ArmBinary.ArmBinaryType.add);
                    addInstr(li, insList, predefine);
                    addInstr(add, insList, predefine);
                }
                value2Reg.put(ptrInst, resReg);
            }
        } else if (ptrInst.getTarget() instanceof PtrInst || ptrInst.getTarget() instanceof PtrSubInst) {
            if (!(ptr2Offset.containsKey(ptrInst.getTarget())
                    || value2Reg.containsKey(ptrInst.getTarget()))) {
                if (ptrInst.getTarget() instanceof PtrInst) {
                    parsePtrInst((PtrInst) ptrInst.getTarget(), true);
                } else {
                    parsePtrSubInst((PtrSubInst) ptrInst.getTarget(), true);
                }
            }
            if (ptr2Offset.containsKey(ptrInst.getTarget())) {
                if (op2 instanceof ArmImm) {
                    int offset = ptr2Offset.get(ptrInst.getTarget()) + ((ArmImm) op2).getIntValue() * 4;
                    ptr2Offset.put(ptrInst, offset);
                } else {
                    assert op2 instanceof ArmReg;
                    int offset = ptr2Offset.get(ptrInst.getTarget());
                    ArmReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
                    ArmBinary add1 = new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), op2)),
                            resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.add);
                    addInstr(add1, insList, predefine);
                    if (ArmTools.isArmImmCanBeEncoded(offset)) {
                        ArmBinary add2 = new ArmBinary(new ArrayList<>(Arrays.asList(resReg, new ArmImm(offset))),
                                resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(add2, insList, predefine);
                    } else {
                        ArmReg assistReg = getNewIntReg();
                        ArmLi li = new ArmLi(new ArmImm(offset), assistReg);
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                                assistReg)), resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                    value2Reg.put(ptrInst, resReg);
                }
            } else {
                assert value2Reg.containsKey(ptrInst.getTarget());
                ArmReg op1 = value2Reg.get(ptrInst.getTarget());
                ArmReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
                if (op2 instanceof ArmImm) {
                    int offset = ((ArmImm) op2).getIntValue() * 4;
                    if (ArmTools.isArmImmCanBeEncoded(offset)) {
                        ArmBinary addi = new ArmBinary(new ArrayList<>(Arrays.asList(op1,
                                new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(addi, insList, predefine);
                    } else {
                        ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(op1,
                                resReg)), resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    assert op2 instanceof ArmReg;
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(op1,
                            op2)), resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.add);
                    addInstr(add, insList, predefine);
                }
                value2Reg.put(ptrInst, resReg);
            }
        } else if (ptrInst.getTarget() instanceof GlobalVar) {
            assert value2Label.containsKey(ptrInst.getTarget());
            ArmLabel label = value2Label.get(ptrInst.getTarget());
            ArmReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
            ArmLi li = new ArmLi(label, resReg);
            addInstr(li, insList, predefine);
            if (!(op2 instanceof ArmImm && ((ArmImm) op2).getValue() == 0)) {
                if (op2 instanceof ArmImm) {
                    int offset = ((ArmImm) op2).getIntValue() * 4;
                    if (ArmTools.isArmImmCanBeEncoded(offset)) {
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                                new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(add, insList, predefine);
                    } else {
                        ArmReg assistReg = getNewIntReg();
                        ArmLi li2 = new ArmLi(new ArmImm(offset), assistReg);
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(assistReg,
                                resReg)), resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(li2, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                            op2)), resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.add);
                    addInstr(add, insList, predefine);
                }
            }
            value2Reg.put(ptrInst, resReg);
        } else if (ptrInst.getTarget() instanceof Argument) {
            ArmVirReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
            if (value2Reg.containsKey(ptrInst.getTarget())) {
                ArmMv mv = new ArmMv(value2Reg.get(ptrInst.getTarget()), resReg);
                addInstr(mv, insList, predefine);
                if (!(op2 instanceof ArmImm && ((ArmImm) op2).getValue() == 0)) {
                    if (op2 instanceof ArmImm) {
                        int offset = ((ArmImm) op2).getIntValue() * 4;
                        if (ArmTools.isArmImmCanBeEncoded(offset)) {
                            ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                                    new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.add);
                            addInstr(add, insList, predefine);
                        } else {
                            ArmReg assistReg = getNewIntReg();
                            ArmLi li = new ArmLi(new ArmImm(offset), assistReg);
                            ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(assistReg,
                                    resReg)), resReg, ArmBinary.ArmBinaryType.add);
                            addInstr(li, insList, predefine);
                            addInstr(add, insList, predefine);
                        }
                    } else {
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                                op2)), resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.add);
                        addInstr(add, insList, predefine);
                    }
                }
            } else {
                int offset = curArmFunction.getOffset(ptrInst.getTarget()) * -1;
                assert !ptrInst.getTarget().getType().isFloatTy();
                ArmReg argReg = getNewIntReg();
                if (offset >= -4095 && offset <= 4095) {
                    ArmLoad lw = new ArmLoad(ArmCPUReg.getArmSpReg(), new ArmImm(offset), argReg);
                    addInstr(lw, insList, predefine);
                } else {
                    ArmLi li = new ArmLi(new ArmImm(offset), argReg);
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(argReg,
                            ArmCPUReg.getArmSpReg())), argReg, ArmBinary.ArmBinaryType.add);
                    ArmLoad lw = new ArmLoad(argReg, new ArmImm(0), argReg);
                    addInstr(li, insList, predefine);
                    addInstr(binary, insList, predefine);
                    addInstr(lw, insList, predefine);
                }
                value2Reg.put(ptrInst.getTarget(), argReg);
                if (op2 instanceof ArmImm) {
                    offset = ((ArmImm) op2).getIntValue() * 4;
                    if (ArmTools.isArmImmCanBeEncoded(offset)) {
                        ArmBinary addi = new ArmBinary(new ArrayList<>(Arrays.asList(argReg,
                                new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(addi, insList, predefine);
                    } else {
                        ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(argReg,
                                resReg)), resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(argReg,
                            op2)), resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.add);
                    addInstr(add, insList, predefine);
                }
            }
            value2Reg.put(ptrInst, resReg);
        } else if (ptrInst.getTarget() instanceof Phi) {
            ArmReg phiReg = getRegOnlyFromValue(ptrInst.getTarget(), insList, predefine);
            ArmVirReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
            if (op2 instanceof ArmImm) {
                int offset = ((ArmImm) op2).getIntValue() * 4;
                if (ArmTools.isArmImmCanBeEncoded(offset)) {
                    ArmBinary addi = new ArmBinary(new ArrayList<>(Arrays.asList(phiReg,
                            new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.add);
                    addInstr(addi, insList, predefine);
                } else {
                    ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(phiReg,
                            resReg)), resReg, ArmBinary.ArmBinaryType.add);
                    addInstr(li, insList, predefine);
                    addInstr(add, insList, predefine);
                }
            } else {
                ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(phiReg,
                        op2)), resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.add);
                addInstr(add, insList, predefine);
            }
            value2Reg.put(ptrInst, resReg);
        } else {
            assert false;
        }
    }

    public void parsePtrSubInst(PtrSubInst ptrInst, boolean predefine) {
        // 考虑 target 来自 Alloca
        if (preProcess(ptrInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(ptrInst, insList);
        ArmOperand op2 = getRegOrImmFromValue(ptrInst.getOffset(), insList, predefine);
        if (ptrInst.getTarget() instanceof AllocInst) {
            if (!curArmFunction.containOffset(ptrInst.getTarget())) {
                parseAlloc((AllocInst) ptrInst.getTarget(), true);
            }
            int offset = curArmFunction.getOffset(ptrInst.getTarget()) * -1;
            if (op2 instanceof ArmImm) {
                offset = offset - ((ArmImm) op2).getIntValue() * 4;
                ptr2Offset.put(ptrInst, offset);
            } else {
                assert op2 instanceof ArmVirReg;
                ArmReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
                ArmBinary add1 = new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), op2)),
                        resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.sub);
                addInstr(add1, insList, predefine);
                if (ArmTools.isArmImmCanBeEncoded(offset)) {
                    ArmBinary add2 = new ArmBinary(new ArrayList<>(Arrays.asList(resReg, new ArmImm(offset))),
                            resReg, ArmBinary.ArmBinaryType.add);
                    addInstr(add2, insList, predefine);
                } else {
                    ArmReg assistReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(offset), assistReg);
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                            assistReg)), resReg, ArmBinary.ArmBinaryType.add);
                    addInstr(li, insList, predefine);
                    addInstr(add, insList, predefine);
                }
                value2Reg.put(ptrInst, resReg);
            }
        } else if (ptrInst.getTarget() instanceof PtrInst || ptrInst.getTarget() instanceof PtrSubInst) {
            if (!(ptr2Offset.containsKey(ptrInst.getTarget())
                    || value2Reg.containsKey(ptrInst.getTarget()))) {
                if (ptrInst.getTarget() instanceof PtrInst) {
                    parsePtrInst((PtrInst) ptrInst.getTarget(), true);
                } else {
                    parsePtrSubInst((PtrSubInst) ptrInst.getTarget(), true);
                }
            }
            if (ptr2Offset.containsKey(ptrInst.getTarget())) {
                if (op2 instanceof ArmImm) {
                    int offset = ptr2Offset.get(ptrInst.getTarget()) - ((ArmImm) op2).getIntValue() * 4;
                    ptr2Offset.put(ptrInst, offset);
                } else {
                    assert op2 instanceof ArmReg;
                    int offset = ptr2Offset.get(ptrInst.getTarget());
                    ArmReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
                    ArmBinary add1 = new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), op2)),
                            resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.sub);
                    addInstr(add1, insList, predefine);
                    if (ArmTools.isArmImmCanBeEncoded(offset)) {
                        ArmBinary add2 = new ArmBinary(new ArrayList<>(Arrays.asList(resReg, new ArmImm(offset))),
                                resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(add2, insList, predefine);
                    } else {
                        ArmReg assistReg = getNewIntReg();
                        ArmLi li = new ArmLi(new ArmImm(offset), assistReg);
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                                assistReg)), resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                    value2Reg.put(ptrInst, resReg);
                }
            } else {
                assert value2Reg.containsKey(ptrInst.getTarget());
                ArmReg op1 = value2Reg.get(ptrInst.getTarget());
                ArmReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
                if (op2 instanceof ArmImm) {
                    int offset = ((ArmImm) op2).getIntValue() * 4;
                    if (ArmTools.isArmImmCanBeEncoded(offset)) {
                        ArmBinary addi = new ArmBinary(new ArrayList<>(Arrays.asList(op1,
                                new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.sub);
                        addInstr(addi, insList, predefine);
                    } else {
                        ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(op1,
                                resReg)), resReg, ArmBinary.ArmBinaryType.sub);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    assert op2 instanceof ArmReg;
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(op1,
                            op2)), resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.sub);
                    addInstr(add, insList, predefine);
                }
                value2Reg.put(ptrInst, resReg);
            }
        } else if (ptrInst.getTarget() instanceof GlobalVar) {
            assert value2Label.containsKey(ptrInst.getTarget());
            ArmLabel label = value2Label.get(ptrInst.getTarget());
            ArmReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
            ArmLi li = new ArmLi(label, resReg);
            addInstr(li, insList, predefine);
            if (!(op2 instanceof ArmImm && ((ArmImm) op2).getValue() == 0)) {
                if (op2 instanceof ArmImm) {
                    int offset = ((ArmImm) op2).getIntValue() * 4;
                    if (ArmTools.isArmImmCanBeEncoded(offset)) {
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                                new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.sub);
                        addInstr(add, insList, predefine);
                    } else {
                        ArmReg assistReg = getNewIntReg();
                        ArmLi li2 = new ArmLi(new ArmImm(offset), assistReg);
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(assistReg,
                                resReg)), resReg, ArmBinary.ArmBinaryType.sub);
                        addInstr(li2, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                            op2)), resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.sub);
                    addInstr(add, insList, predefine);
                }
            }
            value2Reg.put(ptrInst, resReg);
        } else if (ptrInst.getTarget() instanceof Argument) {
            ArmVirReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
            if (value2Reg.containsKey(ptrInst.getTarget())) {
                ArmMv mv = new ArmMv(value2Reg.get(ptrInst.getTarget()), resReg);
                addInstr(mv, insList, predefine);
                if (!(op2 instanceof ArmImm && ((ArmImm) op2).getValue() == 0)) {
                    if (op2 instanceof ArmImm) {
                        int offset = ((ArmImm) op2).getIntValue() * 4;
                        if (ArmTools.isArmImmCanBeEncoded(offset)) {
                            ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                                    new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.sub);
                            addInstr(add, insList, predefine);
                        } else {
                            ArmReg assistReg = getNewIntReg();
                            ArmLi li = new ArmLi(new ArmImm(offset), assistReg);
                            ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(assistReg,
                                    resReg)), resReg, ArmBinary.ArmBinaryType.sub);
                            addInstr(li, insList, predefine);
                            addInstr(add, insList, predefine);
                        }
                    } else {
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(resReg,
                                op2)), resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.sub);
                        addInstr(add, insList, predefine);
                    }
                }
            } else {
                int offset = curArmFunction.getOffset(ptrInst.getTarget()) * -1;
                assert !ptrInst.getTarget().getType().isFloatTy();
                ArmReg argReg = getNewIntReg();
                if (offset >= -4095 && offset <= 4095) {
                    ArmLoad lw = new ArmLoad(ArmCPUReg.getArmSpReg(), new ArmImm(offset), argReg);
                    addInstr(lw, insList, predefine);
                } else {
                    ArmLi li = new ArmLi(new ArmImm(offset), argReg);
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(argReg,
                            ArmCPUReg.getArmSpReg())), argReg, ArmBinary.ArmBinaryType.add);
                    ArmLoad lw = new ArmLoad(argReg, new ArmImm(0), argReg);
                    addInstr(li, insList, predefine);
                    addInstr(binary, insList, predefine);
                    addInstr(lw, insList, predefine);
                }
                value2Reg.put(ptrInst.getTarget(), argReg);
                if (op2 instanceof ArmImm) {
                    offset = ((ArmImm) op2).getIntValue() * 4;
                    if (ArmTools.isArmImmCanBeEncoded(offset)) {
                        ArmBinary addi = new ArmBinary(new ArrayList<>(Arrays.asList(argReg,
                                new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.sub);
                        addInstr(addi, insList, predefine);
                    } else {
                        ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(argReg,
                                resReg)), resReg, ArmBinary.ArmBinaryType.sub);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(argReg,
                            op2)), resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.sub);
                    addInstr(add, insList, predefine);
                }
            }
            value2Reg.put(ptrInst, resReg);
        } else if (ptrInst.getTarget() instanceof Phi) {
            ArmReg phiReg = getRegOnlyFromValue(ptrInst.getTarget(), insList, predefine);
            ArmVirReg resReg = getResReg(ptrInst, ArmVirReg.RegType.intType);
            if (op2 instanceof ArmImm) {
                int offset = ((ArmImm) op2).getIntValue() * 4;
                if (ArmTools.isArmImmCanBeEncoded(offset)) {
                    ArmBinary addi = new ArmBinary(new ArrayList<>(Arrays.asList(phiReg,
                            new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.sub);
                    addInstr(addi, insList, predefine);
                } else {
                    ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(phiReg,
                            resReg)), resReg, ArmBinary.ArmBinaryType.sub);
                    addInstr(li, insList, predefine);
                    addInstr(add, insList, predefine);
                }
            } else {
                ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(phiReg,
                        op2)), resReg, 2, ArmBinary.ArmShiftType.LSL, ArmBinary.ArmBinaryType.sub);
                addInstr(add, insList, predefine);
            }
            value2Reg.put(ptrInst, resReg);
        } else {
            assert false;
        }
    }

    private ArmReg getRegOnlyFromValue(Value value, ArrayList<ArmInstruction> insList, boolean predefine) {
        ArmReg resReg;
        if (value instanceof ConstInteger) {
            resReg = getNewIntReg();
            ArmLi riscvLi = new ArmLi(new ArmImm(((ConstInteger) value).getValue()), resReg);
            addInstr(riscvLi, insList, predefine);
        } else if (value instanceof ConstFloat) {
            resReg = getNewFloatReg();
            makeFli(((ConstFloat) value).getValue(), resReg, insList, predefine);
        } else if (value instanceof Argument) {
            if (value2Reg.containsKey(value)) {
                return value2Reg.get(value);
            } else {
                int offset = -1 * curArmFunction.getOffset(value);
                if (ArmTools.isLegalVLoadStoreImm(offset) && value.getType().isFloatTy()) {
                    resReg = getNewFloatReg();
                    ArmVLoad flw = new ArmVLoad(ArmCPUReg.getArmSpReg(), new ArmImm(offset), resReg);
                    addInstr(flw, insList, predefine);
                    value2Reg.put(value, resReg);
                } else if (offset >= -4095 && offset <= 4095 && !value.getType().isFloatTy()) {
                    assert value.getType().isIntegerTy() || value.getType().isPointerType();
                    resReg = getNewIntReg();
                    ArmLoad lw = new ArmLoad(ArmCPUReg.getArmSpReg(), new ArmImm(offset), resReg);
                    addInstr(lw, insList, predefine);
                    value2Reg.put(value, resReg);
                } else {
                    if (value.getType().isFloatTy()) {
                        resReg = getNewFloatReg();
                        ArmReg virReg = getNewIntReg();
                        ArmLi li = new ArmLi(new ArmImm(offset), virReg);
                        ArmVLoad flw = new ArmVLoad(ArmCPUReg.getArmSpReg(), virReg, resReg);
                        addInstr(li, insList, predefine);
                        addInstr(flw, insList, predefine);
                        value2Reg.put(value, resReg);
                    } else {
                        assert value.getType().isIntegerTy();
                        resReg = getNewIntReg();
                        ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                        ArmLoad lw = new ArmLoad(ArmCPUReg.getArmSpReg(), resReg, resReg);
                        addInstr(li, insList, predefine);
                        addInstr(lw, insList, predefine);
                        value2Reg.put(value, resReg);
                    }
                }
            }
        } else if (value instanceof AllocInst) {
            if (!curArmFunction.containOffset(value)) {
                parseAlloc((AllocInst) value, true);
            }
            if (value2Reg.containsKey(value)) {
                return value2Reg.get(value);
            } else {
                int offset = curArmFunction.getOffset(value) * -1;
                if (ArmTools.isArmImmCanBeEncoded(offset)) {
                    resReg = getNewIntReg();
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(),
                            new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.add);
                    addInstr(add, insList, predefine);
                } else {
                    resReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                    addInstr(li, insList, predefine);
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(),
                            resReg)), resReg, ArmBinary.ArmBinaryType.add);
                    addInstr(add, insList, predefine);
                }
                value2Reg.put(value, resReg);
            }
        } else if (value instanceof BinaryInst && (isIntCmpType(((BinaryInst) value).getOp())
                || isFloatCmpType(((BinaryInst) value).getOp()))) {
            if (value2Reg.containsKey(value)) {
                return value2Reg.get(value);
            }
            parseInstruction((BinaryInst) value, true);
            return value2Reg.get(value);
        } else if (value instanceof GlobalVar) {
            assert value2Label.containsKey(value);
            ArmLabel label = value2Label.get(value);
            resReg = getNewIntReg();
            ArmLi li = new ArmLi(label, resReg);
            addInstr(li, insList, predefine);
            return resReg;
        } else {
            //System.out.println(value.toString());
            assert value instanceof Instruction;
            if (value instanceof PtrInst || value instanceof PtrSubInst) {
                if (!(ptr2Offset.containsKey(value) || value2Reg.containsKey(value))) {
                    if (value instanceof PtrInst) {
                        parsePtrInst((PtrInst) value, true);
                    } else {
                        parsePtrSubInst((PtrSubInst) value, true);
                    }
                }
                if (ptr2Offset.containsKey(value)) {
                    resReg = getNewIntReg();
                    int offset = ptr2Offset.get(value);
                    if (ArmTools.isArmImmCanBeEncoded(offset)) {
                        ArmBinary binary = new ArmBinary(
                                new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(),
                                        new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(binary, insList, predefine);
                    } else {
                        ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), resReg)),
                                resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                    return resReg;
                } else {
                    return value2Reg.get(value);
                }
            }
            if (value instanceof Phi) {
                if (value2Reg.containsKey(value)) {
                    return value2Reg.get(value);
                } else {
                    ArmVirReg reg = curArmFunction.getNewReg(value.getType().isFloatTy() ?
                            ArmVirReg.RegType.floatType : ArmVirReg.RegType.intType);
                    value2Reg.put(value, reg);
                    return reg;
                }
            } else if (!value2Reg.containsKey(value)) {
                parseInstruction((Instruction) value, true);
            }
            return value2Reg.get(value);
        }
        return resReg;
    }

    private ArmOperand getRegOrImmFromValue(Value value,
                                            ArrayList<ArmInstruction> insList, boolean predefine) {
        ArmReg resReg;
        if (value instanceof ConstInteger) {
            return new ArmImm(((ConstInteger) value).getValue());
        } else if (value instanceof ConstFloat) {
            //TODO:根据FADD和FSUB情况进行改动
            resReg = getNewFloatReg();
            makeFli(((ConstFloat) value).getValue(), resReg, insList, predefine);
        } else if (value instanceof Argument) {
            if (value2Reg.containsKey(value)) {
                return value2Reg.get(value);
            } else {
                int offset = -1 * curArmFunction.getOffset(value);
                if (value.getType().isFloatTy() && ArmTools.isLegalVLoadStoreImm(offset)) {
                    resReg = getNewFloatReg();
                    ArmVLoad fld = new ArmVLoad(ArmCPUReg.getArmSpReg(),
                            new ArmImm(offset), resReg);
                    addInstr(fld, insList, predefine);
                    value2Reg.put(value, resReg);
                } else if (offset >= -4095 && offset <= 4095 && !value.getType().isFloatTy()) {
                    assert value.getType().isIntegerTy();
                    resReg = getNewIntReg();
                    ArmLoad ld = new ArmLoad(ArmCPUReg.getArmSpReg(), new ArmImm(offset), resReg);
                    addInstr(ld, insList, predefine);
                    value2Reg.put(value, resReg);
                } else {
                    if (value.getType().isFloatTy()) {
                        resReg = getNewFloatReg();
                        ArmReg virReg = getNewIntReg();
                        ArmLi li = new ArmLi(new ArmImm(offset), virReg);
                        ArmVLoad fld = new ArmVLoad(ArmCPUReg.getArmSpReg(), virReg, resReg);
                        addInstr(li, insList, predefine);
                        addInstr(fld, insList, predefine);
                        value2Reg.put(value, resReg);
                    } else {
                        assert value.getType().isIntegerTy();
                        resReg = getNewIntReg();
                        ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                        ArmLoad ld = new ArmLoad(ArmCPUReg.getArmSpReg(), resReg, resReg);
                        addInstr(li, insList, predefine);
                        addInstr(ld, insList, predefine);
                        value2Reg.put(value, resReg);
                    }
                }
            }
        } else if (value instanceof AllocInst) {
            if (!curArmFunction.containOffset(value)) {
                parseAlloc((AllocInst) value, true);
            }
            if (value2Reg.containsKey(value)) {
                return value2Reg.get(value);
            } else {
                int offset = curArmFunction.getOffset(value) * -1;
                if (ArmTools.isArmImmCanBeEncoded(offset)) {
                    resReg = getNewIntReg();
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(),
                            new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.add);
                    addInstr(add, insList, predefine);
                } else {
                    resReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                    addInstr(li, insList, predefine);
                    ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(),
                            resReg)), resReg, ArmBinary.ArmBinaryType.add);
                    addInstr(add, insList, predefine);
                }
                value2Reg.put(value, resReg);
            }
        } else if (value instanceof BinaryInst && (isIntCmpType(((BinaryInst) value).getOp())
                || isFloatCmpType(((BinaryInst) value).getOp()))) {
            if (value2Reg.containsKey(value)) {
                return value2Reg.get(value);
            }
            parseInstruction((BinaryInst) value, true);
            return value2Reg.get(value);
        } else if (value instanceof GlobalVar) {
            assert value2Label.containsKey(value);
            ArmLabel label = value2Label.get(value);
            resReg = getNewIntReg();
            ArmLi li = new ArmLi(label, resReg);
            addInstr(li, insList, predefine);
            return resReg;
        } else {
            assert value instanceof Instruction;
            if (value instanceof PtrInst || value instanceof PtrSubInst) {
                if (!(ptr2Offset.containsKey(value) || value2Reg.containsKey(value))) {
                    if (value instanceof PtrInst) {
                        parsePtrInst((PtrInst) value, true);
                    } else {
                        parsePtrSubInst((PtrSubInst) value, true);
                    }
                }
                if (ptr2Offset.containsKey(value)) {
                    resReg = getNewIntReg();
                    int offset = ptr2Offset.get(value);
                    if (ArmTools.isArmImmCanBeEncoded(offset)) {
                        ArmBinary binary = new ArmBinary(
                                new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(),
                                        new ArmImm(offset))), resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(binary, insList, predefine);
                    } else {
                        ArmLi li = new ArmLi(new ArmImm(offset), resReg);
                        ArmBinary add = new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), resReg)),
                                resReg, ArmBinary.ArmBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                    return resReg;
                } else {
                    return value2Reg.get(value);
                }
            }
            if (value instanceof Phi) {
                if (value2Reg.containsKey(value)) {
                    return value2Reg.get(value);
                } else {
                    ArmVirReg reg = curArmFunction.getNewReg(value.getType().isFloatTy() ? ArmVirReg.RegType.floatType
                            : ArmVirReg.RegType.intType);
                    value2Reg.put(value, reg);
                    return reg;
                }
            } else if (!value2Reg.containsKey(value)) {
                parseInstruction((Instruction) value, true);
            }
            return value2Reg.get(value);
        }
        return resReg;
    }

    public ArmVirReg getResReg(Instruction ins, ArmVirReg.RegType regType) {
        if (value2Reg.containsKey(ins)) {
            assert value2Reg.get(ins) instanceof ArmVirReg;
            ArmVirReg virReg = (ArmVirReg) value2Reg.get(ins);
            assert virReg.regType == regType;
            return virReg;
        } else {
            return curArmFunction.getNewReg(regType);
        }
    }

    public void makeFli(float imm, ArmReg armReg, ArrayList<ArmInstruction> insList, boolean predefine) {
        if (ArmTools.isFloatImmCanBeEncoded(imm)) {
            ArmFLi li = new ArmFLi(new ArmFloatImm(imm), armReg);
            addInstr(li, insList, predefine);
        } else {
            int mid = Float.floatToIntBits(imm);
            ArmVirReg assistReg = getNewIntReg();
            ArmLi li = new ArmLi(new ArmImm(mid), assistReg);
            ArmConvMv convMv = new ArmConvMv(assistReg, armReg);
            addInstr(li, insList, predefine);
            addInstr(convMv, insList, predefine);
        }
    }

    public ArmVirReg getNewFloatReg() {
        return curArmFunction.getNewReg(ArmVirReg.RegType.floatType);
    }

    public ArmVirReg getNewIntReg() {
        return curArmFunction.getNewReg(ArmVirReg.RegType.intType);
    }

    private boolean preProcess(Instruction instruction, boolean predefine) {
        if (predefines.containsKey(instruction)) {
            if (!predefine) {
                ArrayList<ArmInstruction> insList = predefines.get(instruction);
                for (ArmInstruction ins : insList) {
                    addInstr(ins, null, false);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return armModule.toString();
    }

    private void addInstr(ArmInstruction ins, ArrayList<ArmInstruction> insList, boolean predefine) {
        if (predefine) {
            insList.add(ins);
        } else {
            curArmBlock.addArmInstruction(new IList.INode<>(ins));
        }
    }

    public void dump() {
        try {
            var out = new BufferedWriter(new FileWriter("arm_backend.s"));
            out.write(armModule.toString());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ArmModule getArmModule() {
        return armModule;
    }
}
