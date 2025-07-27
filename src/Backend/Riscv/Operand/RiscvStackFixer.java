package Backend.Riscv.Operand;

import Backend.Riscv.Component.RiscvFunction;

public class RiscvStackFixer extends RiscvImm {
    private final int offset;//参数偏移

    private final RiscvFunction function;

    public RiscvStackFixer(RiscvFunction function, int extraOffset) {
        super();
        this.function = function;
        this.offset = extraOffset;
    }

    @Override
    public String toString() {
        if (function.getStackPosition() == 0) return Integer.toString(offset);
        else return Integer.toString(function.getStackPosition() - 1 -
                (function.getStackPosition() - 1) % 16 + 16 + offset);
    }
}
