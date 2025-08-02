package Backend.Arm.tools;

import static java.lang.Math.abs;

public class ArmTools {
    public static boolean isArmImmCanBeEncoded(int imme) {
        // ARM64 immediate encoding rules:
        // 1. For arithmetic instructions (add/sub): 12-bit unsigned immediate (0-4095)
        //    optionally shifted left by 12 bits (giving range 0-16773120 in steps of 4096)
        // 2. For logical instructions: more complex pattern-based encoding
        
        // Check if it's a simple 12-bit immediate
        if (imme >= 0 && imme <= 4095) {
            return true;
        }
        
        // Check if it's a 12-bit immediate shifted by 12
        if ((imme & 0xFFF) == 0 && (imme >>> 12) <= 4095) {
            return true;
        }
        
        // For negative values, check if the positive value can be encoded
        if (imme < 0) {
            int pos = -imme;
            return (pos >= 0 && pos <= 4095) || ((pos & 0xFFF) == 0 && (pos >>> 12) <= 4095);
        }
        
        return false;
    }

    public static boolean isFloatImmCanBeEncoded(float imm) {
        float eps = 1e-14f;
        float a = imm * 128;
        for (int r = 0; r < 8; ++r) {
            for (int n = 16; n < 32; ++n) {
                if ((abs((n * (1 << (7 - r)) - a)) < eps) ||
                        (abs((n * (1 << (7 - r)) + a)) < eps))
                    return true;
            }
        }
        return false;
    }

    public static boolean isLegalVLoadStoreImm(int offset) {
        // ARMv8-A floating-point load/store: 0 to +32760, multiple of 8
        return offset >= 0 && offset <= 32760 && offset % 8 == 0;
    }

    // ARMv8-A: Check if offset is valid for ldr/str immediate (unscaled)
    public static boolean isLegalLoadStoreImm(int offset) {
        // ARM64 unscaled immediate: -256 to +255
        return offset >= -256 && offset <= 255;
    }

    // ARMv8-A: Check if offset is valid for ldr/str immediate (scaled)
    public static boolean isLegalLoadStoreScaledImm(int offset, int elementSize) {
        // ARM64 scaled immediate: 0 to +4095*elementSize, must be multiple of elementSize
        int maxOffset = 4095 * elementSize;
        return offset >= 0 && offset <= maxOffset && (offset % elementSize == 0);
    }

    // ARMv8-A: Check if immediate can be encoded as logical immediate (for AND, ORR, EOR)
    public static boolean isLogicalImmediate(long value) {
        // ARMv8-A logical immediates must be a repetition of a pattern
        if (value == 0 || value == -1) return false;
        
        // Check for single bit patterns
        if (Long.bitCount(value) == 1) return true;
        
        // Check for patterns that are powers of 2 minus 1
        long temp = value;
        while (temp != 0) {
            if ((temp & 1) == 0) break;
            temp >>= 1;
        }
        if (temp == 0) return true; // All consecutive 1s from LSB
        
        // Check for inverted patterns (consecutive 0s)
        long inverted = ~value;
        if (inverted != 0 && inverted != -1) {
            temp = inverted;
            while (temp != 0) {
                if ((temp & 1) == 0) break;
                temp >>= 1;
            }
            if (temp == 0) return true;
        }
        
        // Check for repeating patterns
        for (int size = 2; size <= 32; size *= 2) {
            long mask = (1L << size) - 1;
            long pattern = value & mask;
            boolean valid = true;
            
            for (int i = size; i < 64; i += size) {
                if (((value >> i) & mask) != pattern) {
                    valid = false;
                    break;
                }
            }
            if (valid && pattern != 0 && pattern != mask) {
                return true;
            }
        }
        
        // Check for common patterns used in practice
        long[] commonPatterns = {
            0x00FF00FF00FF00FFL, 0x0F0F0F0F0F0F0F0FL, 0x3333333333333333L,
            0x5555555555555555L, 0x7777777777777777L, 0x7FFFFFFFFFFFFFFFL,
            0xFFFE, 0xFFFC, 0xFFF8, 0xFFF0, 0xFFE0, 0xFFC0, 0xFF80, 0xFF00,
            0xFE00, 0xFC00, 0xF800, 0xF000, 0xE000, 0xC000, 0x8000,
            0x7FFF, 0x3FFF, 0x1FFF, 0x0FFF, 0x07FF, 0x03FF, 0x01FF,
            0x00FF, 0x007F, 0x003F, 0x001F, 0x000F, 0x0007, 0x0003, 0x0001
        };
        
        for (long pattern : commonPatterns) {
            if (value == pattern) return true;
        }
        
        return false;
    }

    public enum CondType {
        eq,   // ==
        ne,   // !=
        cs,   // carry set (unsigned >=)
        cc,   // carry clear (unsigned <)
        mi,   // minus (negative)
        pl,   // plus (positive or zero)
        vs,   // overflow set
        vc,   // overflow clear
        hi,   // unsigned higher
        ls,   // unsigned lower or same
        ge,   // signed >=
        lt,   // signed <
        gt,   // signed >
        le,   // signed <=
        al,   // always (default)
        nv,   // never (reserved)
        // Aliases
        hs,   // alias for cs (unsigned >=)
        lo,   // alias for cc (unsigned <)
        nope  // no condition
    }

    public static String getCondString(ArmTools.CondType type) {
        switch (type) {
            case eq -> {
                return "eq";
            }
            case ne -> {
                return "ne";
            }
            case cs, hs -> {
                return "cs";
            }
            case cc, lo -> {
                return "cc";
            }
            case mi -> {
                return "mi";
            }
            case pl -> {
                return "pl";
            }
            case vs -> {
                return "vs";
            }
            case vc -> {
                return "vc";
            }
            case hi -> {
                return "hi";
            }
            case ls -> {
                return "ls";
            }
            case ge -> {
                return "ge";
            }
            case lt -> {
                return "lt";
            }
            case gt -> {
                return "gt";
            }
            case le -> {
                return "le";
            }
            case al -> {
                return "al";
            }
            case nv -> {
                return "nv";
            }
            case nope -> {
                return "";
            }
        }
        return null;
    }

    public static ArmTools.CondType getRevCondType(ArmTools.CondType type) {
        switch (type) {
            case eq -> {
                return ArmTools.CondType.ne;
            }
            case lt -> {
                return ArmTools.CondType.ge;
            }
            case le -> {
                return ArmTools.CondType.gt;
            }
            case gt -> {
                return ArmTools.CondType.le;
            }
            case ge -> {
                return ArmTools.CondType.lt;
            }
            case ne -> {
                return ArmTools.CondType.eq;
            }
            case nope -> {
                return CondType.nope;
            }
        }
        return null;
    }

    public static ArmTools.CondType getOnlyRevBigSmallType(ArmTools.CondType type) {
        switch (type) {
            case eq -> {
                return ArmTools.CondType.eq;
            }
            case lt -> {
                return ArmTools.CondType.gt;
            }
            case le -> {
                return ArmTools.CondType.ge;
            }
            case gt -> {
                return ArmTools.CondType.lt;
            }
            case ge -> {
                return ArmTools.CondType.le;
            }
            case ne -> {
                return ArmTools.CondType.ne;
            }
            case nope -> {
                return CondType.nope;
            }
        }
        return null;
    }
}
