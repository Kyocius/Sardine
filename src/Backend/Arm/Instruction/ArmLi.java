package Backend.Arm.Instruction;

import Backend.Arm.Operand.*;
import Backend.Arm.tools.ArmTools;

import java.util.ArrayList;
import java.util.Collections;

public class ArmLi extends ArmInstruction {
    private ArmTools.CondType condType;

    public ArmLi(ArmOperand from, ArmReg toReg) {
        super(toReg, new ArrayList<>(Collections.singletonList(from)));
        assert from instanceof ArmImm;
        condType = ArmTools.CondType.nope;
    }

    public ArmLi(ArmOperand from, ArmReg toReg, ArmTools.CondType type) {
        super(toReg, new ArrayList<>(Collections.singletonList(from)));
        assert from instanceof ArmImm;
        condType = type;
    }

//    public enum ArmMovType {
//        mov,   // ARMv8-A: move immediate
//        movk,  // ARMv8-A: move immediate with keep (for 64-bit immediates)
//        mvn,   // move not
//        Note: ARMv7 movw/movt are replaced with mov/movk in ARMv8-A for 64-bit
//    }

    public ArmTools.CondType getCondType() {
        return condType;
    }

    @Override
    public String toString() {
        if (getOperands().get(0) instanceof ArmLabel) {
            // ARMv8-A: Use adrp/add for label addresses
            String labelName = ((ArmLabel) getOperands().get(0)).getName();
            return "adrp\t" + getDefReg() + ",\t" + labelName + "\n\t" +
                    "add\t" + getDefReg() + ",\t" + getDefReg() + ",\t:lo12:" + labelName;

        } else {
            assert getOperands().get(0) instanceof ArmImm;
            ArmImm imm = (ArmImm) getOperands().get(0);
            long value = imm.getValue();
            
            // For small immediates that can be encoded directly
            if (value >= 0 && value <= 0xFFF) {
                if (condType == ArmTools.CondType.nope) {
                    return "mov\t" + getDefReg() + ",\t#" + value;
                } else {
                    // ARMv8-A: Use csel for conditional moves
                    return "mov\tx16,\t#" + value + "\n" +
                           "\tcsel\t" + getDefReg() + ",\tx16,\txzr,\t" + ArmTools.getCondString(condType);
                }
            } else if (value < 0 && value >= -0xFFF) {
                if (condType == ArmTools.CondType.nope) {
                    return "mov\t" + getDefReg() + ",\t#" + value;
                } else {
                    // ARMv8-A: Use csel for conditional moves
                    return "mov\tx16,\t#" + value + "\n" +
                           "\tcsel\t" + getDefReg() + ",\tx16,\txzr,\t" + ArmTools.getCondString(condType);
                }
            } else {
                // ARMv8-A: Use mov/movk for large immediates
                StringBuilder result = new StringBuilder();
                
                // Load lower 16 bits
                int lowerBits = (int)(value & 0xFFFF);
                result.append("mov").append(ArmTools.getCondString(condType))
                      .append("\t").append(getDefReg()).append(",\t#").append(lowerBits);
                
                // Load upper bits if needed
                if ((value >>> 16) != 0) {
                    int upperBits = (int)((value >>> 16) & 0xFFFF);
                    if (upperBits != 0) {
                        result.append("\n\tmovk\t").append(getDefReg())
                              .append(",\t#").append(upperBits).append(", lsl #16");
                    }
                }
                
                // For 64-bit values, handle higher bits
                if ((value >>> 32) != 0) {
                    int bits32_47 = (int)((value >>> 32) & 0xFFFF);
                    if (bits32_47 != 0) {
                        result.append("\n\tmovk\t").append(getDefReg())
                              .append(",\t#").append(bits32_47).append(", lsl #32");
                    }
                    
                    int bits48_63 = (int)((value >>> 48) & 0xFFFF);
                    if (bits48_63 != 0) {
                        result.append("\n\tmovk\t").append(getDefReg())
                              .append(",\t#").append(bits48_63).append(", lsl #48");
                    }
                }
                
                return result.toString();
            }
        }
    }
}
