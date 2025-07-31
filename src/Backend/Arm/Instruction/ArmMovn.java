package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Collections;

/**
 * AArch64 MOVN instruction - Move inverted immediate
 * Efficient for loading large negative numbers
 */
public class ArmMovn extends ArmInstruction {
    private int shiftAmount;

    public ArmMovn(ArmImm immediate, ArmReg destReg, int shiftAmount) {
        super(destReg, new ArrayList<>(Collections.singletonList(immediate)));
        this.shiftAmount = shiftAmount;
    }

    public ArmMovn(ArmImm immediate, ArmReg destReg) {
        this(immediate, destReg, 0);
    }

    @Override
    public String toString() {
        if (shiftAmount == 0) {
            return "movn\t" + getDefReg() + ",\t" + getOperands().get(0);
        } else {
            return "movn\t" + getDefReg() + ",\t" + getOperands().get(0) + ",\tlsl #" + shiftAmount;
        }
    }
}
