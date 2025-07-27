package Backend.Arm.Structure;

import Backend.Arm.Operand.ArmLabel;
import Backend.Riscv.Component.RiscvGlobalValue;

import java.util.ArrayList;

public class ArmGlobalVariable extends ArmLabel {
    private boolean isInit;
    private int size;
    private final ArrayList<ArmGlobalValue> values;

    public ArmGlobalVariable(String name, boolean isInit, int size,
                               ArrayList<ArmGlobalValue> values) {
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
