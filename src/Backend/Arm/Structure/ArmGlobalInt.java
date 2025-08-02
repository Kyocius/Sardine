package Backend.Arm.Structure;

public class ArmGlobalInt extends ArmGlobalValue {
    private final int value;

    public int getValue() {
        return value;
    }

    public ArmGlobalInt(int value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "\t.4byte\t" + value + "\n";
    }
}
