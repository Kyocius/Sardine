package Backend.Arm.Operand;

import java.util.LinkedHashMap;

public class ArmCPUReg extends ArmPhyReg {
    private static final LinkedHashMap<Integer, String> ArmIntRegNames = new LinkedHashMap<>();
    static {
        ArmIntRegNames.put(0, "r0");
        ArmIntRegNames.put(1, "r1");
        ArmIntRegNames.put(2, "r2");
        ArmIntRegNames.put(3, "r3");
        ArmIntRegNames.put(4, "r4");
        ArmIntRegNames.put(5, "r5");
        ArmIntRegNames.put(6, "r6");
        ArmIntRegNames.put(7, "r7");
        ArmIntRegNames.put(8, "r8");
        ArmIntRegNames.put(9, "r9");
        ArmIntRegNames.put(10, "r10");
        ArmIntRegNames.put(11, "r11");
        ArmIntRegNames.put(12, "r12");
        ArmIntRegNames.put(13, "sp");
        ArmIntRegNames.put(14, "lr");
        ArmIntRegNames.put(15, "pc");
        ArmIntRegNames.put(16, "cspr");
    }

    private static LinkedHashMap<Integer, ArmCPUReg> armCPURegs = new LinkedHashMap<>();
    static {
        for (int i = 0; i <= 16; i++) {
            armCPURegs.put(i, new ArmCPUReg(i, ArmIntRegNames.get(i)));
        }
    }

    public boolean canBeReorder(){
        //TODO: need to revise
        if(index == 1 || (index >= 10 && index <= 17))return false;
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
        return armCPURegs.get(14);
    }

    public static ArmCPUReg getArmCPURetValueReg() {
        return armCPURegs.get(0);
    }

    public static ArmCPUReg getArmSpReg() {
        return armCPURegs.get(13);
    }

    public static ArmCPUReg getArmArgReg(int argIntIndex) {
        assert argIntIndex < 4;
        return armCPURegs.get(argIntIndex);
    }

    public static ArmCPUReg getPCReg() {
        return armCPURegs.get(15);
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
