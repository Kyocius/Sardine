package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;
import Backend.Arm.tools.ArmTools;

import java.util.ArrayList;
import java.util.Arrays;

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
    
    private CselType type;
    private ArmTools.CondType condition;

    public ArmCsel(ArmReg destReg, ArmReg trueReg, ArmReg falseReg, ArmTools.CondType condition, CselType type) {
        super(destReg, new ArrayList<>(Arrays.asList(trueReg, falseReg)));
        this.condition = condition;
        this.type = type;
    }

    // Constructor for single-operand variants (cset, cinc, etc.)
    public ArmCsel(ArmReg destReg, ArmReg sourceReg, ArmTools.CondType condition, CselType type) {
        super(destReg, new ArrayList<>(Arrays.asList(sourceReg)));
        this.condition = condition;
        this.type = type;
    }

    public ArmTools.CondType getCondition() {
        return condition;
    }

    public CselType getType() {
        return type;
    }

    @Override
    public String toString() {
        switch (type) {
            case csel, csinc, csinv, csneg -> {
                return type.toString() + "\t" + getDefReg() + ",\t" + getOperands().get(0) + ",\t" + 
                       getOperands().get(1) + ",\t" + ArmTools.getCondString(condition);
            }
            case cset -> {
                return "cset\t" + getDefReg() + ",\t" + ArmTools.getCondString(condition);
            }
            case csetm -> {
                return "csetm\t" + getDefReg() + ",\t" + ArmTools.getCondString(condition);
            }
            case cinc, cinv, cneg -> {
                return type.toString() + "\t" + getDefReg() + ",\t" + getOperands().get(0) + ",\t" + 
                       ArmTools.getCondString(condition);
            }
        }
        return null;
    }
}
