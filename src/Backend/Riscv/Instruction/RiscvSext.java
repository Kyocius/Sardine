package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvImm;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;
import java.util.Arrays;

public class RiscvSext extends RiscvInstruction {
    public RiscvSext(RiscvReg srcReg, RiscvReg defReg) {
        super(new ArrayList<>(Arrays.asList(srcReg)), defReg);
    }

    @Override
    public String toString() {
        return "sext.w" + " " + getDefReg() + "," + getOperands().get(0);
    }
}
