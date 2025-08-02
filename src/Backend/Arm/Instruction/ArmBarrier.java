package Backend.Arm.Instruction;

import java.util.ArrayList;

/**
 * AArch64 memory barrier instructions
 * Essential for multi-core correctness
 */
public class ArmBarrier extends ArmInstruction {
    public enum BarrierType {
        dmb,    // Data Memory Barrier
        dsb,    // Data Synchronization Barrier  
        isb     // Instruction Synchronization Barrier
    }

    public enum BarrierOption {
        sy,     // Full system - strongest barrier
        st,     // Store operations only
        ld,     // Load operations only
        ish,    // Inner shareable domain
        ishst,  // Inner shareable store
        ishld,  // Inner shareable load
        nsh,    // Non-shareable domain
        nshst,  // Non-shareable store
        nshld,  // Non-shareable load
        osh,    // Outer shareable domain
        oshst,  // Outer shareable store
        oshld   // Outer shareable load
    }

    private BarrierType type;
    private BarrierOption option;

    public ArmBarrier(BarrierType type, BarrierOption option) {
        super(null, new ArrayList<>());
        this.type = type;
        this.option = option;
    }

    public ArmBarrier(BarrierType type) {
        this(type, BarrierOption.sy); // Default to full system barrier
    }

    @Override
    public String toString() {
        // ISB can take options but typically uses sy (system-wide)
        if (type == BarrierType.isb && option == BarrierOption.sy) {
            return "isb"; // Common case: ISB with default sy option
        } else {
            return type.toString() + "\t" + option.toString();
        }
    }

    // Getter methods for better encapsulation
    public BarrierType getType() {
        return type;
    }

    public BarrierOption getOption() {
        return option;
    }

    // Static factory methods for common barrier patterns
    public static ArmBarrier fullSystemBarrier() {
        return new ArmBarrier(BarrierType.dmb, BarrierOption.sy);
    }

    public static ArmBarrier instructionBarrier() {
        return new ArmBarrier(BarrierType.isb);
    }

    public static ArmBarrier storeBarrier() {
        return new ArmBarrier(BarrierType.dmb, BarrierOption.st);
    }

    public static ArmBarrier loadBarrier() {
        return new ArmBarrier(BarrierType.dmb, BarrierOption.ld);
    }
}
