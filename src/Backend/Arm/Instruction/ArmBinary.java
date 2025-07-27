package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;

public class ArmBinary extends ArmInstruction {
    private final ArmBinary.ArmBinaryType instType;
    private final int shiftBit;
    private final ArmBinary.ArmShiftType shiftType;

    public ArmBinary(ArrayList<ArmOperand> uses, ArmReg defReg, ArmBinaryType type) {
        super(defReg, uses);
        this.instType = type;
        this.shiftBit = 0;
        this.shiftType = ArmShiftType.LSL;
    }

    public ArmBinary(ArrayList<ArmOperand> uses, ArmReg defReg, int shiftBit,
                     ArmShiftType shiftType, ArmBinaryType type) {
        super(defReg, uses);
        this.instType = type;
        this.shiftBit = shiftBit;
        this.shiftType = shiftType;
    }

    public ArmBinaryType getInstType() {
        return instType;
    }

    public enum ArmShiftType {
        LSL, // #<n> Logical shift left <n> bits. 1 <= <n> <= 31.
        LSR, // #<n> Logical shift right <n> bits. 1 <= <n> <= 32.
        ASR, // #<n> Arithmetic shift right <n> bits. 1 <= <n> <= 32.
        ROR, // #<n> Rotate right <n> bits. 1 <= <n> <= 31.
    }

    public String shiftTypeToString() {
        switch (shiftType) {
            case LSL -> {
                return "LSL";
            }
            case LSR -> {
                return "LSR";
            }
            case ASR -> {
                return "ASR";
            }
            case ROR -> {
                return "ROR";
            }
        }
        return null;
    }

    public enum ArmBinaryType{
        add,
        sub,
        rsb,
        mul,
        sdiv,
        srem,
        orr,
        and,
        asr, // 算数右移
        lsl, // 逻辑左移(就是左移)
        lsr, // 逻辑右移
        ror, // 循环右移
        rrx,  // 扩展循环右移
        eor,
        vadd,
        vsub,
        vmul,
        vdiv,
    }

    public String binaryTypeToString(){
        switch(instType){
            case add -> {
                return "add";
            }
            case sub -> {
                return "sub";
            }
            case rsb -> {
                return "rsb";
            }
            case mul -> {
                return "mul";
            }
            case sdiv -> {
                return "sdiv";
            }
            case srem -> {
                return "srem";
            }
            case vadd -> {
                return "vadd.f32";
            }
            case vsub -> {
                return "vsub.f32";
            }
            case vmul -> {
                return "vmul.f32";
            }
            case vdiv -> {
                return "vdiv.f32";
            }
            case and -> {
                return "and";
            }
            case orr -> {
                return "orr";
            }
            case asr -> {
                return "asr";
            }
            case lsl -> {
                return "lsl";
            }
            case lsr -> {
                return "lsr";
            }
            case ror -> {
                return "ror";
            }
            case rrx -> {
                return "rrx";
            }
            case eor -> {
                return "eor";
            }
        }
        return null;
    }

//    public boolean is64Ins() {
//        return instType == RiscvBinary.RiscvBinaryType.add || instType == RiscvBinary.RiscvBinaryType.sub ||
//                instType == RiscvBinary.RiscvBinaryType.mul || instType == RiscvBinary.RiscvBinaryType.addi ||
//                instType == RiscvBinary.RiscvBinaryType.slt || instType == RiscvBinary.RiscvBinaryType.slti ||
//                instType == RiscvBinary.RiscvBinaryType.sltiu || instType == RiscvBinary.RiscvBinaryType.sltu;
//    }

    @Override
    public String toString() {
        if (shiftBit == 0) {
            return binaryTypeToString() + "\t" + getDefReg() + "," +
                    getOperands().get(0) +", " + getOperands().get(1);
        } else {
            return binaryTypeToString() + "\t" + getDefReg() + "," +
                    getOperands().get(0) +", " + getOperands().get(1) + ", " + shiftTypeToString() + " #"
                    + shiftBit;
        }
    }
}
