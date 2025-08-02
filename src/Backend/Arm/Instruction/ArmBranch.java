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
        assert getOperands().getFirst() instanceof ArmBlock;
        ArmBlock block1 = (ArmBlock) getOperands().getFirst();
        block1.addPreds(block);
        block.addSuccs(block1);
    }

    public ArmTools.CondType getType() {
        return type;
    }

    public void setType(ArmTools.CondType type1) {
        type = type1;
    }

    /**
     * Get the target block for this branch instruction
     * @return the target ArmBlock
     */
    public ArmBlock getTargetBlock() {
        return (ArmBlock) getOperands().getFirst();
    }

    /**
     * Check if this is an unconditional branch
     * @return true if the branch condition is 'al' (always) or 'nope' (no condition)
     */
    public boolean isUnconditional() {
        return type == ArmTools.CondType.al || type == ArmTools.CondType.nope;
    }

    /**
     * Check if this branch instruction is valid for AArch64
     * @return true if the condition type is supported in AArch64
     */
    public boolean isValidForAArch64() {
        // AArch64 supports all standard ARM condition codes
        // 'nv' (never) is reserved but still valid syntactically
        return type != null;
    }

    @Override
    public String toString() {
        // AArch64 branch instruction format: b[cond] label
        // For unconditional branches, we can omit the condition or use 'al'
        String condString = ArmTools.getCondString(this.type);
        if (type == ArmTools.CondType.nope) {
            // For unconditional branches, just use 'b' without condition
            return "b\t" + getOperands().getFirst();
        } else {
            return "b" + condString + "\t" + getOperands().getFirst();
        }
    }
}
