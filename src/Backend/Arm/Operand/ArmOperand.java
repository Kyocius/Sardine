package Backend.Arm.Operand;

import Backend.Arm.Instruction.ArmInstruction;
import Backend.Arm.Structure.ArmBlock;
import Utils.DataStruct.IList;

import java.util.LinkedHashSet;

public class ArmOperand {
    private LinkedHashSet<IList.INode<ArmInstruction, ArmBlock>> users;

    public ArmOperand() {
        this.users = new LinkedHashSet<>();
    }

    public void beUsed(IList.INode<ArmInstruction, ArmBlock> armInsNode) {
        this.users.add(armInsNode);
    }

    public void replaceAllUser(IList.INode<ArmInstruction, ArmBlock> self,
                               IList.INode<ArmInstruction, ArmBlock> armInstr) {
        LinkedHashSet<IList.INode<ArmInstruction, ArmBlock>> removes = new LinkedHashSet<>();
        for (IList.INode<ArmInstruction, ArmBlock> userInstr : users) {
            if (userInstr.equals(self)) {
                continue;
            }
            removes.add(userInstr);
        }
        for (IList.INode<ArmInstruction, ArmBlock> userInstr : removes) {
            userInstr.getValue().replaceOperands((ArmReg) this, armInstr.getValue().getDefReg(), userInstr);
//            armInstr.getContent().getDefReg().beUsed(userInstr);
        }
    }

    public LinkedHashSet<IList.INode<ArmInstruction, ArmBlock>> getUsers() {
        return this.users;
    }
}
