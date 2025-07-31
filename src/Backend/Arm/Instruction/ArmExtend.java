package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * AArch64 extend instructions for different data widths
 */
public class ArmExtend extends ArmInstruction {
    public enum ExtendType {
        sxtb,   // sign extend byte to 32/64 bit
        sxth,   // sign extend halfword to 32/64 bit  
        sxtw,   // sign extend word to 64 bit
        uxtb,   // zero extend byte to 32/64 bit
        uxth,   // zero extend halfword to 32/64 bit
        uxtw    // zero extend word to 64 bit (actually just mov w to x)
    }
    
    private ExtendType type;

    public ArmExtend(ArmReg destReg, ArmReg sourceReg, ExtendType type) {
        super(destReg, new ArrayList<>(Arrays.asList(sourceReg)));
        this.type = type;
    }

    @Override
    public String toString() {
        if (type == ExtendType.uxtw) {
            // Special case: mov w-reg to x-reg automatically zero-extends
            return "mov\t" + getDefReg() + ",\t" + getOperands().get(0);
        }
        return type.toString() + "\t" + getDefReg() + ",\t" + getOperands().get(0);
    }
}
