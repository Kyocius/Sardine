package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmImm;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Arrays;

public class ArmSmull extends ArmInstruction {
    private ArrayList<ArmReg> defRegs = new ArrayList<>();

    public ArmSmull(ArmReg defReg1, ArmReg defReg2, ArmReg reg1, ArmReg reg2) {
        super(defReg1, new ArrayList<>(Arrays.asList(reg1, reg2)));
        defRegs.add(defReg1);
        defRegs.add(defReg2);
    }

    @Override
    public String toString() {
        return "@\n\tsmull\t" + defRegs.get(0) + ",\t" + defRegs.get(1) +  ",\t"
                +  getOperands().get(0) +  ", " + getOperands().get(1);
    }
}
