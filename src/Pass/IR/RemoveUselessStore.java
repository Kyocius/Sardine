package Pass.IR;

import IR.IRModule;
import IR.Type.PointerType;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.*;
import IR.Value.Value;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class RemoveUselessStore implements Pass.IRPass {

    @Override
    public String getName() {
        return "RemoveUselessStore";
    }

    @Override
    public void run(IRModule module) {
        for(Function function : module.functions()){
            ArrayList<Instruction> deleteInsts = new ArrayList<>();
            for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()){
                BasicBlock bb = bbNode.getValue();
                LinkedHashMap<Value, StoreInst> storePtrMap = new LinkedHashMap<>();
                for(IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()){
                    Instruction inst = instNode.getValue();
                    if(inst instanceof StoreInst storeInst){
                        Value pointer = storeInst.getPointer();
                        if(storePtrMap.containsKey(pointer)){
                            StoreInst lastInst = storePtrMap.get(pointer);
                            deleteInsts.add(lastInst);
                        }
                        storePtrMap.put(pointer, storeInst);
                    }
                    else if(mayUsePtr(inst)){
                        storePtrMap.clear();
                    }
                }
            }

            for(Instruction deleteInst : deleteInsts){
                deleteInst.removeSelf();
            }
        }
    }

    private boolean mayUsePtr(Instruction inst){
        if(inst instanceof AllocInst || inst instanceof BinaryInst
                || inst instanceof BrInst || inst instanceof ConversionInst
                || inst instanceof Phi || inst instanceof RetInst){
            return false;
        }
        if(inst instanceof PtrInst || inst instanceof LoadInst
                || inst instanceof StoreInst) return true;
        if(inst instanceof CallInst callInst){
            for(Value value : callInst.getOperands()){
                if(value.getType() instanceof PointerType){
                    return true;
                }
            }
        }
        return false;
    }
}
