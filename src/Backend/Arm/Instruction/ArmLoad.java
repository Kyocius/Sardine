package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmLoad extends ArmInstruction {
    public ArmLoad(ArmReg baseReg, ArmOperand offset, ArmReg defReg) {
        super(defReg, new ArrayList<>(Arrays.asList(baseReg, offset)));
    }

    @Override
    public String toString() {
        if (getOperands().get(1) instanceof ArmImm imm) {
            if (imm.getValue() == 0) {
                // AArch64 zero offset: ldr x0, [x1]
                return "ldr\t" + getDefReg() + ",\t[" + getOperands().getFirst() + "]";
            } else {
                // AArch64 ldr immediate offset range check
                // Unscaled: -256 to +255
                // Scaled: 0 to +32760 (multiple of 8 for 64-bit, 4 for 32-bit)
                int offset = imm.getIntValue();
                
                // Check unscaled immediate range
                if (offset >= -256 && offset <= 255) {
                    return "ldr\t" + getDefReg() + ",\t[" + getOperands().getFirst() + ", " + getOperands().get(1) + "]";
                }
                // Check scaled immediate range (assuming 8-byte alignment for 64-bit)
                else if (offset >= 0 && offset <= 32760 && offset % 8 == 0) {
                    return "ldr\t" + getDefReg() + ",\t[" + getOperands().getFirst() + ", " + getOperands().get(1) + "]";
                }
                // For large offsets, use temporary register x16
                else {
                    return "mov\tx16,\t#" + offset + "\n" +
                           "\tadd\tx16,\t" + getOperands().getFirst() + ",\tx16\n" +
                           "\tldr\t" + getDefReg() + ",\t[x16]";
                }
            }
        } else {
            // Register offset mode: ldr Rt, [Xn, Xm]
            return "ldr\t" + getDefReg() + ",\t[" + getOperands().getFirst() + ", " + getOperands().get(1) + "]";
        }
    }
}
