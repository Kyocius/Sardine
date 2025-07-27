package IR.Type;

public class FloatType extends Type {
    private FloatType(){
    }

    public static FloatType F32 = new FloatType();

    @Override
    public boolean isFloatTy(){
        return true;
    }

    @Override
    public String toString(){
        return "float";
    }

    @Override
    public String toLLVMString() {
        return "float";
    }
}
