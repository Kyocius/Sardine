package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * AArch64 high-multiply instructions
 * smulh: signed multiply high (64×64=128, returns high 64 bits)
 * umulh: unsigned multiply high (64×64=128, returns high 64 bits)
 */
public class ArmMulh extends ArmInstruction {
    public enum MulhType {
        smulh,  // signed multiply high
        umulh   // unsigned multiply high
    }
    
    private final MulhType type;

    public ArmMulh(ArmReg destReg, ArmReg leftReg, ArmReg rightReg, MulhType type) {
        super(destReg, new ArrayList<>(Arrays.asList(leftReg, rightReg)));
        this.type = type;
    }

    @Override
    public String toString() {
        return type.toString() + "\t" + getDefReg() + ",\t" + getOperands().getFirst() + ",\t" + getOperands().get(1);
    }
}
