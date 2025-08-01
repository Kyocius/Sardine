package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmFSw extends ArmInstruction {
    public ArmFSw(ArmReg storeReg, ArmReg baseReg, ArmOperand armOffset) {
        //TODO:后续可以引入增强 STR R0,[R1],＃8 e.g.
        super(null, new ArrayList<>(Arrays.asList(storeReg, baseReg, armOffset)));
    }

    @Override
    public String toString() {
        if (getOperands().get(2) instanceof ArmImm) {
            ArmImm imm = (ArmImm) getOperands().get(2);
            if (imm.getValue() == 0) {
                // ARMv8-A floating-point store
                return "str\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + "]";
            } else {
                // AArch64 floating-point store offset range: 0 to +32760, multiple of 8
                int offset = imm.getValue();
                if (offset >= 0 && offset <= 32760 && offset % 8 == 0) {
                    return "str\t" + getOperands().get(0) + ",\t[" + getOperands().get(1) + ", " + getOperands().get(2) + "]";
                } else {
                    // For invalid offsets, use temporary register
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
