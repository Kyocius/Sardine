package Backend.Arm.Structure;

public class ArmGlobalZero extends ArmGlobalValue{
    private int size;
    public ArmGlobalZero(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }

    public void addSize(int size) {
        this.size += size;
    }

    @Override
    public String toString() {
        return "\t.zero\t" + size + "\n";
    }
}
