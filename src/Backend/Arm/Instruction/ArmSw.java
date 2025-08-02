package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * AArch64 store instruction
 * str: store register to memory with immediate or register offset
 */
public class ArmSw extends ArmInstruction {
    public ArmSw(ArmReg storeReg, ArmReg offReg, ArmOperand armImm) {
        //TODO:后续可以引入增强 STR R0,[R1],＃8 e.g.
        super(null, new ArrayList<>(Arrays.asList(storeReg, offReg, armImm)));
    }

    @Override
    public String toString() {
        if (getOperands().get(2) instanceof ArmImm imm) {
            if (imm.getValue() == 0) {
                return "str\t" + getOperands().getFirst() + ",\t[" + getOperands().get(1) + "]";
            } else {
                // AArch64 str immediate offset range check
                // Unscaled: -256 to +255
                // Scaled: 0 to +32760 (multiple of 8 for 64-bit, 4 for 32-bit)
                int offset = imm.getValue();
                
                // Check unscaled immediate range
                if (offset >= -256 && offset <= 255) {
                    return "str\t" + getOperands().getFirst() + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
                }
                // Check scaled immediate range (assuming 8-byte alignment for 64-bit)
                else if (offset >= 0 && offset <= 32760 && offset % 8 == 0) {
                    return "str\t" + getOperands().getFirst() + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
                }
                // Handle out-of-range offsets using temporary register
                else {
                    ArmReg baseReg = (ArmReg) getOperands().get(1);
                    ArmReg storeReg = (ArmReg) getOperands().getFirst();
                    return "mov\tx16,\t#" + offset + "\n" +
                           "\tadd\tx16,\t" + baseReg + ",\tx16\n" +
                           "\tstr\t" + storeReg + ",\t[x16]";
                }
            }
        } else {
            // Register offset
            return "str\t" + getOperands().getFirst() + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
        }
    }
}
