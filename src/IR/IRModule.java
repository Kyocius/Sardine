package IR;

import IR.Value.Function;
import IR.Value.GlobalVar;
import java.util.ArrayList;

public record IRModule(ArrayList<Function> functions, ArrayList<GlobalVar> globalVars,
                       ArrayList<Function> libFunctions) {

    public void addGlobalVar(GlobalVar globalVar) {
        globalVars.add(globalVar);
    }

    public void addFunction(Function function) {
        functions.add(function);
    }
    public Function getLibFunction(String name){
        for(var func : libFunctions){
            if(func.getName().equals("@"+name)){
                return func;
            }
        }
        return null;
    }
}
