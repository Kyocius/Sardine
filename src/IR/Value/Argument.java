package IR.Value;

import IR.Type.Type;

public class Argument extends Value {
    private final Function parentFunc;

    public Argument(String name, Type type, Function parentFunc){
        super("%" + name+"_param", type);
        this.parentFunc = parentFunc;
    }

    @Override
    public String toString(){
        return getType() + " " + getName();
    }

    @Override
    public String toLLVMString() {
        return getType() + " " + getName();
    }
}
