package Backend.Arm.Structure;

import IR.Type.IntegerType;
import IR.Value.ConstFloat;
import IR.Value.ConstInteger;
import IR.Value.GlobalVar;
import IR.Value.Value;

import java.lang.invoke.VarHandle;

public class ArmConstDefination {
    public GlobalVar value;
    private String outputString = "";
    public ArmConstDefination(GlobalVar value){
        this.value = value;
        this.updateOutputString();
    }

    private void updateOutputString(){
        var builder = new StringBuilder();
        builder.append("global"+this.value.getName()+":\n");
        if(this.value.isArray()){
            int last_non_zero_index = -1;
            var array = this.value.getValues();
            for(int i = 0;i<array.size();i++){
                var v =  array.get(i);
                if(v instanceof ConstInteger intv){
                    int value = intv.getValue();
                    if(value != 0){
                        if(last_non_zero_index != i-1){
                            builder.append("    .zero ");
                            builder.append(4*(i-1-last_non_zero_index));
                            builder.append("\n");
                        }
                        last_non_zero_index = i;
                        builder.append("    .word ");
                        builder.append(value);
                        builder.append('\n');
                    }
                }else if(v instanceof ConstFloat floatv){
                    float value = floatv.getValue();
                    if(value != 0){
                        if(last_non_zero_index != i-1){
                            builder.append("    .zero ");
                            builder.append(4*(i-1-last_non_zero_index));
                            builder.append("\n");
                        }
                        last_non_zero_index = i;
                        builder.append("    .word ");
                        builder.append("0x").append(Integer.toHexString(Float.floatToIntBits(value)).toUpperCase());
                        builder.append('\n');
                    }
                }
            }
            if(last_non_zero_index != array.size()-1){
                builder.append("    .zero ");
                builder.append(4*(array.size()-1-last_non_zero_index));
                builder.append("\n");
            }
        }else{
            var v =  this.value.getValue();
            if(v instanceof ConstInteger intv){
                int value = intv.getValue();
                builder.append("    .word ");
                builder.append(value);
                builder.append('\n');
            }else if(v instanceof ConstFloat floatv){
                float value = floatv.getValue();
                builder.append("    .word ");
                builder.append("0x").append(Integer.toHexString(Float.floatToIntBits(value)).toUpperCase());
                builder.append('\n');
            }
        }
        this.outputString = builder.toString();
    }

    @Override
    public String toString() {
        return this.outputString;
    }
}
