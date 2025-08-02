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
        // rsb,  // Removed: RSB is not available in AArch64
        mul,
        sdiv,
        // srem,  // Removed: SREM is not a direct instruction in AArch64, use sdiv + msub
        orr,
        and,
        // Move shift operations to be used as modifiers, not standalone instructions
        // asr, lsl, lsr, ror should be used as shift modifiers, not binary operations
        // rrx,  // RRX is limited in AArch64
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
        // Additional AArch64 instructions
        adc,     // add with carry (without setting flags)
        sbc,     // subtract with carry (without setting flags)
        // Shift instructions as separate operations (when needed)
        asrv,    // arithmetic shift right variable
        lslv,    // logical shift left variable
        lsrv,    // logical shift right variable
        rorv,    // rotate right variable
    }

    public String binaryTypeToString(){
        switch(instType){
            case add -> {
                return "add";
            }
            case sub -> {
                return "sub";
            }
            case mul -> {
                return "mul";
            }
            case sdiv -> {
                return "sdiv";
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
            case adc -> {
                return "adc";
            }
            case sbc -> {
                return "sbc";
            }
            case asrv -> {
                return "asrv";
            }
            case lslv -> {
                return "lslv";
            }
            case lsrv -> {
                return "lsrv";
            }
            case rorv -> {
                return "rorv";
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
        
        // Handle variable shift instructions (3 operands: rd, rn, rm)
        if (instType == ArmBinaryType.asrv || instType == ArmBinaryType.lslv ||
            instType == ArmBinaryType.lsrv || instType == ArmBinaryType.rorv) {
            return binaryTypeToString() + "\t" + getDefReg() + ",\t" +
                    getOperands().get(0) + ",\t" + getOperands().get(1);
        }

        if (shiftBit == 0) {
            return binaryTypeToString() + "\t" + getDefReg() + ",\t" +
                    getOperands().get(0) + ",\t" + getOperands().get(1);
        } else {
            return binaryTypeToString() + "\t" + getDefReg() + ",\t" +
                    getOperands().get(0) + ",\t" + getOperands().get(1) + ",\t" + shiftTypeToString() + " #"
                    + shiftBit;
        }
    }
}
