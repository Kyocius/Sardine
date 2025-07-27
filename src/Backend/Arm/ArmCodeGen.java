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
    private ArmFunction ldivmod = new ArmFunction("__aeabi_ldivmod");
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
        System.out.println("start gen code for armv8-a, now using v7");
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
            armModule.addFunction(function.getName(), armFunction);
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
                false, 4, new ArrayList<>(List.of(new ArmGlobalZero(4))));
        armModule.addBssVar(stoR7);
        ArmGlobalVariable stoR5 = new ArmGlobalVariable("parallell_stoR5",
                false, 4, new ArrayList<>(List.of(new ArmGlobalZero(4))));
        armModule.addBssVar(stoR5);
        ArmGlobalVariable stoLr = new ArmGlobalVariable("parallell_stoLR",
                false, 4, new ArrayList<>(List.of(new ArmGlobalZero(4))));
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

        addInstr(new ArmLi(new ArmImm(120), ArmCPUReg.getArmCPUReg(7)), null, false);
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

    public void buildParallelEnd(ArmFunction parallelEnd) {
        curArmFunction = parallelEnd;
        ArmBlock armBlock0 = new ArmBlock("parallel_end0");
        ArmBlock armBlock1 = new ArmBlock("parallel_end1");
        ArmBlock armBlock2 = new ArmBlock("parallel_end2");
        ArmBlock armBlock3 = new ArmBlock("parallel_end3");
        ArmBlock armBlock4 = new ArmBlock("parallel_end4");

        ArmGlobalVariable stoR7 = new ArmGlobalVariable("parallell_endR7",
                false, 4, new ArrayList<>(List.of(new ArmGlobalZero(4))));
        armModule.addBssVar(stoR7);
        ArmGlobalVariable stoLr = new ArmGlobalVariable("parallell_endLR",
                false, 4, new ArrayList<>(List.of(new ArmGlobalZero(4))));
        armModule.addBssVar(stoLr);

        curArmBlock = armBlock0;
        curArmFunction.addBlock(new IList.INode<>(curArmBlock));
        addInstr(new ArmCompare(ArmCPUReg.getArmCPUReg(0), new ArmImm(0), ArmCompare.CmpType.cmp),
                null, false);
        addInstr(new ArmBranch(armBlock2, ArmTools.CondType.eq), null, false);

        curArmBlock = armBlock1;
        curArmFunction.addBlock(new IList.INode<>(curArmBlock));
        addInstr(new ArmLi(new ArmImm(1), ArmCPUReg.getArmCPUReg(7)), null, false);
        addInstr(new ArmSyscall(new ArmImm(0)), null, false);

        curArmBlock = armBlock2;
        curArmFunction.addBlock(new IList.INode<>(curArmBlock));
        addInstr(new ArmLi(stoR7, ArmCPUReg.getArmCPUReg(2)), null, false);
        addInstr(new ArmSw(ArmCPUReg.getArmCPUReg(7),
                ArmCPUReg.getArmCPUReg(2), new ArmImm(0)), null, false);
        addInstr(new ArmLi(stoLr, ArmCPUReg.getArmCPUReg(2)), null, false);
        addInstr(new ArmSw(ArmCPUReg.getArmRetReg(),
                ArmCPUReg.getArmCPUReg(2), new ArmImm(0)), null, false);

        addInstr(new ArmLi(new ArmImm(Config.parallelNum), ArmCPUReg.getArmCPUReg(7)), null, false);

        curArmBlock = armBlock3;
        curArmFunction.addBlock(new IList.INode<>(curArmBlock));
        addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmCPUReg(7),
                new ArmImm(1))), ArmCPUReg.getArmCPUReg(7), ArmBinary.ArmBinaryType.sub), null, false);
        addInstr(new ArmCompare(ArmCPUReg.getArmCPUReg(7), new ArmImm(0),
                ArmCompare.CmpType.cmp), null, false);
        addInstr(new ArmBranch(armBlock4, ArmTools.CondType.eq), null, false);
        addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), new ArmImm(4))),
                ArmCPUReg.getArmCPUReg(0), ArmBinary.ArmBinaryType.sub), null, false);
        addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), new ArmImm(4))),
                ArmCPUReg.getArmSpReg(), ArmBinary.ArmBinaryType.sub), null, false);
        addInstr(new ArmWait(), null, false);
        addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(ArmCPUReg.getArmSpReg(), new ArmImm(4))),
                ArmCPUReg.getArmSpReg(), ArmBinary.ArmBinaryType.add), null, false);
        addInstr(new ArmJump(armBlock3, curArmBlock), null, false);

        curArmBlock = armBlock4;
        curArmFunction.addBlock(new IList.INode<>(curArmBlock));
        addInstr(new ArmLi(stoR7, ArmCPUReg.getArmCPUReg(2)), null, false);
        addInstr(new ArmLoad(ArmCPUReg.getArmCPUReg(2),
                new ArmImm(0), ArmCPUReg.getArmCPUReg(7)), null, false);
        addInstr(new ArmLi(stoLr, ArmCPUReg.getArmCPUReg(2)), null, false);
        addInstr(new ArmLoad(ArmCPUReg.getArmCPUReg(2),
                new ArmImm(0), ArmCPUReg.getArmRetReg()), null, false);
        addInstr(new ArmRet(ArmCPUReg.getArmRetReg(), null), null, false);
    }

    public void parseGlobalVar(GlobalVar var) {
        boolean flag = true;
        if (var.isArray()) {
            boolean isIntType = ((PointerType) var.getType()).getEleType() instanceof IntegerType;
            int zeros = 0;
            ArrayList<ArmGlobalValue> values = new ArrayList<>();
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
                        }
                    }
                }
                if (zeros > 0) {
                    values.add(new ArmGlobalZero(zeros));
                }
            }
            ArmGlobalVariable globalVariable = new ArmGlobalVariable(removeLeadingAt(var.getName()),
                    !var.isZeroInit(), 4 * var.getSize(), values);
            if (!flag) {
                armModule.addDataVar(globalVariable);
            } else {
                armModule.addBssVar(globalVariable);
            }
            value2Label.put(var, globalVariable);
        } else {
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
                } else {
                    armModule.addDataVar(globalVariable);
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
                value2Label.put(var, globalVariable);
            }
        }
    }

    public void parseFunction(Function function) {
        curArmBlock = null;
        curArmFunction = (ArmFunction) value2Label.get(function);
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
            if (!flag) {
                //将所有函数中的用于参数的mv指令加入Block
                for (ArmMv armMv : curArmFunction.getMvs()) {
                    addInstr(armMv, null, false);
                }
                //处理返回地址
                ArmMv mv = new ArmMv(ArmCPUReg.getArmRetReg(), curArmFunction.getRetReg());
                addInstr(mv, null, false);
                flag = true;
            }
            parseBasicBlock(bb);
        }
        if (function.getBbs().getSize() != 0) {
            curArmFunction.addBlock(new IList.INode<>(curArmBlock));
        }
    }

    public void parseBasicBlock(BasicBlock block) {
        for (IList.INode<Instruction, BasicBlock> insNode : block.getInsts()) {
            Instruction ins = insNode.getValue();
            parseInstruction(ins, false);
        }
    }

    public void parseInstruction(Instruction ins, boolean predefine) {
        if (ins instanceof AllocInst) {
            parseAlloc((AllocInst) ins, predefine);
        } else if (ins instanceof BinaryInst) {
            parseBinaryInst((BinaryInst) ins, predefine);
        } else if (ins instanceof BrInst) {
            parseBrInst((BrInst) ins, predefine);
        } else if (ins instanceof CallInst) {
            parseCallInst((CallInst) ins, predefine);
        } else if (ins instanceof ConversionInst) {
            parseConversionInst((ConversionInst) ins, predefine);
        } else if (ins instanceof LoadInst) {
            parseLoad((LoadInst) ins, predefine);
        } else if (ins instanceof Move) {
            parseMove((Move) ins, predefine);
        } else if (ins instanceof PtrInst) {
            parsePtrInst((PtrInst) ins, predefine);
        } else if (ins instanceof PtrSubInst) {
            parsePtrSubInst((PtrSubInst) ins, predefine);
        } else if (ins instanceof RetInst) {
            parseRetInst((RetInst) ins, predefine);
        } else if (ins instanceof StoreInst) {
            parseStore((StoreInst) ins, predefine);
        } else {
            System.err.println("ERROR");
        }
    }

    public void parseBinaryInst(BinaryInst binaryInst, boolean predefine) {
        if (binaryInst.getOp() == OP.Add) {
            parseAdd(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Sub) {
            parseSub(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Mul) {
            parseMul(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Div) {
            parseDiv(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Mod) {
            parseMod(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.And) {
            parseAnd(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Or) {
            parseOr(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Xor) {
            parseXor(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Fsub || binaryInst.getOp() == OP.Fadd
                || binaryInst.getOp() == OP.Fmul || binaryInst.getOp() == OP.Fdiv) {
            parseFbin(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Fmod) {
            assert false;
        } else if (binaryInst.getOp() == OP.Lt || binaryInst.getOp() == OP.Le
                || binaryInst.getOp() == OP.Gt || binaryInst.getOp() == OP.Ge
                || binaryInst.getOp() == OP.Eq || binaryInst.getOp() == OP.Ne) {
            parseIcmp(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.FLt || binaryInst.getOp() == OP.FLe
                || binaryInst.getOp() == OP.FGt || binaryInst.getOp() == OP.FGe
                || binaryInst.getOp() == OP.FEq || binaryInst.getOp() == OP.FNe) {
            parseFcmp(binaryInst, predefine);
        } else {
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
        if(binaryInst.I64) {
            /***
             * movw	r2,	#1
             * movt	r2,	#15232
             * smull r0, r1, r0, r1
             * asr r3, r2, #31
             * bl __aeabi_ldivmod
             * mov r0, r2
             */
            assert binaryInst.getLeftVal() instanceof BinaryInst
                    && ((BinaryInst) binaryInst.getLeftVal()).I64;
            ArmReg reg1 = getRegOnlyFromValue(((BinaryInst) binaryInst.getLeftVal()).
                    getLeftVal(), insList, predefine);
            ArmReg reg2 = getRegOnlyFromValue(((BinaryInst)
                    binaryInst.getLeftVal()).getRightVal(), insList, predefine);
            addInstr(new ArmSmull(ArmCPUReg.getArmCPUReg(0), ArmCPUReg.getArmCPUReg(1),
                    reg1, reg2), insList, predefine);
            assert binaryInst.getRightVal() instanceof ConstInteger;
            addInstr(new ArmLi(new ArmImm(((ConstInteger)binaryInst.getRightVal()).getValue()),
                    ArmCPUReg.getArmCPUReg(2)), insList, predefine);
            addInstr(new ArmBinary(
                    new ArrayList<>(Arrays.asList(ArmCPUReg.getArmCPUReg(2), new ArmImm(31))),
                    ArmCPUReg.getArmCPUReg(3), ArmBinary.ArmBinaryType.asr), insList, predefine);
            ArmBinary ins2 = new ArmBinary(new ArrayList<>(
                    Arrays.asList(ArmCPUReg.getArmSpReg(), new ArmStackFixer(curArmFunction, 0))),
                    ArmCPUReg.getArmSpReg(), ArmBinary.ArmBinaryType.sub);
            addInstr(ins2, insList, predefine);

            ArmCall call = new ArmCall(ldivmod);
            call.addUsedReg(ArmCPUReg.getArmCPUReg(0));
            call.addUsedReg(ArmCPUReg.getArmCPUReg(1));
            call.addUsedReg(ArmCPUReg.getArmCPUReg(2));
            call.addUsedReg(ArmCPUReg.getArmCPUReg(3));
            addInstr(call, insList, predefine);
            ArmBinary ins3 = new ArmBinary(new ArrayList<>(
                    Arrays.asList(ArmCPUReg.getArmSpReg(), new ArmStackFixer(curArmFunction, 0))),
                    ArmCPUReg.getArmSpReg(), ArmBinary.ArmBinaryType.add);
            addInstr(ins3, insList, predefine);
            addInstr(new ArmMv(ArmCPUReg.getArmCPUReg(2), resReg), insList, predefine);
            value2Reg.put(binaryInst, resReg);
            return;
        }
        ArmOperand leftOperand = getRegOnlyFromValue(binaryInst.getLeftVal(), insList, predefine);
        ArmOperand rightOperand;
        if (binaryInst.getRightVal() instanceof ConstInteger) {
            int val = ((ConstInteger) binaryInst.getRightVal()).getValue();
            int temp = Math.abs(val);
            if ((temp & (temp - 1)) == 0) {
                int shift = 0;
                while (temp >= 2) {
                    shift++;
                    temp /= 2;
                }
                ArmReg reg = getNewIntReg();
                addInstr(new ArmBinary(
                        new ArrayList<>(Arrays.asList(leftOperand, new ArmImm(31))),
                        reg, ArmBinary.ArmBinaryType.asr), insList, predefine);
                addInstr(new ArmBinary(
                        new ArrayList<>(Arrays.asList(reg, new ArmImm(32 - shift))),
                        reg, ArmBinary.ArmBinaryType.lsr), insList, predefine);
                addInstr(new ArmBinary(
                        new ArrayList<>(Arrays.asList(leftOperand, reg)),
                        reg, ArmBinary.ArmBinaryType.add), insList, predefine);
                addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(reg, new ArmImm(shift))),
                        reg, ArmBinary.ArmBinaryType.lsr), insList, predefine);
                addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(reg, new ArmImm(shift))),
                        reg, ArmBinary.ArmBinaryType.lsl), insList, predefine);
                addInstr(new ArmBinary(new ArrayList<>(Arrays.asList(leftOperand, reg)),
                        resReg, ArmBinary.ArmBinaryType.sub), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            }
        }
        rightOperand = getRegOnlyFromValue(binaryInst.getRightVal(), insList, predefine);
        ArmBinary rem = new ArmBinary(new ArrayList<>(
                Arrays.asList(leftOperand, rightOperand)), resReg, ArmBinary.ArmBinaryType.srem);
        value2Reg.put(binaryInst, resReg);
        addInstr(rem, insList, predefine);
    }

    public boolean isIntCmpType(OP op) {
        return op == OP.Eq || op == OP.Ne || op == OP.Ge || op == OP.Gt
                || op == OP.Le || op == OP.Lt;
    }

    public boolean isFloatCmpType(OP op) {
        return op == OP.FEq || op == OP.FNe || op == OP.FGe || op == OP.FGt
                || op == OP.FLe || op == OP.FLt;
    }

    public void parseBrInst(BrInst brInst, boolean predefine) {
        if (preProcess(brInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(brInst, insList);
        assert value2Label.containsKey(brInst.getParentbb())
                && value2Label.get(brInst.getParentbb()) instanceof ArmBlock;
        ArmBlock block = (ArmBlock) value2Label.get(brInst.getParentbb());
        if (brInst.isJump() || brInst.getTrueBlock() == brInst.getFalseBlock()) {
            assert value2Label.containsKey(brInst.getJumpBlock());
            addInstr(new ArmJump(value2Label.get(brInst.getJumpBlock()), block), insList, predefine);
        } else {
            if (!value2Reg.containsKey(brInst.getJudVal())) {
                assert brInst.getJudVal() instanceof Instruction;
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
            addInstr(new ArmCompare(value2Reg.get(brInst.getJudVal()), new ArmImm(1),
                    ArmCompare.CmpType.cmp), insList, predefine);
            if (nextBlock != brInst.getFalseBlock()) {
                ArmBranch br = new ArmBranch((ArmBlock) value2Label.get(brInst.getFalseBlock()),
                        ArmTools.CondType.ne);
                addInstr(br, insList, predefine);
                br.setPredSucc(curArmBlock);
            }
            if (nextBlock != brInst.getTrueBlock()) {
                ArmBranch br = new ArmBranch((ArmBlock) value2Label.get(brInst.getTrueBlock()),
                        ArmTools.CondType.eq);
                addInstr(br, insList, predefine);
                br.setPredSucc(curArmBlock);
            }
        }
    }

    public void parseIcmp(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(binaryInst, insList);
        ArmOperand leftOp = getRegOrImmFromValue(binaryInst.getOperands().get(0), insList, predefine);
        ArmOperand rightOp = getRegOrImmFromValue(binaryInst.getOperands().get(1), insList, predefine);
        ArmTools.CondType type = null;
        switch (binaryInst.getOp()) {
            case Eq -> type = ArmTools.CondType.eq;
            case Ne -> type = ArmTools.CondType.ne;
            case Ge -> type = ArmTools.CondType.ge;
            case Gt -> type = ArmTools.CondType.gt;
            case Le -> type = ArmTools.CondType.le;
            case Lt -> type = ArmTools.CondType.lt;
            default -> {
                assert false;
            }
        }
        if (leftOp instanceof ArmImm) {
            ArmOperand op = leftOp;
            leftOp = rightOp;
            rightOp = op;
            type = getOnlyRevBigSmallType(type);
        }
        if (rightOp instanceof ArmImm) {
            if (ArmTools.isArmImmCanBeEncoded(((ArmImm) rightOp).getValue())) {
                addInstr(new ArmCompare(leftOp, rightOp, ArmCompare.CmpType.cmp), insList, predefine);
            } else {
                ArmReg reg = getNewIntReg();
                addInstr(new ArmLi(rightOp, reg), insList, predefine);
                addInstr(new ArmCompare(leftOp, reg, ArmCompare.CmpType.cmp), insList, predefine);
            }
        } else {
            addInstr(new ArmCompare(leftOp, rightOp, ArmCompare.CmpType.cmp), insList, predefine);
        }
        ArmReg reg = getNewIntReg();
        assert type != null;
        addInstr(new ArmLi(new ArmImm(0), reg, ArmTools.getRevCondType(type))
                , insList, predefine);
        addInstr(new ArmLi(new ArmImm(1), reg, type)
                , insList, predefine);
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
                if (ArmTools.isArmImmCanBeEncoded(((ArmImm) right).getValue())) {
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
                if (ArmTools.isArmImmCanBeEncoded(((ArmImm) right).getValue())) {
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
                if (ArmTools.isArmImmCanBeEncoded(((ArmImm) right).getValue())) {
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
                if (ArmTools.isArmImmCanBeEncoded(((ArmImm) right).getValue())) {
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
                if (ArmTools.isArmImmCanBeEncoded(((ArmImm) left).getValue())) {
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(right,
                            new ArmImm(((ArmImm) left).getValue()))), resReg,
                            ArmBinary.ArmBinaryType.rsb);
                    addInstr(binary, insList, predefine);
                } else {
                    ArmReg assistReg = getNewIntReg();
                    ArmLi li = new ArmLi(new ArmImm(((ArmImm) left).getValue()), assistReg);
                    addInstr(li, insList, predefine);
                    ArmBinary binary = new ArmBinary(new ArrayList<>(Arrays.asList(right, assistReg)), resReg,
                            ArmBinary.ArmBinaryType.rsb);
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
                    if (ArmTools.isArmImmCanBeEncoded(((ArmImm) right).getValue())) {
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

    public void parseDiv(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(binaryInst, insList);
        //TODO:Ready to be optimized
        ArmVirReg resReg = getResReg(binaryInst, ArmVirReg.RegType.intType);
        ArmReg leftOperand = getRegOnlyFromValue(binaryInst.getLeftVal(), insList, predefine);
        ArmOperand rightOperand;
        if (binaryInst.getRightVal() instanceof ConstInteger) {
            int val = ((ConstInteger) binaryInst.getRightVal()).getValue();
            if (val == 1) {
                addInstr(new ArmMv(leftOperand, resReg), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else if (val == -1) {
                addInstr(new ArmRev(leftOperand, resReg), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else if ((Math.abs(val) & (Math.abs(val) - 1)) == 0) {
                //判断是否为2的倍数
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
                    fma.setSigned(true);
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
        if (preProcess(storeInst, predefine)) {
            return;
        }
        ArrayList<ArmInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(storeInst, insList);
        ArmReg stoReg = getRegOnlyFromValue(storeInst.getValue(), insList, predefine);
        if (storeInst.getPointer() instanceof PtrInst || storeInst.getPointer() instanceof PtrSubInst) {
            if (!(value2Reg.containsKey(storeInst.getPointer())
                    || ptr2Offset.containsKey(storeInst.getPointer()))) {
                if (storeInst.getPointer() instanceof PtrInst) {
                    parsePtrInst((PtrInst) storeInst.getPointer(), true);
                } else {
                    parsePtrSubInst((PtrSubInst) storeInst.getPointer(), true);
                }
            }
            if (ptr2Offset.containsKey(storeInst.getPointer())) {
                int offset = ptr2Offset.get(storeInst.getPointer());
                if (offset >= -4095 && offset <= 4095 &&
                        !((PointerType) storeInst.getPointer().getType()).getEleType().isFloatTy()) {
                    ArmSw armSw = new ArmSw(stoReg, ArmCPUReg.getArmSpReg(), new ArmImm(offset));
                    addInstr(armSw, insList, predefine);
                } else if (ArmTools.isLegalVLoadStoreImm(offset)
                        && ((PointerType) storeInst.getPointer().getType()).getEleType().isFloatTy()) {
                    ArmFSw fsw = new ArmFSw(stoReg, ArmCPUReg.getArmSpReg(), new ArmImm(offset));
                    addInstr(fsw, insList, predefine);
                } else {
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
                if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                    ArmSw sw = new ArmSw(stoReg, value2Reg.get(storeInst.getPointer()), new ArmImm(0));
                    addInstr(sw, insList, predefine);
                } else {
                    ArmFSw fsw = new ArmFSw(stoReg, value2Reg.get(storeInst.getPointer()), new ArmImm(0));
                    addInstr(fsw, insList, predefine);
                }
            }
        } else if (storeInst.getPointer() instanceof GlobalVar) {
            assert value2Label.containsKey(storeInst.getPointer());
            ArmGlobalVariable var = (ArmGlobalVariable) value2Label.get(storeInst.getPointer());
            ArmReg assistReg = getNewIntReg();
            ArmLi li = new ArmLi(var, assistReg);
            addInstr(li, insList, predefine);
            if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                ArmSw sw = new ArmSw(stoReg, assistReg, new ArmImm(0));
                addInstr(sw, insList, predefine);
            } else {
                ArmFSw fsw = new ArmFSw(stoReg, assistReg, new ArmImm(0));
                addInstr(fsw, insList, predefine);
            }
        } else if (storeInst.getPointer() instanceof AllocInst) {
            if (!curArmFunction.containOffset(storeInst.getPointer())) {
                parseAlloc((AllocInst) storeInst.getPointer(), true);
            }
            int offset = curArmFunction.getOffset(storeInst.getPointer()) * -1;
            if (offset <= 4095 && offset >= -4095 &&
                    !((PointerType) storeInst.getPointer().getType()).getEleType().isFloatTy()) {
                ArmSw armSw = new ArmSw(stoReg, ArmCPUReg.getArmSpReg(), new ArmImm(offset));
                addInstr(armSw, insList, predefine);
            } else if (ArmTools.isLegalVLoadStoreImm(offset)
                    && ((PointerType) storeInst.getPointer().getType()).getEleType().isFloatTy()) {
                ArmFSw fsw = new ArmFSw(stoReg, ArmCPUReg.getArmSpReg(), new ArmImm(offset));
                addInstr(fsw, insList, predefine);
            } else {
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
        } else if (storeInst.getPointer() instanceof Argument || storeInst.getPointer() instanceof Phi) {
            ArmReg assistReg = getRegOnlyFromValue(storeInst.getPointer(), insList, predefine);
            assert storeInst.getPointer().getType() instanceof PointerType;
            if ((((PointerType) storeInst.getPointer().getType()).getEleType().isFloatTy())) {
                ArmFSw fsw = new ArmFSw(stoReg, assistReg, new ArmImm(0));
                addInstr(fsw, insList, predefine);
            } else {
                ArmSw sw = new ArmSw(stoReg, assistReg, new ArmImm(0));
                addInstr(sw, insList, predefine);
            }
        } else {
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
                if (offset <= 4095 && offset >= -4095 &&
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
                        ArmLoad lw = new ArmLoad(assistReg, ArmCPUReg.getArmSpReg(), resReg);
                        addInstr(lw, insList, predefine);
                    } else {
                        resReg = getResReg(loadInst, ArmVirReg.RegType.floatType);
                        ArmVLoad flw = new ArmVLoad(assistReg, ArmCPUReg.getArmSpReg(), resReg);
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
            if (offset <= 4095 && offset >= -4095 &&
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
        // jr ra
        addInstr(new ArmMv(curArmFunction.getRetReg(), ArmCPUReg.getArmRetReg()), insList, predefine);
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
                offset = offset + ((ArmImm) op2).getValue() * 4;
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
                    int offset = ptr2Offset.get(ptrInst.getTarget()) + ((ArmImm) op2).getValue() * 4;
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
                    int offset = ((ArmImm) op2).getValue() * 4;
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
                    int offset = ((ArmImm) op2).getValue() * 4;
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
                        int offset = ((ArmImm) op2).getValue() * 4;
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
                    offset = ((ArmImm) op2).getValue() * 4;
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
                int offset = ((ArmImm) op2).getValue() * 4;
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
                offset = offset - ((ArmImm) op2).getValue() * 4;
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
                    int offset = ptr2Offset.get(ptrInst.getTarget()) - ((ArmImm) op2).getValue() * 4;
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
                    int offset = ((ArmImm) op2).getValue() * 4;
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
                    int offset = ((ArmImm) op2).getValue() * 4;
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
                        int offset = ((ArmImm) op2).getValue() * 4;
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
                    offset = ((ArmImm) op2).getValue() * 4;
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
                int offset = ((ArmImm) op2).getValue() * 4;
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
