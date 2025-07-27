package IR.Value.Instructions;

import IR.Type.PointerType;
import IR.Type.Type;
import IR.Value.BasicBlock;
import IR.Value.ConstFloat;
import IR.Value.ConstInteger;
import IR.Value.Value;
import Utils.LLVMIRDump;

import java.util.ArrayList;


public class AllocInst extends Instruction{
    private boolean isArray;
    //  只有当isArray是true的时候，才会有size属性，用于记录要申请多少空间
    private int size;

    private boolean isConst = false;

    private ArrayList<Value> initValues;

    public AllocInst(Type type) {
        super("%" + (++Value.valNumber), type, OP.Alloca);
        isArray = false;
    }

    public AllocInst(Type type, int size){
        super("%" + (++Value.valNumber), type, OP.Alloca);
        isArray = true;
        this.size = size;
    }

    public void setConst(boolean isConst){
        this.isConst = isConst;
    }

    public boolean isConst(){
        return isConst;
    }

    public void setInitValues(ArrayList<Value> initValues){
        this.initValues = initValues;
    }

    public ArrayList<Value> getInitValues(){
        return initValues;
    }

    public Type getAllocType(){
        return ((PointerType) getType()).getEleType();
    }

    public boolean isArray() {
        return isArray;
    }

    public int getSize(){
        return size;
    }

    @Override
    public String getInstString(){
        if(!isArray){
            return getName() + " = alloc " + getAllocType();
        }
        else{
            return getName() + " = alloc [" + size + " x " + getAllocType() + "]";
        }
    }

    @Override
    public String toLLVMString() {
        if(isArray) {
            StringBuilder sb = new StringBuilder();
            sb.append("%bitcast").append(getName(), 1, getName().length()).append(" = alloca ");
            String type;
            if (size == 1) {
                type = getAllocType().toLLVMString();
            } else {
                type = "[" + size + " x " + getAllocType().toLLVMString() + "]";
            }
            sb.append(type).append("\n");
            sb.append("\t").append(LLVMIRDump.getLLVMName(getName()))
                    .append(" = bitcast ").append(type).append("* ")
                    .append("%bitcast").append(getName(), 1, getName().length()).append(" to ")
                    .append(getAllocType().toLLVMString()).append("*");
            return sb.toString();
        } else {
            return LLVMIRDump.getLLVMName(getName()) + " = alloca " + getAllocType().toLLVMString();
        }
    }
}
