package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * AArch64 store pair instruction
 * stp: store two registers to consecutive memory locations
 */
public class ArmStp extends ArmInstruction {
    private final boolean preIndex; // true for [sp, -16]!, false for [sp, -16]

    public ArmStp(ArmReg reg1, ArmReg reg2, ArmReg baseReg, ArmImm offset, boolean preIndex) {
        super(null, new ArrayList<>(Arrays.asList(reg1, reg2, baseReg, offset)));
        this.preIndex = preIndex;
    }

    @Override
    public String toString() {
        ArmReg reg1 = (ArmReg) getOperands().getFirst();
        ArmReg reg2 = (ArmReg) getOperands().get(1);
        ArmReg baseReg = (ArmReg) getOperands().get(2);
        ArmImm offset = (ArmImm) getOperands().get(3);
        
        if (preIndex) {
            return "stp\t" + reg1 + ",\t" + reg2 + ",\t[" + baseReg + ", " + offset + "]!";
        } else {
            return "stp\t" + reg1 + ",\t" + reg2 + ",\t[" + baseReg + ", " + offset + "]";
        }
    }
}
