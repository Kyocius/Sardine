package Backend.Riscv.Operand;

import java.util.LinkedHashMap;

public class RiscvCPUReg extends RiscvPhyReg {
    // 静态的32个CPU寄存器
    private static LinkedHashMap<Integer, String> CPURegNames = new LinkedHashMap<>();
    static {
        CPURegNames.put(0, "zero");
        CPURegNames.put(1, "ra");
        CPURegNames.put(2, "sp");
        CPURegNames.put(3, "gp");
        CPURegNames.put(4, "tp");
        CPURegNames.put(5, "t0");
        CPURegNames.put(6, "t1");
        CPURegNames.put(7, "t2");
        CPURegNames.put(8, "s0");
        CPURegNames.put(9, "s1");
        CPURegNames.put(10, "a0");
        CPURegNames.put(11, "a1");
        CPURegNames.put(12, "a2");
        CPURegNames.put(13, "a3");
        CPURegNames.put(14, "a4");
        CPURegNames.put(15, "a5");
        CPURegNames.put(16, "a6");
        CPURegNames.put(17, "a7");
        CPURegNames.put(18, "s2");
        CPURegNames.put(19, "s3");
        CPURegNames.put(20, "s4");
        CPURegNames.put(21, "s5");
        CPURegNames.put(22, "s6");
        CPURegNames.put(23, "s7");
        CPURegNames.put(24, "s8");
        CPURegNames.put(25, "s9");
        CPURegNames.put(26, "s10");
        CPURegNames.put(27, "s11");
        CPURegNames.put(28, "t3");
        CPURegNames.put(29, "t4");
        CPURegNames.put(30, "t5");
        CPURegNames.put(31, "t6");
    }

    private static LinkedHashMap<Integer, RiscvCPUReg> riscvCPURegs = new LinkedHashMap<>();
    static {
        for (int i = 0; i <= 31; i++) {
            riscvCPURegs.put(i, new RiscvCPUReg(i, CPURegNames.get(i)));
        }
    }

    public boolean canBeReorder(){
        if(index == 1 || index == 2 || (index >= 10 && index <= 17))return false;
        return true;
    }


    public static LinkedHashMap<Integer, RiscvCPUReg> getAllCPURegs() {
        return riscvCPURegs;
    }

    public static RiscvCPUReg getRiscvCPUReg(int index) {
        return riscvCPURegs.get(index);
    }

    // 每个CPU寄存器的属性和方法
    private int index;
    private String name;

    public RiscvCPUReg(int index, String name) {
        this.index = index;
        this.name = name;
    }

    public static RiscvCPUReg getRiscvRetReg() {
        return riscvCPURegs.get(10);
    }

    public static RiscvCPUReg getRiscvRaReg() {
        return riscvCPURegs.get(1);
    }

    public static RiscvCPUReg getRiscvSpReg() {
        return riscvCPURegs.get(2);
    }

    public static RiscvCPUReg getZeroReg() {
        return riscvCPURegs.get(0);
    }

    public static RiscvCPUReg getRiscvArgReg(int argIntIndex) {
        return riscvCPURegs.get(10 + argIntIndex);
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
