package Backend.Riscv.Operand;

import Backend.Riscv.Component.RiscvFunction;

public class RiscvVirReg extends RiscvReg {
    public enum RegType {
        intType,
        floatType
    }

    private String name;
    public RegType regType;

    public RiscvVirReg(int index, RegType regType, RiscvFunction rvFunction) {
        this.name = regType == RegType.intType? "%int" + index: "%float" + index;
        this.regType = regType;
        rvFunction.addVirReg(this);
    }

    @Override
    public String toString() {
        return name;
    }
}
