package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Value.Argument;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.*;
import IR.Value.Value;
import Pass.Pass;
import Utils.DataStruct.IList;

import static Pass.IR.Utils.UtilFunc.buildCallRelation;
import java.util.ArrayList;
import java.util.Objects;

public class TailRecursiveElimination implements Pass.IRPass {
    ArrayList<Function> tailRecFunctions;
    IRBuildFactory f = IRBuildFactory.getInstance();

    @Override
    public void run(IRModule module) {
        buildCallRelation(module);
        tailRecFunctions = new ArrayList<>();
        for (Function function : module.functions()) {
            if (canEliminate(function)) {
                runEliminationForFunc(function);
            }
        }
    }

    private void runEliminationForFunc(Function function) {
        ArrayList<RetInst> retInsts = new ArrayList<>();
        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof RetInst retInst && retInst.getValue() instanceof CallInst) {
                        retInsts.add(retInst);
                }
            }
        }

        //  这里限制call和return必须是相邻的
        for (RetInst retInst : retInsts) {
            CallInst callInst = (CallInst) retInst.getValue();
            IList.INode<Instruction, BasicBlock> instNode = callInst.getNode().getNext();
            if (instNode.getValue() != retInst) return ;
        }
        //  1. 修改ret -> Br
        BasicBlock entry = function.getBbEntry();
        for (RetInst retInst : retInsts) {
            f.buildBrInst(entry, retInst.getParentbb());
        }

        //  2. 构建phi指令记录更新的参数值
        ArrayList<Argument> actualParams = function.getArgs();
        for (int i = 0; i < actualParams.size(); i++) {
            Value param = actualParams.get(i);
            ArrayList<Value> values = new ArrayList<>();
            values.add(null);
            for (RetInst retInst : retInsts) {
                CallInst callInst = (CallInst) retInst.getValue();
                Value newParam = callInst.getOperand(i);
                values.add(newParam);
            }
            Phi phi = f.buildPhi(entry, param.getType(), values);
            param.replaceUsedWith(phi);
            phi.replaceOperand(0, param);
        }

        //  3. 新建入口基本块，修正前驱后继关系
        BasicBlock newEntry = f.getBasicBlock(function);
        newEntry.insertBefore(entry);
        f.buildBrInst(entry, newEntry);
        entry.setPreBlock(newEntry);
        newEntry.setNxtBlock(entry);
        for (RetInst retInst : retInsts) {
            BasicBlock retBb = retInst.getParentbb();
            entry.setPreBlock(retBb);
            retBb.setNxtBlock(entry);
        }

        //  4. 删除多余指令
        for (RetInst retInst : retInsts) {
            CallInst callInst = (CallInst) retInst.getValue();
            retInst.removeSelf();
            callInst.removeSelf();
        }
    }

    private boolean canEliminate(Function function) {
        if (function.getCalleeList().size() != 1) return false;
        if (!Objects.equals(function.getCalleeList().get(0).getName(), function.getName())) return false;
        for (Value value : function.getArgs()) {
            if (!value.getType().isIntegerTy() && !value.getType().isFloatTy()) return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "TailRecursiveElimination";
    }
}
