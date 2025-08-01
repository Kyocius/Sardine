package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmVLoad extends ArmInstruction {
    public ArmVLoad(ArmReg baseReg, ArmOperand offset, ArmReg defReg) {
        super(defReg, new ArrayList<>(Arrays.asList(baseReg, offset)));
    }

    @Override
    public String toString() {
        if (getOperands().get(1) instanceof ArmImm) {
            ArmImm imm = (ArmImm) getOperands().get(1);
            if (imm.getValue() == 0) {
                // ARMv8-A floating-point load
                return "ldr\t" + getDefReg() + ",\t[" + getOperands().get(0) +  "]";
            } else {
                // AArch64 floating-point load offset range: 0 to +32760, multiple of 8
                int offset = imm.getValue();
                if (offset >= 0 && offset <= 32760 && offset % 8 == 0) {
                    return "ldr\t" + getDefReg() + ",\t[" + getOperands().get(0) + ", " + getOperands().get(1) + "]";
                } else {
                    // For invalid offsets, use temporary register
                    return "mov\tx16,\t#" + offset + "\n" +
                           "\tadd\tx16,\t" + getOperands().get(0) + ",\tx16\n" +
                           "\tldr\t" + getDefReg() + ",\t[x16]";
                }
            }
        } else {
            return "ldr\t" + getDefReg() + ",\t[" + getOperands().get(0) + ", " + getOperands().get(1) + "]";
        }
    }
}
