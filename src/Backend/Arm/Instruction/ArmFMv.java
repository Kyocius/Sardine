package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Collections;

public class ArmFMv extends ArmMv {
    public ArmFMv(ArmReg from, ArmReg toReg) {
        super(from, toReg);
    }

    @Override
    public String toString() {
        return "vmov\t" + getDefReg() + ",\t" + getOperands().get(0);
    }
}
