package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Collections;

public class ArmRev extends ArmInstruction{
    public ArmRev(ArmReg from, ArmReg toReg) {
        super(toReg, new ArrayList<>(Collections.singletonList(from)));
    }

    @Override
    public String toString() {
        return "neg\t" + getDefReg() + ",\t" + getOperands().get(0);
    }

}
