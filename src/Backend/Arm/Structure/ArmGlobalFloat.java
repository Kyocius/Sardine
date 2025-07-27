package Backend.Arm.Structure;

public class ArmGlobalFloat extends ArmGlobalValue {
    private final float value;

    public ArmGlobalFloat(float value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "\t.word\t" + Float.floatToIntBits(value) + "\n";
    }
}
