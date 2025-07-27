package Backend.Arm.Operand;

public class ArmLabel extends ArmImm {
    private String name;

    // 全局变量 + 函数
    public ArmLabel(String name) {
        super();
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public ArmLabel lo() {
        return new ArmLabel(":lower16:" + name);
    }

    public ArmLabel hi() {
        return new ArmLabel(":upper16:" + name);
    }

    @Override
    public String toString() {
        return name;
    }

    public String printName() {
        return name + ":\n";
    }
}
