package Backend.Riscv;

import Backend.Riscv.Component.*;
import Backend.Riscv.Instruction.*;
import Backend.Riscv.Operand.*;
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

public class RiscvCodeGen {

    private final IRModule irModule;
    private final LinkedHashMap<Value, RiscvLabel> value2Label = new LinkedHashMap<>();
    private final LinkedHashMap<Value, RiscvReg> value2Reg = new LinkedHashMap<>();
    private final LinkedHashMap<Float, RiscvConstFloat> floats = new LinkedHashMap<>();
    private final LinkedHashMap<Value, Integer> ptr2Offset = new LinkedHashMap<>();
    private RiscvBlock curRvBlock = null;
    private RiscvFunction curRvFunction = null;
    private final LinkedHashMap<BrInst, ArrayList<RiscvBranch>> br2Branch = new LinkedHashMap<>();
    private final LinkedHashMap<Instruction, ArrayList<RiscvInstruction>> predefines = new LinkedHashMap<>();

    private final RiscvModule riscvModule = new RiscvModule(floats);

    public RiscvCodeGen(IRModule irModule) {
        this.irModule = irModule;
    }

    public String removeLeadingAt(String name) {
        if (name.startsWith("@")) {
            return name.substring(1);
        }
        return name;
    }

    public void buildParallelBegin(RiscvFunction parallelStart) {

    }

    public void buildParallelEnd(RiscvFunction parallelEnd) {

    }

    public void run() {
        RiscvCPUReg.getRiscvCPUReg(0);
        RiscvFPUReg.getRiscvFPUReg(0);
        for (var globalVariable : irModule.globalVars()) {
            parseGlobalVar(globalVariable);
        }
        String parallelStartName = "@parallelStart";
        String parallelEndName = "@parallelEnd";
        for (Function function : irModule.libFunctions()) {
            if(Objects.equals(function.getName(), parallelStartName) && Config.parallelOpen) {
                RiscvFunction parallelStart = new RiscvFunction(removeLeadingAt(parallelStartName));
                buildParallelBegin(parallelStart);
                parallelStart.parseArgs(function.getArgs(), value2Reg);
                riscvModule.addFunction(parallelStartName, parallelStart);
                value2Label.put(function, parallelStart);
            } else if (Objects.equals(function.getName(), parallelEndName) && Config.parallelOpen) {
                RiscvFunction parallelEnd = new RiscvFunction(removeLeadingAt(parallelEndName));
                buildParallelEnd(parallelEnd);
                parallelEnd.parseArgs(function.getArgs(), value2Reg);
                riscvModule.addFunction(parallelStartName, parallelEnd);
                value2Label.put(function, parallelEnd);
            } else {
                RiscvFunction rvFunction = new RiscvFunction(removeLeadingAt(function.getName()));
                riscvModule.addFunction(function.getName(), rvFunction);
                value2Label.put(function, rvFunction);
                rvFunction.parseArgs(function.getArgs(), value2Reg);
            }
        }
        for (Function function : irModule.functions()) {
            RiscvFunction rvFunction = new RiscvFunction(removeLeadingAt(function.getName()));
            riscvModule.addFunction(function.getName(), rvFunction);
            value2Label.put(function, rvFunction);
            rvFunction.parseArgs(function.getArgs(), value2Reg);
        }
        for (var function : irModule.functions()) {
            parseFunction(function);
        }
        for (var function : irModule.functions()) {
            for (IList.INode<RiscvBlock, RiscvFunction> bb : ((RiscvFunction) value2Label.get(function)).getBlocks()) {
                if (bb.getValue().getRiscvInstructions().isEmpty()) {
                    System.out.println(bb.getValue());
                }
                if (bb.getPrev() != null) {
                    if (!(bb.getPrev().getValue().getRiscvInstructions().getTail().getValue() instanceof RiscvJ ||
                            bb.getPrev().getValue().getRiscvInstructions().getTail().getValue() instanceof RiscvJr)) {
                        bb.getValue().addPreds(bb.getPrev().getValue());
                    }
                }
                if (bb.getNext() != null) {
                    if (!(bb.getValue().getRiscvInstructions().getTail().getValue() instanceof RiscvJ ||
                                    bb.getValue().getRiscvInstructions().getTail().getValue() instanceof RiscvJr)) {
                        bb.getValue().addSuccs(bb.getNext().getValue());
                    }
                }
                for (IList.INode<RiscvInstruction, RiscvBlock> insNode = bb.getValue().getRiscvInstructions().getHead();
                     insNode != null; insNode = insNode.getNext()) {
                    RiscvInstruction ins = insNode.getValue();
                    if (!(ins instanceof RiscvLw)) {
                        continue;
                    }
                    boolean flag = false;
                    for (IList.INode<RiscvInstruction, RiscvBlock> user : ins.getDefReg().getUsers()) {
                        RiscvInstruction insUser = user.getValue();
                        if ((insUser instanceof RiscvBinary && ((RiscvBinary) insUser).is64Ins())
                                || insUser instanceof RiscvBranch) {
                            if (insUser.getDefReg() != null && !insUser.getDefReg().equals(ins.getDefReg())) {
                                flag = true;
                                break;
                            }
                        }
                    }
                    if (flag) {
                        IList.INode<RiscvInstruction, RiscvBlock> sext =
                                new IList.INode<>(new RiscvSext(ins.getDefReg(), ins.getDefReg()));
                        sext.insertAfter(insNode);
                        ins.getDefReg().beUsed(sext);
                    }
                }
            }
        }
    }

    public void parseFunction(Function function) {
        curRvBlock = null;
        curRvFunction = (RiscvFunction) value2Label.get(function);
        for (IList.INode<BasicBlock, Function> basicBlockNode : function.getBbs()) {
            BasicBlock bb = basicBlockNode.getValue();
            RiscvBlock temp_block = new RiscvBlock(curRvFunction.getName() + "_block" + curRvFunction.allocBlockIndex());
            value2Label.put(bb, temp_block);
        }
        boolean flag = false;
        for (IList.INode<BasicBlock, Function> basicBlockNode : function.getBbs()) {
            if (curRvBlock != null) {
                curRvFunction.addBlock(new IList.INode<>(curRvBlock));
            }
            BasicBlock bb = basicBlockNode.getValue();
            curRvBlock = (RiscvBlock) value2Label.get(bb);
            if (!flag) {
                //将所有函数中的用于参数的mv指令加入Block
                for (RiscvMv riscvMv : curRvFunction.getMvs()) {
                    addInstr(riscvMv, null, false);
                }
                //处理返回地址
                RiscvMv mv = new RiscvMv(RiscvCPUReg.getRiscvRaReg(), curRvFunction.getRetReg());
                addInstr(mv, null, false);
                flag = true;
            }
            parseBasicBlock(bb);
        }
        if (function.getBbs().getSize() != 0) {
            curRvFunction.addBlock(new IList.INode<>(curRvBlock));
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
            parseLoadInst((LoadInst) ins, predefine);
        } else if (ins instanceof Move) {
            parseMove((Move) ins, predefine);
        } else if (ins instanceof PtrInst) {
            parsePtrInst((PtrInst) ins, predefine);
        } else if (ins instanceof PtrSubInst) {
            parsePtrSubInst((PtrSubInst) ins, predefine);
        } else if (ins instanceof RetInst) {
            parseRetInst((RetInst) ins, predefine);
        } else if (ins instanceof StoreInst) {
            parseStoreInst((StoreInst) ins, predefine);
        } else if (ins instanceof Phi) {
            if (!value2Reg.containsKey(ins)) {
                RiscvVirReg reg = getNewVirReg(ins.getType().isFloatTy() ? RiscvVirReg.RegType.floatType
                        : RiscvVirReg.RegType.intType);
                value2Reg.put(ins, reg);
            }
        } else {
            System.err.println("ERROR");
        }
    }

    public RiscvVirReg getResReg(Instruction ins, RiscvVirReg.RegType regType) {
        if (value2Reg.containsKey(ins)) {
            assert value2Reg.get(ins) instanceof RiscvVirReg;
            RiscvVirReg virReg = (RiscvVirReg) value2Reg.get(ins);
            assert virReg.regType == regType;
            return virReg;
        } else {
            return getNewVirReg(regType);
        }
    }

