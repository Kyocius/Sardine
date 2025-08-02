package Backend.Arm.Operand;

import Backend.Arm.Structure.ArmFunction;

/**
 * AArch64 Virtual Register
 * Represents virtual registers before register allocation
 * Supports both integer (X/W) and floating-point (D/S/V) register types
 */
public class ArmVirReg extends ArmReg {
    public enum RegType {
        intType,    // General-purpose registers (x0-x31, w0-w31)
        floatType   // SIMD&FP registers (d0-d31, s0-s31, v0-v31)
    }

    private final String name;
    public final RegType regType;
    private final int index;

    public ArmVirReg(int index, ArmVirReg.RegType regType, ArmFunction armFunction) {
        super();
        this.index = index;
        this.name = generateRegName(index, regType);
        this.regType = regType;
        armFunction.addVirReg(this);
    }

    /**
     * Generate AArch64-style virtual register name
     * @param index Register index
     * @param regType Register type (int or float)
     * @return Virtual register name
     */
    private static String generateRegName(int index, RegType regType) {
        return regType == RegType.intType ? "%int" + index : "%float" + index;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String toString() {
        return name;
    }
}
