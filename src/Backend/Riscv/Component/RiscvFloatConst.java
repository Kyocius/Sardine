package Backend.Riscv.Component;

import Backend.Riscv.Operand.RiscvLabel;

public class RiscvFloatConst extends RiscvLabel {
    private double val;

    public RiscvFloatConst(String name, double val) {
        super(name);
        this.val = val;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(printName());
        sb.append("\t.word\t" + Float.floatToIntBits((float) val) + "\n");
        return sb.toString();
    }
}
