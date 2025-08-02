package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmCPUReg;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * AArch64 return instruction
 * ret: return from subroutine using link register (x30/LR)
 */
public class ArmRet extends ArmInstruction {
    public ArmRet(ArmReg armReg, ArmReg retUsedReg) {
        super(null, new ArrayList<>((retUsedReg == null? Collections.singletonList(armReg): Arrays.asList(armReg, retUsedReg))));
        assert armReg == ArmCPUReg.getArmRetReg();
    }

    @Override
    public String toString() {
        // AArch64 return instruction - uses x30 (LR) register
        return "ret";
    }
}
