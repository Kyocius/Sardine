package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;
import java.util.Arrays;

public class RiscvJr extends RiscvInstruction {
    public RiscvJr(RiscvReg riscvReg) {
        super(new ArrayList<>(Arrays.asList(riscvReg)),null );
    }

    @Override
    public String toString() {
        return "jr" + " " + getOperands().get(0);
    }
}
