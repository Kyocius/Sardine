package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmCPUReg;
import Backend.Arm.Operand.ArmLabel;
import Backend.Arm.Operand.ArmReg;
import Backend.Riscv.Operand.RiscvCPUReg;
import Backend.Riscv.Operand.RiscvLabel;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;

public class ArmCall extends ArmInstruction {
    public LinkedHashSet<ArmReg> usedRegs = new LinkedHashSet<>();
    public ArmCall(ArmLabel targetFunction) {
        super(ArmCPUReg.getArmRetReg(), new ArrayList<>(Collections.singleton(targetFunction)));//修改了ra
    }

    public void addUsedReg(ArmReg usedReg) {
        usedRegs.add(usedReg);
    }

    public LinkedHashSet<ArmReg> getUsedRegs() {
        return usedRegs;
    }

    @Override
    public String toString() {
        return "bl\t" + getOperands().get(0) + "\n";
    }
}
