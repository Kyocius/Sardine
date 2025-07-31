package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmLongMul extends ArmInstruction {
    public ArmLongMul(ArmReg defReg, ArmReg leftOp, ArmReg rightOp) {
        super(defReg, new ArrayList<>(Arrays.asList(leftOp, rightOp)));
    }

    @Override
    public String toString() {
        // AArch64: Use smulh for signed multiply high (64-bit result, return high 64 bits)
        return "smulh\t" + getDefReg() +
                ",\t" + getOperands().get(0) + ",\t" + getOperands().get(1);
    }
}
