package IR.Type;

public class VoidType extends Type{
    private VoidType(){
    }

    public static VoidType voidType = new VoidType();
    @Override
    public boolean isVoidTy(){
        return true;
    }

    @Override
    public String toString(){
        return "void";
    }

    @Override
    public String toLLVMString() {
        return "void";
    }
}
