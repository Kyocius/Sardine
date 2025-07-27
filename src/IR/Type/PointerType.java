package IR.Type;

public class PointerType extends Type{
    Type EleType;
    public PointerType(Type EleType){
        this.EleType = EleType;
    }

    public static PointerType PI32 = new PointerType(IntegerType.I32);
    public static PointerType PF32 = new PointerType(FloatType.F32);

    @Override
    public boolean isPointerType() {
        return true;
    }

    public Type getEleType(){
        return EleType;
    }

    @Override
    public String toString(){
        return EleType.toString() + "*";
    }

    @Override
    public String toLLVMString(){
        return EleType.toLLVMString() + "*";
    }
}
