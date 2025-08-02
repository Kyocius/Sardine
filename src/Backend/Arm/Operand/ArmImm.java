package Backend.Arm.Operand;

/**
 * AArch64 immediate operand
 * Represents integer constants in AArch64 assembly with encoding validation
 */
public class ArmImm extends ArmOperand{
    private final long value;  // Changed from int to long for 64-bit support

    public ArmImm(long number) {  // Support 64-bit immediates
        super();
        this.value = number;
    }

    public ArmImm(int number) {  // Legacy constructor for backward compatibility
        super();
        this.value = number;
    }

    public ArmImm() {
        super();
        this.value = 1;
    }

    public long getValue() {  // Return long for 64-bit support
        return this.value;
    }

    public int getIntValue() {  // Legacy method for backward compatibility
        return (int) this.value;
    }

    /**
     * Check if this immediate can be encoded in AArch64 instructions
     * Different instructions have different immediate encoding limitations
     */
    public boolean isValidFor12BitImm() {
        // 12-bit unsigned immediate (0 to 4095) for ADD/SUB instructions
        return value >= 0 && value <= 4095;
    }

    public boolean isValidFor12BitImmShifted() {
        // 12-bit immediate shifted left by 12 bits for ADD/SUB instructions
        return isValidFor12BitImm() ||
               (value >= 0 && value <= 0xFFF000L && (value & 0xFFF) == 0);
    }

    public boolean isValidForLogicalImm() {
        // Logical immediate encoding is complex - simplified check
        // Full implementation would require bitmask immediate encoding analysis
        return value != 0 && value != -1L;
    }

    public boolean isValidFor16BitImm() {
        // 16-bit immediate for MOV/MOVK instructions
        return value >= 0 && value <= 0xFFFF;
    }

    @Override
    public String toString() {
        return "#" + value;
    }
}
