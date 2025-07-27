package Backend.Riscv.Component;

public class RiscvGlobalInt extends RiscvGlobalValue {
    private final int value;

    public int getValue() {
        return value;
    }

    public RiscvGlobalInt(int value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "\t.word\t" + value + "\n";
    }
}
