package IR.Value.Instructions;

import IR.Type.IntegerType;
import IR.Type.PointerType;
import IR.Type.Type;
import IR.Value.BasicBlock;
import IR.Value.ConstFloat;
import IR.Value.ConstInteger;
import IR.Value.Value;
import Utils.LLVMIRDump;

import java.util.LinkedList;

public class BinaryInst extends Instruction {

    public boolean I64 = false;
    public BinaryInst(OP op, Value left, Value right, Type type) {
        super("%" + (++Value.valNumber), type, op);
        this.addOperand(left);
        this.addOperand(right);
    }

    public Value getLeftVal() {
        return getOperand(0);
    }

    public Value getRightVal() {
        return getOperand(1);
    }

    public boolean hasOneConst() {
        boolean isLeftConst = (getOperand(0) instanceof ConstInteger)
                || (getOperand(0) instanceof ConstFloat);
        boolean isRightConst = (getOperand(1) instanceof ConstInteger)
                || (getOperand(1) instanceof ConstFloat);
        if (isLeftConst && isRightConst) return false;
        return isLeftConst || isRightConst;
    }

    @Override
    public String getInstString() {
        String type_str;
        Type valueTy = getLeftVal().getType();
        if (valueTy == IntegerType.I32) {
            type_str = "i32";
        }
        else if (valueTy == PointerType.PI32) {
            type_str = "i32*";
        }
        else type_str = "float";
        return getName() + " = " +
                getOp() + " " + type_str + " " +
                getLeftVal().getName() + ", " +
                getRightVal().getName();
    }

    @Override
    public String toLLVMString() {
        StringBuilder sb = new StringBuilder();
        if (getOp() == OP.FEq || getOp() == OP.FGt || getOp() == OP.FGe
                || getOp() == OP.FLe || getOp() == OP.FLt || getOp() == OP.FNe
                || getOp() == OP.Eq || getOp() == OP.Ge || getOp() == OP.Gt
                || getOp() == OP.Le || getOp() == OP.Lt || getOp() == OP.Ne) {
            IntegerType type = IntegerType.I1;
            this.setType(type);
        }
        if(this.getType().isFloatTy()) {
            sb.append(LLVMIRDump.getLLVMName(getName())).append(" = ").append(OP.getLLVMOpName(getOp()));
            sb.append(" ").
                    append(getLeftVal().getType().toLLVMString()).append(" ").
                    append(LLVMIRDump.getLLVMName(getLeftVal().getName())).append(", ").
                    append(LLVMIRDump.getLLVMName(getRightVal().getName()));
        } else {
            String used_left_name = LLVMIRDump.getLLVMName(getLeftVal().getName());
            Type used_left_type = getLeftVal().getType();
            String used_right_name = LLVMIRDump.getLLVMName(getRightVal().getName());
            if(definitelyInt32(getLeftVal()) || definitelyInt32(getRightVal()) || needI32()) {
                if(getLeftVal().getType() instanceof IntegerType
                        && IntegerType.isI1((IntegerType) getLeftVal().getType())) {
                    used_left_name = used_left_name + "_zext";
                    sb.append(used_left_name).append(" = zext ").append("i1 ").append(LLVMIRDump.getLLVMName(getLeftVal().getName())).append(" to i32\n\t");
                    used_left_type = IntegerType.I32;
                }
                if(getRightVal().getType() instanceof IntegerType
                        && IntegerType.isI1((IntegerType) getRightVal().getType())) {
                    used_right_name = used_right_name + "_zext";
                    sb.append(used_right_name).append(" = zext ").append("i1 ").append(LLVMIRDump.getLLVMName(getRightVal().getName())).append(" to i32\n\t");
                }
            } else {
                if(getLeftVal() instanceof ConstInteger) {
                    used_left_type = getRightVal().getType();
                }
            }
            sb.append(LLVMIRDump.getLLVMName(getName())).append(" = ").
                    append(OP.getLLVMOpName(getOp()));
            sb.append(" ").
                    append(used_left_type.toLLVMString()).append(" ").
                    append(used_left_name).append(", ").
                    append(used_right_name);
        }
        return sb.toString();
    }

    private boolean definitelyInt32(Value value) {
        if(value instanceof ConstInteger) {
            return ((ConstInteger) value).getValue() != 1 && ((ConstInteger) value).getValue() != 0;
        }
        if(value.getType() instanceof IntegerType) {
            return !IntegerType.isI1((IntegerType) value.getType());
        }
        return false;
    }

    public boolean needI32() {
        return getOp() == OP.Add || getOp() == OP.Sub || getOp() == OP.Mul || getOp() == OP.Div
                || getOp() == OP.Mod;
    }
}
