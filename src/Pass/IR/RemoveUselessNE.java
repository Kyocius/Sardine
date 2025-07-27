package Pass.IR;

import IR.IRModule;
import IR.Type.IntegerType;
import IR.Value.ConstInteger;
import IR.Value.Function;
import IR.Value.Instructions.*;
import IR.Value.Value;
import Pass.Pass;

public class RemoveUselessNE implements Pass.IRPass {

    @Override
    public String getName() {
        return "RemoveUselessNE";
    }

    @Override
    public void run(IRModule module) {
        for (Function function : module.functions()) {
            for (var bbnode : function.getBbs()) {
                var bb = bbnode.getValue();
                for (var instnode : bb.getInsts()) {
                    var inst = instnode.getValue();
                    if (inst instanceof BinaryInst binaryInst
                            && binaryInst.getOp() == OP.Ne) {
                        Value necmpValue;
                        if(binaryInst.getOperand(0) instanceof ConstInteger cn && cn.getValue()==0){
                            necmpValue = binaryInst.getOperand(1);
                        }else if(binaryInst.getOperand(1) instanceof ConstInteger cn && cn.getValue()==0){
                            necmpValue = binaryInst.getOperand(0);
                        }else{
                            continue;
                        }
                        if(necmpValue instanceof BinaryInst necmpbi){
                            necmpbi.toLLVMString(); //using to get type
                            if(necmpbi.getType() == IntegerType.I1){
                                binaryInst.replaceUsedWith(necmpbi);
                            }
                        }
                    }
                }
            }
        }
        var dce = new DCE();
        dce.run(module);
    }

}
