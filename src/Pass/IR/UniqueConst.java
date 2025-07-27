package Pass.IR;

import IR.IRModule;
import IR.Type.IntegerType;
import IR.Type.PointerType;
import IR.Value.*;
import IR.Value.Instructions.*;
import Pass.IR.Utils.AccessAnalysis;
import Pass.IR.Utils.AliasAnalysis;
import Pass.IR.Utils.InterproceduralAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicBoolean;




public class UniqueConst implements Pass.IRPass {

    @Override
    public String getName() {
        return "UniqueConst";
    }

    @Override
    public void run(IRModule module) {
        for(var f : module.functions()){
            runForFunc(f);
        }
    }

    public boolean check(IRPass pass, IRModule module){
        LinkedHashSet<Const> consts = new LinkedHashSet<>();
        for(var f : module.functions()){
            for(var bbnode : f.getBbs()){
                for(var instnode : bbnode.getValue().getInsts()){
                    for(var op : instnode.getValue().getOperands()){
                        if(!(op instanceof Const))continue;
                        if(!consts.contains(op)){
                            consts.add((Const) op);
                        }else{
                            System.err.println(pass.getName() + "has duplicate const");
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private void runForFunc(Function function){
        for(var bbnode : function.getBbs()){
            for(var instnode : bbnode.getValue().getInsts()){
                var inst = instnode.getValue();
                for(int i = 0;i<inst.getOperands().size();i++){
                    if(inst.getOperand(i) instanceof ConstInteger oric){
                        inst.replaceOperand(i, new ConstInteger(oric.getValue(), oric.getType()));
                    }
                    else if(inst.getOperand(i) instanceof ConstFloat oric){
                        inst.replaceOperand(i, new ConstFloat(oric.getValue()));
                    }
                }
            }
        }
    }

}
