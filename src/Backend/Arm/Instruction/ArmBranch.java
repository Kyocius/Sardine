package Backend.Arm.Instruction;

import Backend.Arm.Structure.ArmBlock;
import Backend.Arm.tools.ArmTools;

import java.util.ArrayList;
import java.util.Collections;

public class ArmBranch extends ArmInstruction {
    private ArmTools.CondType type;

    public ArmBranch(ArmBlock block, ArmTools.CondType type) {
        super(null, new ArrayList<>(Collections.singletonList(block)));
        this.type = type;
    }

    public void setPredSucc(ArmBlock block) {
        assert getOperands().get(0) instanceof ArmBlock;
        ArmBlock block1 = (ArmBlock) getOperands().get(0);
        block1.addPreds(block);
        block.addSuccs(block1);
    }

    public ArmTools.CondType getType() {
        return type;
    }

    public void setType(ArmTools.CondType type1) {
        type = type1;
    }

    @Override
    public String toString() {
        return "b" + ArmTools.getCondString(this.type) + "\t" + getOperands().get(0);
    }
}
