package Backend.Arm.Operand;

import java.text.DecimalFormat;

/**
 * AArch64 floating-point immediate operand
 * Represents floating-point constants in AArch64 assembly
 */
public class ArmFloatImm extends ArmOperand{
    private final double value;  // Changed from float to double for AArch64

    public ArmFloatImm(double number) {  // Changed parameter type
        super();
        this.value = number;
    }

    // Support legacy float constructor for backward compatibility
    public ArmFloatImm(float number) {
        super();
        this.value = number;  // Implicit cast to double
    }

    public double getValue() {  // Changed return type
        return this.value;
    }

    // Legacy method for backward compatibility
    public float getFloatValue() {
        return (float) this.value;
    }

    @Override
    public String toString() {
        // AArch64 floating-point immediate format
        // For FMOV instruction: #<floating-point constant>
        String s = new DecimalFormat("0.0#############E0").
                format(value).replace("E", "e");
        return "#" + s;
    }

    /**
     * Check if this immediate can be encoded in AArch64 FMOV instruction
     * AArch64 FMOV supports a limited set of floating-point immediates
     * @return true if encodable as immediate, false if needs literal pool
     */
    public boolean isEncodableImmediate() {
        // AArch64 FMOV immediate encoding rules:
        // - Must be representable in 8-bit immediate format
        // - Special patterns like 0.0, 1.0, 2.0, etc.

        // Common encodable values
        if (value == 0.0 || value == 1.0 || value == 2.0 ||
            value == -1.0 || value == -2.0) {
            return true;
        }

        // For more complex encoding check, would need full IEEE 754 analysis
        // For now, assume larger values need literal pool
        return Math.abs(value) <= 31.0 && (value == Math.floor(value));
    }
}
