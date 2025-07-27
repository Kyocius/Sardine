package Backend.Arm.Operand;

import java.util.LinkedHashMap;

public class ArmCPUReg extends ArmPhyReg {
    private static final LinkedHashMap<Integer, String> ArmIntRegNames = new LinkedHashMap<>();
    static {
        // ARMv8-A 64-bit general purpose registers
        ArmIntRegNames.put(0, "x0");
        ArmIntRegNames.put(1, "x1");
        ArmIntRegNames.put(2, "x2");
        ArmIntRegNames.put(3, "x3");
        ArmIntRegNames.put(4, "x4");
        ArmIntRegNames.put(5, "x5");
        ArmIntRegNames.put(6, "x6");
        ArmIntRegNames.put(7, "x7");
        ArmIntRegNames.put(8, "x8");
        ArmIntRegNames.put(9, "x9");
        ArmIntRegNames.put(10, "x10");
        ArmIntRegNames.put(11, "x11");
        ArmIntRegNames.put(12, "x12");
        ArmIntRegNames.put(13, "x13");
        ArmIntRegNames.put(14, "x14");
        ArmIntRegNames.put(15, "x15");
        ArmIntRegNames.put(16, "x16");
        ArmIntRegNames.put(17, "x17");
        ArmIntRegNames.put(18, "x18");
        ArmIntRegNames.put(19, "x19");
        ArmIntRegNames.put(20, "x20");
        ArmIntRegNames.put(21, "x21");
        ArmIntRegNames.put(22, "x22");
        ArmIntRegNames.put(23, "x23");
        ArmIntRegNames.put(24, "x24");
        ArmIntRegNames.put(25, "x25");
        ArmIntRegNames.put(26, "x26");
        ArmIntRegNames.put(27, "x27");
        ArmIntRegNames.put(28, "x28");
        ArmIntRegNames.put(29, "x29"); // Frame pointer
        ArmIntRegNames.put(30, "x30"); // Link register
        ArmIntRegNames.put(31, "sp");  // Stack pointer
    }

    private static LinkedHashMap<Integer, ArmCPUReg> armCPURegs = new LinkedHashMap<>();
    static {
        for (int i = 0; i <= 31; i++) {
            armCPURegs.put(i, new ArmCPUReg(i, ArmIntRegNames.get(i)));
        }
    }

    public boolean canBeReorder(){
        // ARMv8-A preserved registers: x19-x28, x29 (FP), x30 (LR), x31 (SP)
        if(index == 1 || (index >= 19 && index <= 31)) return false;
        return true;
    }

    public static LinkedHashMap<Integer, ArmCPUReg> getAllCPURegs() {
        return armCPURegs;
    }

    public static ArmCPUReg getArmCPUReg(int index) {
        return armCPURegs.get(index);
    }

    private final int index;
    private final String name;

    public ArmCPUReg(int index, String name) {
        this.index = index;
        this.name = name;
    }

    public static ArmCPUReg getArmRetReg() {
        return armCPURegs.get(30); // x30 is the link register in ARMv8-A
    }

    public static ArmCPUReg getArmCPURetValueReg() {
        return armCPURegs.get(0); // x0 is the return value register
    }

    public static ArmCPUReg getArmSpReg() {
        return armCPURegs.get(31); // sp (x31)
    }

    public static ArmCPUReg getArmArgReg(int argIntIndex) {
        assert argIntIndex < 8; // ARMv8-A has 8 argument registers x0-x7
        return armCPURegs.get(argIntIndex);
    }

    public static ArmCPUReg getPCReg() {
        // PC is not directly accessible in ARMv8-A like in ARMv7
        // Return x30 (LR) as it's commonly used for PC-related operations
        return armCPURegs.get(30);
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
