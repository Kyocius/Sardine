package IR.Value.Instructions;

import IR.Type.PointerType;
import IR.Type.Type;
import IR.Value.BasicBlock;
import IR.Value.Value;
import Utils.LLVMIRDump;

public class LoadInst extends Instruction{

    public LoadInst(Value pointer, Type type) {
        super("%" + (++Value.valNumber), type, OP.Load);
        this.addOperand(pointer);
    }

    public Value getPointer(){
        return getOperand(0);
    }

    @Override
    public String getInstString(){
        Value pointer = getPointer();

        try{
            Type type = ((PointerType) pointer.getType()).getEleType();
            return getName() + " = " + "load " + type + ", "
                    + pointer.getType() + " " + pointer.getName();
        }
       catch (Exception e )
       {
           return getName() + " = " + "load " + ", "
                   + pointer.getType() + " " + pointer.getName();
       }
    }

    @Override
    public String toLLVMString() {
        return LLVMIRDump.getLLVMName(getName()) + " = load " +
                ((PointerType) getPointer().getType()).getEleType().toLLVMString() + ", " +
                getPointer().getType().toLLVMString() + " " +
                LLVMIRDump.getLLVMName(getPointer().getName());
    }
}
