package Backend.Riscv.Operand;

public class RiscvImm extends RiscvOperand {
    private final int value;

    public RiscvImm(int number) {
        super();
        this.value = number;
    }

    public RiscvImm() {
        super();
        this.value = 1;
    }

    public int getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return Integer.toString(this.value);
    }
}
