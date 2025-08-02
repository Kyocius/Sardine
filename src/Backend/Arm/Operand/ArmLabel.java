package Backend.Arm.Operand;

/**
 * AArch64 Label Operand
 * Represents symbolic references (functions, global variables) in AArch64 assembly
 * Uses ADRP/ADD instruction pairs for 64-bit address computation
 */
public class ArmLabel extends ArmImm {
    private final String name;  // Made final as suggested by compiler

    /**
     * Constructor for global variables and functions
     * @param name The symbol name
     */
    public ArmLabel(String name) {
        super();
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    /**
     * Generate ADRP-compatible page address reference for AArch64
     * ADRP loads the page address (bits [63:12]) of the symbol
     */
    public ArmLabel page() {
        return new ArmLabel(":pg_hi21:" + name);
    }

    /**
     * Generate ADD-compatible page offset reference for AArch64
     * Used with ADD instruction to get the final address within the page
     */
    public ArmLabel pageoff() {
        return new ArmLabel(":lo12:" + name);
    }

    /**
     * Legacy ARMv7 lower 16 bits - kept for backward compatibility
     * @deprecated Use page() and pageoff() for AArch64
     */
    @Deprecated
    public ArmLabel lo() {
        return new ArmLabel(":lower16:" + name);
    }

    /**
     * Legacy ARMv7 upper 16 bits - kept for backward compatibility
     * @deprecated Use page() and pageoff() for AArch64
     */
    @Deprecated
    public ArmLabel hi() {
        return new ArmLabel(":upper16:" + name);
    }

    @Override
    public String toString() {
        return name;
    }

    public String printName() {
        return name + ":\n";
    }
}
