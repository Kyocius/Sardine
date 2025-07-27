package Backend.Arm.Structure;

import Backend.Arm.Instruction.ArmInstruction;
import Backend.Arm.Operand.ArmLabel;
import Backend.Arm.Operand.ArmOperand;
import Backend.Riscv.Component.RiscvBlock;
import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvOperand;
import Utils.DataStruct.IList;

import java.util.ArrayList;

public class ArmBlock extends ArmLabel {
    private IList<ArmInstruction, ArmBlock> armInsList;
    private ArrayList<ArmBlock> preds;
    private ArrayList<ArmBlock> succs;

    public ArmBlock(String name) {
        super(name);
        armInsList = new IList<>(this);
        preds = new ArrayList<>();
        succs = new ArrayList<>();
    }

    public void addArmInstruction(IList.INode<ArmInstruction, ArmBlock> armInstructionNode) {
        for (ArmOperand operand : armInstructionNode.getValue().getOperands()) {
            operand.beUsed(armInstructionNode);
        }
        if (armInstructionNode.getValue().getDefReg() != null) {
            armInstructionNode.getValue().getDefReg().beUsed(armInstructionNode);
        }
        armInsList.add(armInstructionNode);
    }

    public void addPreds(ArmBlock block) {
        this.preds.add(block);
    }

    public void addSuccs(ArmBlock block) {
        this.succs.add(block);
    }

    public IList<ArmInstruction, ArmBlock> getArmInstructions() {
        return this.armInsList;
    }

    public ArrayList<ArmBlock> getPreds() {
        return this.preds;
    }

    public ArrayList<ArmBlock> getSuccs() {
        return this.succs;
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.printName());
        for(IList.INode<ArmInstruction, ArmBlock> bbIns: armInsList) {
            sb.append("\t" + bbIns.getValue() + "\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getName();
    }
}
