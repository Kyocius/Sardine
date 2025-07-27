package IR.Type;

public class IntegerType extends Type{

    private IntegerType(){}

    public static IntegerType I32 = new IntegerType();
    public static IntegerType I1 = new IntegerType();

    public static Boolean isI1(IntegerType type) {
        return type == I1;
    }

    @Override
    public boolean isIntegerTy(){
        return true;
    }

    @Override
    public String toString(){
        return "i32" ;
    }

    public String toLLVMString() {
        if(this == I1) {
            return "i1";
        } else {
            return "i32";
        }
    }
}
