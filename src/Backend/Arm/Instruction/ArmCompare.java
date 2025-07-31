package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmCompare extends ArmInstruction {
    private CmpType type;
    
    public ArmCompare(ArmOperand left, ArmOperand right, CmpType type) {
        super(null, new ArrayList<>(Arrays.asList(left, right)));
        this.type = type;
    }

    public enum CmpType {
        cmp,    // compare
        cmn,    // compare negative
        ccmp,   // conditional compare (AArch64)
        ccmn,   // conditional compare negative (AArch64)
        tst,    // test bits (logical AND and set flags)
    }

    public String getCmpTypeStr() {
        switch (this.type) {
            case cmn -> {
                return "cmn";
            }
            case cmp -> {
                return "cmp";
            }
            case ccmp -> {
                return "ccmp";
            }
            case ccmn -> {
                return "ccmn";
            }
            case tst -> {
                return "tst";
            }
        }
        return null;
    }

    public CmpType getType() {
        return type;
    }

    @Override
    public String toString() {
        return getCmpTypeStr() + "\t" + getOperands().get(0) + ",\t" + getOperands().get(1);
    }
}
