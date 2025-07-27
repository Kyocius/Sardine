package Backend.Riscv.Operand;

public class RiscvPhyReg extends RiscvReg {
    public boolean canBeReorder(){
        return true;
    }
}
