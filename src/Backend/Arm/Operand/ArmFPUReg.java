package Backend.Arm.Operand;

import java.util.LinkedHashMap;

public class ArmFPUReg extends ArmPhyReg {
    private static LinkedHashMap<Integer, ArmFPUReg> armFPURegs = new LinkedHashMap<>();
    static {
        // ARMv8-A has 32 SIMD and floating-point registers d0-d31
        for (int i = 0; i <= 31; i++) {
            armFPURegs.put(i, new ArmFPUReg(i, "d" + i));
        }
    }

    @Override
    public boolean canBeReorder() {
        // ARMv8-A: d8-d15 are callee-saved registers
        if(index >= 8 && index <= 15){
            return false;
        }
        return true;
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

    // 每个CPU寄存器的属性和方法
    private int index;
    private String name;

    public ArmFPUReg(int index, String name) {
        this.index = index;
        this.name = name;
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
