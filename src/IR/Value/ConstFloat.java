package IR.Value;

import IR.Type.FloatType;
import IR.Type.IntegerType;

public class ConstFloat extends Const {
    private final float value;
    public ConstFloat(float value) {
        super(String.format("0x%x", Double.doubleToRawLongBits(value)), FloatType.F32);
        this.value = value;
    }

    public float getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConstFloat that = (ConstFloat) o;
        return value == that.value;
    }

    @Override
    public String toString() {
        return "float " + this.value;
    }
}
