package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;
import Backend.Arm.tools.ArmTools;

import java.util.ArrayList;

/**
 * AArch64 CSET instruction - Conditionally Set register
 * Sets register to 1 if condition is true, 0 otherwise
 */
public class ArmCset extends ArmInstruction {
    private final ArmTools.CondType condType;

    public ArmCset(ArmReg toReg, ArmTools.CondType condType) {
        super(toReg, new ArrayList<>());
        this.condType = condType;
    }

    public ArmTools.CondType getCondType() {
        return condType;
    }

    @Override
    public String toString() {
        return "cset\t" + getDefReg() + ",\t" + ArmTools.getCondString(condType);
    }
}
