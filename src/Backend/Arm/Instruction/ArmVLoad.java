package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * AArch64 floating-point load instruction
 * ldr: load floating-point value from memory to FP/SIMD register
 */
public class ArmVLoad extends ArmInstruction {
    public ArmVLoad(ArmReg baseReg, ArmOperand offset, ArmReg defReg) {
        super(defReg, new ArrayList<>(Arrays.asList(baseReg, offset)));
    }

    @Override
    public String toString() {
        if (getOperands().get(1) instanceof ArmImm imm) {
            if (imm.getValue() == 0) {
                // AArch64 floating-point load with zero offset
                return "ldr\t" + getDefReg() + ",\t[" + getOperands().getFirst() + "]";
            } else {
                // AArch64 floating-point load offset range: 0 to +32760, multiple of 8
                int offset = imm.getIntValue();
                if (offset >= 0 && offset <= 32760 && offset % 8 == 0) {
                    return "ldr\t" + getDefReg() + ",\t[" + getOperands().getFirst() + ", " + getOperands().get(1) + "]";
                } else {
                    // Handle out-of-range offsets using temporary register x16
                    ArmReg baseReg = (ArmReg) getOperands().getFirst();
                    return "mov\tx16,\t#" + offset + "\n" +
                           "\tadd\tx16,\t" + baseReg + ",\tx16\n" +
                           "\tldr\t" + getDefReg() + ",\t[x16]";
                }
            }
        } else {
            // Register offset addressing
            return "ldr\t" + getDefReg() + ",\t[" + getOperands().getFirst() + ", " + getOperands().get(1) + "]";
        }
    }
}
