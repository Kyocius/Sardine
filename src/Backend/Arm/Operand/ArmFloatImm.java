package Backend.Arm.Operand;

import java.text.DecimalFormat;

public class ArmFloatImm extends ArmOperand{
    private final float value;

    public ArmFloatImm(float number) {
        super();
        this.value = number;
    }

    public float getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        String s = new DecimalFormat("0.0#############E0").
                format(value).replace("E", "e");
        return "#" + s;
    }
}
