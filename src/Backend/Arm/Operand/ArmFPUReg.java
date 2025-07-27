package Backend.Arm.Operand;

import java.util.LinkedHashMap;

public class ArmFPUReg extends ArmPhyReg {
    private static LinkedHashMap<Integer, ArmFPUReg> armFPURegs = new LinkedHashMap<>();
    static {
        for (int i = 0; i <= 31; i++) {
            armFPURegs.put(i, new ArmFPUReg(i, "s" + i));
        }
    }

    @Override
    public boolean canBeReorder() {
        //TODO
        if(index >= 10 && index <= 16){
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
        assert argIndex < 4;
        return getArmFloatReg(argIndex);
    }

    public static ArmFPUReg getArmFPURetValueReg() {
        return armFPURegs.get(0);
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
