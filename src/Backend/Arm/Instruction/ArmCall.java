package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmCPUReg;
import Backend.Arm.Operand.ArmLabel;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;

public class ArmCall extends ArmInstruction {
    public LinkedHashSet<ArmReg> usedRegs = new LinkedHashSet<>();

    public ArmCall(ArmLabel targetFunction) {
        super(ArmCPUReg.getArmRetReg(), new ArrayList<>(Collections.singleton(targetFunction)));
    }

    public void addUsedReg(ArmReg usedReg) {
        usedRegs.add(usedReg);
    }

    public LinkedHashSet<ArmReg> getUsedRegs() {
        return usedRegs;
    }

    @Override
    public String toString() {
        // AArch64 function call format: bl <label>
        // The 'bl' instruction automatically stores return address in x30 (LR)
        return "bl\t" + getOperands().getFirst() + "\n";
    }
}
