package Backend.Arm.Operand;

import Backend.Riscv.Operand.RiscvOperand;
import Backend.Riscv.Operand.RiscvPhyReg;

public class ArmReg extends ArmOperand {
    public boolean isPreColored() {
        return this instanceof ArmPhyReg;
    }
}
