package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmFSw extends ArmInstruction {
    public ArmFSw(ArmReg storeReg, ArmReg baseReg, ArmOperand armOffset) {
        //TODO:后续可以引入增强 STR R0,[R1],＃8 e.g.
        super(null, new ArrayList<>(Arrays.asList(storeReg, baseReg, armOffset)));
    }

    @Override
    public String toString() {
        if (getOperands().get(2) instanceof ArmImm && ((ArmImm)(getOperands().get(2))).getValue() == 0) {
            return "vstr\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + "]";
        } else {
            return "vstr\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
        }
    }
}
