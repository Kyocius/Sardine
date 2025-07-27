package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmFloatImm;
import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Collections;

public class ArmFLi extends ArmInstruction {
    public ArmFLi(ArmOperand from, ArmReg toReg) {
        super(toReg, new ArrayList<>(Collections.singletonList(from)));
        assert from instanceof ArmFloatImm;
    }

    //vmov	s1,	#4.0e0
    @Override
    public String toString() {
        return "vmov\t" + getDefReg() + ",\t" + getOperands().get(0);
    }
}
