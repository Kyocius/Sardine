package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;
import Backend.Arm.tools.ArmTools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * AArch64 Conditional Select and related instructions
 * These replace most conditional execution from ARMv7
 */
public class ArmCsel extends ArmInstruction {
    public enum CselType {
        csel,   // conditional select
        csinc,  // conditional select increment
        csinv,  // conditional select invert  
        csneg,  // conditional select negate
        cset,   // conditional set (alias for csinc with wzr/xzr)
        csetm,  // conditional set mask (alias for csinv with wzr/xzr)
        cinc,   // conditional increment (alias for csinc with same reg)
        cinv,   // conditional invert (alias for csinv with same reg)
        cneg    // conditional negate (alias for csneg with same reg)
    }
    
    private final CselType type;
    private final ArmTools.CondType condition;

    // Constructor for 3-operand CSEL instructions (csel, csinc, csinv, csneg)
    public ArmCsel(ArmReg destReg, ArmReg trueReg, ArmReg falseReg, ArmTools.CondType condition, CselType type) {
        super(destReg, new ArrayList<>(Arrays.asList(trueReg, falseReg)));
        this.condition = condition;
        this.type = type;
    }

    // Constructor for 2-operand CSEL instructions (cset, cinc, cinv, cneg)
    public ArmCsel(ArmReg destReg, ArmReg sourceReg, ArmTools.CondType condition, CselType type) {
        super(destReg, new ArrayList<>(Collections.singletonList(sourceReg)));
        this.condition = condition;
        this.type = type;
    }

    public ArmTools.CondType getCondition() {
        return condition;
    }

    public CselType getCselType() {
        return type;
    }

    /**
     * Get the "true" register (first operand)
     */
    public ArmReg getTrueReg() {
        return (ArmReg) getOperands().getFirst();
    }

    /**
     * Get the "false" register (second operand, if exists)
     */
    public ArmReg getFalseReg() {
        return getOperands().size() > 1 ? (ArmReg) getOperands().get(1) : null;
    }

    /**
     * Check if this is a 3-operand CSEL instruction
     */
    public boolean isThreeOperand() {
        return type == CselType.csel || type == CselType.csinc ||
               type == CselType.csinv || type == CselType.csneg;
    }

    /**
     * Check if this is an alias instruction (2-operand)
     */
    public boolean isAliasInstruction() {
        return type == CselType.cset || type == CselType.csetm ||
               type == CselType.cinc || type == CselType.cinv || type == CselType.cneg;
    }

    @Override
    public String toString() {
        String condString = ArmTools.getCondString(this.condition);

        // Handle 3-operand instructions
        if (isThreeOperand()) {
            return type + "\t" + getDefReg() + ",\t" + getOperands().getFirst() + ",\t" +
                   getOperands().get(1) + ",\t" + condString;
        }

        // Handle 2-operand alias instructions
        switch (type) {
            case cset:
                // cset rd, cond (alias for csinc rd, wzr, wzr, cond)
                return "cset\t" + getDefReg() + ",\t" + condString;
            case csetm:
                // csetm rd, cond (alias for csinv rd, wzr, wzr, cond)
                return "csetm\t" + getDefReg() + ",\t" + condString;
            case cinc:
                // cinc rd, rn, cond (alias for csinc rd, rn, rn, invert(cond))
                return "cinc\t" + getDefReg() + ",\t" + getOperands().getFirst() + ",\t" +
                       ArmTools.getCondString(ArmTools.getRevCondType(condition));
            case cinv:
                // cinv rd, rn, cond (alias for csinv rd, rn, rn, invert(cond))
                return "cinv\t" + getDefReg() + ",\t" + getOperands().getFirst() + ",\t" +
                       ArmTools.getCondString(ArmTools.getRevCondType(condition));
            case cneg:
                // cneg rd, rn, cond (alias for csneg rd, rn, rn, invert(cond))
                return "cneg\t" + getDefReg() + ",\t" + getOperands().getFirst() + ",\t" +
                       ArmTools.getCondString(ArmTools.getRevCondType(condition));
            default:
                return type + "\t" + getDefReg() + ",\t" + getOperands().getFirst() + ",\t" + condString;
        }
    }
}
