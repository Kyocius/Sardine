package Pass.IR;

import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.Instruction;
import IR.Value.Instructions.*;
import IR.Value.Value;
import Pass.IR.Utils.InterproceduralAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.*;

public class DCE implements Pass.IRPass {

    @Override
    public String getName() {
        return "DCE";
    }

    private final LinkedHashSet<Instruction> usefulInstrClosure = new LinkedHashSet<>();
    @Override
    public void run(IRModule module) {
        ArrayList<Function> functions = module.functions();
        InterproceduralAnalysis.run(module);
        for (Function function : functions){
            runDCE(function);
        }
    }

    private void runDCE(Function function){
        usefulInstrClosure.clear();
        ArrayList<Instruction> deleteInsts = new ArrayList<>();
        for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()){
            BasicBlock bb = bbNode.getValue();
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (isUseful(inst)) {
                    findUsefulClosure(inst);
                }
            }
        }


        // 删除不在闭包内的指令
        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (!usefulInstrClosure.contains(inst)) {
                    deleteInsts.add(inst);
                }
            }
        }

        for(Instruction deleteInst : deleteInsts){
            deleteInst.removeSelf();
        }
    }

    private void findUsefulClosure(Instruction inst){
        if (!usefulInstrClosure.contains(inst)) {
            // 记录所有用到的指令
            usefulInstrClosure.add(inst);
            for (Value operand : inst.getUseValues()) {
                if (operand instanceof Instruction) {
                    findUsefulClosure((Instruction) operand);
                }
            }
        }
    }

    private boolean isUseful(Instruction inst) {
        if(inst instanceof CallInst callInst){
            Function function = callInst.getFunction();
            return function.isLibFunction() || function.isMayHasSideEffect();
        }
        return inst instanceof BrInst ||
                inst instanceof RetInst || inst instanceof StoreInst;
    }
}

