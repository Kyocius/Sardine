package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;

public class RiscvLongLi extends RiscvInstruction {
    private final long val;
    public RiscvLongLi(long imm, RiscvReg defReg) {
        super(new ArrayList<>(), defReg);
        this.val = imm;
    }

    @Override
    public String toString() {
        return "li " + getDefReg() + "," + val;
    }
}
