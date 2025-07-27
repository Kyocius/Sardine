package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvReg;

import java.util.ArrayList;
import java.util.Collections;

public class RiscvCvt extends RiscvInstruction {
    public final boolean ToFloat;

    public RiscvCvt(RiscvReg srcReg, boolean toFloat, RiscvReg defReg) {
        super(new ArrayList<>(Collections.singletonList(srcReg)), defReg);
        ToFloat = toFloat;
    }

    @Override
    public String toString() {
        if(ToFloat){
            return "fcvt.s.w" + "\t" + getDefReg() + "," + getOperands().get(0);
        }
        else{
            return "fcvt.w.s" + "\t"+ getDefReg() + "," + getOperands().get(0) + "," + "rtz";
        }
    }
}
