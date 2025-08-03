package Backend.Arm.Instruction;

import Backend.Arm.Operand.*;
import Backend.Arm.tools.ArmTools;

import java.util.ArrayList;
import java.util.Collections;

public class ArmLi extends ArmInstruction {
    private final ArmTools.CondType condType;

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

    public ArmTools.CondType getCondType() {
        return condType;
    }

    @Override
    public String toString() {
        // Handle label references (for address calculations)
        if (getOperands().getFirst() instanceof ArmLabel) {
            // AArch64: Use adrp/add for label addresses
            String labelName = ((ArmLabel) getOperands().getFirst()).getName();
            if (condType == ArmTools.CondType.nope) {
                // Load label address using adrp + add
                return "adrp\t" + getDefReg() + ",\t" + labelName + "\n" +
                       "\tadd\t" + getDefReg() + ",\t" + getDefReg() + ",\t:lo12:" + labelName;
            } else {
                // Conditional label loading not commonly used in AArch64
                return "adrp\t" + getDefReg() + ",\t" + labelName + "\n" +
                       "\tadd\t" + getDefReg() + ",\t" + getDefReg() + ",\t:lo12:" + labelName;
            }
        }

        // Handle immediate values
        assert getOperands().getFirst() instanceof ArmImm;
        ArmImm imm = (ArmImm) getOperands().getFirst();
        long value = imm.getValue();
        String condSuffix = (condType == ArmTools.CondType.nope) ? "" :
                           ArmTools.getCondString(condType);

        // AArch64 immediate loading strategy
        if (condType == ArmTools.CondType.nope) {
            // Unconditional load
            if (value == 0) {
                // Use wzr/xzr for zero
                return "mov\t" + getDefReg() + ",\t" + "xzr";
            } else if (value >= 0 && value <= 0xFFFF) {
                // 16-bit positive immediate
                return "mov\t" + getDefReg() + ",\t#" + value;
            } else if (value < 0 && value >= -0x10000) {
                // 16-bit negative immediate
                return "mov\t" + getDefReg() + ",\t#" + value;
            } else if ((value & 0xFFFF) == 0 && (value >>> 16) <= 0xFFFF) {
                // Can be loaded with single MOV with LSL #16
                return "mov\t" + getDefReg() + ",\t#" + (value >>> 16) + ", lsl #16";
            } else if ((~value) <= 0xFFFF) {
                // Use MVN for bitwise NOT of small immediate
                return "mvn\t" + getDefReg() + ",\t#" + (~value);
            } else if (((~value) & 0xFFFF) == 0 && ((~value) >>> 16) <= 0xFFFF) {
                // Use MVN with shift
                return "mvn\t" + getDefReg() + ",\t#" + ((~value) >>> 16) + ", lsl #16";
            } else {
                // For complex 64-bit immediates, use MOV + MOVK sequence
                StringBuilder result = new StringBuilder();

                // Load lower 16 bits
                result.append("mov\t")
                      .append(getDefReg()).append(",\t#").append(value & 0xFFFF);

                // Load upper bits if needed using MOVK
                long upper16 = (value >>> 16) & 0xFFFF;
                if (upper16 != 0) {
                    result.append("\n\tmovk\t")
                          .append(getDefReg()).append(",\t#").append(upper16)
                          .append(", lsl #16");
                }

                long upper32 = (value >>> 32) & 0xFFFF;
                if (upper32 != 0) {
                    result.append("\n\tmovk\t")
                          .append(getDefReg()).append(",\t#").append(upper32)
                          .append(", lsl #32");
                }

                long upper48 = (value >>> 48) & 0xFFFF;
                if (upper48 != 0) {
                    result.append("\n\tmovk\t")
                          .append(getDefReg()).append(",\t#").append(upper48)
                          .append(", lsl #48");
                }

                return result.toString();
            }
        } else {
            // AArch64 conditional execution: use CSEL (conditional select)
            // For conditional mov, we need to use CSEL with wzr/xzr as alternative
            if (value == 0) {
                return "csel\t" + getDefReg() + ",\txzr,\t" + getDefReg() + ",\t" + condSuffix;
            } else if (value == 1) {
                // For mov #1, we can use CSET which is more efficient
                return "cset\t" + getDefReg() + ",\t" + condSuffix;
            } else {
                // For other values, use CSEL with a temporary register
                // This is more complex and might need rethinking of the approach
                return "mov\tx10,\t#" + value + "\n" +
                       "\tcsel\t" + getDefReg() + ",\tx10,\t" + getDefReg() + ",\t" + condSuffix;
            }
        }
    }
}
