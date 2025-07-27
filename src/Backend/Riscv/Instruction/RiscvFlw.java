package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvImm;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;
import java.util.Arrays;

public class RiscvFlw extends RiscvInstruction {
    public RiscvFlw(RiscvImm imm, RiscvReg srcReg, RiscvReg defReg) {
        super(new ArrayList<>(Arrays.asList(imm, srcReg)), defReg);
    }

    @Override
    public String toString() {
        return "flw" + " " + getDefReg() + "," + getOperands().get(0) + "(" + getOperands().get(1) + ")";
    }
}
