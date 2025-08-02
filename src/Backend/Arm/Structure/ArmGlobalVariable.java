package Backend.Arm.Structure;

import Backend.Arm.Operand.ArmLabel;

import java.util.ArrayList;

public class ArmGlobalVariable extends ArmLabel {
    private final boolean isInit;
    private final int size;
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
        for (var armGlobalElement : values) {
            sb.append(armGlobalElement);
        }
        return sb.toString();
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(".globl\t").append(getName()).append("\n");
        sb.append(getName()).append(":\n");
        if(isInit) {
            for(var armGlobalValue: values) {
                sb.append(armGlobalValue);
            }
        } else{
            sb.append("\t.zero\t").append(size).append("\n");
        }
        return sb.toString();
    }
}
