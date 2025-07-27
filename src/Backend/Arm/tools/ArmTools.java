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
        return Math.abs(offset) <= 1020 && Math.abs(offset) >= 0 && offset % 4 == 0;
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
