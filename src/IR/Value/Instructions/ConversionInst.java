package IR.Value.Instructions;

import IR.Type.Type;
import IR.Value.Value;
import Utils.LLVMIRDump;

public class ConversionInst extends Instruction {
    public ConversionInst(Value value, Type type, OP op){
        super("%" + (++Value.valNumber), type, op);
        addOperand(value);
    }

    public Value getValue(){
        return getOperand(0);
    }

    @Override
    public String getInstString(){
        if(getOp() == OP.Itof){
            return getName() + " = itof " + getValue();
        }
        else if(getOp() == OP.Ftoi){
            return getName() + " = ftoi " + getValue();
        }
        return null;
    }

    @Override
    public String toLLVMString() {
        if(getOp() == OP.Itof) {
            return LLVMIRDump.getLLVMName(getName()) + " = sitofp " +
                    getValue().getType().toLLVMString() + " " +
                    LLVMIRDump.getLLVMName(getValue().getName()) + " to float";
        } else {
            return LLVMIRDump.getLLVMName(getName()) + " = fptosi " +
                    getValue().getType().toLLVMString() + " " +
                    LLVMIRDump.getLLVMName(getValue().getName()) + " to i32";
        }
    }
}
