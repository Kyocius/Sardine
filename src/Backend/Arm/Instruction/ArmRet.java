package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmCPUReg;
import Backend.Arm.Operand.ArmReg;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class ArmRet extends ArmInstruction {
    public ArmRet(ArmReg armReg, ArmReg retUsedReg) {
        super(null, new ArrayList<>((retUsedReg == null? Collections.singletonList(armReg): Arrays.asList(armReg, retUsedReg))));
        assert armReg == ArmCPUReg.getArmRetReg();
    }

    @Override
    public String toString() {
        // ARMv8-A return instruction
        return "ret";
    }
}
