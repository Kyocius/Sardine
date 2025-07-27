package Backend.Riscv.Operand;

import Backend.Riscv.Component.RiscvBlock;
import Backend.Riscv.Component.RiscvInstruction;
import Utils.DataStruct.IList;

import java.util.LinkedHashSet;

public class RiscvOperand {
    private LinkedHashSet<IList.INode<RiscvInstruction, RiscvBlock>> users;

    public RiscvOperand() {
        this.users = new LinkedHashSet<>();
    }

    public void beUsed(IList.INode<RiscvInstruction, RiscvBlock> riscvInstructionNode) {
        this.users.add(riscvInstructionNode);
    }

    public void replaceAllUser(IList.INode<RiscvInstruction, RiscvBlock> self,
                               IList.INode<RiscvInstruction, RiscvBlock> riscvInstr) {
        LinkedHashSet<IList.INode<RiscvInstruction, RiscvBlock>> removes = new LinkedHashSet<>();
        for (IList.INode<RiscvInstruction, RiscvBlock> userInstr : users) {
            if (userInstr.equals(self)) {
                continue;
            }
            removes.add(userInstr);
        }
        for (IList.INode<RiscvInstruction, RiscvBlock> userInstr : removes) {
            userInstr.getValue().replaceOperands((RiscvReg) this, riscvInstr.getValue().getDefReg(), userInstr);
//            riscvInstr.getContent().getDefReg().beUsed(userInstr);
        }
    }

    public LinkedHashSet<IList.INode<RiscvInstruction, RiscvBlock>> getUsers() {
        return this.users;
    }
}
