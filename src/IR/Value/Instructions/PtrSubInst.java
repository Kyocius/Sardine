package IR.Value.Instructions;

import IR.Type.PointerType;
import IR.Value.ConstInteger;
import IR.Value.Value;
import Utils.LLVMIRDump;

public class PtrSubInst extends Instruction {
    public PtrSubInst(Value pointer, Value offset) {
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
        return getName() + " = ptrsub " + getTarget().getName() + ", " + getOffset();
    }

    @Override
    public String toLLVMString() {
        if(getOffset() instanceof ConstInteger) {
            ConstInteger newConst = new ConstInteger(((ConstInteger) getOffset()).getValue() * -1, getOffset().getType());
            return LLVMIRDump.getLLVMName(getName()) + " = getelementptr " +
                    ((PointerType) (getTarget().getType())).getEleType().toLLVMString() + ", " +
                    getTarget().getType().toLLVMString() + " " + LLVMIRDump.getLLVMName(getTarget().getName()) + ", " +
                    getOffset().getType().toLLVMString() + " " + LLVMIRDump.getLLVMName(newConst.getName());
        } else {
            String rev_name = LLVMIRDump.getLLVMName(getOffset().getName()) + "_reverse";
            String cmd1 = rev_name + " = sub " + getOffset().getType().toLLVMString() + " 0, + " + LLVMIRDump.getLLVMName(getOffset().getName());
            String cmd2 = LLVMIRDump.getLLVMName(getName()) + " = getelementptr " +
                    ((PointerType) (getTarget().getType())).getEleType().toLLVMString() + ", " +
                    getTarget().getType().toLLVMString() + " " + LLVMIRDump.getLLVMName(getTarget().getName()) + ", " +
                    getOffset().getType().toLLVMString() + " " + rev_name;
            return cmd1 + "\n\t" + cmd2;
        }
    }
}
