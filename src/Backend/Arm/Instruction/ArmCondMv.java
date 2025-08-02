package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;
import Backend.Arm.tools.ArmTools;

import java.util.ArrayList;
import java.util.Collections;

public class ArmCondMv extends ArmInstruction {
    private ArmTools.CondType type;

    public ArmCondMv(ArmTools.CondType type, ArmOperand operand, ArmReg defReg) {
        super(defReg, new ArrayList<>(Collections.singleton(operand)));
        this.type = type;
    }

    @Override
    public String toString() {
        // For AArch64 compatibility, use CSEL instead of conditional MOV
        // AArch64 CSEL format: csel rd, rn, rm, cond
        // For conditional move: csel rd, rn, rd, cond (move rn to rd if condition true)
        String condString = ArmTools.getCondString(this.type);
        if (type == ArmTools.CondType.nope) {
            // Unconditional move, use regular mov
            return "mov\t" + getDefReg() + ",\t" + getOperands().getFirst();
        } else {
            // Conditional move using CSEL
            return "csel\t" + getDefReg() + ",\t" + getOperands().getFirst() +
                   ",\t" + getDefReg() + ",\t" + condString;
        }
    }
}
