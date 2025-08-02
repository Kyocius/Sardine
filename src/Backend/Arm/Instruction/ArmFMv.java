package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;

public class ArmFMv extends ArmMv {
    public ArmFMv(ArmReg from, ArmReg toReg) {
        super(from, toReg);
    }

    @Override
    public String toString() {
        // AArch64 floating-point move between SIMD&FP registers
        return "fmov\t" + getDefReg() + ",\t" + getOperands().getFirst();
    }
}
