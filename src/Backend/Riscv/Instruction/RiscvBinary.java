package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvOperand;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;

public class RiscvBinary extends RiscvInstruction {
    private final RiscvBinaryType instType;
    public RiscvBinary(ArrayList<RiscvOperand> uses, RiscvReg defReg, RiscvBinaryType type) {
        super(uses, defReg);
        this.instType = type;
    }

    public RiscvBinaryType getInstType() {
        return instType;
    }

    public enum RiscvBinaryType{
        addw,
        add,
        addiw,
        addi,
        subw,
        sub,
        slliw,
        sraiw,
        mulw,
        mul,
        divw,
        remw,
        fadd,
        fsub,
        fmul,
        fdiv,
        feq,
        flt,
        fle,
        sltiu,
        and,
        andi,
        or,
        ori,
        xor,
        xori,
        slt,
        slti,
        sltu,
        srliw,
        slli,
        mulh,
        srli,
        rem
    }

    public String binaryTypeToString(){
        switch(instType){
            case addw -> {
                return "addw";
            }
            case addiw -> {
                return "addiw";
            }
            case subw -> {
                return "subw";
            }
            case slliw -> {
                return "slliw";
            }
            case sraiw -> {
                return "sraiw";
            }
            case mulw -> {
                return "mulw";
            }
            case divw -> {
                return "divw";
            }
            case remw -> {
                return "remw";
            }
            case fadd -> {
                return "fadd.s";
            }
            case fsub -> {
                return "fsub.s";
            }
            case fmul -> {
                return "fmul.s";
            }
            case fdiv -> {
                return "fdiv.s";
            }
            case feq -> {
                return "feq.s";
            }
            case fle -> {
                return "fle.s";
            }
            case flt -> {
                return "flt.s";
            }
            case sltiu -> {
                return "sltiu";
            }
            case and -> {
                return "and";
            }
            case or -> {
                return "or";
            }
            case xor -> {
                return "xor";
            }
            case andi -> {
                return "andi";
            }
            case ori -> {
                return "ori";
            }
            case xori -> {
                return "xori";
            }
            case slt -> {
                return "slt";
            }
            case slti -> {
                return "slti";
            }
            case sltu -> {
                return "sltu";
            }
            case addi -> {
                return "addi";
            }
            case add -> {
                return "add";
            }
            case sub -> {
                return "sub";
            }
            case mul -> {
                return "mul";
            }
            case srliw -> {
                return "srliw";
            }
            case slli -> {
                return "slli";
            }
            case mulh -> {
                return "mulh";
            }
            case srli -> {
                return "srli";
            }
            case rem -> {
                return "rem";
            }
        }
        return null;
    }

    public boolean is64Ins() {
        return instType == RiscvBinaryType.add || instType == RiscvBinaryType.sub ||
                instType == RiscvBinaryType.mul || instType == RiscvBinaryType.addi ||
                instType == RiscvBinaryType.slt || instType == RiscvBinaryType.slti ||
                instType == RiscvBinaryType.sltiu || instType == RiscvBinaryType.sltu ||
                instType == RiscvBinaryType.rem;
    }

    @Override
    public String toString() {
        return binaryTypeToString() + " " + getDefReg() + "," +
                getOperands().get(0) +", " + getOperands().get(1);
    }
}
