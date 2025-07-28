package Backend.Arm.Structure;


import java.util.ArrayList;
import java.util.LinkedHashMap;

public class ArmModule {
    private final ArrayList<ArmGlobalVariable> dataGlobalVariables = new ArrayList<>();
    private final ArrayList<ArmGlobalVariable> bssGlobalVariables = new ArrayList<>();
    private final LinkedHashMap<String, ArmFunction> functions;

    public ArmModule() {
        this.functions = new LinkedHashMap<>();
    }

    public void addFunction(String functionName, ArmFunction function) {
        this.functions.put(functionName, function);
    }

    public ArrayList<ArmGlobalVariable> getDataGlobalVariables() {
        return this.dataGlobalVariables;
    }

//    public ArrayList<ArmGlobalVariable> getBssGlobalVariables() {
//        return this.bssGlobalVariables;
//    }

    public ArmFunction getFunction(String functionName) {
        return functions.get(functionName);
    }

    public LinkedHashMap<String, ArmFunction> getFunctions() {
        return this.functions;
    }

    public void addDataVar(ArmGlobalVariable var) {
        dataGlobalVariables.add(var);
    }

    public void addBssVar(ArmGlobalVariable var) {
        bssGlobalVariables.add(var);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        // ARMv8-A 64-bit architecture declaration
        sb.append(".arch armv8-a\n");
        if(!dataGlobalVariables.isEmpty()) {
            sb.append(".data\n");
            sb.append(".align\t8\n"); // ARMv8-A: 8-byte alignment for 64-bit
            for (ArmGlobalVariable globalVariable : dataGlobalVariables) {
                sb.append(globalVariable.dump());
            }
        }

        if(!bssGlobalVariables.isEmpty()) {
            sb.append(".bss\n");
            sb.append(".align\t8\n"); // ARMv8-A: 8-byte alignment for 64-bit
            for (ArmGlobalVariable globalVariable : bssGlobalVariables) {
                sb.append(globalVariable.dump());
            }
        }
        sb.append(".text\n");
        for(ArmFunction function:functions.values()){
            if(function.getName().equals("main")){
                sb.append(function.dump());
                break;
            }
        }
        for(ArmFunction function:functions.values()){
            if(!(function.getName().equals("main")))
                sb.append(function.dump());
        }
        return sb.toString();
    }
}
