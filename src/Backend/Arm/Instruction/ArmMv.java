package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Collections;

public class ArmMv extends ArmInstruction {
    public ArmMv(ArmReg from, ArmReg toReg) {
        super(toReg, new ArrayList<>(Collections.singletonList(from)));
    }

    @Override
    public String toString() {
        return "mov\t" + getDefReg() + ",\t" + getOperands().get(0);
    }
}
