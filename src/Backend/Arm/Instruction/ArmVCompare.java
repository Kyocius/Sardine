package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmVCompare extends ArmInstruction {
    public ArmVCompare(ArmOperand leftOperand, ArmOperand rightOperand) {
        super(null, new ArrayList<>(Arrays.asList(leftOperand, rightOperand)));
    }

    public String toString() {
        // ARMv8-A floating-point compare
        return "fcmp\t" + getOperands().get(0) + ",\t" + getOperands().get(1) + "\n";
    }
}
