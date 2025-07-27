package IR.Value.Instructions;

import IR.Type.Type;
import IR.Value.Value;

//伪指令，不满足ssa条件。仅在进行后端生成前RemovePhi处生成。
public class Move extends Instruction{
    public Move(Value targetRegister, Value source) {
        super(targetRegister.getName(), source.getType(), OP.Move);
        this.addOperand(targetRegister);
        this.addOperand(source);
    }

    public String getRegister(){
        return getName().substring(1);
    }

    @Override
    public String getInstString() {
        return String.format("MOVE %s, %s", getOperand(0).getName(), getOperand(1).getName());
    }

    public Value getDestination(){
        return getOperand(0);
    }

    public Value getSource(){
        return getOperand(1);
    }
}
