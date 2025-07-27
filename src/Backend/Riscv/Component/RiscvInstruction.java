package Backend.Riscv.Component;

import Backend.Riscv.Operand.RiscvOperand;
import Backend.Riscv.Operand.RiscvReg;
import Utils.DataStruct.IList;

import java.util.ArrayList;

public class RiscvInstruction {
    private ArrayList<RiscvOperand> operands;
    private RiscvReg defReg;

    public RiscvInstruction(ArrayList<RiscvOperand> operands, RiscvReg defReg) {
        this.operands = operands;
        this.defReg = defReg;
    }

    public void replaceOperands(RiscvReg riscvReg1, RiscvReg riscvReg2,
                                IList.INode<RiscvInstruction, RiscvBlock> riscvInstructionNode) {
        for(int i = 0; i < operands.size(); i++) {
            if(operands.get(i).equals(riscvReg1)) {
                operands.get(i).getUsers().remove(riscvInstructionNode);
                riscvReg2.getUsers().add(riscvInstructionNode);
                operands.set(i, riscvReg2);
                // riscvReg2.addDefOrUse(riscvInstructionNode.getParent().loopdepth, riscvInstructionNode.getParent().loop);
            }
        }
    }

    public void replaceDefReg(RiscvReg riscvReg, IList.INode<RiscvInstruction, RiscvBlock> riscvInstructionNode) {
        defReg = riscvReg;
        riscvReg.getUsers().remove(riscvInstructionNode);
        defReg.getUsers().add(riscvInstructionNode);
        // defReg.addDefOrUse(riscvInstructionNode.getParent().loopdepth, riscvInstructionNode.getParent().loop);
    }

    public ArrayList<RiscvOperand> getOperands() {
        return this.operands;
    }

    public RiscvReg getDefReg() {
        return this.defReg;
    }
}
