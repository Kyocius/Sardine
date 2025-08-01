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
            if (condType == ArmTools.CondType.nope) {
                return "adrp\t" + getDefReg() + ",\t" + labelName + "\n\t" +
                        "add\t" + getDefReg() + ",\t" + getDefReg() + ",\t:lo12:" + labelName;
            } else {
                // For conditional label loading, use temporary register
                return "adrp\tx16,\t" + labelName + "\n\t" +
                        "add\tx16,\tx16,\t:lo12:" + labelName + "\n\t" +
                        "csel\t" + getDefReg() + ",\tx16,\txzr,\t" + ArmTools.getCondString(condType);
            }
        } else {
            assert getOperands().get(0) instanceof ArmImm;
            ArmImm imm = (ArmImm) getOperands().get(0);
            long value = imm.getValue();
            
            // Strategy 1: Try mov with logical immediate (most efficient)
            if (ArmTools.isLogicalImmediate(value)) {
                if (condType == ArmTools.CondType.nope) {
                    return "mov\t" + getDefReg() + ",\t#" + value;
                } else {
                    return "mov\tx16,\t#" + value + "\n" +
                           "\tcsel\t" + getDefReg() + ",\tx16,\txzr,\t" + ArmTools.getCondString(condType);
                }
            }
            
            // Strategy 2: Small immediates (0-65535)
            else if (value >= 0 && value <= 0xFFFF) {
                if (condType == ArmTools.CondType.nope) {
                    return "mov\t" + getDefReg() + ",\t#" + value;
                } else {
                    return "mov\tx16,\t#" + value + "\n" +
                           "\tcsel\t" + getDefReg() + ",\tx16,\txzr,\t" + ArmTools.getCondString(condType);
                }
            } 
            
            // Strategy 3: Negative immediates that fit in 16 bits when negated
            else if (value < 0 && value >= -0xFFFF) {
                if (condType == ArmTools.CondType.nope) {
                    return "mov\t" + getDefReg() + ",\t#" + value;
                } else {
                    return "mov\tx16,\t#" + value + "\n" +
                           "\tcsel\t" + getDefReg() + ",\tx16,\txzr,\t" + ArmTools.getCondString(condType);
                }
            } 
            
            // Strategy 4: Try movn (move not) for inverted values
            else if ((~value & 0xFFFFFFFFFFFFFFFFL) <= 0xFFFF) {
                long inverted = ~value & 0xFFFF;
                if (condType == ArmTools.CondType.nope) {
                    return "movn\t" + getDefReg() + ",\t#" + inverted;
                } else {
                    return "movn\tx16,\t#" + inverted + "\n" +
                           "\tcsel\t" + getDefReg() + ",\tx16,\txzr,\t" + ArmTools.getCondString(condType);
                }
            }
            
            // Strategy 5: Multi-instruction sequence for large values
            else {
                StringBuilder result = new StringBuilder();
                boolean first = true;
                
                if (condType != ArmTools.CondType.nope) {
                    // For conditional moves with large immediates, use a temporary register
                    result.append("// Conditional load of large immediate\n\t");
                }
                
                // Process 16-bit chunks
                for (int shift = 0; shift < 64; shift += 16) {
                    int chunk = (int)((value >>> shift) & 0xFFFF);
                    if (chunk != 0) {
                        if (first) {
                            // Use movz for the first non-zero chunk
                            String reg = (condType != ArmTools.CondType.nope) ? "x16" : getDefReg().toString();
                            result.append("movz\t").append(reg).append(",\t#").append(chunk);
                            if (shift > 0) {
                                result.append(",\tlsl #").append(shift);
                            }
                            first = false;
                        } else {
                            // Use movk for subsequent chunks
                            String reg = (condType != ArmTools.CondType.nope) ? "x16" : getDefReg().toString();
                            result.append("\n\tmovk\t").append(reg)
                                  .append(",\t#").append(chunk).append(",\tlsl #").append(shift);
                        }
                    }
                }
                
                // Add conditional select if needed
                if (condType != ArmTools.CondType.nope) {
                    result.append("\n\tcsel\t").append(getDefReg()).append(",\tx16,\txzr,\t")
                          .append(ArmTools.getCondString(condType));
                }
                
                return result.toString();
            }
        }
    }
}
