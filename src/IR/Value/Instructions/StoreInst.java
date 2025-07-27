package IR.Value.Instructions;

import IR.Type.VoidType;
import IR.Value.BasicBlock;
import IR.Value.Value;
import Utils.LLVMIRDump;

public class StoreInst extends Instruction{
    private boolean isInitArrayInst = false;
    public StoreInst(Value value, Value pointer) {
        super("", VoidType.voidType, OP.Store);
        this.addOperand(value);
        this.addOperand(pointer);
    }

    public Value getValue(){
        return getOperand(0);
    }

    public Value getPointer(){
        return getOperand(1);
    }

    //  是否是数组初始化时的存储指令(优化用)
    public boolean isInitArrayInst(){
        return this.isInitArrayInst;
    }

    public void setAsInitArrayInst(){
        this.isInitArrayInst = true;
    }

    @Override
    public boolean hasName(){
        return false;
    }

    @Override
    public String getInstString(){
        return "store " + getValue() + ", " + getPointer();
    }

    @Override
    public String toLLVMString() {
        return "store " + getValue().getType().toLLVMString() + " " +
                LLVMIRDump.getLLVMName(getValue().getName()) + ", " +
                getPointer().getType().toLLVMString() + " " +
                LLVMIRDump.getLLVMName(getPointer().getName());
    }
}
