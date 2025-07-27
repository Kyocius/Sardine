package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;

import java.util.ArrayList;
import java.util.Collections;

public class ArmWait extends ArmInstruction{
    public ArmWait() {
        super(null, new ArrayList<>());
    }

    @Override
    public String toString() {
        return "bl\twait";
    }
}
