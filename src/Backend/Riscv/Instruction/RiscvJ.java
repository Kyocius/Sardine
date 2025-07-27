package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvBlock;
import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvLabel;

import java.util.ArrayList;
import java.util.Arrays;

public class RiscvJ extends RiscvInstruction {
    public RiscvJ(RiscvLabel label, RiscvBlock parent) {
        super(new ArrayList<>(Arrays.asList(label)), null);
        assert label instanceof RiscvBlock;
        RiscvBlock block1 = (RiscvBlock) label;
        block1.addPreds(parent);
        parent.addSuccs(block1);
    }

    @Override
    public String toString() {
        return "j" + " " + getOperands().get(0);
    }
}
