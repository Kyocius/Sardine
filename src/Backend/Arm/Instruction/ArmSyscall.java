package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmLabel;
import Backend.Arm.Structure.ArmBlock;

import java.util.ArrayList;
import java.util.Collections;

public class ArmSyscall extends ArmInstruction {
    public ArmSyscall(ArmImm syscallNum) {
        super(null, new ArrayList<>(Collections.singletonList(syscallNum)));
    }

    @Override
    public String toString() {
        return "swi\t" + getOperands().get(0);
    }
}
