package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvImm;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;
import java.util.Arrays;

public class RiscvSd extends RiscvInstruction {
    public RiscvSd(RiscvReg storeReg, RiscvImm riscvImm, RiscvReg offReg) {
        super(new ArrayList<>(Arrays.asList(storeReg, riscvImm, offReg)), null);
    }

    @Override
    public String toString() {
        return "sd" + " " + getOperands().get(0) + "," + getOperands().get(1) + "(" + getOperands().get(2) + ")";
    }
}
