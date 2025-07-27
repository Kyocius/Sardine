package Backend.Riscv.Operand;

public class RiscvConstFloat extends RiscvLabel {
    private double val;

    public RiscvConstFloat(String name, double val) {
        super(name);
        this.val = val;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.printName());
        sb.append("\t.word\t" + Float.floatToIntBits((float) val) + "\n");
        return sb.toString();
    }
}
