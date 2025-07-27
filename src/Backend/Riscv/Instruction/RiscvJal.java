package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvCPUReg;
import Backend.Riscv.Operand.RiscvLabel;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;

public class RiscvJal extends RiscvInstruction {
    public LinkedHashSet<RiscvReg> usedRegs = new LinkedHashSet<RiscvReg>();
    public RiscvJal(RiscvLabel targetFunction) {
        super(new ArrayList<>(Collections.singleton(targetFunction)),
                RiscvCPUReg.getRiscvRaReg());//修改了ra
    }

    public void addUsedReg(RiscvReg usedReg) {
        usedRegs.add(usedReg);
    }

    public LinkedHashSet<RiscvReg> getUsedRegs() {
        return usedRegs;
    }

    @Override
    public String toString() {
        return "jal " + getOperands().get(0) + "\n";
    }
}
