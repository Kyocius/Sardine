package IR.Value;

import IR.Type.PointerType;
import IR.Type.Type;

import java.util.ArrayList;

public class GlobalVar extends Value{
    private Value value;
    boolean isArray;
    //  数组是否默认0初始化
    private boolean isZeroInit = false;
    //  全局数组zero init的时候，不记录values，只记录size
    private int size;
    //  代表全局数组的初始值
    private ArrayList<Value> values;
    private boolean isConstArray = false;

    //  全局变量
    public GlobalVar(String name, Type type, Value value){
        super(name, type);
        //  这个Value是他的初始值
        this.value = value;
        isArray = false;
    }

    //  全局数组
    public GlobalVar(String name, Type type, ArrayList<Value> values){
        super(name, type);
        this.values = values;
        isArray = true;
    }

    public GlobalVar(String name, Type type, ArrayList<Value> values, boolean isconst){
        super(name, type);
        this.values = values;
        isArray = true;
        isConstArray = isconst;
    }

    public boolean isConst(){
        return isConstArray;
    }

    public boolean isArray(){
        return isArray;
    }

    public Value getValue() {
        return value;
    }

    public ArrayList<Value> getValues() {
        return values;
    }

    public void setZeroInit(int size) {
        isZeroInit = true;
        this.size = size;
    }

    public int getSize() {
        if (isZeroInit) return size;
        else return values.size();
    }

    public boolean isZeroInit() {
        return isZeroInit;
    }
    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(" = global ").append(getType()).append(" ");
        if (isArray) {
            if (isZeroInit) sb.append("zero initializer");
            else {
                sb.append("[");
                for (int i = 0; i < values.size(); i++) {
                    sb.append(values.get(i));
                    if (i != values.size() - 1) sb.append(", ");
                }
                sb.append("]");
            }
        }
        else {
            sb.append(value);
        }
        return sb.toString();
    }

    @Override
    public String toLLVMString() {
        StringBuilder sb = new StringBuilder();
        if (isArray) {
            sb.append(getName()).append(" = global ").append("[ ").append(getSize()).append(" x ").append(((PointerType) getType()).getEleType().toLLVMString()).append(" ]").append(" ");
            if (isZeroInit) {
                sb.append("zeroinitializer");
            } else {
                sb.append("[");
                for (int i = 0; i < values.size(); i++) {
                    sb.append(values.get(i));
                    if (i != values.size() - 1) sb.append(", ");
                }
                sb.append("]");
            }
        }
        else {
            sb.append(getName()).append(" = global ");
            sb.append(value);
        }
        return sb.toString();
    }

    public String getBitcast() {
        //在每一个函数的开头加入这条将全局变量转为int*的指令
        String real_type;
        if(!isArray) {
            real_type = ((PointerType)getType()).getEleType().toLLVMString() + "*";
        } else {
            real_type =
                    "[" + getSize() +
                    " x " + ((PointerType) getType()).getEleType().toLLVMString() + "]* ";
        }
        String to_type = ((PointerType)getType()).getEleType().toLLVMString() + "*";
        String func_used_name = "%" + getName().substring(1) + "_global";
        return func_used_name + " = bitcast " + real_type + " " + getName() + " to " + to_type;
    }
}
