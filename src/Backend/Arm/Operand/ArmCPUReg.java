package Backend.Arm.Operand;

import java.util.LinkedHashMap;

/**
 * AArch64 CPU Register implementation
 * Manages both 64-bit (x0-x31, xzr) and 32-bit (w0-w31, wzr) general purpose registers
 */
public class ArmCPUReg extends ArmPhyReg {
    private static final LinkedHashMap<Integer, String> ArmIntRegNames = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, String> ArmIntRegNames32 = new LinkedHashMap<>();
    
    static {
        // ARMv8-A 64-bit general purpose registers (x0-x31)
        for (int i = 0; i <= 30; i++) {
            ArmIntRegNames.put(i, "x" + i);
        }
        ArmIntRegNames.put(31, "sp");  // Stack pointer
        
        // Add special zero register (conceptual index 32 for xzr)
        ArmIntRegNames.put(32, "xzr"); // Zero register (64-bit)

        // ARMv8-A 32-bit general purpose registers (w0-w31)
        for (int i = 0; i <= 30; i++) {
            ArmIntRegNames32.put(i, "w" + i);
        }
        ArmIntRegNames32.put(31, "wsp"); // 32-bit stack pointer
        ArmIntRegNames32.put(32, "wzr"); // Zero register (32-bit)
    }

    private static final LinkedHashMap<Integer, ArmCPUReg> armCPURegs = new LinkedHashMap<>();
    static {
        // Create regular registers (0-31)
        for (int i = 0; i <= 31; i++) {
            armCPURegs.put(i, new ArmCPUReg(i, ArmIntRegNames.get(i)));
        }
        // Create zero register (index 32)
        armCPURegs.put(32, new ArmCPUReg(32, ArmIntRegNames.get(32)));
    }

    private final int index;
    private final String name;
    private final boolean is32Bit;

    public ArmCPUReg(int index, String name) {
        this.index = index;
        this.name = name;
        this.is32Bit = name.startsWith("w");
    }

    // Create 32-bit version of this register
    public ArmCPUReg to32Bit() {
        return new ArmCPUReg(index, ArmIntRegNames32.get(index));
    }

    // Create 64-bit version of this register  
    public ArmCPUReg to64Bit() {
        return new ArmCPUReg(index, ArmIntRegNames.get(index));
    }

    public boolean is32Bit() {
        return is32Bit;
    }

    public boolean isZeroReg() {
        return index == 32; // xzr/wzr
    }

    public boolean canBeReorder(){
        // AArch64 AAPCS64 calling convention:
        // - x0-x7: argument/result registers (can be reordered with care)
        // - x8: indirect result location register
        // - x9-x15: temporary registers (can be reordered)
        // - x16-x17: intra-procedure-call temporary registers
        // - x18: platform register (may be used by OS)
        // - x19-x28: callee-saved registers (should not be reordered)
        // - x29: frame pointer (should not be reordered)
        // - x30: link register (should not be reordered)
        // - x31: stack pointer (should not be reordered)
        // - x32: zero register (should not be reordered)

        // Preserved registers, special registers, and platform register cannot be reordered
        return !(index >= 19 && index <= 32) && index != 18;
    }

    public static LinkedHashMap<Integer, ArmCPUReg> getAllCPURegs() {
        return armCPURegs;
    }

    public static ArmCPUReg getArmCPUReg(int index) {
        return armCPURegs.get(index);
    }

    public static ArmCPUReg getArmRetReg() {
        return armCPURegs.get(30); // x30 is the link register in AArch64
    }

    public static ArmCPUReg getArmCPURetValueReg() {
        return armCPURegs.get(0); // x0 is the return value register
    }

    public static ArmCPUReg getArmSpReg() {
        return armCPURegs.get(31); // sp (x31)
    }

    public static ArmCPUReg getArmFpReg() {
        return armCPURegs.get(29); // x29 is the frame pointer in AArch64
    }

    public static ArmCPUReg getArmArgReg(int argIntIndex) {
        assert argIntIndex < 8 : "AArch64 AAPCS64 has 8 argument registers x0-x7, requested: " + argIntIndex;
        return armCPURegs.get(argIntIndex);
    }

    /**
     * Get zero register (xzr/wzr)
     * @param is32Bit true for wzr, false for xzr
     * @return zero register
     */
    public static ArmCPUReg getZeroReg(boolean is32Bit) {
        ArmCPUReg zeroReg = armCPURegs.get(32);
        return is32Bit ? zeroReg.to32Bit() : zeroReg;
    }

    /**
     * AArch64 does not have a directly accessible PC register like ARMv7
     * This method should not be used in AArch64 code generation
     * @deprecated Use ADRP/ADD for address calculations instead
     */
    @Deprecated
    public static ArmCPUReg getPCReg() {
        throw new UnsupportedOperationException(
            "PC register is not directly accessible in AArch64. Use ADRP/ADD for address calculations."
        );
    }

    // Get temporary register for address calculations (AArch64 convention)
    public static ArmCPUReg getTempReg() {
        return armCPURegs.get(16); // x16 is intra-procedure-call temporary in AArch64
    }

    // Get second temporary register if needed
    public static ArmCPUReg getTempReg2() {
        return armCPURegs.get(17); // x17 is second intra-procedure-call temporary
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
