package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * AArch64 high-multiply instructions
 * smulh: signed multiply high
 * umulh: unsigned multiply high
 */
public class ArmMulh extends ArmInstruction {
    public enum MulhType {
        smulh,  // signed multiply high
        umulh   // unsigned multiply high
    }
    
    private MulhType type;

    public ArmMulh(ArmReg destReg, ArmReg leftReg, ArmReg rightReg, MulhType type) {
        super(destReg, new ArrayList<>(Arrays.asList(leftReg, rightReg)));
        this.type = type;
    }

    @Override
    public String toString() {
        return type.toString() + "\t" + getDefReg() + ",\t" + getOperands().get(0) + ",\t" + getOperands().get(1);
    }
}
