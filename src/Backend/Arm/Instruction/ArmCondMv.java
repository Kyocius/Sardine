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
        return "mov" + ArmTools.getCondString(this.type) +
                "\t" + getDefReg() + ",\t" + getOperands().get(0);
    }
}
