package Backend.Riscv.Instruction;

import Backend.Riscv.Operand.RiscvReg;

public class RiscvFmv extends RiscvMv {
    public RiscvFmv(RiscvReg from, RiscvReg toReg) {
        super(from, toReg);
    }

    @Override
    public String toString() {
        return "fmv.s" + " " + getDefReg() + "," + getOperands().get(0);
    }
}
