package IR.Value.Instructions;

import IR.Type.IntegerType;
import IR.Type.Type;
import IR.Value.BasicBlock;
import IR.Value.Value;
import Utils.DataStruct.Pair;
import Utils.LLVMIRDump;

import java.util.ArrayList;

public class Phi extends Instruction {

    public Phi(Type type, ArrayList<Value> values) {
        super("%" + (++Value.valNumber), type, OP.Phi);
        for(Value value : values){
            addOperand(value);
        }
    }

    public ArrayList<Pair<Value, BasicBlock>> getPhiValues(){
        ArrayList<Pair<Value, BasicBlock>> phiValues = new ArrayList<>();
        ArrayList<Value> useValues = getUseValues();
        for (int i = 0; i < useValues.size(); i++) {
            Value value = useValues.get(i);
            BasicBlock bb = getParentbb().getPreBlocks().get(i);
            phiValues.add(new Pair<>(value, bb));
        }
        return phiValues;
    }

    //  这个函数默认oldPrebb是包含newPrebb的，不会有新的prebb
    public void fixPreBlocks(ArrayList<BasicBlock> oldPreBbs, ArrayList<BasicBlock> newPreBbs){
        ArrayList<Value> values = new ArrayList<>();
        for (BasicBlock newPreBb : newPreBbs) {
            int index = oldPreBbs.indexOf(newPreBb);
            values.add(getOperand(index));
        }
        this.operands = values;
    }

    @Override
    public String getInstString() {
        ArrayList<Value> useValues = getUseValues();
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(" = phi ");
        sb.append(getType()).append(" ");
        for (int i = 0; i < useValues.size(); i++) {
            Value useValue = useValues.get(i);

            if (i != 0) {
                sb.append(",");
            }
            sb.append("[ ");

            sb.append(useValue.getName()).append(", ");
            sb.append("%").append(getParentbb().getPreBlocks().get(i).getName()).append(" ]");
        }
        return sb.toString();
    }

    @Override
    public String toLLVMString() {
        StringBuilder sb = new StringBuilder();
        ArrayList<Value> useValues = getUseValues();
        sb.append(LLVMIRDump.getLLVMName(getName())).append(" = phi ");
        boolean isI1 = true;
        for (int i = 0; i < useValues.size(); i++) {
            Value useValue = useValues.get(i);
            if (!(useValue.getType() instanceof IntegerType
                    && IntegerType.isI1((IntegerType) useValue.getType()))) {
                isI1 = false;
            }
        }
        if (isI1) {
            sb.append("i1 ");
        } else {
            sb.append(getType()).append(" ");
        }
        for (int i = 0; i < useValues.size(); i++) {
            Value useValue = useValues.get(i);
            if (i != 0) {
                sb.append(",");
            }
            sb.append("[ ");
            sb.append(LLVMIRDump.getLLVMName(useValue.getName())).append(", ");
            sb.append("%").append(getParentbb().getPreBlocks().get(i).getName()).append(" ]");
        }
        return sb.toString();
    }
}
