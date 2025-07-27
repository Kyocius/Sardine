package IR.Value.Instructions;

import IR.Type.FloatType;
import IR.Type.IntegerType;
import IR.Type.PointerType;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Value;
import Utils.LLVMIRDump;

import java.util.ArrayList;

public class CallInst extends Instruction {
    private boolean hasName = false;
    private Function function;

    public CallInst(Function function, ArrayList<Value> values) {
        super("%" + (++Value.valNumber), function.getType(), OP.Call);
        this.hasName = !function.getType().isVoidTy();
        this.function = function;
        for (Value value : values) {
            this.addOperand(value);
        }
    }

    public Function getFunction() {
        return function;
    }

    public ArrayList<Value> getParams() {
        return getUseValues();
    }

    @Override
    public boolean hasName() {
        return this.hasName;
    }

    @Override
    public String getInstString() {
        StringBuilder sb = new StringBuilder();
        if (!this.getType().isVoidTy()) {
            sb.append(getName()).append(" = ");
        }
        sb.append("call ").append(getFunction().getType()).append(' ').append(getFunction().getName()).append("(");
        ArrayList<Value> operands = getOperands();
        for (int i = 0; i < operands.size(); i++) {
            Value value = operands.get(i);
            sb.append(value.getType()).append(" ").append(value.getName());
            if (i != operands.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String toLLVMString() {
        StringBuilder sb = new StringBuilder();
        ArrayList<String> true_value_name = new ArrayList<>();
        for(int i = 0;i < operands.size();i++) {
            Value value = operands.get(i);
            String used_type = value.getType().toLLVMString();
            String used_name = LLVMIRDump.getLLVMName(value.getName());
            if(value.getType() instanceof IntegerType
                    && function.getArgs().get(i).getType() instanceof FloatType) {
                sb.append(used_name).append("_2float").append(" = sitofp ").append(used_type).append(" ").append(used_name).append(" ").append(" to float\n\t");
                used_type = function.getArgs().get(i).getType().toLLVMString();
                used_name = used_name + "_2float";
            } else if(value.getType() instanceof FloatType
                    && function.getArgs().get(i).getType() instanceof IntegerType) {
                sb.append(used_name).append("_2int").append(" = fptosi ").append(used_type).append(" ").append(used_name).append(" to ").append(function.getArgs().get(i).getType().toLLVMString()).append("\n\t");
                used_type = function.getArgs().get(i).getType().toLLVMString();
                used_name = used_name + "_2int";
            } else if(value.getType() instanceof PointerType) {
                assert function.getArgs().get(i).getType() instanceof PointerType;
                if(((PointerType)(value.getType())).getEleType().isIntegerTy()
                        && ((PointerType)(function.getArgs().get(i).getType())).getEleType().isFloatTy()) {
                    sb.append(used_name).append("_bitcast").append(" = bitcast ").append(used_type).append(" ").append(used_name).append(" to ").append(function.getArgs().get(i).getType().toLLVMString()).append("\n\t");
                    used_type = function.getArgs().get(i).getType().toLLVMString();
                    used_name = used_name + "_bitcast";
                } else if(((PointerType)(value.getType())).getEleType().isFloatTy()
                        && ((PointerType)(function.getArgs().get(i).getType())).getEleType().isIntegerTy()) {
                    sb.append(used_name).append("_bitcast").append(" = bitcast ").append(used_type).append(" ").append(used_name).append(" to ").append(function.getArgs().get(i).getType().toLLVMString()).append("\n\t");
                    used_type = function.getArgs().get(i).getType().toLLVMString();
                    used_name = used_name + "_bitcast";
                }
            }
            true_value_name.add(used_type + " " + used_name);
        }
        if (!this.getType().isVoidTy()) {
            sb.append(LLVMIRDump.getLLVMName(getName())).append(" = ");
        }
        sb.append("call ").append(function.getType().toLLVMString()).append(" ").append(function.getName()).append("(");
        for (int i = 0; i < true_value_name.size(); i++) {
            String used_full_name = true_value_name.get(i);
            sb.append(used_full_name);
            if (i != operands.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
