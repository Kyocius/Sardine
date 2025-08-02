package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmCompare extends ArmInstruction {
    private final CmpType type;

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
        // AArch64 specific floating-point compare instructions
        fcmp,   // floating-point compare
        fcmpe,  // floating-point compare with exception
    }

    public CmpType getType() {
        return type;
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
            case fcmp -> {
                return "fcmp";
            }
            case fcmpe -> {
                return "fcmpe";
            }
        }
        return null;
    }

    @Override
    public String toString() {
        // AArch64 compare instruction format: cmp[type] operand1, operand2
        return getCmpTypeStr() + "\t" + getOperands().getFirst() + ",\t" + getOperands().get(1);
    }
}
