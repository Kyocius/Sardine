package Backend.Riscv.Component;

import Backend.Riscv.Operand.RiscvLabel;

import java.util.ArrayList;

public class RiscvGlobalVariable extends RiscvLabel {
    private boolean isInit;
    private int size;
    private final ArrayList<RiscvGlobalValue> values;

    public RiscvGlobalVariable(String name, boolean isInit, int size,
                               ArrayList<RiscvGlobalValue> values) {
        super(name);
        this.isInit = isInit;
        this.size = size;
        this.values = values;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (var riscvGlobalElement : values) {
            sb.append(riscvGlobalElement);
        }
        return sb.toString();
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(".global\t").append(getName()).append("\n");
        sb.append(getName()).append(":\n");
        if(isInit) {
            for(var riscvGlobalValue: values) {
                sb.append(riscvGlobalValue);
            }
        } else{
            sb.append("\t.zero\t").append(size).append("\n");
        }
        return sb.toString();
    }
}
