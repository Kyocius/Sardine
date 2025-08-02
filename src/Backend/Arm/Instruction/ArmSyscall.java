package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;

import java.util.ArrayList;
import java.util.Collections;

/**
 * AArch64 supervisor call instruction
 * svc: supervisor call to invoke system services
 */
public class ArmSyscall extends ArmInstruction {
    public ArmSyscall(ArmImm syscallNum) {
        super(null, new ArrayList<>(Collections.singletonList(syscallNum)));
    }

    @Override
    public String toString() {
        // AArch64 uses svc (supervisor call) instead of ARMv7's swi (software interrupt)
        return "svc\t" + getOperands().getFirst();
    }
}
