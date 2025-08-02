package Backend.Arm.Structure;

import IR.Value.ConstFloat;
import IR.Value.ConstInteger;
import IR.Value.GlobalVar;

/**
 * AArch64 Constant Definition Generator
 * Generates assembly data section definitions for global constants and arrays
 * Supports both integer and floating-point constant optimization
 */
public class ArmConstDefination {
    public GlobalVar value;
    private String outputString = "";

    public ArmConstDefination(GlobalVar value){
        this.value = value;
        this.updateOutputString();
    }

    private void updateOutputString(){
        var builder = new StringBuilder();
        builder.append("global").append(this.value.getName()).append(":\n");

        if(this.value.isArray()){
            int last_non_zero_index = -1;
            var array = this.value.getValues();
            for(int i = 0; i < array.size(); i++){
                var v = array.get(i);
                if(v instanceof ConstInteger intv){
                    int value = intv.getValue();
                    if(value != 0){
                        if(last_non_zero_index != i-1){
                            builder.append("    .zero ");
                            builder.append(4 * (i - 1 - last_non_zero_index));
                            builder.append("\n");
                        }
                        last_non_zero_index = i;
                        builder.append("    .word ");
                        builder.append(value);
                        builder.append('\n');
                    }
                }else if(v instanceof ConstFloat floatv){
                    float value = floatv.getValue();
                    if(value != 0.0f){
                        if(last_non_zero_index != i-1){
                            builder.append("    .zero ");
                            builder.append(4 * (i - 1 - last_non_zero_index));
                            builder.append("\n");
                        }
                        last_non_zero_index = i;
                        // AArch64: Use .word for 32-bit float representation
                        builder.append("    .word ");
                        builder.append(Float.floatToIntBits(value));
                        builder.append('\n');
                    }
                }
            }
            // Handle trailing zeros
            if(last_non_zero_index < array.size() - 1){
                builder.append("    .zero ");
                builder.append(4 * (array.size() - 1 - last_non_zero_index));
                builder.append("\n");
            }
        } else {
            // Single value (not array) - simplified without type checking
            var firstValue = this.value.getValues().getFirst();
            if(firstValue instanceof ConstInteger constInt){
                builder.append("    .word ");
                builder.append(constInt.getValue());
                builder.append('\n');
            } else if(firstValue instanceof ConstFloat constFloat){
                // AArch64: Use .word for 32-bit float representation
                builder.append("    .word ");
                builder.append(Float.floatToIntBits(constFloat.getValue()));
                builder.append('\n');
            }
        }

        this.outputString = builder.toString();
    }

    @Override
    public String toString(){
        return this.outputString;
    }
}
