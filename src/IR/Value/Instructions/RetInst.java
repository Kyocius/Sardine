package IR.Value.Instructions;

import IR.Type.VoidType;
import IR.Value.ConstFloat;
import IR.Value.ConstInteger;
import IR.Value.Value;
import Utils.LLVMIRDump;

public class RetInst extends Instruction {

    //  对于inst来说，它的type是自己本身的type，因此这里没有用retValue的type
    public RetInst(Value value){
        super("", VoidType.voidType, OP.Ret);
        this.addOperand(value);
    }

    public RetInst(){
        super("", VoidType.voidType, OP.Ret);
    }

    public boolean isVoid(){
        return getOperand(0).getName().equals("void");
    }

    public Value getValue(){
        return this.getOperand(0);
    }

    @Override
    public String getInstString(){
        StringBuilder sb = new StringBuilder();
        sb.append("ret ");

        Value value = getValue();
        if(value.getType().isIntegerTy()) sb.append("i32 ");
        else if(value.getType().isFloatTy()) sb.append("float ");

        if(value instanceof ConstInteger constInt) {
            sb.append(constInt.getValue());
        }
        else if(value instanceof ConstFloat constFloat){
            sb.append(constFloat.getValue());
        }

        else sb.append(value.getName());
        return sb.toString();
    }

    @Override
    public boolean hasName(){
        return false;
    }

    @Override
    public String toLLVMString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ret ");
        if (isVoid()) {
            sb.append("void");
        } else {
            sb.append(getValue().getType().toLLVMString() + " " +
                    LLVMIRDump.getLLVMName(getValue().getName()));
        }
        return sb.toString();
    }
}
