package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvImm;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;
import java.util.Arrays;

public class RiscvLui extends RiscvInstruction {
    public RiscvLui(RiscvImm imm, RiscvReg defReg) {
        super(new ArrayList<>(Arrays.asList(imm)), defReg);
    }

    @Override
    public String toString() {
        return "lui " + getDefReg() + "," + getOperands().get(0);
    }
}
