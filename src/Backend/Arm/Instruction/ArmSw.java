package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmSw extends ArmInstruction {
    public ArmSw(ArmReg storeReg, ArmReg offReg, ArmOperand armImm) {
        //TODO:后续可以引入增强 STR R0,[R1],＃8 e.g.
        super(null, new ArrayList<>(Arrays.asList(storeReg, offReg, armImm)));
    }

    @Override
    public String toString() {
        if (getOperands().get(2) instanceof ArmImm && ((ArmImm)(getOperands().get(2))).getValue() == 0) {
            return "str\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + "]";
        } else {
            return "str\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
        }
    }
}
