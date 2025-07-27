package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class RiscvMv extends RiscvInstruction {
    public RiscvMv(RiscvReg from, RiscvReg toReg) {
        super(new ArrayList<>(Collections.singletonList(from)), toReg);
    }

    @Override
    public String toString() {
        return "mv" + " " + getDefReg() + "," + getOperands().get(0);
    }
}
