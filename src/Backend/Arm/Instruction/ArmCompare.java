package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;
import Backend.Riscv.Operand.RiscvOperand;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmCompare extends ArmInstruction {
    private CmpType type;
    public ArmCompare(ArmOperand left, ArmOperand right, CmpType type) {
        super(null, new ArrayList<>(Arrays.asList(left, right)));
        this.type = type;
    }

    public enum CmpType {
        cmp,
        cmn,
    }

    public String getCmpTypeStr() {
        switch (this.type) {
            case cmn -> {
                return "cmn";
            }
            case cmp -> {
                return "cmp";
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return getCmpTypeStr() + "\t" + getOperands().get(0) + ",\t" + getOperands().get(1) + "\n";
    }
}
