package IR.Value.Instructions;

import IR.Type.PointerType;
import IR.Type.Type;
import IR.Value.Value;
import Utils.LLVMIRDump;

import java.util.ArrayList;

public class PtrInst extends Instruction{

    public PtrInst(Value pointer, Value offset) {
        super("%" + (++Value.valNumber), pointer.getType(), OP.Ptradd);
        this.addOperand(pointer);
        this.addOperand(offset);
    }

    public Value getTarget() {
        return getOperand(0);
    }

    public Value getOffset(){
        return getOperand(1);
    }

    @Override
    public String getInstString(){
        return getName() + " = ptradd " + getTarget().getName() + ", " + getOffset();
    }

    @Override
    public String toLLVMString() {
        return LLVMIRDump.getLLVMName(getName()) + " = getelementptr " +
                ((PointerType) (getTarget().getType())).getEleType().toLLVMString() + ", " +
                getTarget().getType().toLLVMString() + " " + LLVMIRDump.getLLVMName(getTarget().getName()) + ", " +
                getOffset().getType().toLLVMString() + " " + LLVMIRDump.getLLVMName(getOffset().getName());
    }
}