    public void parseAlloc(AllocInst allocInst, boolean predefine) {
        if (!predefines.containsKey(allocInst)) {
            curRvFunction.alloc(allocInst);
            predefines.put(allocInst, null);
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
        } else if (binaryInst.getOp() == OP.Xor) {
            parseXor(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.And) {
            parseAnd(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Or) {
            parseOr(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Fsub || binaryInst.getOp() == OP.Fadd
                || binaryInst.getOp() == OP.Fmul || binaryInst.getOp() == OP.Fdiv) {
            parseFbin(binaryInst, predefine);
        } else if (binaryInst.getOp() == OP.Mod || binaryInst.getOp() == OP.Fmod) {
            assert binaryInst.getOp() != OP.Fmod;
            parseMod(binaryInst, predefine);
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

    public boolean judgeCond(BinaryInst binaryInst) {
        if (binaryInst.getLeftVal() instanceof Move || binaryInst.getRightVal() instanceof Move) {
            return true;
        }
        return false;
    }

    public void parseIcmp(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        boolean flag = false;
        if (judgeCond(binaryInst)) {
            for (User user : binaryInst.getUserList()) {
                if (!(user instanceof BrInst)) {
                    flag = true;
                } else {
                    //只有lt,ge,eq
                    RiscvOperand leftOperand = getRegOnlyFromValue(binaryInst.getOperand(0), insList, predefine);
                    RiscvOperand rightOperand = getRegOnlyFromValue(binaryInst.getOperand(1), insList, predefine);
                    BasicBlock block = ((BrInst) user).getParentbb();
                    Function func = block.getParentFunc();
                    BasicBlock nextBlock = null;
                    for (IList.INode<BasicBlock, Function> bb : func.getBbs()) {
                        if (bb.getValue() == block && bb.getNext() != null) {
                            nextBlock = bb.getNext().getValue();
                        }
                    }
                    if (((BrInst) user).getTrueBlock() == ((BrInst) user).getFalseBlock()) {
                        continue;
                    }
                    assert !((BrInst) user).isJump();
                    if (((BrInst) user).getTrueBlock() == nextBlock ||
                            ((BrInst) user).getFalseBlock() == nextBlock) {
                        boolean reverseFlag = ((BrInst) user).getTrueBlock() == nextBlock;
                        RiscvLabel jBlock = (((BrInst) user).getTrueBlock() == nextBlock) ?
                                value2Label.get(((BrInst) user).getFalseBlock()) :
                                value2Label.get(((BrInst) user).getTrueBlock());
                        RiscvBranch branch =
                                RiscvBranch.buildRiscvBranch(leftOperand, rightOperand, binaryInst.getOp(), jBlock, reverseFlag);
                        br2Branch.put((BrInst) user, new ArrayList<>(Collections.singleton(branch)));
                    } else {
                        RiscvBranch branch1 =
                                RiscvBranch.buildRiscvBranch(leftOperand, rightOperand, binaryInst.getOp(),
                                        value2Label.get(((BrInst) user).getTrueBlock()), false);
                        RiscvBranch branch2 =
                                RiscvBranch.buildRiscvBranch(leftOperand, rightOperand, binaryInst.getOp(),
                                        value2Label.get(((BrInst) user).getFalseBlock()), true);
                        br2Branch.put((BrInst) user, new ArrayList<>(Arrays.asList(branch1, branch2)));
                    }
                }
            }
            if (flag) {
                RiscvReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.intType);
                RiscvOperand leftOperand = getRegOnlyFromValue(binaryInst.getOperand(0), insList, predefine);
                RiscvOperand rightOperand = getRegOnlyFromValue(binaryInst.getOperand(1), insList, predefine);
                if (binaryInst.getOp() == OP.Lt) {
                    RiscvBinary ins1 = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(leftOperand, rightOperand)), resReg,
                            RiscvBinary.RiscvBinaryType.slt);
                    addInstr(ins1, insList, predefine);
                } else if (binaryInst.getOp() == OP.Gt) {
                    RiscvBinary ins1 = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(rightOperand, leftOperand)), resReg,
                            RiscvBinary.RiscvBinaryType.slt);
                    addInstr(ins1, insList, predefine);
                } else if (binaryInst.getOp() == OP.Ge) {
                    RiscvBinary ins1 = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(rightOperand, leftOperand)), resReg,
                            RiscvBinary.RiscvBinaryType.subw);
                    addInstr(ins1, insList, predefine);
                    RiscvBinary ins2 = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(resReg, new RiscvImm(1))), resReg,
                            RiscvBinary.RiscvBinaryType.slti);
                    addInstr(ins2, insList, predefine);
                } else if (binaryInst.getOp() == OP.Ne) {
                    RiscvBinary ins1 = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(rightOperand, leftOperand)), resReg,
                            RiscvBinary.RiscvBinaryType.subw);
                    addInstr(ins1, insList, predefine);
                    RiscvBinary ins2 = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvCPUReg(0)
                                    , resReg)), resReg, RiscvBinary.RiscvBinaryType.sltu);
                    addInstr(ins2, insList, predefine);
                } else if (binaryInst.getOp() == OP.Eq) {
                    RiscvBinary ins1 = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(leftOperand, rightOperand)), resReg,
                            RiscvBinary.RiscvBinaryType.subw);
                    addInstr(ins1, insList, predefine);
                    RiscvBinary ins2 = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(resReg, new RiscvImm(1))), resReg,
                            RiscvBinary.RiscvBinaryType.sltiu);
                    addInstr(ins2, insList, predefine);
                } else if (binaryInst.getOp() == OP.Le) {
                    RiscvBinary ins1 = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(leftOperand, rightOperand)), resReg,
                            RiscvBinary.RiscvBinaryType.subw);
                    addInstr(ins1, insList, predefine);
                    RiscvBinary ins2 = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(resReg, new RiscvImm(1))), resReg,
                            RiscvBinary.RiscvBinaryType.slti);
                    addInstr(ins2, insList, predefine);
                }
                value2Reg.put(binaryInst, resReg);
            }
            predefines.put(binaryInst, insList);
        } else {
            RiscvReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.intType);
            RiscvOperand leftOperand = getRegOnlyFromValue(binaryInst.getOperand(0), insList, predefine);
            RiscvOperand rightOperand = getRegOnlyFromValue(binaryInst.getOperand(1), insList, predefine);
            if (binaryInst.getOp() == OP.Lt) {
                RiscvBinary ins1 = new RiscvBinary(
                        new ArrayList<>(Arrays.asList(leftOperand, rightOperand)), resReg,
                        RiscvBinary.RiscvBinaryType.slt);
                addInstr(ins1, insList, predefine);
            } else if (binaryInst.getOp() == OP.Gt) {
                RiscvBinary ins1 = new RiscvBinary(
                        new ArrayList<>(Arrays.asList(rightOperand, leftOperand)), resReg,
                        RiscvBinary.RiscvBinaryType.slt);
                addInstr(ins1, insList, predefine);
            } else if (binaryInst.getOp() == OP.Ge) {
                RiscvBinary ins1 = new RiscvBinary(
                        new ArrayList<>(Arrays.asList(rightOperand, leftOperand)), resReg,
                        RiscvBinary.RiscvBinaryType.subw);
                addInstr(ins1, insList, predefine);
                RiscvBinary ins2 = new RiscvBinary(
                        new ArrayList<>(Arrays.asList(resReg, new RiscvImm(1))), resReg,
                        RiscvBinary.RiscvBinaryType.slti);
                addInstr(ins2, insList, predefine);
            } else if (binaryInst.getOp() == OP.Ne) {
                RiscvBinary ins1 = new RiscvBinary(
                        new ArrayList<>(Arrays.asList(rightOperand, leftOperand)), resReg,
                        RiscvBinary.RiscvBinaryType.subw);
                addInstr(ins1, insList, predefine);
                RiscvBinary ins2 = new RiscvBinary(
                        new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvCPUReg(0)
                                , resReg)), resReg, RiscvBinary.RiscvBinaryType.sltu);
                addInstr(ins2, insList, predefine);
            } else if (binaryInst.getOp() == OP.Eq) {
                RiscvBinary ins1 = new RiscvBinary(
                        new ArrayList<>(Arrays.asList(leftOperand, rightOperand)), resReg,
                        RiscvBinary.RiscvBinaryType.subw);
                addInstr(ins1, insList, predefine);
                RiscvBinary ins2 = new RiscvBinary(
                        new ArrayList<>(Arrays.asList(resReg, new RiscvImm(1))), resReg,
                        RiscvBinary.RiscvBinaryType.sltiu);
                addInstr(ins2, insList, predefine);
            } else if (binaryInst.getOp() == OP.Le) {
                RiscvBinary ins1 = new RiscvBinary(
                        new ArrayList<>(Arrays.asList(leftOperand, rightOperand)), resReg,
                        RiscvBinary.RiscvBinaryType.subw);
                addInstr(ins1, insList, predefine);
                RiscvBinary ins2 = new RiscvBinary(
                        new ArrayList<>(Arrays.asList(resReg, new RiscvImm(1))), resReg,
                        RiscvBinary.RiscvBinaryType.slti);
                addInstr(ins2, insList, predefine);
            }
            value2Reg.put(binaryInst, resReg);
            predefines.put(binaryInst, insList);
        }
    }

    public void parseFcmp(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        boolean flag = false;
        for (User user : binaryInst.getUserList()) {
            if (user instanceof BrInst && !((BrInst) user).isJump()
                    && ((BrInst) user).getTrueBlock() == ((BrInst) user).getFalseBlock()) {
                continue;
            }
            if (!flag) {
                flag = true;
                RiscvReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.intType);
                RiscvOperand leftOperand = getRegOnlyFromValue(binaryInst.getOperand(0), insList, predefine);
                RiscvOperand rightOperand = getRegOnlyFromValue(binaryInst.getOperand(1), insList, predefine);
                RiscvBinary.RiscvBinaryType type = RiscvBinary.RiscvBinaryType.feq;
                boolean reverse = false;
                switch (binaryInst.getOp()) {
                    case FEq -> {
                    }
                    case FNe -> reverse = true;
                    case FGt -> {
                        type = RiscvBinary.RiscvBinaryType.flt;
                        RiscvOperand temp = rightOperand;
                        rightOperand = leftOperand;
                        leftOperand = temp;
                    }
                    case FGe -> {
                        type = RiscvBinary.RiscvBinaryType.fle;
                        RiscvOperand temp = rightOperand;
                        rightOperand = leftOperand;
                        leftOperand = temp;
                    }
                    case FLt -> type = RiscvBinary.RiscvBinaryType.flt;
                    case FLe -> type = RiscvBinary.RiscvBinaryType.fle;
                }
                RiscvBinary riscvInstr1 = new RiscvBinary(new ArrayList<>(
                        Arrays.asList(leftOperand, rightOperand)), resReg, type);
                addInstr(riscvInstr1, insList, predefine);
                if (reverse) {
                    RiscvBinary riscvInstr2 = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(resReg, new RiscvImm(1))), resReg,
                            RiscvBinary.RiscvBinaryType.xori);
                    addInstr(riscvInstr2, insList, predefine);
                }
                value2Reg.put(binaryInst, resReg);
            }
            if (!(binaryInst.getRightVal() instanceof Move) && !(binaryInst.getLeftVal() instanceof Move)
                    && user instanceof BrInst) {
                BasicBlock block = ((BrInst) user).getParentbb();
                Function func = block.getParentFunc();
                BasicBlock nextBlock = null;
                for (IList.INode<BasicBlock, Function> bb : func.getBbs()) {
                    if (bb.getValue() == block && bb.getNext() != null) {
                        nextBlock = bb.getNext().getValue();
                    }
                }
                if (((BrInst) user).getTrueBlock() == nextBlock ||
                        ((BrInst) user).getFalseBlock() == nextBlock) {
                    RiscvLabel jBlock = (((BrInst) user).getTrueBlock() == nextBlock) ?
                            value2Label.get(((BrInst) user).getFalseBlock()) :
                            value2Label.get(((BrInst) user).getTrueBlock());
                    //flag == true 代表为1 跳往trueblock 为0去falseblock
                    //flag == true 那么就说明 ret == 0 时 才需要跳 所以是bne ret, 0 , jblock
                    RiscvBranch.RiscvCmpType cmpType =
                            (((BrInst) user).getTrueBlock() == nextBlock) ? RiscvBranch.RiscvCmpType.beq : RiscvBranch.RiscvCmpType.bne;
                    RiscvBranch riscvBranch = new RiscvBranch(value2Reg.get(binaryInst), RiscvCPUReg.getZeroReg(), jBlock, cmpType);
                    br2Branch.put((BrInst) user, new ArrayList<>(Collections.singleton(riscvBranch)));
                } else {
                    RiscvBranch riscvBranch1 = new RiscvBranch(value2Reg.get(binaryInst), RiscvCPUReg.getZeroReg(),
                            value2Label.get(((BrInst) user).getFalseBlock()),
                            RiscvBranch.RiscvCmpType.beq);
                    RiscvBranch riscvBranch2 = new RiscvBranch(value2Reg.get(binaryInst), RiscvCPUReg.getZeroReg(),
                            value2Label.get(((BrInst) user).getTrueBlock()),
                            RiscvBranch.RiscvCmpType.bne);
                    br2Branch.put((BrInst) user, new ArrayList<>(Arrays.asList(riscvBranch1, riscvBranch2)));
                }
            }
        }
        predefines.put(binaryInst, insList);
    }

    public void parseAdd(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        RiscvVirReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.intType);
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        if (leftVal instanceof ConstInteger) {
            Value tempVal = leftVal;
            leftVal = rightVal;
            rightVal = tempVal;
        }
        RiscvReg left = getRegOnlyFromValue(leftVal, insList, predefine);
        RiscvOperand right = getRegOrImmFromValue(rightVal, true, insList, predefine);
        if (right instanceof RiscvImm) {
            if (((RiscvImm) right).getValue() == 0) {
                RiscvMv mv = new RiscvMv(left, resReg);
                addInstr(mv, insList, predefine);
            } else {
                RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                        RiscvBinary.RiscvBinaryType.addiw);
                addInstr(binary, insList, predefine);
            }
        } else {
            RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                    RiscvBinary.RiscvBinaryType.addw);
            addInstr(binary, insList, predefine);
        }
        value2Reg.put(binaryInst, resReg);
        predefines.put(binaryInst, insList);
    }

    public void parseAnd(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        RiscvVirReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.intType);
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        if (leftVal instanceof ConstInteger) {
            Value tempVal = leftVal;
            leftVal = rightVal;
            rightVal = tempVal;
        }
        RiscvReg left = getRegOnlyFromValue(leftVal, insList, predefine);
        RiscvOperand right = getRegOrImmFromValue(rightVal, true, insList, predefine);
        if (right instanceof RiscvImm) {
            if (((RiscvImm) right).getValue() == 0) {
                RiscvMv mv = new RiscvMv(left, resReg);
                addInstr(mv, insList, predefine);
            } else {
                RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                        RiscvBinary.RiscvBinaryType.andi);
                addInstr(binary, insList, predefine);
            }
        } else {
            RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                    RiscvBinary.RiscvBinaryType.and);
            addInstr(binary, insList, predefine);
        }
        value2Reg.put(binaryInst, resReg);
        predefines.put(binaryInst, insList);
    }

    public void parseOr(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        RiscvVirReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.intType);
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        if (leftVal instanceof ConstInteger) {
            Value tempVal = leftVal;
            leftVal = rightVal;
            rightVal = tempVal;
        }
        RiscvReg left = getRegOnlyFromValue(leftVal, insList, predefine);
        RiscvOperand right = getRegOrImmFromValue(rightVal, true, insList, predefine);
        if (right instanceof RiscvImm) {
            if (((RiscvImm) right).getValue() == 0) {
                RiscvMv mv = new RiscvMv(left, resReg);
                addInstr(mv, insList, predefine);
            } else {
                RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                        RiscvBinary.RiscvBinaryType.ori);
                addInstr(binary, insList, predefine);
            }
        } else {
            RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                    RiscvBinary.RiscvBinaryType.or);
            addInstr(binary, insList, predefine);
        }
        value2Reg.put(binaryInst, resReg);
        predefines.put(binaryInst, insList);
    }

    public void parseXor(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        RiscvVirReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.intType);
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        if (leftVal instanceof ConstInteger) {
            Value tempVal = leftVal;
            leftVal = rightVal;
            rightVal = tempVal;
        }
        RiscvReg left = getRegOnlyFromValue(leftVal, insList, predefine);
        RiscvOperand right = getRegOrImmFromValue(rightVal, true, insList, predefine);
        if (right instanceof RiscvImm) {
            if (((RiscvImm) right).getValue() == 0) {
                RiscvMv mv = new RiscvMv(left, resReg);
                addInstr(mv, insList, predefine);
            } else {
                RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                        RiscvBinary.RiscvBinaryType.xori);
                addInstr(binary, insList, predefine);
            }
        } else {
            RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                    RiscvBinary.RiscvBinaryType.xor);
            addInstr(binary, insList, predefine);
        }
        value2Reg.put(binaryInst, resReg);
        predefines.put(binaryInst, insList);
    }

    public void parseSub(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        RiscvVirReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.intType);
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        RiscvReg left = getRegOnlyFromValue(leftVal, insList, predefine);
        if (rightVal instanceof ConstInteger) {
            rightVal = new ConstInteger(((ConstInteger) rightVal).getValue() * -1, IntegerType.I32);
        }
        RiscvOperand right = getRegOrImmFromValue(rightVal, true, insList, predefine);
        if (right instanceof RiscvImm) {
            if (((RiscvImm) right).getValue() == 0) {
                RiscvMv mv = new RiscvMv(left, resReg);
                addInstr(mv, insList, predefine);
            } else {
                RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                        RiscvBinary.RiscvBinaryType.addiw);
                addInstr(binary, insList, predefine);
            }
        } else {
            RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(left, right)), resReg,
                    RiscvBinary.RiscvBinaryType.subw);
            addInstr(binary, insList, predefine);
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
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(binaryInst, insList);
        RiscvReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.intType);
        if(binaryInst.I64) {
            RiscvReg leftOperand = getRegOnlyFromValue(binaryInst.getLeftVal(), insList, predefine);
            RiscvReg rightOperand = getRegOnlyFromValue(binaryInst.getRightVal(), insList, predefine);
            addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(leftOperand, rightOperand)),
                            resReg, RiscvBinary.RiscvBinaryType.mul), insList, predefine);
            value2Reg.put(binaryInst, resReg);
            return;
        }
        //TODO: ready to be optimized
        Value leftVal = binaryInst.getLeftVal();
        Value rightVal = binaryInst.getRightVal();
        if (leftVal instanceof ConstInteger) {
            Value temp = leftVal;
            leftVal = rightVal;
            rightVal = temp;
        }
        RiscvReg leftOperand = getRegOnlyFromValue(leftVal, insList, predefine);
        RiscvOperand rightOperand;
        if (rightVal instanceof ConstInteger) {
            if (((ConstInteger) rightVal).getValue() == 1) {
                addInstr(new RiscvMv(leftOperand, resReg), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else if (((ConstInteger) rightVal).getValue() == -1) {
                addInstr(new RiscvBinary(new ArrayList<>(
                        Arrays.asList(RiscvCPUReg.getRiscvCPUReg(0), leftOperand)),
                        resReg, RiscvBinary.RiscvBinaryType.subw), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else if (((ConstInteger) rightVal).getValue() == 0) {
                addInstr(new RiscvLi(new RiscvImm(0), resReg), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else {
                ArrayList<Integer> ans = canOpt(Math.abs(((ConstInteger) rightVal).getValue()));
                if (ans.size() > 0) {
                    if (((ConstInteger) rightVal).getValue() < 0) {
                        RiscvVirReg reg = getNewVirReg(RiscvVirReg.RegType.intType);
                        addInstr(new RiscvBinary(
                                new ArrayList<>(Arrays.asList(
                                        RiscvCPUReg.getRiscvCPUReg(0), leftOperand)),
                                reg, RiscvBinary.RiscvBinaryType.subw), insList, predefine);
                        leftOperand = reg;
                    }
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    if (ans.size() == 1) {
                        int shift = getShift(Math.abs(ans.get(0)));
                        addInstr(new RiscvBinary(
                                new ArrayList<>(Arrays.asList(leftOperand, new RiscvImm(shift))),
                                resReg, RiscvBinary.RiscvBinaryType.slliw), insList, predefine);
                        value2Reg.put(binaryInst, resReg);
                        return;
                    } else if (ans.size() == 2) {
                        assert ans.get(0) > 0;
                        int shift = getShift(Math.abs(ans.get(0)));
                        if (shift == 0) {
                            addInstr(new RiscvMv(leftOperand, resReg), insList, predefine);
                        } else {
                            addInstr(new RiscvBinary(
                                    new ArrayList<>(Arrays.asList(leftOperand, new RiscvImm(shift))),
                                    resReg, RiscvBinary.RiscvBinaryType.slliw), insList, predefine);
                        }
                        boolean flag = ans.get(1) > 0;
                        shift = getShift(Math.abs(ans.get(1)));
                        if (flag) {
                            if (shift == 0) {
                                addInstr(new RiscvBinary(
                                        new ArrayList<>(Arrays.asList(leftOperand, resReg)),
                                        resReg, RiscvBinary.RiscvBinaryType.addw), insList, predefine);
                            } else {
                                addInstr(new RiscvBinary(
                                        new ArrayList<>(Arrays.asList(leftOperand, new RiscvImm(shift))),
                                        assistReg, RiscvBinary.RiscvBinaryType.slliw), insList, predefine);
                                addInstr(new RiscvBinary(
                                        new ArrayList<>(Arrays.asList(assistReg, resReg)),
                                        resReg, RiscvBinary.RiscvBinaryType.addw), insList, predefine);
                            }
                        } else {
                            if (shift == 0) {
                                addInstr(new RiscvBinary(
                                        new ArrayList<>(Arrays.asList(resReg, leftOperand)),
                                        resReg, RiscvBinary.RiscvBinaryType.subw), insList, predefine);
                            } else {
                                addInstr(new RiscvBinary(
                                        new ArrayList<>(Arrays.asList(leftOperand, new RiscvImm(shift))),
                                        assistReg, RiscvBinary.RiscvBinaryType.slliw), insList, predefine);
                                addInstr(new RiscvBinary(
                                        new ArrayList<>(Arrays.asList(resReg, assistReg)),
                                        resReg, RiscvBinary.RiscvBinaryType.subw), insList, predefine);
                            }
                        }
                        value2Reg.put(binaryInst, resReg);
                        return;
                    }
                }
            }
        }
        rightOperand = getRegOnlyFromValue(rightVal, insList, predefine);
        RiscvBinary mul = new RiscvBinary(new ArrayList<>(
                Arrays.asList(leftOperand, rightOperand)), resReg, RiscvBinary.RiscvBinaryType.mulw);
        value2Reg.put(binaryInst, resReg);
        addInstr(mul, insList, predefine);
    }

    public void parseDiv(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(binaryInst, insList);
        //TODO:Ready to be optimized
        RiscvVirReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.intType);
        RiscvReg leftOperand = getRegOnlyFromValue(binaryInst.getLeftVal(), insList, predefine);
        RiscvOperand rightOperand;
        if (binaryInst.getRightVal() instanceof ConstInteger) {
            int val = ((ConstInteger) binaryInst.getRightVal()).getValue();
            if (val == 1) {
                addInstr(new RiscvMv(leftOperand, resReg), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else if (val == -1) {
                addInstr(new RiscvBinary(
                        new ArrayList<>(Arrays.asList(
                                RiscvCPUReg.getRiscvCPUReg(0), leftOperand)),
                        resReg, RiscvBinary.RiscvBinaryType.subw), insList, predefine);
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
                    RiscvVirReg reg1 = getNewVirReg(RiscvVirReg.RegType.intType);
                    addInstr(new RiscvBinary(
                            new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvCPUReg(0), leftOperand)),
                            reg1, RiscvBinary.RiscvBinaryType.subw), insList, predefine);
                    leftOperand = reg1;
                }
                RiscvReg reg = getNewVirReg(RiscvVirReg.RegType.intType);
                addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(leftOperand, new RiscvImm(31))),
                        reg, RiscvBinary.RiscvBinaryType.sraiw), insList, predefine);
                addInstr(new RiscvBinary(
                        new ArrayList<>(Arrays.asList(reg, new RiscvImm(32 - shift))),
                        reg, RiscvBinary.RiscvBinaryType.srliw), insList, predefine);
                addInstr(new RiscvBinary(
                        new ArrayList<>(Arrays.asList(leftOperand, reg)),
                        reg, RiscvBinary.RiscvBinaryType.addw), insList, predefine);
                addInstr(new RiscvBinary(
                        new ArrayList<>(Arrays.asList(reg, new RiscvImm(shift))),
                        resReg, RiscvBinary.RiscvBinaryType.sraiw), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            } else if (Config.divOptOpen) {
                int divNum = ((ConstInteger) binaryInst.getRightVal()).getValue();
                int le = (int) Math.max(Math.log(divNum) / Math.log(2), 1);
                long ini = (1L + ((1L << (31 + le)) / divNum)) - (1L << 32);
                int shift = le - 1;
                addInstr(new RiscvLongLi(ini, resReg), insList, predefine);
                addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(resReg, leftOperand)),
                        resReg, RiscvBinary.RiscvBinaryType.mul), insList, predefine);
                addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(resReg, new RiscvImm(32))),
                        resReg, RiscvBinary.RiscvBinaryType.srli), insList, predefine);
                addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(resReg, leftOperand)),
                        resReg, RiscvBinary.RiscvBinaryType.add), insList, predefine);
                addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(resReg, new RiscvImm(shift))),
                        resReg, RiscvBinary.RiscvBinaryType.sraiw), insList, predefine);
                RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(leftOperand, RiscvCPUReg.getZeroReg())),
                        assistReg, RiscvBinary.RiscvBinaryType.slt), insList, predefine);
                addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(resReg, assistReg)),
                        resReg, RiscvBinary.RiscvBinaryType.add), insList, predefine);
                if (divNum < 0) {
                    addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getZeroReg(),
                            resReg)), resReg, RiscvBinary.RiscvBinaryType.subw), insList, predefine);
                }
                value2Reg.put(binaryInst, resReg);
                return;
            }
        }
        rightOperand = getRegOnlyFromValue(binaryInst.getRightVal(), insList, predefine);
        RiscvBinary div = new RiscvBinary(new ArrayList<>(
                Arrays.asList(leftOperand, rightOperand)), resReg, RiscvBinary.RiscvBinaryType.divw);
        value2Reg.put(binaryInst, resReg);
        addInstr(div, insList, predefine);
    }

    public void parseFbin(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(binaryInst, insList);
        RiscvVirReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.floatType);
        RiscvOperand left = getRegOnlyFromValue(binaryInst.getLeftVal(), insList, predefine);
        RiscvOperand right = getRegOnlyFromValue(binaryInst.getRightVal(), insList, predefine);
        RiscvBinary.RiscvBinaryType type = null;
        switch (binaryInst.getOp()) {
            case Fadd -> type = RiscvBinary.RiscvBinaryType.fadd;
            case Fsub -> type = RiscvBinary.RiscvBinaryType.fsub;
            case Fmul -> type = RiscvBinary.RiscvBinaryType.fmul;
            case Fdiv -> type = RiscvBinary.RiscvBinaryType.fdiv;
        }
        RiscvBinary binary = new RiscvBinary(new ArrayList<>(
                Arrays.asList(left, right)), resReg, type);
        value2Reg.put(binaryInst, resReg);
        addInstr(binary, insList, predefine);
    }

    public void parseMod(BinaryInst binaryInst, boolean predefine) {
        if (preProcess(binaryInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(binaryInst, insList);
        RiscvVirReg resReg = getResReg(binaryInst, RiscvVirReg.RegType.intType);
        if(binaryInst.I64) {
            RiscvReg leftOperand = getRegOnlyFromValue(binaryInst.getLeftVal(), insList, predefine);
            RiscvReg rightOperand = getRegOnlyFromValue(binaryInst.getRightVal(), insList, predefine);
            addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(leftOperand, rightOperand)),
                    resReg, RiscvBinary.RiscvBinaryType.rem), insList, predefine);
            value2Reg.put(binaryInst, resReg);
            return;
        }
        RiscvOperand leftOperand = getRegOnlyFromValue(binaryInst.getLeftVal(), insList, predefine);
        RiscvOperand rightOperand;
        if (binaryInst.getRightVal() instanceof ConstInteger) {
            int val = ((ConstInteger) binaryInst.getRightVal()).getValue();
            int temp = Math.abs(val);
            if ((temp & (temp - 1)) == 0) {
                int shift = 0;
                while (temp >= 2) {
                    shift++;
                    temp /= 2;
                }
                RiscvReg reg = getNewVirReg(RiscvVirReg.RegType.intType);
                addInstr(new RiscvBinary(
                        new ArrayList<>(Arrays.asList(leftOperand, new RiscvImm(31))),
                        reg, RiscvBinary.RiscvBinaryType.sraiw), insList, predefine);
                addInstr(new RiscvBinary(
                        new ArrayList<>(Arrays.asList(reg, new RiscvImm(32 - shift))),
                        reg, RiscvBinary.RiscvBinaryType.srliw), insList, predefine);
                addInstr(new RiscvBinary(
                        new ArrayList<>(Arrays.asList(leftOperand, reg)),
                        reg, RiscvBinary.RiscvBinaryType.addw), insList, predefine);
                addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(reg, new RiscvImm(shift))),
                        reg, RiscvBinary.RiscvBinaryType.srliw), insList, predefine);
                addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(reg, new RiscvImm(shift))),
                        reg, RiscvBinary.RiscvBinaryType.slli), insList, predefine);
                addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(leftOperand, reg)),
                        resReg, RiscvBinary.RiscvBinaryType.subw), insList, predefine);
                value2Reg.put(binaryInst, resReg);
                return;
            }
        }
        rightOperand = getRegOnlyFromValue(binaryInst.getRightVal(), insList, predefine);
        RiscvBinary rem = new RiscvBinary(new ArrayList<>(
                Arrays.asList(leftOperand, rightOperand)), resReg, RiscvBinary.RiscvBinaryType.remw);
        value2Reg.put(binaryInst, resReg);
        addInstr(rem, insList, predefine);
    }

    public void parseBrInst(BrInst brInst, boolean predefine) {
        if (preProcess(brInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(brInst, insList);
        assert value2Label.containsKey(brInst.getParentbb())
                && value2Label.get(brInst.getParentbb()) instanceof RiscvBlock;
        RiscvBlock block = (RiscvBlock) value2Label.get(brInst.getParentbb());
        if (brInst.isJump()) {
            assert value2Label.containsKey(brInst.getJumpBlock());
            addInstr(new RiscvJ(value2Label.get(brInst.getJumpBlock()), block), insList, predefine);
        } else {
            if (!br2Branch.containsKey(brInst)) {
                assert brInst.getJudVal() instanceof Instruction;
                parseInstruction((Instruction) brInst.getJudVal(), true);
            }
            if (brInst.getFalseBlock() == brInst.getTrueBlock()) {
                addInstr(new RiscvJ(value2Label.get(brInst.getTrueBlock()), block), insList, predefine);
                return;
            }
            if (br2Branch.containsKey(brInst)) {
                if (br2Branch.get(brInst).size() == 1) {
                    RiscvBranch ins = br2Branch.get(brInst).get(0);
                    ins.setPredSucc(block);
                    addInstr(ins, insList, predefine);
                } else {
                    RiscvBranch ins = br2Branch.get(brInst).get(0);
                    ins.setPredSucc(block);
                    addInstr(br2Branch.get(brInst).get(0), insList, predefine);
                    ins = br2Branch.get(brInst).get(1);
                    ins.setPredSucc(block);
                    addInstr(ins, insList, predefine);
                }
            } else {
                assert value2Reg.containsKey(brInst.getJudVal());
                BasicBlock block1 = brInst.getParentbb();
                Function func = block1.getParentFunc();
                BasicBlock nextBlock = null;
                for (IList.INode<BasicBlock, Function> bb : func.getBbs()) {
                    if (bb.getValue() == block1 && bb.getNext() != null) {
                        nextBlock = bb.getNext().getValue();
                    }
                }
                if (nextBlock != brInst.getTrueBlock()) {
                    RiscvBranch br = new RiscvBranch(value2Reg.get(brInst.getJudVal()), RiscvCPUReg.getZeroReg(),
                            value2Label.get(brInst.getTrueBlock()), RiscvBranch.RiscvCmpType.bne);
                    addInstr(br, insList, predefine);
                    br.setPredSucc(block);
                }
                if (nextBlock != brInst.getFalseBlock()) {
                    RiscvBranch br = new RiscvBranch(value2Reg.get(brInst.getJudVal()), RiscvCPUReg.getZeroReg(),
                            value2Label.get(brInst.getFalseBlock()), RiscvBranch.RiscvCmpType.beq);
                    addInstr(br, insList, predefine);
                    br.setPredSucc(block);
                }
            }
        }
    }

    public void parseCallInst(CallInst callInst, boolean predefine) {
        if (preProcess(callInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(callInst, insList);
        RiscvLabel targetFunction = value2Label.get(callInst.getFunction());
        RiscvJal call = new RiscvJal(targetFunction);
        int argc = callInst.getParams().size();
        assert argc == callInst.getFunction().getArgs().size();
        assert targetFunction instanceof RiscvFunction;
        int stackCur = 0;//表示调用此函数时jal时的栈顶参数栈位置
        int otherCur = 0, floatCur = 0;//表示当前参数保存的位置
        for (var arg : callInst.getOperands()) {
            if (arg.getType().isFloatTy()) {
                if (floatCur < 8) {
                    if (arg instanceof ConstFloat) {
                        RiscvConstFloat label;
                        if (floats.containsKey(((ConstFloat) arg).getValue())) {
                            label = floats.get(((ConstFloat) arg).getValue());
                        } else {
                            label = new RiscvConstFloat("float" + floats.size(),
                                    ((ConstFloat) arg).getValue());
                            floats.put(((ConstFloat) arg).getValue(), label);
                        }
                        RiscvReg reg1 = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLui lui = new RiscvLui(label.hi(), reg1);
                        addInstr(lui, insList, predefine);
                        RiscvReg reg2 = RiscvFPUReg.getRiscvFArgReg(floatCur);
                        RiscvFlw flw = new RiscvFlw(label.lo(), reg1, reg2);
                        addInstr(flw, insList, predefine);
                        call.addUsedReg(reg2);
                    } else if (arg instanceof ConstInteger) {
                        assert false;
                    } else {
                        RiscvReg argReg = RiscvFPUReg.getRiscvFArgReg(floatCur);
                        RiscvReg reg = getRegOnlyFromValue(arg, insList, predefine);
                        assert reg instanceof RiscvVirReg
                                && ((RiscvVirReg) reg).regType == RiscvVirReg.RegType.floatType;
                        RiscvFmv fmv = new RiscvFmv(reg, argReg);
                        addInstr(fmv, insList, predefine);
                        call.addUsedReg(argReg);
                    }
                } else {
                    stackCur++;
                    int offset = stackCur * 8;
                    if (arg instanceof ConstFloat) {
                        RiscvConstFloat label;
                        if (floats.containsKey(((ConstFloat) arg).getValue())) {
                            label = floats.get(((ConstFloat) arg).getValue());
                        } else {
                            label = new RiscvConstFloat("float" + floats.size(),
                                    ((ConstFloat) arg).getValue());
                            floats.put(((ConstFloat) arg).getValue(), label);
                        }
                        RiscvReg reg1 = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLui lui = new RiscvLui(label.hi(), reg1);
                        addInstr(lui, insList, predefine);
                        RiscvReg reg2 = RiscvFPUReg.getRiscvFPUReg(10 + floatCur);
                        RiscvFlw flw = new RiscvFlw(label.lo(), reg1, reg2);
                        addInstr(flw, insList, predefine);
                        RiscvReg reg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLi li = new RiscvLi(new RiscvStackFixer(curRvFunction, offset), reg);
                        addInstr(li, insList, predefine);
                        RiscvBinary sub = new RiscvBinary(
                                new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvCPUReg(2), reg)), reg,
                                RiscvBinary.RiscvBinaryType.sub);
                        addInstr(sub, insList, predefine);
                        RiscvFsd fsd = new RiscvFsd(reg2, new RiscvImm(0), reg);
                        addInstr(fsd, insList, predefine);
                    } else if (arg instanceof ConstInteger) {
                        assert false;
                    } else {
                        RiscvReg reg = getRegOnlyFromValue(arg, insList, predefine);
                        assert reg instanceof RiscvVirReg
                                && ((RiscvVirReg) reg).regType == RiscvVirReg.RegType.floatType;
                        RiscvReg offReg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLi li = new RiscvLi(new RiscvStackFixer(curRvFunction, offset), offReg);
                        addInstr(li, insList, predefine);
                        RiscvBinary sub = new RiscvBinary(
                                new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvCPUReg(2), offReg)), offReg,
                                RiscvBinary.RiscvBinaryType.sub);
                        addInstr(sub, insList, predefine);
                        RiscvFsd fsd = new RiscvFsd(reg, new RiscvImm(0), offReg);
                        addInstr(fsd, insList, predefine);
                    }
                }
                floatCur++;
            } else {
                /*整数类型*/
                if (otherCur < 8) {
                    RiscvReg reg = RiscvCPUReg.getRiscvArgReg(otherCur);
                    call.addUsedReg(reg);
                    if (arg instanceof ConstInteger) {
                        RiscvLi li = new RiscvLi(new RiscvImm(((ConstInteger) arg).getValue()), reg);
                        addInstr(li, insList, predefine);
                    } else if (arg instanceof ConstFloat) {
                        assert false;
                    } else if (arg instanceof GlobalVar) {
                        RiscvLabel label = value2Label.get(arg);
                        RiscvLui lui = new RiscvLui(label.hi(), reg);
                        addInstr(lui, insList, predefine);
                        RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(reg, label.lo())),
                                reg, RiscvBinary.RiscvBinaryType.addi);
                        addInstr(binary, insList, predefine);
                    } else if (arg instanceof AllocInst) {
                        RiscvReg virReg = getRegOnlyFromValue(arg, insList, predefine);
                        RiscvMv mv = new RiscvMv(virReg, reg);
                        addInstr(mv, insList, predefine);
                    } else if (arg instanceof PtrInst || arg instanceof PtrSubInst) {
                        if (!(ptr2Offset.containsKey(arg) || value2Reg.containsKey(arg))) {
                            if (arg instanceof PtrInst) {
                                parsePtrInst((PtrInst) arg, true);
                            } else if (arg instanceof PtrSubInst) {
                                parsePtrSubInst((PtrSubInst) arg, true);
                            }
                        }
                        if (ptr2Offset.containsKey(arg)) {
                            int offset = ptr2Offset.get(arg);
                            if (offset < 2048 && offset >= -2048) {
                                RiscvBinary binary = new RiscvBinary(
                                        new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(),
                                                new RiscvImm(offset))), reg, RiscvBinary.RiscvBinaryType.addi);
                                addInstr(binary, insList, predefine);
                            } else {
                                RiscvLi li = new RiscvLi(new RiscvImm(offset), reg);
                                RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(), reg)),
                                        reg, RiscvBinary.RiscvBinaryType.add);
                                addInstr(li, insList, predefine);
                                addInstr(add, insList, predefine);
                            }
                        } else {
                            RiscvMv mv = new RiscvMv(value2Reg.get(arg), reg);
                            addInstr(mv, insList, predefine);
                        }
                    } else {
                        RiscvMv mv = new RiscvMv(getRegOnlyFromValue(arg, insList, predefine), reg);
                        addInstr(mv, insList, predefine);
                    }
                } else {
                    stackCur++;
                    int offset = stackCur * 8;
                    RiscvReg virReg = getRegOnlyFromValue(arg, insList, predefine);
                    assert virReg instanceof RiscvVirReg
                            && ((RiscvVirReg) virReg).regType == RiscvVirReg.RegType.intType;
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvLi li = new RiscvLi(new RiscvStackFixer(curRvFunction, offset), assistReg);
                    addInstr(li, insList, predefine);
                    RiscvBinary sub = new RiscvBinary(
                            new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(), assistReg)), assistReg,
                            RiscvBinary.RiscvBinaryType.sub);
                    addInstr(sub, insList, predefine);
                    RiscvSd sd = new RiscvSd(virReg, new RiscvImm(0), assistReg);
                    addInstr(sd, insList, predefine);
                }
                otherCur++;
            }
        }

        RiscvReg regUp = RiscvCPUReg.getRiscvCPUReg(9);
        RiscvLi ins1 = new RiscvLi(new RiscvStackFixer(curRvFunction, 0), regUp);
        addInstr(ins1, insList, predefine);
        RiscvBinary ins2 = new RiscvBinary(new ArrayList<>(
                Arrays.asList(RiscvCPUReg.getRiscvCPUReg(2), regUp)),
                RiscvCPUReg.getRiscvCPUReg(2), RiscvBinary.RiscvBinaryType.sub);
        addInstr(ins2, insList, predefine);
        addInstr(call, insList, predefine);
        RiscvBinary ins3 = new RiscvBinary(new ArrayList<>(
                Arrays.asList(RiscvCPUReg.getRiscvCPUReg(2), regUp)),
                RiscvCPUReg.getRiscvCPUReg(2), RiscvBinary.RiscvBinaryType.add);
        addInstr(ins3, insList, predefine);

        if (callInst.getFunction().getType().isFloatTy()) {
            RiscvVirReg resReg = getResReg(callInst, RiscvVirReg.RegType.floatType);
            RiscvFmv fmv = new RiscvFmv(RiscvFPUReg.getRiscvFPUReg(10), resReg);
            value2Reg.put(callInst, resReg);
            addInstr(fmv, insList, predefine);
        } else if (callInst.getFunction().getType().isIntegerTy()) {
            RiscvVirReg resReg = getResReg(callInst, RiscvVirReg.RegType.intType);
            RiscvMv mv = new RiscvMv(RiscvCPUReg.getRiscvCPUReg(10), resReg);
            value2Reg.put(callInst, resReg);
            addInstr(mv, insList, predefine);
        }
    }

    public void parseConversionInst(ConversionInst conversionInst, boolean predefine) {
        if (preProcess(conversionInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(conversionInst, insList);
        RiscvReg srcReg = getRegOnlyFromValue(conversionInst.getValue(), insList, predefine);
        if (conversionInst.getOp() == OP.Ftoi) {
            assert srcReg instanceof RiscvVirReg && ((RiscvVirReg) srcReg).regType == RiscvVirReg.RegType.floatType;
            RiscvReg resReg = getResReg(conversionInst, RiscvVirReg.RegType.intType);
            RiscvCvt cvt = new RiscvCvt(srcReg, false, resReg);
            addInstr(cvt, insList, predefine);
            value2Reg.put(conversionInst, resReg);
        } else {
            assert conversionInst.getOp() == OP.Itof;
            assert srcReg instanceof RiscvVirReg && ((RiscvVirReg) srcReg).regType == RiscvVirReg.RegType.intType;
            RiscvReg resReg = getResReg(conversionInst, RiscvVirReg.RegType.floatType);
            RiscvCvt cvt = new RiscvCvt(srcReg, true, resReg);
            addInstr(cvt, insList, predefine);
            value2Reg.put(conversionInst, resReg);
        }
    }

    public void parseLoadInst(LoadInst loadInst, boolean predefine) {
        if (preProcess(loadInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(loadInst, insList);
        RiscvReg resReg = null;
        if (loadInst.getPointer() instanceof PtrInst || loadInst.getPointer() instanceof PtrSubInst) {
            if (!(ptr2Offset.containsKey(loadInst.getPointer()) || value2Reg.containsKey(loadInst.getPointer()))) {
                if (loadInst.getPointer() instanceof PtrInst) {
                    parsePtrInst((PtrInst) loadInst.getPointer(), true);
                } else if (loadInst.getPointer() instanceof PtrSubInst) {
                    parsePtrSubInst((PtrSubInst) loadInst.getPointer(), true);
                }
            }
            if (ptr2Offset.containsKey(loadInst.getPointer())) {
                int offset = ptr2Offset.get(loadInst.getPointer());
                if (offset < 2048 && offset >= -2048) {
                    if (((PointerType) loadInst.getPointer().getType()).getEleType().isIntegerTy()) {
                        resReg = getResReg(loadInst, RiscvVirReg.RegType.intType);
                        RiscvLw lw = new RiscvLw(new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg(), resReg);
                        addInstr(lw, insList, predefine);
                    } else {
                        resReg = getResReg(loadInst, RiscvVirReg.RegType.floatType);
                        RiscvFlw flw = new RiscvFlw(new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg(), resReg);
                        addInstr(flw, insList, predefine);
                    }
                } else {
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvLi li = new RiscvLi(new RiscvImm(offset), assistReg);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                            RiscvCPUReg.getRiscvSpReg())), assistReg, RiscvBinary.RiscvBinaryType.add);
                    addInstr(li, insList, predefine);
                    addInstr(add, insList, predefine);
                    if (((PointerType) loadInst.getPointer().getType()).getEleType().isIntegerTy()) {
                        resReg = getResReg(loadInst, RiscvVirReg.RegType.intType);
                        RiscvLw lw = new RiscvLw(new RiscvImm(0), assistReg, resReg);
                        addInstr(lw, insList, predefine);
                    } else {
                        resReg = getResReg(loadInst, RiscvVirReg.RegType.floatType);
                        RiscvFlw flw = new RiscvFlw(new RiscvImm(0), assistReg, resReg);
                        addInstr(flw, insList, predefine);
                    }
                }
            } else {
                if (((PointerType) loadInst.getPointer().getType()).getEleType().isIntegerTy()) {
                    resReg = getResReg(loadInst, RiscvVirReg.RegType.intType);
                    RiscvLw lw = new RiscvLw(new RiscvImm(0), value2Reg.get(loadInst.getPointer()), resReg);
                    addInstr(lw, insList, predefine);
                } else {
                    resReg = getResReg(loadInst, RiscvVirReg.RegType.floatType);
                    RiscvFlw flw = new RiscvFlw(new RiscvImm(0), value2Reg.get(loadInst.getPointer()), resReg);
                    addInstr(flw, insList, predefine);
                }
            }
        } else if (loadInst.getPointer() instanceof GlobalVar) {
            assert value2Label.containsKey(loadInst.getPointer());
            RiscvGlobalVariable var = (RiscvGlobalVariable) value2Label.get(loadInst.getPointer());
            RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
            RiscvLui lui = new RiscvLui(var.hi(), assistReg);
            addInstr(lui, insList, predefine);
            if (((PointerType) loadInst.getPointer().getType()).getEleType().isIntegerTy()) {
                resReg = getResReg(loadInst, RiscvVirReg.RegType.intType);
                RiscvLw lw = new RiscvLw(var.lo(), assistReg, resReg);
                addInstr(lw, insList, predefine);
            } else {
                resReg = getResReg(loadInst, RiscvVirReg.RegType.floatType);
                RiscvFlw flw = new RiscvFlw(var.lo(), assistReg, resReg);
                addInstr(flw, insList, predefine);
            }
        } else if (loadInst.getPointer() instanceof AllocInst) {
            if (!curRvFunction.containOffset(loadInst.getPointer())) {
                parseAlloc((AllocInst) loadInst.getPointer(), true);
            }
            int offset = curRvFunction.getOffset(loadInst.getPointer()) * -1;
            if (offset < 2048 && offset >= -2048) {
                if (((PointerType) loadInst.getPointer().getType()).getEleType().isIntegerTy()) {
                    resReg = getResReg(loadInst, RiscvVirReg.RegType.intType);
                    RiscvLw lw = new RiscvLw(new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg(), resReg);
                    addInstr(lw, insList, predefine);
                } else {
                    resReg = getResReg(loadInst, RiscvVirReg.RegType.floatType);
                    RiscvFlw flw = new RiscvFlw(new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg(), resReg);
                    addInstr(flw, insList, predefine);
                }
            } else {
                RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                RiscvLi li = new RiscvLi(new RiscvImm(offset), assistReg);
                RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(), assistReg)),
                        assistReg, RiscvBinary.RiscvBinaryType.add);
                addInstr(li, insList, predefine);
                addInstr(add, insList, predefine);
                if (((PointerType) loadInst.getPointer().getType()).getEleType().isIntegerTy()) {
                    resReg = getResReg(loadInst, RiscvVirReg.RegType.intType);
                    RiscvLw lw = new RiscvLw(new RiscvImm(0), assistReg, resReg);
                    addInstr(lw, insList, predefine);
                } else {
                    resReg = getResReg(loadInst, RiscvVirReg.RegType.floatType);
                    RiscvFlw flw = new RiscvFlw(new RiscvImm(0), assistReg, resReg);
                    addInstr(flw, insList, predefine);
                }
            }
        } else if (loadInst.getPointer() instanceof Argument || loadInst.getPointer() instanceof Phi) {
            RiscvReg assistReg = getRegOnlyFromValue(loadInst.getPointer(), insList, predefine);
            assert loadInst.getPointer().getType() instanceof PointerType;
            if ((((PointerType) loadInst.getPointer().getType()).getEleType().isFloatTy())) {
                resReg = getResReg(loadInst, RiscvVirReg.RegType.floatType);
                RiscvFlw flw = new RiscvFlw(new RiscvImm(0), assistReg, resReg);
                addInstr(flw, insList, predefine);
            } else {
                resReg = getResReg(loadInst, RiscvVirReg.RegType.intType);
                RiscvLw lw = new RiscvLw(new RiscvImm(0), assistReg, resReg);
                addInstr(lw, insList, predefine);
            }
        } else {
            assert false;
        }
        value2Reg.put(loadInst, resReg);
    }

    public void parseMove(Move mv, boolean predefine) {
        if (preProcess(mv, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(mv, insList);
        if (!value2Reg.containsKey(mv.getDestination()) && !(mv.getDestination() instanceof Argument)) {
            value2Reg.put(mv.getDestination(), getNewVirReg(mv.getDestination().getType().isFloatTy() ?
                    RiscvVirReg.RegType.floatType : RiscvVirReg.RegType.intType));
        }
//        System.out.println(mv.getSource().getClass());
//        System.out.println(mv.getInstString());
//        if (!(mv.getSource() instanceof ConstInteger)) {
//            assert mv.getSource() instanceof Phi;
//            System.out.println(mv.getSource() + "\n");
//        }
        RiscvReg src = getRegOnlyFromValue(mv.getSource(), insList, predefine);
        RiscvReg dst = getRegOnlyFromValue(mv.getDestination(), insList, predefine);
        if (mv.getDestination().getType().isFloatTy()) {
            RiscvFmv fmv = new RiscvFmv(src, dst);
            addInstr(fmv, insList, predefine);
        } else {
            RiscvMv rv_mv = new RiscvMv(src, dst);
            addInstr(rv_mv, insList, predefine);
        }
    }

    public void parsePtrInst(PtrInst ptrInst, boolean predefine) {
        // 考虑 target 来自 Alloca
        if (preProcess(ptrInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(ptrInst, insList);
        RiscvOperand op2 = getRegOrImmFromValue(ptrInst.getOffset(), false, insList, predefine);
        if (ptrInst.getTarget() instanceof AllocInst) {
            if (!curRvFunction.containOffset(ptrInst.getTarget())) {
                parseAlloc((AllocInst) ptrInst.getTarget(), true);
            }
            int offset = curRvFunction.getOffset(ptrInst.getTarget()) * -1;
            if (op2 instanceof RiscvImm) {
                offset = offset + ((RiscvImm) op2).getValue() * 4;
                ptr2Offset.put(ptrInst, offset);
            } else {
                assert op2 instanceof RiscvVirReg;
                RiscvReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
                if (offset < 2048 && offset >= -2048) {
                    RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                            new RiscvImm(2))), resReg, RiscvBinary.RiscvBinaryType.slli);
                    addInstr(sll, insList, predefine);
                    RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                            new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                    addInstr(addi, insList, predefine);
                } else {
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                            new RiscvImm(2))), assistReg, RiscvBinary.RiscvBinaryType.slli);
                    RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                            assistReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                    addInstr(sll, insList, predefine);
                    addInstr(li, insList, predefine);
                    addInstr(add, insList, predefine);
                }
                RiscvBinary add1 = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(),
                        resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                addInstr(add1, insList, predefine);
                value2Reg.put(ptrInst, resReg);
            }
        } else if (ptrInst.getTarget() instanceof PtrInst || ptrInst.getTarget() instanceof PtrSubInst) {
            if (!(ptr2Offset.containsKey(ptrInst.getTarget()) || value2Reg.containsKey(ptrInst.getTarget()))) {
                if (ptrInst.getTarget() instanceof PtrInst) {
                    parsePtrInst((PtrInst) ptrInst.getTarget(), true);
                } else if (ptrInst.getTarget() instanceof PtrSubInst) {
                    parsePtrSubInst((PtrSubInst) ptrInst.getTarget(), true);
                }
            }
            if (ptr2Offset.containsKey(ptrInst.getTarget())) {
                if (op2 instanceof RiscvImm) {
                    int offset = ptr2Offset.get(ptrInst.getTarget()) + ((RiscvImm) op2).getValue() * 4;
                    ptr2Offset.put(ptrInst, offset);
                } else {
                    assert op2 instanceof RiscvReg;
                    int offset = ptr2Offset.get(ptrInst.getTarget());
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
                    RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                            new RiscvImm(2))), assistReg, RiscvBinary.RiscvBinaryType.slli);
                    addInstr(sll, insList, predefine);
                    //assistReg + offset
                    if (offset < 2048 && offset >= -2048) {
                        RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                                new RiscvImm(offset))), assistReg, RiscvBinary.RiscvBinaryType.addi);
                        addInstr(addi, insList, predefine);
                    } else {
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                                resReg)), assistReg, RiscvBinary.RiscvBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(),
                            assistReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                    addInstr(add, insList, predefine);
                    value2Reg.put(ptrInst, resReg);
                }
            } else {
                assert value2Reg.containsKey(ptrInst.getTarget());
                RiscvReg op1 = value2Reg.get(ptrInst.getTarget());
                RiscvReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
                if (op2 instanceof RiscvImm) {
                    int offset = ((RiscvImm) op2).getValue() * 4;
                    if (offset < 2048 && offset >= -2048) {
                        RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(op1,
                                new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                        addInstr(addi, insList, predefine);
                    } else {
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(op1,
                                resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    assert op2 instanceof RiscvReg;
                    RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                            new RiscvImm(2))), resReg, RiscvBinary.RiscvBinaryType.slli);
                    addInstr(sll, insList, predefine);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                            op1)), resReg, RiscvBinary.RiscvBinaryType.add);
                    addInstr(add, insList, predefine);
                }
                value2Reg.put(ptrInst, resReg);
            }
        } else if (ptrInst.getTarget() instanceof GlobalVar) {
            assert value2Label.containsKey(ptrInst.getTarget());
            RiscvLabel label = value2Label.get(ptrInst.getTarget());
            RiscvReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
            RiscvLui lui = new RiscvLui(label.hi(), resReg);
            RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg, label.lo())),
                    resReg, RiscvBinary.RiscvBinaryType.addi);
            addInstr(lui, insList, predefine);
            addInstr(addi, insList, predefine);
            if (!(op2 instanceof RiscvImm && ((RiscvImm) op2).getValue() == 0)) {
                if (op2 instanceof RiscvImm) {
                    int offset = ((RiscvImm) op2).getValue() * 4;
                    if (offset < 2048 && offset >= -2048) {
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                                new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                        addInstr(add, insList, predefine);
                    } else {
                        RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), assistReg);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                                resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                            new RiscvImm(2))), assistReg, RiscvBinary.RiscvBinaryType.slli);
                    addInstr(sll, insList, predefine);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                            assistReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                    addInstr(add, insList, predefine);
                }
            }
            value2Reg.put(ptrInst, resReg);
        } else if (ptrInst.getTarget() instanceof Argument) {
            RiscvVirReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
            if (value2Reg.containsKey(ptrInst.getTarget())) {
                RiscvMv mv = new RiscvMv(value2Reg.get(ptrInst.getTarget()), resReg);
                addInstr(mv, insList, predefine);
                if (!(op2 instanceof RiscvImm && ((RiscvImm) op2).getValue() == 0)) {
                    if (op2 instanceof RiscvImm) {
                        int offset = ((RiscvImm) op2).getValue() * 4;
                        if (offset < 2048 && offset >= -2048) {
                            RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                                    new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                            addInstr(add, insList, predefine);
                        } else {
                            RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                            RiscvLi li = new RiscvLi(new RiscvImm(offset), assistReg);
                            RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                                    resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                            addInstr(li, insList, predefine);
                            addInstr(add, insList, predefine);
                        }
                    } else {
                        RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                                new RiscvImm(2))), assistReg, RiscvBinary.RiscvBinaryType.slli);
                        addInstr(sll, insList, predefine);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                                assistReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                        addInstr(add, insList, predefine);
                    }
                }
            } else {
                int offset = curRvFunction.getOffset(ptrInst.getTarget()) * -1;
                assert !ptrInst.getTarget().getType().isFloatTy();
                RiscvReg argReg = getNewVirReg(RiscvVirReg.RegType.intType);
                if (offset >= -2048 && offset < 2048) {
                    RiscvLd lw = new RiscvLd(new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg(), argReg);
                    addInstr(lw, insList, predefine);
                } else {
                    RiscvLi li = new RiscvLi(new RiscvImm(offset), argReg);
                    RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(argReg,
                            RiscvCPUReg.getRiscvSpReg())), argReg, RiscvBinary.RiscvBinaryType.add);
                    RiscvLd lw = new RiscvLd(new RiscvImm(0), argReg, argReg);
                    addInstr(li, insList, predefine);
                    addInstr(binary, insList, predefine);
                    addInstr(lw, insList, predefine);
                }
                value2Reg.put(ptrInst.getTarget(), argReg);
                if (op2 instanceof RiscvImm) {
                    offset = ((RiscvImm) op2).getValue() * 4;
                    if (offset < 2048 && offset >= -2048) {
                        RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(argReg,
                                new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                        addInstr(addi, insList, predefine);
                    } else {
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(argReg,
                                resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                            new RiscvImm(2))), assistReg, RiscvBinary.RiscvBinaryType.slli);
                    addInstr(sll, insList, predefine);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(argReg,
                            assistReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                    addInstr(add, insList, predefine);
                }
            }
            value2Reg.put(ptrInst, resReg);
        } else if (ptrInst.getTarget() instanceof Phi) {
            RiscvReg phiReg = getRegOnlyFromValue(ptrInst.getTarget(), insList, predefine);
            RiscvVirReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
            if (op2 instanceof RiscvImm) {
                int offset = ((RiscvImm) op2).getValue() * 4;
                if (offset < 2048 && offset >= -2048) {
                    RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(phiReg,
                            new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                    addInstr(addi, insList, predefine);
                } else {
                    RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(phiReg,
                            resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                    addInstr(li, insList, predefine);
                    addInstr(add, insList, predefine);
                }
            } else {
                RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                        new RiscvImm(2))), resReg, RiscvBinary.RiscvBinaryType.slli);
                addInstr(sll, insList, predefine);
                RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                        phiReg)), resReg, RiscvBinary.RiscvBinaryType.add);
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
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(ptrInst, insList);
        RiscvOperand op2 = getRegOrImmFromValue(ptrInst.getOffset(), false, insList, predefine);
        if (ptrInst.getTarget() instanceof AllocInst) {
            if (!curRvFunction.containOffset(ptrInst.getTarget())) {
                parseAlloc((AllocInst) ptrInst.getTarget(), true);
            }
            int offset = curRvFunction.getOffset(ptrInst.getTarget()) * -1;
            if (op2 instanceof RiscvImm) {
                offset = offset - ((RiscvImm) op2).getValue() * 4;
                ptr2Offset.put(ptrInst, offset);
            } else {
                assert op2 instanceof RiscvVirReg;
                RiscvReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
                RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                        new RiscvImm(2))), resReg, RiscvBinary.RiscvBinaryType.slli);
                addInstr(sll, insList, predefine);
                RiscvBinary sub = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(),
                        resReg)), resReg, RiscvBinary.RiscvBinaryType.sub);
                addInstr(sub, insList, predefine);
                if (offset < 2048 && offset >= -2048) {
                    RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                            new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                    addInstr(addi, insList, predefine);
                } else {
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvLi li = new RiscvLi(new RiscvImm(offset), assistReg);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                            assistReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                    addInstr(li, insList, predefine);
                    addInstr(add, insList, predefine);
                }
                value2Reg.put(ptrInst, resReg);
            }
        } else if (ptrInst.getTarget() instanceof PtrInst || ptrInst.getTarget() instanceof PtrSubInst) {
            if (!(ptr2Offset.containsKey(ptrInst.getTarget()) || value2Reg.containsKey(ptrInst.getTarget()))) {
                if (ptrInst.getTarget() instanceof PtrInst) {
                    parsePtrInst((PtrInst) ptrInst.getTarget(), true);
                } else if (ptrInst.getTarget() instanceof PtrSubInst) {
                    parsePtrSubInst((PtrSubInst) ptrInst.getTarget(), true);
                }
            }
            if (ptr2Offset.containsKey(ptrInst.getTarget())) {
                if (op2 instanceof RiscvImm) {
                    int offset = ptr2Offset.get(ptrInst.getTarget()) - ((RiscvImm) op2).getValue() * 4;
                    ptr2Offset.put(ptrInst, offset);
                } else {
                    assert op2 instanceof RiscvReg;
                    int offset = ptr2Offset.get(ptrInst.getTarget());
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
                    RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                            new RiscvImm(2))), resReg, RiscvBinary.RiscvBinaryType.slli);
                    addInstr(sll, insList, predefine);
                    RiscvBinary sub = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(),
                            resReg)), resReg, RiscvBinary.RiscvBinaryType.sub);
                    addInstr(sub, insList, predefine);
                    if (offset < 2048 && offset >= -2048) {
                        RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                                new RiscvImm(offset))), assistReg, RiscvBinary.RiscvBinaryType.addi);
                        addInstr(addi, insList, predefine);
                    } else {
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), assistReg);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                                resReg)), assistReg, RiscvBinary.RiscvBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                    value2Reg.put(ptrInst, resReg);
                }
            } else {
                assert value2Reg.containsKey(ptrInst.getTarget());
                RiscvReg op1 = value2Reg.get(ptrInst.getTarget());
                RiscvReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
                if (op2 instanceof RiscvImm) {
                    int offset = -4 * ((RiscvImm) op2).getValue();
                    if (offset < 2048 && offset >= -2048) {
                        RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(op1,
                                new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                        addInstr(addi, insList, predefine);
                    } else {
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(op1,
                                resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    assert op2 instanceof RiscvReg;
                    RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                            new RiscvImm(2))), resReg, RiscvBinary.RiscvBinaryType.slli);
                    addInstr(sll, insList, predefine);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                            op1)), resReg, RiscvBinary.RiscvBinaryType.sub);
                    addInstr(add, insList, predefine);
                }
                value2Reg.put(ptrInst, resReg);
            }
        } else if (ptrInst.getTarget() instanceof GlobalVar) {
            assert value2Label.containsKey(ptrInst.getTarget());
            RiscvLabel label = value2Label.get(ptrInst.getTarget());
            RiscvReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
            RiscvLui lui = new RiscvLui(label.hi(), resReg);
            RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg, label.lo())),
                    resReg, RiscvBinary.RiscvBinaryType.addi);
            addInstr(lui, insList, predefine);
            addInstr(addi, insList, predefine);
            if (!(op2 instanceof RiscvImm && ((RiscvImm) op2).getValue() == 0)) {
                if (op2 instanceof RiscvImm) {
                    int offset = ((RiscvImm) op2).getValue() * -4;
                    if (offset < 2048 && offset >= -2048) {
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                                new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                        addInstr(add, insList, predefine);
                    } else {
                        RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), assistReg);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                                resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                            new RiscvImm(2))), assistReg, RiscvBinary.RiscvBinaryType.slli);
                    addInstr(sll, insList, predefine);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                            assistReg)), resReg, RiscvBinary.RiscvBinaryType.sub);
                    addInstr(add, insList, predefine);
                }
            }
            value2Reg.put(ptrInst, resReg);
        } else if (ptrInst.getTarget() instanceof Argument) {
            RiscvVirReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
            if (value2Reg.containsKey(ptrInst.getTarget())) {
                RiscvMv mv = new RiscvMv(value2Reg.get(ptrInst.getTarget()), resReg);
                addInstr(mv, insList, predefine);
                if (!(op2 instanceof RiscvImm && ((RiscvImm) op2).getValue() == 0)) {
                    if (op2 instanceof RiscvImm) {
                        int offset = ((RiscvImm) op2).getValue() * -4;
                        if (offset < 2048 && offset >= -2048) {
                            RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                                    new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                            addInstr(add, insList, predefine);
                        } else {
                            RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                            RiscvLi li = new RiscvLi(new RiscvImm(offset), assistReg);
                            RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                                    resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                            addInstr(li, insList, predefine);
                            addInstr(add, insList, predefine);
                        }
                    } else {
                        RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                                new RiscvImm(2))), assistReg, RiscvBinary.RiscvBinaryType.slli);
                        addInstr(sll, insList, predefine);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                                assistReg)), resReg, RiscvBinary.RiscvBinaryType.sub);
                        addInstr(add, insList, predefine);
                    }
                }
            } else {
                int offset = curRvFunction.getOffset(ptrInst.getTarget()) * -1;
                assert !ptrInst.getTarget().getType().isFloatTy();
                RiscvReg argReg = getNewVirReg(RiscvVirReg.RegType.intType);
                if (offset >= -2048 && offset < 2048) {
                    RiscvLd lw = new RiscvLd(new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg(), argReg);
                    addInstr(lw, insList, predefine);
                } else {
                    RiscvLi li = new RiscvLi(new RiscvImm(offset), argReg);
                    RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(argReg,
                            RiscvCPUReg.getRiscvSpReg())), argReg, RiscvBinary.RiscvBinaryType.add);
                    RiscvLd lw = new RiscvLd(new RiscvImm(0), argReg, argReg);
                    addInstr(li, insList, predefine);
                    addInstr(binary, insList, predefine);
                    addInstr(lw, insList, predefine);
                }
                value2Reg.put(ptrInst.getTarget(), argReg);
                if (op2 instanceof RiscvImm) {
                    offset = ((RiscvImm) op2).getValue() * -4;
                    if (offset < 2048 && offset >= -2048) {
                        RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(argReg,
                                new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                        addInstr(addi, insList, predefine);
                    } else {
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(argReg,
                                resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                        addInstr(li, insList, predefine);
                        addInstr(add, insList, predefine);
                    }
                } else {
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                            new RiscvImm(2))), assistReg, RiscvBinary.RiscvBinaryType.slli);
                    addInstr(sll, insList, predefine);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(argReg,
                            assistReg)), resReg, RiscvBinary.RiscvBinaryType.sub);
                    addInstr(add, insList, predefine);
                }
            }
            value2Reg.put(ptrInst, resReg);
        } else if (ptrInst.getTarget() instanceof Phi) {
            RiscvReg phiReg = getRegOnlyFromValue(ptrInst.getTarget(), insList, predefine);
            RiscvVirReg resReg = getResReg(ptrInst, RiscvVirReg.RegType.intType);
            if (op2 instanceof RiscvImm) {
                int offset = ((RiscvImm) op2).getValue() * -4;
                if (offset < 2048 && offset >= -2048) {
                    RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(phiReg,
                            new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                    addInstr(addi, insList, predefine);
                } else {
                    RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(phiReg,
                            resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                    addInstr(li, insList, predefine);
                    addInstr(add, insList, predefine);
                }
            } else {
                RiscvBinary sll = new RiscvBinary(new ArrayList<>(Arrays.asList(op2,
                        new RiscvImm(2))), resReg, RiscvBinary.RiscvBinaryType.slli);
                addInstr(sll, insList, predefine);
                RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                        phiReg)), resReg, RiscvBinary.RiscvBinaryType.sub);
                addInstr(add, insList, predefine);
            }
            value2Reg.put(ptrInst, resReg);
        } else {
            assert false;
        }
    }

    public void parseRetInst(RetInst retInst, boolean predefine) {
        if (!predefine) {
            curRvFunction.getRetBlocks().add(curRvBlock);
        }
        if (preProcess(retInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(retInst, insList);
        if (!retInst.isVoid()) {
            if (retInst.getValue() instanceof ConstInteger) {
                RiscvLi li = new RiscvLi(new RiscvImm(((ConstInteger) retInst.getValue()).getValue()),
                        RiscvCPUReg.getRiscvRetReg());
                addInstr(li, insList, predefine);
            } else if (retInst.getValue() instanceof ConstFloat) {
                RiscvConstFloat label;
                if (floats.containsKey(((ConstFloat) retInst.getValue()).getValue())) {
                    label = floats.get(((ConstFloat) retInst.getValue()).getValue());
                } else {
                    label = new RiscvConstFloat("float" + floats.size(),
                            ((ConstFloat) retInst.getValue()).getValue());
                    floats.put(((ConstFloat) retInst.getValue()).getValue(), label);
                }
                RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                RiscvLui lui = new RiscvLui(label.hi(), assistReg);
                addInstr(lui, insList, predefine);
                RiscvFlw flw = new RiscvFlw(label.lo(), assistReg, RiscvFPUReg.getRiscvRetReg());
                addInstr(flw, insList, predefine);
            } else {
                RiscvReg reg = getRegOnlyFromValue(retInst.getValue(), insList, predefine);
                assert reg instanceof RiscvVirReg;
                if (((RiscvVirReg) reg).regType == RiscvVirReg.RegType.intType) {
                    addInstr(new RiscvMv(reg, RiscvCPUReg.getRiscvRetReg()), insList, predefine);
                } else {
                    addInstr(new RiscvFmv(reg, RiscvFPUReg.getRiscvRetReg()), insList, predefine);
                }
            }
        }
        // jr ra
        addInstr(new RiscvMv(curRvFunction.getRetReg(), RiscvCPUReg.getRiscvRaReg()), insList, predefine);
        addInstr(new RiscvJr(RiscvCPUReg.getRiscvRaReg()), insList, predefine);
    }

    public void parseStoreInst(StoreInst storeInst, boolean predefine) {
        if (preProcess(storeInst, predefine)) {
            return;
        }
        ArrayList<RiscvInstruction> insList = predefine ? new ArrayList<>() : null;
        predefines.put(storeInst, insList);
        RiscvReg stoReg = getRegOnlyFromValue(storeInst.getValue(), insList, predefine);
        if (storeInst.getPointer() instanceof PtrInst || storeInst.getPointer() instanceof PtrSubInst) {
            if (!(value2Reg.containsKey(storeInst.getPointer())
                    || ptr2Offset.containsKey(storeInst.getPointer()))) {
                if (storeInst.getPointer() instanceof PtrInst) {
                    parsePtrInst((PtrInst) storeInst.getPointer(), predefine);
                } else {
                    parsePtrSubInst((PtrSubInst) storeInst.getPointer(), predefine);
                }
            }
            if (ptr2Offset.containsKey(storeInst.getPointer())) {
                int offset = ptr2Offset.get(storeInst.getPointer());
                if (offset < 2048 && offset >= -2048) {
                    if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                        RiscvSw riscvSw = new RiscvSw(stoReg, new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg());
                        addInstr(riscvSw, insList, predefine);
                    } else {
                        RiscvFsw fsw = new RiscvFsw(stoReg, new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg());
                        addInstr(fsw, insList, predefine);
                    }
                } else {
                    RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    addInstr(new RiscvLi(new RiscvImm(offset), assistReg), insList, predefine);
                    addInstr(new RiscvBinary(new ArrayList<>(Arrays.asList(assistReg,
                                    RiscvCPUReg.getRiscvSpReg())), assistReg, RiscvBinary.RiscvBinaryType.add),
                            insList, predefine);
                    if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                        RiscvSw riscvSw = new RiscvSw(stoReg, new RiscvImm(0), assistReg);
                        addInstr(riscvSw, insList, predefine);
                    } else {
                        RiscvFsw fsw = new RiscvFsw(stoReg, new RiscvImm(0), assistReg);
                        addInstr(fsw, insList, predefine);
                    }
                }
            } else {
                if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                    RiscvSw riscvSw = new RiscvSw(stoReg, new RiscvImm(0), value2Reg.get(storeInst.getPointer()));
                    addInstr(riscvSw, insList, predefine);
                } else {
                    RiscvFsw fsw = new RiscvFsw(stoReg, new RiscvImm(0), value2Reg.get(storeInst.getPointer()));
                    addInstr(fsw, insList, predefine);
                }
            }
        } else if (storeInst.getPointer() instanceof GlobalVar) {
            assert value2Label.containsKey(storeInst.getPointer());
            RiscvGlobalVariable var = (RiscvGlobalVariable) value2Label.get(storeInst.getPointer());
            RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
            RiscvLui lui = new RiscvLui(var.hi(), assistReg);
            addInstr(lui, insList, predefine);
            if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                RiscvSw sw = new RiscvSw(stoReg, var.lo(), assistReg);
                addInstr(sw, insList, predefine);
            } else {
                RiscvFsw fsw = new RiscvFsw(stoReg, var.lo(), assistReg);
                addInstr(fsw, insList, predefine);
            }
        } else if (storeInst.getPointer() instanceof AllocInst) {
            if (!curRvFunction.containOffset(storeInst.getPointer())) {
                parseAlloc((AllocInst) storeInst.getPointer(), true);
            }
            int offset = curRvFunction.getOffset(storeInst.getPointer()) * -1;
            if (offset < 2048 && offset >= -2048) {
                if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                    RiscvSw sw = new RiscvSw(stoReg, new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg());
                    addInstr(sw, insList, predefine);
                } else {
                    RiscvFsw fsw = new RiscvFsw(stoReg, new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg());
                    addInstr(fsw, insList, predefine);
                }
            } else {
                RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
                RiscvLi li = new RiscvLi(new RiscvImm(offset), assistReg);
                RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(), assistReg)),
                        assistReg, RiscvBinary.RiscvBinaryType.add);
                addInstr(li, insList, predefine);
                addInstr(add, insList, predefine);
                if (((PointerType) storeInst.getPointer().getType()).getEleType().isIntegerTy()) {
                    RiscvSw sw = new RiscvSw(stoReg, new RiscvImm(0), assistReg);
                    addInstr(sw, insList, predefine);
                } else {
                    RiscvFsw fsw = new RiscvFsw(stoReg, new RiscvImm(0), assistReg);
                    addInstr(fsw, insList, predefine);
                }
            }
        } else if (storeInst.getPointer() instanceof Argument || storeInst.getPointer() instanceof Phi) {
            RiscvReg assistReg = getRegOnlyFromValue(storeInst.getPointer(), insList, predefine);
            assert storeInst.getPointer().getType() instanceof PointerType;
            if ((((PointerType) storeInst.getPointer().getType()).getEleType().isFloatTy())) {
                RiscvFsw fsw = new RiscvFsw(stoReg, new RiscvImm(0), assistReg);
                addInstr(fsw, insList, predefine);
            } else {
                RiscvSw sw = new RiscvSw(stoReg, new RiscvImm(0), assistReg);
                addInstr(sw, insList, predefine);
            }
        } else {
            assert false;
        }
    }

    public void parseGlobalVar(GlobalVar var) {
        boolean flag = true;
        if (var.isArray()) {
            boolean isIntType = ((PointerType) var.getType()).getEleType() instanceof IntegerType;
            int zeros = 0;
            ArrayList<RiscvGlobalValue> values = new ArrayList<>();
            if (!var.isZeroInit()) {
                for (Value value : var.getValues()) {
                    if (isIntType) {
                        assert value instanceof ConstInteger;
                        if (((ConstInteger) value).getValue() == 0) {
                            zeros += 4;
                        } else {
                            flag = false;
                            if (zeros > 0) {
                                values.add(new RiscvGlobalZero(zeros));
                                zeros = 0;
                            }
                            values.add(new RiscvGlobalInt(((ConstInteger) value).getValue()));
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
                                values.add(new RiscvGlobalZero(zeros));
                                zeros = 0;
                            }
                            values.add(new RiscvGlobalFloat(val));
                        }
                    }
                }
                if (zeros > 0) {
                    values.add(new RiscvGlobalZero(zeros));
                }
            }
            RiscvGlobalVariable globalVariable = new RiscvGlobalVariable(removeLeadingAt(var.getName()),
                    !var.isZeroInit(), 4 * var.getSize(), values);
            if (!flag) {
                riscvModule.addDataVar(globalVariable);
            } else {
                riscvModule.addBssVar(globalVariable);
            }
            value2Label.put(var, globalVariable);
        } else {
            boolean isIntType = !var.getType().isFloatTy();
            if (isIntType) {
                assert var.getValue() instanceof ConstInteger;
                RiscvGlobalValue riscvGlobalInt = new RiscvGlobalInt(((ConstInteger) var.getValue()).getValue());
                ArrayList<RiscvGlobalValue> values = new ArrayList<>();
                values.add(riscvGlobalInt);
                RiscvGlobalVariable globalVariable = new RiscvGlobalVariable(removeLeadingAt(var.getName()),
                        true, 4, values);
                if (((ConstInteger) var.getValue()).getValue() == 0) {
                    riscvModule.addBssVar(globalVariable);
                } else {
                    riscvModule.addDataVar(globalVariable);
                }
                value2Label.put(var, globalVariable);
            } else {
                assert var.getValue() instanceof ConstFloat || var.getValue() instanceof ConstInteger;
                float val = (var.getValue() instanceof ConstInteger) ?
                        ((ConstInteger) var.getValue()).getValue() :
                        ((ConstFloat) var.getValue()).getValue();
                RiscvGlobalValue riscvGlobalFloat = new RiscvGlobalFloat(val);
                ArrayList<RiscvGlobalValue> values = new ArrayList<>();
                values.add(riscvGlobalFloat);
                RiscvGlobalVariable globalVariable = new RiscvGlobalVariable(removeLeadingAt(var.getName()),
                        true, 4, values);
                riscvModule.addDataVar(globalVariable);
                value2Label.put(var, globalVariable);
            }
        }
    }

    private void addInstr(RiscvInstruction ins, ArrayList<RiscvInstruction> insList, boolean predefine) {
        if (predefine) {
            insList.add(ins);
        } else {
            curRvBlock.addRiscvInstruction(new IList.INode<>(ins));
        }
    }

    private RiscvReg getRegOnlyFromValue(Value value, ArrayList<RiscvInstruction> insList, boolean predefine) {
        RiscvReg resReg;
        if (value instanceof ConstInteger) {
            resReg = getNewVirReg(RiscvVirReg.RegType.intType);
            RiscvLi riscvLi = new RiscvLi(new RiscvImm(((ConstInteger) value).getValue()), resReg);
            addInstr(riscvLi, insList, predefine);
        } else if (value instanceof ConstFloat) {
            resReg = getNewVirReg(RiscvVirReg.RegType.floatType);
            RiscvConstFloat label;
            if (floats.containsKey(((ConstFloat) value).getValue())) {
                label = floats.get(((ConstFloat) value).getValue());
            } else {
                label = new RiscvConstFloat("float" + floats.size(),
                        ((ConstFloat) value).getValue());
                floats.put(((ConstFloat) value).getValue(), label);
            }
            RiscvReg reg = getNewVirReg(RiscvVirReg.RegType.intType);
            RiscvLui lui = new RiscvLui(label.hi(), reg);
            addInstr(lui, insList, predefine);
            RiscvFlw flw = new RiscvFlw(label.lo(), reg, resReg);
            addInstr(flw, insList, predefine);
        } else if (value instanceof GlobalVar) {
            RiscvLabel label = value2Label.get(value);
            resReg = getNewVirReg(RiscvVirReg.RegType.intType);
            RiscvLui lui = new RiscvLui(label.hi(), resReg);
            addInstr(lui, insList, predefine);
            RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg, label.lo())),
                    resReg, RiscvBinary.RiscvBinaryType.addi);
            addInstr(addi, insList, predefine);
            return resReg;
        } else if (value instanceof Argument) {
            if (value2Reg.containsKey(value)) {
                return value2Reg.get(value);
            } else {
                int offset = -1 * curRvFunction.getOffset(value);
                if (offset >= -2048 && offset < 2048) {
                    if (value.getType().isFloatTy()) {
                        resReg = getNewVirReg(RiscvVirReg.RegType.floatType);
                        RiscvFlw flw = new RiscvFlw(new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg(), resReg);
                        addInstr(flw, insList, predefine);
                        value2Reg.put(value, resReg);
                    } else {
                        assert value.getType().isIntegerTy() || value.getType().isPointerType();
                        resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLw lw = new RiscvLw(new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg(), resReg);
                        addInstr(lw, insList, predefine);
                        value2Reg.put(value, resReg);
                    }
                } else {
                    if (value.getType().isFloatTy()) {
                        resReg = getNewVirReg(RiscvVirReg.RegType.floatType);
                        RiscvReg virReg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), virReg);
                        RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(virReg,
                                RiscvCPUReg.getRiscvSpReg())), virReg, RiscvBinary.RiscvBinaryType.add);
                        RiscvFlw flw = new RiscvFlw(new RiscvImm(0), virReg, resReg);
                        addInstr(li, insList, predefine);
                        addInstr(binary, insList, predefine);
                        addInstr(flw, insList, predefine);
                        value2Reg.put(value, resReg);
                    } else {
                        assert value.getType().isIntegerTy();
                        resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                        RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                                RiscvCPUReg.getRiscvSpReg())), resReg, RiscvBinary.RiscvBinaryType.add);
                        RiscvLw lw = new RiscvLw(new RiscvImm(0), resReg, resReg);
                        addInstr(li, insList, predefine);
                        addInstr(binary, insList, predefine);
                        addInstr(lw, insList, predefine);
                        value2Reg.put(value, resReg);
                    }
                }
            }
        } /*else if (value instanceof PtrInst) {
            assert ptr2Offset.containsKey(value) || value2Reg.containsKey(value);
            if(ptr2Offset.containsKey(value)) {
                resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                int offset = ptr2Offset.get(value);
                RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                addInstr(li);
            } else {
                return value2Reg.get(value);
            }
        } */ else if (value instanceof AllocInst) {
            if (!curRvFunction.containOffset(value)) {
                parseAlloc((AllocInst) value, true);
            }
            if (value2Reg.containsKey(value)) {
                return value2Reg.get(value);
            } else {
                int offset = curRvFunction.getOffset(value) * -1;
                if (offset >= -2048 && offset < 2048) {
                    resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(),
                            new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                    addInstr(add, insList, predefine);
                } else {
                    resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                    addInstr(li, insList, predefine);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(),
                            resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                    addInstr(add, insList, predefine);
                }
                value2Reg.put(value, resReg);
            }
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
                    resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    int offset = ptr2Offset.get(value);
                    if (offset < 2048 && offset >= -2048) {
                        RiscvBinary binary = new RiscvBinary(
                                new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(),
                                        new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                        addInstr(binary, insList, predefine);
                    } else {
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(), resReg)),
                                resReg, RiscvBinary.RiscvBinaryType.add);
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
                    RiscvVirReg reg = getNewVirReg(value.getType().isFloatTy() ? RiscvVirReg.RegType.floatType
                            : RiscvVirReg.RegType.intType);
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

    private RiscvOperand getRegOrImmFromValue(Value value, Boolean _2048Flag,
                                              ArrayList<RiscvInstruction> insList, boolean predefine) {
        RiscvReg resReg;
        if (value instanceof ConstInteger) {
            if (_2048Flag && (((ConstInteger) value).getValue() < -2048
                    || ((ConstInteger) value).getValue() >= 2048)) {
                resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                RiscvLi riscvLi = new RiscvLi(new RiscvImm(((ConstInteger) value).getValue()), resReg);
                addInstr(riscvLi, insList, predefine);
            } else {
                return new RiscvImm(((ConstInteger) value).getValue());
            }
        } else if (value instanceof ConstFloat) {
            resReg = getNewVirReg(RiscvVirReg.RegType.floatType);
            RiscvConstFloat label;
            if (floats.containsKey(((ConstFloat) value).getValue())) {
                label = floats.get(((ConstFloat) value).getValue());
            } else {
                label = new RiscvConstFloat("float" + floats.size(),
                        ((ConstFloat) value).getValue());
                floats.put(((ConstFloat) value).getValue(), label);
            }
            RiscvReg assistReg = getNewVirReg(RiscvVirReg.RegType.intType);
            RiscvLui lui = new RiscvLui(label.hi(), assistReg);
            addInstr(lui, insList, predefine);
            RiscvFlw flw = new RiscvFlw(label.lo(), assistReg, resReg);
            addInstr(flw, insList, predefine);
        } else if (value instanceof GlobalVar) {
            RiscvLabel label = value2Label.get(value);
            resReg = getNewVirReg(RiscvVirReg.RegType.intType);
            RiscvLui lui = new RiscvLui(label.hi(), resReg);
            addInstr(lui, insList, predefine);
            RiscvBinary addi = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg, label.lo())),
                    resReg, RiscvBinary.RiscvBinaryType.addi);
            addInstr(addi, insList, predefine);
            return resReg;
        } else if (value instanceof Argument) {
            if (value2Reg.containsKey(value)) {
                return value2Reg.get(value);
            } else {
                int offset = -1 * curRvFunction.getOffset(value);
                if (offset >= -2048 && offset < 2048) {
                    if (value.getType().isFloatTy()) {
                        resReg = getNewVirReg(RiscvVirReg.RegType.floatType);
                        RiscvFld fld = new RiscvFld(new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg(), resReg);
                        addInstr(fld, insList, predefine);
                        value2Reg.put(value, resReg);
                    } else {
                        assert value.getType().isIntegerTy();
                        resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLd ld = new RiscvLd(new RiscvImm(offset), RiscvCPUReg.getRiscvSpReg(), resReg);
                        addInstr(ld, insList, predefine);
                        value2Reg.put(value, resReg);
                    }
                } else {
                    if (value.getType().isFloatTy()) {
                        resReg = getNewVirReg(RiscvVirReg.RegType.floatType);
                        RiscvReg virReg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), virReg);
                        RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(virReg,
                                RiscvCPUReg.getRiscvSpReg())), virReg, RiscvBinary.RiscvBinaryType.add);
                        RiscvFld fld = new RiscvFld(new RiscvImm(0), virReg, resReg);
                        addInstr(li, insList, predefine);
                        addInstr(binary, insList, predefine);
                        addInstr(fld, insList, predefine);
                        value2Reg.put(value, resReg);
                    } else {
                        assert value.getType().isIntegerTy();
                        resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                        RiscvBinary binary = new RiscvBinary(new ArrayList<>(Arrays.asList(resReg,
                                RiscvCPUReg.getRiscvSpReg())), resReg, RiscvBinary.RiscvBinaryType.add);
                        RiscvLd ld = new RiscvLd(new RiscvImm(0), resReg, resReg);
                        addInstr(li, insList, predefine);
                        addInstr(binary, insList, predefine);
                        addInstr(ld, insList, predefine);
                        value2Reg.put(value, resReg);
                    }
                }
            }
        } /*else if (value instanceof PtrInst) {
            assert false;
            assert ptr2Offset.containsKey(value) || value2Reg.containsKey(value);
            if(ptr2Offset.containsKey(value)) {
                int offset = ptr2Offset.get(value);
                if(_2048Flag && (offset < -2048 || offset >= 2048)) {
                    resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvLi riscvLi = new RiscvLi(new RiscvImm(offset), resReg);
                    addInstr(riscvLi);
                } else {
                    return new RiscvImm(offset);
                }
                RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                addInstr(li);
            } else {
                return value2Reg.get(value);
            }
        }*/ else if (value instanceof AllocInst) {
            if (!curRvFunction.containOffset(value)) {
                parseAlloc((AllocInst) value, true);
            }
            if (value2Reg.containsKey(value)) {
                return value2Reg.get(value);
            } else {
                int offset = curRvFunction.getOffset(value) * -1;
                if (offset >= -2048 && offset < 2048) {
                    resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(),
                            new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                    addInstr(add, insList, predefine);
                } else {
                    resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                    addInstr(li, insList, predefine);
                    RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(),
                            resReg)), resReg, RiscvBinary.RiscvBinaryType.add);
                    addInstr(add, insList, predefine);
                }
                value2Reg.put(value, resReg);
            }
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
                    resReg = getNewVirReg(RiscvVirReg.RegType.intType);
                    int offset = ptr2Offset.get(value);
                    if (offset < 2048 && offset >= -2048) {
                        RiscvBinary binary = new RiscvBinary(
                                new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(),
                                        new RiscvImm(offset))), resReg, RiscvBinary.RiscvBinaryType.addi);
                        addInstr(binary, insList, predefine);
                    } else {
                        RiscvLi li = new RiscvLi(new RiscvImm(offset), resReg);
                        RiscvBinary add = new RiscvBinary(new ArrayList<>(Arrays.asList(RiscvCPUReg.getRiscvSpReg(), resReg)),
                                resReg, RiscvBinary.RiscvBinaryType.add);
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
                    RiscvVirReg reg = getNewVirReg(value.getType().isFloatTy() ? RiscvVirReg.RegType.floatType
                            : RiscvVirReg.RegType.intType);
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

    private RiscvVirReg getNewVirReg(RiscvVirReg.RegType regType) {
        return new RiscvVirReg(curRvFunction.updateVirRegIndex(), regType, curRvFunction);
    }

    private boolean preProcess(Instruction instruction, boolean predefine) {
        if (predefines.containsKey(instruction)) {
            if (!predefine) {
                ArrayList<RiscvInstruction> insList = predefines.get(instruction);
                for (RiscvInstruction ins : insList) {
                    addInstr(ins, null, false);
                }
            }
            return true;
        }
        return false;
    }

    public void dump() {
        try {
            var out = new BufferedWriter(new FileWriter("rv_backend.s"));
            out.write(riscvModule.toString());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //    RiscvGlobalVariable(String name, boolean isInit, int size,
//                        ArrayList<RiscvGlobalValue> values)
    public RiscvModule getRvModule() {
        return riscvModule;
    }
}
