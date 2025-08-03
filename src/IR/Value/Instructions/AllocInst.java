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
    // 标记是否为数组分配
    private boolean isArray;
    // 只有当 isArray 为 true 时，size 才有效，表示分配的元素数量
    private int size;

    // 标记是否为常量分配
    private boolean isConst = false;

    // 初始化值列表，仅在全局常量或全局变量初始化时使用
    private ArrayList<Value> initValues;

    /**
     * 普通变量分配构造函数
     * @param type 分配的类型（应为指针类型）
     */
    public AllocInst(Type type) {
        super("%" + (++Value.valNumber), type, OP.Alloca);
        isArray = false;
        this.size = 1; // 普通变量分配时 size 设为 1
    }

    /**
     * 数组分配构造函数
     * @param type 分配的类型（应为指针类型）
     * @param size 数组元素数量
     */
    public AllocInst(Type type, int size){
        super("%" + (++Value.valNumber), type, OP.Alloca);
        isArray = true;
        this.size = size;
    }

    /**
     * 设置是否为常量分配
     * @param isConst 是否为常量
     */
    public void setConst(boolean isConst){
        this.isConst = isConst;
    }

    /**
     * 判断是否为常量分配
     * @return 是否为常量
     */
    public boolean isConst(){
        return isConst;
    }

    /**
     * 设置初始化值列表
     * @param initValues 初始化值
     */
    public void setInitValues(ArrayList<Value> initValues){
        this.initValues = initValues;
    }

    /**
     * 获取初始化值列表
     * @return 初始化值
     */
    public ArrayList<Value> getInitValues(){
        return initValues;
    }

    /**
     * 获取分配的元素类型（去除指针包装）
     * @return 元素类型
     */
    public Type getAllocType(){
        return ((PointerType) getType()).getEleType();
    }

    /**
     * 判断是否为数组分配
     * @return 是否为数组
     */
    public boolean isArray() {
        return isArray;
    }

    /**
     * 获取数组分配的元素数量
     * @return 元素数量
     */
    public int getSize(){
        return size;
    }

    /**
     * 获取指令字符串（中间表示）
     * @return 指令字符串
     */
    @Override
    public String getInstString(){
        if(!isArray){
            // 普通变量分配
            return getName() + " = alloc " + getAllocType();
        }
        else{
            // 数组分配
            return getName() + " = alloc [" + size + " x " + getAllocType() + "]";
        }
    }

    /**
     * 生成 LLVM IR 字符串
     * aarch64 要求分配类型必须为指针类型，且对齐方式需在后续 IR 生成时指定
     * @return LLVM IR 字符串
     */
    @Override
    public String toLLVMString() {
        if(isArray) {
            // 数组分配，aarch64 下建议直接分配为 [size x type]，后续可加 align 属性
            StringBuilder sb = new StringBuilder();
            sb.append("%bitcast").append(getName(), 1, getName().length()).append(" = alloca ");
            String type;
            if (size == 1) {
                // 单元素分配
                type = getAllocType().toLLVMString();
            } else {
                // 多元素分配
                type = "[" + size + " x " + getAllocType().toLLVMString() + "]";
            }
            sb.append(type).append("\n");
            // aarch64 下建议直接使用 alloca 分配数组，无需 bitcast，以下为兼容原有 IR
            sb.append("\t").append(LLVMIRDump.getLLVMName(getName()))
                    .append(" = bitcast ").append(type).append("* ")
                    .append("%bitcast").append(getName(), 1, getName().length()).append(" to ")
                    .append(getAllocType().toLLVMString()).append("*");
            return sb.toString();
        } else {
            // 普通变量分配
            return LLVMIRDump.getLLVMName(getName()) + " = alloca " + getAllocType().toLLVMString();
        }
    }
}
