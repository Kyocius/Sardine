package Backend.Arm.Instruction;

import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;
import Backend.Arm.Structure.ArmBlock;
import Utils.DataStruct.IList;

import java.util.ArrayList;

/**
 * AArch64 instruction base class
 * Abstract base class for all ARM instruction types
 */
public abstract class ArmInstruction {
    protected ArmReg defReg;
    protected ArrayList<ArmOperand> operands;

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
        for (int i = 0; i < operands.size(); i++) {
            if (operands.get(i).equals(armReg1)) {
                operands.get(i).getUsers().remove(armInstructionNode);
                armReg2.getUsers().add(armInstructionNode);
                operands.set(i, armReg2);
            }
        }
    }

    public void replaceDefReg(ArmReg armReg, IList.INode<ArmInstruction, ArmBlock> armInstructionNode) {
        if (defReg != null) {
            defReg.getUsers().remove(armInstructionNode);
        }
        defReg = armReg;
        if (armReg != null) {
            armReg.getUsers().add(armInstructionNode);
        }
    }

    // Getter methods
    public ArmReg getDefReg() {
        return defReg;
    }

    public ArrayList<ArmOperand> getOperands() {
        return operands;
    }

    // Abstract method that must be implemented by subclasses
    public abstract String toString();
}
