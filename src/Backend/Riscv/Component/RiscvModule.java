package Backend.Riscv.Component;

import Backend.Riscv.Operand.RiscvConstFloat;
import Backend.Riscv.Operand.RiscvLabel;
import IR.Value.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class RiscvModule {
    private final ArrayList<RiscvGlobalVariable> dataGlobalVariables = new ArrayList<>();
    private final ArrayList<RiscvGlobalVariable> bssGlobalVariables = new ArrayList<>();
    private final LinkedHashMap<String, RiscvFunction> functions;
    private final LinkedHashMap<Float, RiscvConstFloat> floats;

    public RiscvModule(LinkedHashMap<Float, RiscvConstFloat> floats) {
        this.floats = floats;
        this.functions = new LinkedHashMap<>();
    }

    public void addFunction(String functionName, RiscvFunction function) {
        this.functions.put(functionName, function);
    }

    public ArrayList<RiscvGlobalVariable> getDataGlobalVariables() {
        return this.dataGlobalVariables;
    }

//    public ArrayList<RiscvGlobalVariable> getBssGlobalVariables() {
//        return this.bssGlobalVariables;
//    }

    public RiscvFunction getFunction(String functionName) {
        return functions.get(functionName);
    }

    public LinkedHashMap<String, RiscvFunction> getFunctions() {
        return this.functions;
    }

    public void addDataVar(RiscvGlobalVariable var) {
        dataGlobalVariables.add(var);
    }

    public void addBssVar(RiscvGlobalVariable var) {
        bssGlobalVariables.add(var);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        //TODO 貌似这个代码是没法直接跑起来的,再前面貌似需要加上一些别的关键字来指定体系结构
        if(!dataGlobalVariables.isEmpty()) {
            sb.append(".data\n");
            sb.append(".align\t4\n");
            for (RiscvGlobalVariable globalVariable : dataGlobalVariables) {
                sb.append(globalVariable.dump());
            }
        }

        if(!bssGlobalVariables.isEmpty()) {
            sb.append(".bss\n");
            sb.append(".align\t4\n");
            for (RiscvGlobalVariable globalVariable : bssGlobalVariables) {
                sb.append(globalVariable.dump());
            }
        }
        sb.append(".text\n");
        for(RiscvFunction function:functions.values()){
            if(function.getName().equals("main")){
                sb.append(function.dump());
                break;
            }
        }
        for(RiscvFunction function:functions.values()){
            if(!(function.getName().equals("main")))
                sb.append(function.dump());
        }
        sb.append(" .section        .srodata.cst4,\"aM\",@progbits,4\n" +
                "        .align  2\n");
        for(RiscvConstFloat floatConst: floats.values()) {
            sb.append(floatConst);
        }
        return sb.toString();
    }
}
