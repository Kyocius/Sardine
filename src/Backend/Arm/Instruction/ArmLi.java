package Backend.Arm.Instruction;

import Backend.Arm.Operand.*;
import Backend.Arm.tools.ArmTools;

import java.util.ArrayList;
import java.util.Collections;

public class ArmLi extends ArmInstruction {
    private ArmTools.CondType condType;

    public ArmLi(ArmOperand from, ArmReg toReg) {
        super(toReg, new ArrayList<>(Collections.singletonList(from)));
        assert from instanceof ArmImm;
        condType = ArmTools.CondType.nope;
    }

    public ArmLi(ArmOperand from, ArmReg toReg, ArmTools.CondType type) {
        super(toReg, new ArrayList<>(Collections.singletonList(from)));
        assert from instanceof ArmImm;
        condType = type;
    }

//    public enum ArmMovType {
//        mov,
//        movt, // 将16位立即数加载到目标寄存器高位, 保持低位不变
//        mvn,
//        movw // 加载到目标寄存器低位, 并将其高16位清空
//    }

    public ArmTools.CondType getCondType() {
        return condType;
    }

    @Override
    public String toString() {
        if (getOperands().get(0) instanceof ArmLabel) {
            return "movw" + ArmTools.getCondString(condType) + "\t" + getDefReg() + ",\t" + ((ArmLabel) getOperands().get(0)).lo() + "\n\t" +
                    "movt\t" + getDefReg() + ",\t" + ((ArmLabel) getOperands().get(0)).hi();

        } else {
            assert getOperands().get(0) instanceof ArmImm;
            ArmImm imm = (ArmImm) getOperands().get(0);
            if (ArmTools.isArmImmCanBeEncoded(imm.getValue())) {
                return "mov" + ArmTools.getCondString(condType) + "\t"
                        + getDefReg() + ",\t#" + imm.getValue();
            } else if (ArmTools.isArmImmCanBeEncoded(~(imm).getValue())) {
                int oppo = ~imm.getValue();
                return "mvn" + ArmTools.getCondString(condType) + "\t"
                        + getDefReg() + ",\t#" + oppo;
            } else {
                int highBits = (imm.getValue() >>> 16) & 0xffff;
                int lowBits = (imm.getValue()) & 0xffff;
                String res_str = "";
                res_str = res_str + "movw" + ArmTools.getCondString(condType) + "\t"
                        + getDefReg() + ",\t#" + lowBits + "\n";
                if(highBits != 0) {
                    res_str = res_str + "\t" +
                    "movt" + ArmTools.getCondString(condType) + "\t"
                            + getDefReg() + ",\t#" + highBits;
                }
                return res_str;
            }
        }
    }
}
