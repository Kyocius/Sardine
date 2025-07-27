package Pass.IR;

import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.*;
import IR.Value.Value;
import Pass.IR.Utils.DomAnalysis;
import Pass.IR.Utils.InterproceduralAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.*;

public class GVN implements Pass.IRPass {

    private final LinkedHashMap<String, Value> GVNMap = new LinkedHashMap<>();

    @Override
    public void run(IRModule module) {
        InterproceduralAnalysis.run(module);
        for(Function function : module.functions()){
            if (function.isLibFunction()) {
                continue;
            }
            runGVNForFunc(function);
        }
    }

    private void runGVNForFunc(Function function){
        GVNMap.clear();
        DomAnalysis.run(function);
        BasicBlock entry = function.getBbEntry();

        RPOSearch(entry);
    }

    private void RPOSearch(BasicBlock bb){
        IList.INode<Instruction, BasicBlock> itInstNode = bb.getInsts().getHead();
        LinkedHashSet<Instruction> numberedInsts = new LinkedHashSet<>();
        while (itInstNode != null){
            Instruction inst = itInstNode.getValue();
            itInstNode = itInstNode.getNext();
            if (canGVN(inst)){
                boolean isNumbered = runGVNOnInst(inst);
                if(isNumbered){
                    numberedInsts.add(inst);
                }
            }
        }

        for(BasicBlock idomBb : bb.getIdoms()){
            RPOSearch(idomBb);
        }

        for(Instruction inst : numberedInsts){
            removeInstFromGVN(inst);
        }
    }

    private void removeInstFromGVN(Instruction inst){
        String hash = getHash(inst);
        GVNMap.remove(hash);
        if(inst instanceof BinaryInst binaryInst && canSwap(binaryInst.getOp())){
            String hash_2 = getSwapHash(binaryInst);
            GVNMap.remove(hash_2);
        }
    }

    private void addToGVNMap(String string, Instruction value){
        GVNMap.put(string, value);
    }

    private boolean runGVNOnInst(Instruction inst) {
        if (inst instanceof BinaryInst binaryInst) {
            String hash_1 = getHash(binaryInst);
            if (GVNMap.containsKey(hash_1)) {
                inst.replaceUsedWith(GVNMap.get(hash_1));
                inst.removeSelf();
                return false;
            }
            if (canSwap(binaryInst.getOp())) {
                String hash_2 = getSwapHash(binaryInst);
                if (GVNMap.containsKey(hash_2)) {
                    inst.replaceUsedWith(GVNMap.get(hash_2));
                    inst.removeSelf();
                    return false;
                }
                addToGVNMap(hash_1, inst);
                addToGVNMap(hash_2, inst);
            }
            else {
                addToGVNMap(hash_1, inst);
            }
            return true;
        }
        else if (inst instanceof CallInst || inst instanceof PtrInst
                || inst instanceof ConversionInst) {
            String hash = getHash(inst);
            if (GVNMap.containsKey(hash)) {
                inst.replaceUsedWith(GVNMap.get(hash));
                inst.removeSelf();
                return false;
            }
            addToGVNMap(hash, inst);
            return true;
        }
        return false;
    }

    //  为inst创建hash
    private String getHash(Instruction inst){
        if(inst instanceof BinaryInst binaryInst){
            String leftName = binaryInst.getLeftVal().getName();
            OP op = binaryInst.getOp();
            String rightName = binaryInst.getRightVal().getName();
            return leftName + op.name() + rightName;
        }
        else if(inst instanceof CallInst callInst){
            StringBuilder hashBuilder = new StringBuilder(callInst.getFunction().getName() + "(");
            ArrayList<Value> params = callInst.getParams();
            for (int i = 0; i < params.size(); i++) {
                hashBuilder.append(params.get(i).getName());
                if (i < params.size() - 1) {
                    hashBuilder.append(", ");
                }
            }
            hashBuilder.append(")");
            return hashBuilder.toString();
        }
        else if(inst instanceof PtrInst ptrInst){
            String targetName = ptrInst.getTarget().getName();
            String offsetName = ptrInst.getOffset().getName();
            return targetName + "ptr" + offsetName;
        }
        else if(inst instanceof ConversionInst conversionInst) {
            return conversionInst.getInstString();
        }
        return null;
    }

    //  部分binaryInst满足交换律，存在第二个hash
    private String getSwapHash(BinaryInst binaryInst){
        String leftName = binaryInst.getLeftVal().getName();
        OP op = binaryInst.getOp();
        String rightName = binaryInst.getRightVal().getName();
        return rightName + op.name() + leftName;
    }

    private boolean canGVN(Instruction inst){
        if(inst instanceof CallInst callInst){
            Function function = callInst.getFunction();
            return !function.isLibFunction() && !function.isMayHasSideEffect();
        }
        return inst instanceof BinaryInst || inst instanceof PtrInst || inst instanceof ConversionInst;
    }

    private boolean canSwap(OP op){
        return op == OP.Add || op == OP.Fadd
                || op == OP.Mul || op == OP.Fmul
                || op == OP.Eq || op == OP.Ne
                || op == OP.FEq || op == OP.FNe;
    }


    @Override
    public String getName() {
        return "GVN";
    }
}
