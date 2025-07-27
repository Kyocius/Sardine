package Backend.Riscv.Operand;

public class RiscvLabel extends RiscvImm {
    private String name;

    // 全局变量 + 函数
    public RiscvLabel(String name) {
        super();
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public RiscvLabel hi() {
        return new RiscvLabel("%hi(" + name + ")");
    }

    public RiscvLabel lo() {
        return new RiscvLabel("%lo(" + name + ")");
    }

    @Override
    public String toString() {
        return name;
    }

    public String printName() {
        return name + ":\n";
    }
}
