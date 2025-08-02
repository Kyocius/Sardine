package Backend.Arm.Operand;

import Backend.Arm.Structure.ArmFunction;

/**
 * AArch64 Stack Offset Fixer
 * Handles dynamic stack offset calculation with 16-byte alignment
 * Computes correct offsets for parameters and local variables in stack frame
 */
public class ArmStackFixer extends ArmImm {
    private final int offset; // Parameter offset
    private final ArmFunction function;

    public ArmStackFixer(ArmFunction function, int extraOffset) {
        super();
        this.function = function;
        this.offset = extraOffset;
    }

    @Override
    public long getValue() {
        if (function.getStackPosition() == 0) {
            return offset;
        } else {
            // AArch64: 16-byte stack alignment requirement
            int stackSize = function.getStackPosition() - 1;
            // Align stack frame to 16 bytes (AArch64 requirement)
            int alignedStackSize = (stackSize + 15) & ~15;
            // Calculate final offset from aligned stack frame
            return alignedStackSize + offset;
        }
    }

    @Override
    public String toString() {
        return "#" + getValue();
    }
}
