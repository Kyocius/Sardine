package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmFma extends ArmInstruction {
    public ArmFma(ArmReg mulOp1, ArmReg mulOp2, ArmReg addOp, ArmReg defReg) {
        super(defReg, new ArrayList<>(Arrays.asList(mulOp1, mulOp2, addOp)));
    }

    @Override
    public String toString() {
        // AArch64 uses madd instruction for multiply-accumulate
        // madd Rd, Rn, Rm, Ra: Rd = Ra + (Rn * Rm)
        return "madd\t" + getDefReg() + ",\t" +
               getOperands().get(0) + ",\t" + getOperands().get(1) + ",\t" +
               getOperands().get(2);
    }
}
