package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmReg;

import java.util.ArrayList;
import java.util.Collections;

public class ArmConvMv extends ArmInstruction {
    //浮点与整数寄存器之间的Move
    public ArmConvMv(ArmReg from, ArmReg toReg) {
        super(toReg, new ArrayList<>(Collections.singletonList(from)));
    }

    @Override
    public String toString() {
        // AArch64 fmov instruction format: fmov dest, src
        // Supports move between general-purpose and SIMD&FP registers
        return "fmov\t" + getDefReg() + ",\t" + getOperands().getFirst();
    }
}
