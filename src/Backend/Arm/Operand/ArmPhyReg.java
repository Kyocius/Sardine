package Backend.Arm.Operand;

public class ArmPhyReg extends ArmReg{
    public boolean canBeReorder(){
        return true;
    }
}
