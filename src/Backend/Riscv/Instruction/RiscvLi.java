package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvImm;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;
import java.util.Arrays;

public class RiscvLi extends RiscvInstruction {
    public RiscvLi(RiscvImm riscvImm, RiscvReg defReg) {
        super(new ArrayList<>(Arrays.asList(riscvImm)), defReg);
    }

    @Override
    public String toString() {
        return "li " + getDefReg() + "," + getOperands().get(0);
    }
}
