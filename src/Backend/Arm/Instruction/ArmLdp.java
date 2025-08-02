package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmLdp extends ArmInstruction {
    private final boolean postIndex; // true for [sp], 16, false for [sp, 16]

    public ArmLdp(ArmReg reg1, ArmReg reg2, ArmReg baseReg, ArmImm offset, boolean postIndex) {
        super(null, new ArrayList<>(Arrays.asList(reg1, reg2, baseReg, offset)));
        this.postIndex = postIndex;
    }

    @Override
    public String toString() {
        ArmReg reg1 = (ArmReg) getOperands().getFirst();
        ArmReg reg2 = (ArmReg) getOperands().get(1);
        ArmReg baseReg = (ArmReg) getOperands().get(2);
        ArmImm offset = (ArmImm) getOperands().get(3);
        
        if (postIndex) {
            // AArch64 post-index addressing with writeback: ldp x1, x2, [sp], #16
            return "ldp\t" + reg1 + ",\t" + reg2 + ",\t[" + baseReg + "], " + offset;
        } else {
            // AArch64 pre-index addressing: ldp x1, x2, [sp, #16]
            return "ldp\t" + reg1 + ",\t" + reg2 + ",\t[" + baseReg + ", " + offset + "]";
        }
    }
}
