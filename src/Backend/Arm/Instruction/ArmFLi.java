package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmFloatImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Collections;

public class ArmFLi extends ArmInstruction {
    public ArmFLi(ArmOperand from, ArmReg toReg) {
        super(toReg, new ArrayList<>(Collections.singletonList(from)));
        assert from instanceof ArmFloatImm;
    }

    // AArch64: fmov d1, #4.0 (changed from vmov to fmov)
    @Override
    public String toString() {
        return "fmov\t" + getDefReg() + ",\t" + getOperands().getFirst();
    }
}
