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
            // AArch64: convert signed integer to double-precision floating-point
            return "scvtf\t" + getDefReg() + ",\t" + getOperands().getFirst();
        }
        else {
            // AArch64: convert double-precision floating-point to signed integer (round toward zero)
            return "fcvtzs\t" + getDefReg() + ",\t" + getOperands().getFirst();
        }
    }
}
