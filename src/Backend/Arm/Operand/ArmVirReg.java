package Backend.Arm.Operand;

import Backend.Arm.Structure.ArmFunction;
import Backend.Riscv.Component.RiscvFunction;
import Backend.Riscv.Operand.RiscvVirReg;

public class ArmVirReg extends ArmReg {
    public enum RegType {
        intType,
        floatType
    }

    private String name;
    public ArmVirReg.RegType regType;

    public ArmVirReg(int index, ArmVirReg.RegType regType, ArmFunction armFunction) {
        this.name = regType == RegType.intType? "%int" + index: "%float" + index;
        this.regType = regType;
        armFunction.addVirReg(this);
    }

    @Override
    public String toString() {
        return name;
    }
}
