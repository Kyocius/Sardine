package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * AArch64 signed multiply long instruction
 * smull: 32-bit × 32-bit signed multiplication producing 64-bit result
 */
public class ArmSmull extends ArmInstruction {
    public ArmSmull(ArmReg destReg, ArmReg reg1, ArmReg reg2) {
        super(destReg, new ArrayList<>(Arrays.asList(reg1, reg2)));
    }

    @Override
    public String toString() {
        // AArch64 signed multiply long: smull Xd, Wn, Wm
        // Multiplies two 32-bit signed values to produce a 64-bit result
        return "smull\t" + getDefReg() + ",\t" + getOperands().getFirst() + ",\t" + getOperands().get(1);
    }
}
