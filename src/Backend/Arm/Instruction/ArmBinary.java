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
        LSL, // #<n> Logical shift left <n> bits. 0 <= <n> <= 63 (AArch64 64-bit)
        LSR, // #<n> Logical shift right <n> bits. 1 <= <n> <= 64 (AArch64 64-bit)
        ASR, // #<n> Arithmetic shift right <n> bits. 1 <= <n> <= 64 (AArch64 64-bit)
        ROR, // #<n> Rotate right <n> bits. 1 <= <n> <= 63 (AArch64 64-bit)
        MSL, // #<n> Masked shift left (SIMD only)
    }

    public String shiftTypeToString() {
        switch (shiftType) {
            case LSL -> {
                return "lsl";
            }
            case LSR -> {
                return "lsr";
            }
            case ASR -> {
                return "asr";
            }
            case ROR -> {
                return "ror";
            }
            case MSL -> {
                return "msl";
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
        // AArch64 specific instructions
        madd,    // multiply-add: rd = rn + rm * ra
        msub,    // multiply-subtract: rd = ra - rn * rm
        smaddl,  // signed multiply-add long
        smsubl,  // signed multiply-subtract long
        umaddl,  // unsigned multiply-add long
        umsubl,  // unsigned multiply-subtract long
        udiv,    // unsigned divide
        orn,     // bitwise OR NOT
        bic,     // bitwise bit clear (AND NOT)
        bics,    // bitwise bit clear and set flags
        ands,    // bitwise AND and set flags
        adcs,    // add with carry and set flags
        sbcs,    // subtract with carry and set flags
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
                // ARMv8-A: rsb (reverse subtract) needs special handling
                // Will be handled in toString() method to swap operands
                return "sub";
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
                return "fadd";
            }
            case vsub -> {
                return "fsub";
            }
            case vmul -> {
                return "fmul";
            }
            case vdiv -> {
                return "fdiv";
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
            // AArch64 specific instructions
            case madd -> {
                return "madd";
            }
            case msub -> {
                return "msub";
            }
            case smaddl -> {
                return "smaddl";
            }
            case smsubl -> {
                return "smsubl";
            }
            case umaddl -> {
                return "umaddl";
            }
            case umsubl -> {
                return "umsubl";
            }
            case udiv -> {
                return "udiv";
            }
            case orn -> {
                return "orn";
            }
            case bic -> {
                return "bic";
            }
            case bics -> {
                return "bics";
            }
            case ands -> {
                return "ands";
            }
            case adcs -> {
                return "adcs";
            }
            case sbcs -> {
                return "sbcs";
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
        // Handle multiply-accumulate instructions (4 operands: rd, rn, rm, ra)
        if (instType == ArmBinaryType.madd || instType == ArmBinaryType.msub ||
            instType == ArmBinaryType.smaddl || instType == ArmBinaryType.smsubl ||
            instType == ArmBinaryType.umaddl || instType == ArmBinaryType.umsubl) {
            if (getOperands().size() >= 3) {
                return binaryTypeToString() + "\t" + getDefReg() + ",\t" +
                        getOperands().get(0) + ",\t" + getOperands().get(1) + ",\t" + getOperands().get(2);
            }
        }
        
        if (shiftBit == 0) {
            // Special handling for RSB (reverse subtract): swap operands
            if (instType == ArmBinaryType.rsb) {
                return binaryTypeToString() + "\t" + getDefReg() + ",\t" +
                        getOperands().get(1) + ",\t" + getOperands().get(0);
            }
            return binaryTypeToString() + "\t" + getDefReg() + ",\t" +
                    getOperands().get(0) + ",\t" + getOperands().get(1);
        } else {
            // Special handling for RSB with shift: swap operands
            if (instType == ArmBinaryType.rsb) {
                return binaryTypeToString() + "\t" + getDefReg() + ",\t" +
                        getOperands().get(1) + ",\t" + getOperands().get(0) + ",\t" + shiftTypeToString() + " #"
                        + shiftBit;
            }
            return binaryTypeToString() + "\t" + getDefReg() + ",\t" +
                    getOperands().get(0) + ",\t" + getOperands().get(1) + ",\t" + shiftTypeToString() + " #"
                    + shiftBit;
        }
    }
}
