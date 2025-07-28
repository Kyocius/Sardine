package Backend.Arm.tools;

import Backend.Arm.Instruction.ArmBranch;

import static java.lang.Math.abs;

public class ArmTools {
    public static boolean isArmImmCanBeEncoded(int imme) {
        for (int shift = 0; shift <= 32; shift += 2) {
            if ((((imme << shift) | (imme >>> (32 - shift))) & ~0xff) == 0) {
                return true;
            }
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
        return offset >= -256 && offset <= 255;
    }

    // ARMv8-A: Check if offset is valid for ldr/str immediate (scaled)
    public static boolean isLegalLoadStoreScaledImm(int offset, int elementSize) {
        int maxOffset = 4095 * elementSize;
        return offset >= 0 && offset <= maxOffset && (offset % elementSize == 0);
    }

    // ARMv8-A: Check if immediate can be encoded as logical immediate (for AND, ORR, EOR)
    public static boolean isLogicalImmediate(long value) {
        // ARMv8-A logical immediates must be a repetition of a pattern
        // This is a simplified check - full implementation would be more complex
        if (value == 0 || value == -1) return false;
        
        // Check for common patterns
        long[] commonPatterns = {
            0x00FF00FF00FF00FFL, 0x0F0F0F0F0F0F0F0FL, 0x3333333333333333L,
            0x5555555555555555L, 0x7777777777777777L, 0x7FFFFFFFFFFFFFFFL,
            0xFFFE, 0xFFFC, 0xFFF8, 0xFFF0, 0xFFE0, 0xFFC0, 0xFF80, 0xFF00
        };
        
        for (long pattern : commonPatterns) {
            if (value == pattern) return true;
        }
        
        return false;
    }

    public enum CondType {
        eq,  // ==
        ne,  // !=
        lt,  // <  s->signed 有符号
        le,  // <=
        gt,  // >
        ge,   // >=
        nope
    }

    public static String getCondString(ArmTools.CondType type) {
        switch (type) {
            case eq -> {
                return "eq";
            }
            case lt -> {
                return "lt";
            }
            case le -> {
                return "le";
            }
            case gt -> {
                return "gt";
            }
            case ge -> {
                return "ge";
            }
            case ne -> {
                return "ne";
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
