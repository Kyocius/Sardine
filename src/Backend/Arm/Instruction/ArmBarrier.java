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
        sy,     // Full system
        st,     // Store
        ld,     // Load
        ish,    // Inner shareable
        ishst,  // Inner shareable store
        ishld,  // Inner shareable load
        nsh,    // Non-shareable
        nshst,  // Non-shareable store
        nshld,  // Non-shareable load
        osh,    // Outer shareable
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
        if (type == BarrierType.isb) {
            return "isb"; // ISB doesn't take options in most cases
        } else {
            return type.toString() + "\t" + option.toString();
        }
    }
}
