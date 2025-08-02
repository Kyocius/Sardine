package Backend.Arm.Operand;

import java.util.LinkedHashMap;

/**
 * AArch64 SIMD and Floating-Point Register implementation
 * Manages both single-precision (s0-s31), double-precision (d0-d31),
 * and vector registers (v0-v31) with different element sizes
 */
public class ArmFPUReg extends ArmPhyReg {
    private static final LinkedHashMap<Integer, ArmFPUReg> armFPURegs = new LinkedHashMap<>();

    static {
        // AArch64 has 32 SIMD and floating-point registers v0-v31
        // Default to double-precision (d0-d31) for backward compatibility
        for (int i = 0; i <= 31; i++) {
            armFPURegs.put(i, new ArmFPUReg(i, "d" + i, 64));
        }
    }

    // 每个FPU寄存器的属性和方法
    private final int index;
    private final String name;
    private final int bitWidth; // 32, 64, or 128 bits

    public ArmFPUReg(int index, String name, int bitWidth) {
        this.index = index;
        this.name = name;
        this.bitWidth = bitWidth;
    }

    // Legacy constructor for backward compatibility
    public ArmFPUReg(int index, String name) {
        this(index, name, 64); // Default to 64-bit double precision
    }

    @Override
    public boolean canBeReorder() {
        // AArch64 AAPCS64: d8-d15 are callee-saved registers
        // v0-v7: argument/result registers (caller-saved)
        // v8-v15: callee-saved registers (lower 64 bits only)
        // v16-v31: temporary registers (caller-saved)
        return !(index >= 8 && index <= 15);
    }

    public static LinkedHashMap<Integer, ArmFPUReg> getAllFPURegs() {
        return armFPURegs;
    }

    public static ArmFPUReg getArmFloatReg(int index) {
        return armFPURegs.get(index);
    }

    public static ArmFPUReg getArmFArgReg(int argIndex) {
        assert argIndex < 8 : "AArch64 only has 8 floating-point argument registers d0-d7, requested: " + argIndex;
        return getArmFloatReg(argIndex);
    }

    public static ArmFPUReg getArmFPURetValueReg() {
        return armFPURegs.get(0); // d0 is the floating-point return value register
    }

    // Check if this is a callee-saved register
    public boolean isCalleeSaved() {
        return index >= 8 && index <= 15; // d8-d15 are callee-saved in AArch64
    }

    // Check if this is an argument register
    public boolean isArgumentReg() {
        return index >= 0 && index <= 7; // d0-d7 are argument registers
    }

    /**
     * Get single-precision variant (s0-s31)
     */
    public ArmFPUReg toSinglePrecision() {
        return new ArmFPUReg(index, "s" + index, 32);
    }

    /**
     * Get double-precision variant (d0-d31)
     */
    public ArmFPUReg toDoublePrecision() {
        return new ArmFPUReg(index, "d" + index, 64);
    }

    /**
     * Get vector register variant (v0-v31)
     */
    public ArmFPUReg toVectorReg() {
        return new ArmFPUReg(index, "v" + index, 128);
    }

    public boolean isSinglePrecision() {
        return bitWidth == 32;
    }

    public boolean isDoublePrecision() {
        return bitWidth == 64;
    }

    public boolean isVectorReg() {
        return bitWidth == 128;
    }

    public int getBitWidth() {
        return bitWidth;
    }

    public int getIndex() {
        return this.index;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
