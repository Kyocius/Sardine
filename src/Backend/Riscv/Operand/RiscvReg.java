package Backend.Riscv.Operand;

public class RiscvReg extends RiscvOperand {
    public boolean isPreColored() {
        return this instanceof RiscvPhyReg;
    }

}
