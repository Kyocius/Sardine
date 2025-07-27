package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmFma extends ArmInstruction {
    private boolean signed = false;

    public ArmFma(ArmReg mulOp1,
                  ArmReg mulOp2, ArmReg addOp, ArmReg defReg) {
        super(defReg, new ArrayList<>(Arrays.asList(mulOp1, mulOp2, addOp)));
    }

    public void setSigned(boolean signed) {
        this.signed = signed;
    }

    @Override
    public String toString() {
        String pre = signed ? "sm" : "";
        return "\t" + pre + "mla" + "\t" +
                getDefReg() + ",\t" + getOperands().get(0) + ",\t" + getOperands().get(1) + ",\t" +
                getOperands().get(2) + "\n";
    }
}
