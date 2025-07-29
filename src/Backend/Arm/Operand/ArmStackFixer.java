package Backend.Arm.Operand;

import Backend.Arm.Structure.ArmFunction;

public class ArmStackFixer extends ArmImm {
    private final int offset;//参数偏移

    private final ArmFunction function;

    public ArmStackFixer(ArmFunction function, int extraOffset) {
        super();
        this.function = function;
        this.offset = extraOffset;
    }

    @Override
    public int getValue() {
        if (function.getStackPosition() == 0) return offset;
        else {
            // ARM64: Use positive offset from stack pointer (after stack allocation)
            int stackFrameSize = function.getStackPosition() - 1 -
                    (function.getStackPosition() - 1) % 16 + 16;
            // Align to 16 bytes
            stackFrameSize = (stackFrameSize + 15) & ~15;
            return stackFrameSize - (function.getStackPosition() - 1 -
                    (function.getStackPosition() - 1) % 16 + 16 + offset);
        }
    }

    @Override
    public String toString() {
        return "#" + Integer.toString(getValue());
    }
}
