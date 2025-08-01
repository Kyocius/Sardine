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
                // Unscaled: -256 to +255
                // Scaled: 0 to +32760 (multiple of 8 for 64-bit, 4 for 32-bit)
                int offset = imm.getValue();
                
                // Check unscaled immediate range
                if (offset >= -256 && offset <= 255) {
                    return "str\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
                }
                // Check scaled immediate range (assuming 8-byte alignment for 64-bit)
                else if (offset >= 0 && offset <= 32760 && offset % 8 == 0) {
                    return "str\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
                } 
                // For large offsets, use temporary register x16
                else {
                    return "mov\tx16,\t#" + offset + "\n" +
                           "\tadd\tx16,\t" + getOperands().get(1) + ",\tx16\n" +
                           "\tstr\t" + getOperands().get(0) + ",\t[x16]";
                }
            }
        } else {
            return "str\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
        }
    }
}
