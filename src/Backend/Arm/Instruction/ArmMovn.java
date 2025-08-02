package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Collections;

/**
 * AArch64 MOVN instruction - Move inverted immediate
 * Efficient for loading large negative numbers
 */
public class ArmMovn extends ArmInstruction {
    private final int shiftAmount;

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
            // AArch64: movn Xd, #imm16
            return "movn\t" + getDefReg() + ",\t" + getOperands().getFirst();
        } else {
            // AArch64: movn Xd, #imm16, lsl #shift (shift can be 0, 16, 32, 48)
            return "movn\t" + getDefReg() + ",\t" + getOperands().getFirst() + ",\tlsl #" + shiftAmount;
        }
    }
}
