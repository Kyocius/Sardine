package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * AArch64 floating-point compare instruction
 * fcmp: compare two floating-point values and set condition flags
 */
public class ArmVCompare extends ArmInstruction {
    public ArmVCompare(ArmOperand leftOperand, ArmOperand rightOperand) {
        super(null, new ArrayList<>(Arrays.asList(leftOperand, rightOperand)));
    }

    @Override
    public String toString() {
        // AArch64 floating-point compare instruction
        return "fcmp\t" + getOperands().getFirst() + ",\t" + getOperands().get(1);
    }
}
