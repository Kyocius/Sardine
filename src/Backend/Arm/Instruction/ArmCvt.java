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
            return "vcvt.f32.s32" + "\t" + getDefReg() + "," + getOperands().get(0);
        }
        else {
            return "vcvt.s32.f32" + "\t"+ getDefReg() + "," + getOperands().get(0);
        }
    }
}
