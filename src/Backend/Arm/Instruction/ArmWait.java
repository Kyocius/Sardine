package Backend.Arm.Instruction;

import java.util.ArrayList;

/**
 * AArch64 wait function call instruction
 * bl wait: branch with link to wait function for process synchronization
 */
public class ArmWait extends ArmInstruction {
    public ArmWait() {
        super(null, new ArrayList<>());
    }

    @Override
    public String toString() {
        // AArch64 branch with link to wait function
        return "bl\twait";
    }
}
