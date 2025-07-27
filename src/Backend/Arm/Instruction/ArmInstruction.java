package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;
import Backend.Arm.Structure.ArmBlock;
import Utils.DataStruct.IList;

import java.util.ArrayList;

public class ArmInstruction {
    public ArmReg defReg;

    public ArrayList<ArmOperand> operands;

    public ArmInstruction() {
        this.defReg = null;
        this.operands = new ArrayList<>();
    }

    public ArmInstruction(ArmReg rd, ArrayList<ArmOperand> operands) {
        this.operands = new ArrayList<>();
        this.operands.addAll(operands);
        this.defReg = rd;
    }

    public void replaceOperands(ArmReg armReg1, ArmOperand armReg2,
                                IList.INode<ArmInstruction, ArmBlock> armInstructionNode) {
        for(int i = 0; i < operands.size(); i++) {
            if(operands.get(i).equals(armReg1)) {
                operands.get(i).getUsers().remove(armInstructionNode);
                armReg2.getUsers().add(armInstructionNode);
                operands.set(i, armReg2);
                // riscvReg2.addDefOrUse(riscvInstructionNode.getParent().loopdepth, riscvInstructionNode.getParent().loop);
            }
        }
    }

    public void replaceDefReg(ArmReg armReg, IList.INode<ArmInstruction, ArmBlock> armInstructionNode) {
        defReg = armReg;
        armReg.getUsers().remove(armInstructionNode);
        defReg.getUsers().add(armInstructionNode);
        // defReg.addDefOrUse(riscvInstructionNode.getParent().loopdepth, riscvInstructionNode.getParent().loop);
    }

    public ArrayList<ArmOperand> getOperands() {
        return this.operands;
    }

    public ArmReg getDefReg() {
        return this.defReg;
    }

}
