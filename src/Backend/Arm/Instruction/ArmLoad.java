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
        if (getOperands().get(1) instanceof ArmImm) {
            ArmImm imm = (ArmImm) getOperands().get(1);
            if (imm.getValue() == 0) {
                return "ldr\t" + getDefReg() + ",\t[" + getOperands().get(0) + "]";
            } else {
                // AArch64 ldr immediate offset range check
                // For 64-bit: -256 to +4088 (multiple of 8)
                // For 32-bit: -256 to +4092 (multiple of 4)
                int offset = imm.getValue();
                if (offset >= -256 && offset <= 4088 && offset % 8 == 0) {
                    return "ldr\t" + getDefReg() + ",\t[" + getOperands().get(0) + ", " + getOperands().get(1) + "]";
                } else {
                    // For large offsets, use add instruction to calculate address
                    return "add\t" + getDefReg() + ",\t" + getOperands().get(0) + ",\t" + getOperands().get(1) + "\n" +
                           "ldr\t" + getDefReg() + ",\t[" + getDefReg() + "]";
                }
            }
        } else {
            return "ldr\t" + getDefReg() + ",\t[" + getOperands().get(0) + ", " + getOperands().get(1) + "]";
        }
    }
}
