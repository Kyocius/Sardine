package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmLabel;
import Backend.Arm.Structure.ArmBlock;

import java.util.ArrayList;
import java.util.Collections;

public class ArmJump extends ArmInstruction {
    public ArmJump(ArmLabel label, ArmBlock parent) {
        super(null, new ArrayList<>(Collections.singletonList(label)));
        assert label instanceof ArmBlock;
        ArmBlock block1 = (ArmBlock) label;
        block1.addPreds(parent);
        parent.addSuccs(block1);
    }

    @Override
    public String toString() {
        // AArch64 unconditional branch instruction
        return "b\t" + getOperands().getFirst();
    }
}
