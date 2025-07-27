package Backend.Riscv.Operand;

import java.util.LinkedHashMap;

public class RiscvFPUReg extends RiscvPhyReg {
    // 静态的32个FPU寄存器
    private static LinkedHashMap<Integer, String> FPURegNames = new LinkedHashMap<>();
    static {
        FPURegNames.put(0, "ft0");
        FPURegNames.put(1, "ft1");
        FPURegNames.put(2, "ft2");
        FPURegNames.put(3, "ft3");
        FPURegNames.put(4, "ft4");
        FPURegNames.put(5, "ft5");
        FPURegNames.put(6, "ft6");
        FPURegNames.put(7, "ft7");
        FPURegNames.put(8, "fs0");
        FPURegNames.put(9, "fs1");
        FPURegNames.put(10, "fa0");
        FPURegNames.put(11, "fa1");
        FPURegNames.put(12, "fa2");
        FPURegNames.put(13, "fa3");
        FPURegNames.put(14, "fa4");
        FPURegNames.put(15, "fa5");
        FPURegNames.put(16, "fa6");
        FPURegNames.put(17, "fa7");
        FPURegNames.put(18, "fs2");
        FPURegNames.put(19, "fs3");
        FPURegNames.put(20, "fs4");
        FPURegNames.put(21, "fs5");
        FPURegNames.put(22, "fs6");
        FPURegNames.put(23, "fs7");
        FPURegNames.put(24, "fs8");
        FPURegNames.put(25, "fs9");
        FPURegNames.put(26, "fs10");
        FPURegNames.put(27, "fs11");
        FPURegNames.put(28, "ft8");
        FPURegNames.put(29, "ft9");
        FPURegNames.put(30, "ft10");
        FPURegNames.put(31, "ft11");
    }

    private static LinkedHashMap<Integer, RiscvFPUReg> riscvFPURegs = new LinkedHashMap<>();
    static {
        for (int i = 0; i <= 31; i++) {
            riscvFPURegs.put(i, new RiscvFPUReg(i, FPURegNames.get(i)));
        }
    }

    @Override
    public boolean canBeReorder() {
        if(index >= 10 && index <= 16){
            return false;
        }
        return true;
    }

    public static LinkedHashMap<Integer, RiscvFPUReg> getAllFPURegs() {
        return riscvFPURegs;
    }

    public static RiscvFPUReg getRiscvRetReg() {
        return getRiscvFPUReg(10);
    }

    public static RiscvFPUReg getRiscvFPUReg(int index) {
        return riscvFPURegs.get(index);
    }

    public static RiscvFPUReg getRiscvFArgReg(int argIndex) {
        return getRiscvFPUReg(10 + argIndex);
    }

    // 每个CPU寄存器的属性和方法
    private int index;
    private String name;

    public RiscvFPUReg(int index, String name) {
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
