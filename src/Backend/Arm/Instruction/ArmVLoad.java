package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmVLoad extends ArmInstruction {
    public ArmVLoad(ArmReg baseReg, ArmOperand offset, ArmReg defReg) {
        super(defReg, new ArrayList<>(Arrays.asList(baseReg, offset)));
    }

    @Override
    public String toString() {
        if (getOperands().get(1) instanceof ArmImm && ((ArmImm)(getOperands().get(1))).getValue() == 0) {
            return "vldr\t" + getDefReg() + ",\t[" + getOperands().get(0) +  "]";
        } else {
            return "vldr\t" + getDefReg() + ",\t[" + getOperands().get(0) + ", " + getOperands().get(1) + "]";
        }
    }
}
