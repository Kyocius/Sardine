package Backend.Riscv.Component;

import Backend.Riscv.Operand.RiscvLabel;
import Backend.Riscv.Operand.RiscvOperand;
import Utils.DataStruct.IList;

import java.util.ArrayList;

public class RiscvBlock extends RiscvLabel {
    private IList<RiscvInstruction, RiscvBlock> riscvInstructions;
    private ArrayList<RiscvBlock> preds;
    private ArrayList<RiscvBlock> succs;

    public RiscvBlock(String name) {
        super(name);
        riscvInstructions = new IList<>(this);
        preds = new ArrayList<>();
        succs = new ArrayList<>();
    }

    public void addRiscvInstruction(IList.INode<RiscvInstruction, RiscvBlock> riscvInstructionNode) {
        for (RiscvOperand operand : riscvInstructionNode.getValue().getOperands()) {
            operand.beUsed(riscvInstructionNode);
        }
        if (riscvInstructionNode.getValue().getDefReg() != null) {
            riscvInstructionNode.getValue().getDefReg().beUsed(riscvInstructionNode);
        }
        riscvInstructions.add(riscvInstructionNode);
    }

    public void addPreds(RiscvBlock block) {
        this.preds.add(block);
    }

    public void addSuccs(RiscvBlock block) {
        this.succs.add(block);
    }

    public IList<RiscvInstruction, RiscvBlock> getRiscvInstructions() {
        return this.riscvInstructions;
    }

    public ArrayList<RiscvBlock> getPreds() {
        return this.preds;
    }

    public ArrayList<RiscvBlock> getSuccs() {
        return this.succs;
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.printName());
        for(IList.INode<RiscvInstruction, RiscvBlock> bbIns: riscvInstructions) {
            sb.append("\t" + bbIns.getValue() + "\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getName();
    }
}
