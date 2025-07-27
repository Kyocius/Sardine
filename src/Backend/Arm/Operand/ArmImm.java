package Backend.Arm.Operand;

public class ArmImm extends ArmOperand{
    private final int value;

    public ArmImm(int number) {
        super();
        this.value = number;
    }

    public ArmImm() {
        super();
        this.value = 1;
    }

    public int getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return "#" + value;
    }
}
