package Pass.IR.Utils;

import IR.IRBuildFactory;
import IR.Type.IntegerType;
import IR.Type.Type;
import IR.Value.*;
import IR.Value.Instructions.*;
import Utils.DataStruct.IList;

import java.util.*;

import static Pass.IR.Utils.UtilFunc.makeCFG;

//  如何使用CloneFactory
//  首先
public class CloneHelper {

    private static final IRBuildFactory f = IRBuildFactory.getInstance();

    private final LinkedHashMap<Value, Value> valueMap = new LinkedHashMap<>();

    public void clear(){
        valueMap.clear();
    }

    public void addValueMapping(Value key, Value value){
        valueMap.put(key, value);
    }

    public Function copyFunction(Function srcFunction, ArrayList<GlobalVar> globalVars){
        Function copyFunc = f.getFunction(srcFunction.getName(), srcFunction.getType());
        LinkedHashSet<BasicBlock> visitedMap = new LinkedHashSet<>();
        ArrayList<Argument> srcArgs = srcFunction.getArgs();
        //  初始化数据结构
        for (Argument arg : srcArgs) {
            Argument copyArg;
            Type type = arg.getType();
            copyArg = f.getArgument(arg.getName(), type, copyFunc);
            copyFunc.addArg(copyArg);
            valueMap.put(arg, copyArg);
        }
        for (Value globalVar : globalVars) {
            valueMap.put(globalVar, globalVar);
        }

        for (IList.INode<BasicBlock, Function> bbNode : srcFunction.getBbs()) {
            BasicBlock newBlock = f.buildBasicBlock(copyFunc);
            valueMap.put(bbNode.getValue(), newBlock);
        }

        Stack<BasicBlock> dfsStack = new Stack<>();
        dfsStack.push(srcFunction.getBbEntry());
        while (!dfsStack.isEmpty()) {
            BasicBlock loopBlock = dfsStack.pop();
            BasicBlock newBlock = ((BasicBlock) valueMap.get(loopBlock));
            copyBlockToBlock(loopBlock, newBlock);
            if(!loopBlock.getNxtBlocks().isEmpty()) {
                for (BasicBlock basicBlock : loopBlock.getNxtBlocks()) {
                    if (!visitedMap.contains(basicBlock)) {
                        visitedMap.add(basicBlock);
                        dfsStack.push(basicBlock);
                    }
                }
            }
        }

        //  这些block间的前驱后继关系需要建立一下
        makeCFG(copyFunc);

        ArrayList<Phi> phiArrayList = new ArrayList<>();
        for (IList.INode<BasicBlock, Function> bbNode : srcFunction.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof Phi) {
                    phiArrayList.add((Phi) inst);
                }
            }
        }

        //  修改phi指令中的前驱基本块
        for (Phi phi : phiArrayList) {
            for (int i = 0; i < phi.getOperands().size(); i++) {
                BasicBlock preBB = phi.getParentbb().getPreBlocks().get(i);
                BasicBlock nowBB = (BasicBlock) valueMap.get(preBB);
                Phi copyPhi = (Phi) valueMap.get(phi);
                int index = copyPhi.getParentbb().getPreBlocks().indexOf(nowBB);

                Value value = phi.getOperand(i);
                Value copyValue;
                if(value instanceof ConstInteger){
                    int val = ((ConstInteger) value).getValue();
                    copyValue = new ConstInteger(val, IntegerType.I32);
                }
                else if(value instanceof ConstFloat){
                    float val = ((ConstFloat) value).getValue();
                    copyValue = new ConstFloat(val);
                }
                else copyValue = valueMap.get(value);
                copyPhi.replaceOperand(index, copyValue);
            }
        }

        return copyFunc;
    }

    public void copyBlockToBlock(BasicBlock srcBlock, BasicBlock dstBlock){
        for (IList.INode<Instruction, BasicBlock> instNode : srcBlock.getInsts()) {
            Instruction inst = instNode.getValue();
            Instruction copyInst = copyInstruction(inst);
            dstBlock.addInst(copyInst);
            valueMap.put(inst, copyInst);
        }
    }

    public BasicBlock copyBlock(BasicBlock srcBlock){
        BasicBlock newBlock = f.getBasicBlock(srcBlock.getParentFunc());
        copyBlockToBlock(srcBlock, newBlock);
        valueMap.put(srcBlock, newBlock);
        return newBlock;
    }

    public Instruction copyInstruction(Instruction inst){
        Instruction copyInst = null;
        if(inst instanceof AllocInst allocInst){
            if(allocInst.isArray()){
                copyInst = f.getAllocInst(allocInst.getAllocType(), allocInst.getSize());
            }
            else copyInst = f.getAllocInst(allocInst.getAllocType());
        }
        else if(inst instanceof LoadInst loadInst){
            copyInst = f.getLoadInst(findValue(loadInst.getPointer()));
        }
        else if(inst instanceof StoreInst storeInst){
            Value value = findValue(storeInst.getValue());
            Value pointer = findValue(storeInst.getPointer());
            copyInst = f.getStoreInst(value, pointer);
        }
        //  由于我们还没完全建立所有的基本块，无法确定preBlocks的顺序
        //  所以我们在其他value都建完之后再进行phi的赋值
        //  这里只要先建个phi占位即可
        else if(inst instanceof Phi phi){
            int length = phi.getOperands().size();
            ArrayList<Value> copyValues;
            if(phi.getType().isIntegerTy()) {
                copyValues = new ArrayList<>(Collections.nCopies(length, f.buildNumber(0)));
            }
            else copyValues = new ArrayList<>(Collections.nCopies(length, f.buildNumber((float) 0.0)));
            copyInst = f.getPhi(phi.getType(), copyValues);
        }
        else if(inst instanceof BrInst brInst){
            if(!brInst.isJump()){
                Value judVal = findValue(brInst.getJudVal());
                BasicBlock left = (BasicBlock) findValue(brInst.getTrueBlock());
                BasicBlock right = (BasicBlock) findValue(brInst.getFalseBlock());
                copyInst = f.getBrInst(judVal, left, right);
            }
            else{
                BasicBlock jumpBb = (BasicBlock) findValue(brInst.getJumpBlock());
                copyInst = f.getBrInst(jumpBb);
            }
        }
        else if(inst instanceof RetInst retInst){
            if (!retInst.isVoid()) {
                Value retVal = findValue(retInst.getValue());
                copyInst = f.getRetInst(retVal);
            }
            else {
                copyInst = f.getRetInst();
            }
        }
        else if(inst instanceof CallInst callInst){
            ArrayList<Value> args = new ArrayList<>();
            for (int i = 0; i < callInst.getOperands().size(); i++) {
                args.add(findValue(callInst.getOperands().get(i)));
            }

            copyInst = f.getCallInst(callInst.getFunction(), args);
        }
        else if(inst instanceof BinaryInst binaryInst){
            OP op = binaryInst.getOp();
            Value left = findValue(binaryInst.getLeftVal());
            Value right = findValue(binaryInst.getRightVal());
            copyInst = f.getBinaryInst(left, right, op, binaryInst.getType());
            ((BinaryInst)copyInst).I64 = binaryInst.I64;
        }
        else if(inst instanceof PtrInst ptrInst){
            Value pointer = findValue(ptrInst.getTarget());
            Value offset = findValue(ptrInst.getOffset());
            copyInst = f.getPtrInst(pointer, offset);
        }
        else if(inst instanceof ConversionInst conversionInst){
            Value src = findValue(conversionInst.getValue());
            copyInst = f.getConversionInst(src, conversionInst.getOp());
        }
        return copyInst;
    }

    public void copyPreNxt(BasicBlock srcBb, BasicBlock dstBb){
        for(BasicBlock preBb : srcBb.getPreBlocks()){
            dstBb.setPreBlock((BasicBlock) valueMap.get(preBb));
        }
        for(BasicBlock nxtBb : srcBb.getNxtBlocks()){
            dstBb.setNxtBlock((BasicBlock) valueMap.get(nxtBb));
        }
    }

    public Value findValue(Value value) {
        if (value instanceof ConstInteger || value instanceof ConstFloat) {
            return value;
        }
        else {
            if(valueMap.get(value) == null){
                return value;
            }
            return valueMap.get(value);
        }
    }

}
