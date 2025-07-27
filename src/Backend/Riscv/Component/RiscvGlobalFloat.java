package Backend.Riscv.Component;

public class RiscvGlobalFloat extends RiscvGlobalValue {
    private final float value;

    public RiscvGlobalFloat(float value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "\t.word\t" + Float.floatToIntBits(value) + "\n";
    }
}
