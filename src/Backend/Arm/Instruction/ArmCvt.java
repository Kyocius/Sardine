package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Collections;

public class ArmCvt extends ArmInstruction {
    public final boolean ToFloat;

    public ArmCvt(ArmReg srcReg, boolean toFloat, ArmReg defReg) {
        super(defReg, new ArrayList<>(Collections.singletonList(srcReg)));
        ToFloat = toFloat;
    }

    @Override
    public String toString() {
        if(ToFloat) {
            // ARMv8-A: convert signed integer to double-precision floating-point
            return "scvtf" + "\t" + getDefReg() + "," + getOperands().get(0);
        }
        else {
            // ARMv8-A: convert double-precision floating-point to signed integer
            return "fcvtzs" + "\t"+ getDefReg() + "," + getOperands().get(0);
        }
    }
}
