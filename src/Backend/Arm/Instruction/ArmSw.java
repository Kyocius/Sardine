package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmSw extends ArmInstruction {
    public ArmSw(ArmReg storeReg, ArmReg offReg, ArmOperand armImm) {
        //TODO:后续可以引入增强 STR R0,[R1],＃8 e.g.
        super(null, new ArrayList<>(Arrays.asList(storeReg, offReg, armImm)));
    }

    @Override
    public String toString() {
        if (getOperands().get(2) instanceof ArmImm) {
            ArmImm imm = (ArmImm) getOperands().get(2);
            if (imm.getValue() == 0) {
                return "str\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + "]";
            } else {
                // AArch64 str immediate offset range check
                // For 64-bit: -256 to +4088 (multiple of 8)
                // For 32-bit: -256 to +4092 (multiple of 4)
                int offset = imm.getValue();
                if (offset >= -256 && offset <= 4088 && offset % 8 == 0) {
                    return "str\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
                } else {
                    // For large offsets, need to use a temporary register
                    // This is a limitation - we should generate additional instructions in the code generator
                    return "// Warning: offset " + offset + " out of range for str immediate\n" +
                           "str\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
                }
            }
        } else {
            return "str\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
        }
    }
}
