package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Type.IntegerType;
import IR.Value.*;
import IR.Value.Instructions.*;
import Pass.IR.Utils.UtilFunc;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;

import static Pass.IR.Utils.UtilFunc.makeCFG;

public class ConstFold implements Pass.IRPass {
    private static final IRBuildFactory f = IRBuildFactory.getInstance();
    private ConstInteger CurConstInt;
    private ConstFloat CurConstFloat;

    //  这里的foldBrInst跨基本块，
    @Override
    public void run(IRModule module) {
        foldCalculateInst(module);
        foldBrInst(module);
        for(Function function : module.functions()){
            makeCFG(function);
        }
    }

    private void foldBrInst(IRModule module){
        for(Function function : module.functions()){
            for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                BasicBlock bb = bbNode.getValue();
                for(IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()){
                    Instruction inst = instNode.getValue();
                    if(!(inst instanceof BrInst brInst)){
                        continue;
                    }
                    if(brInst.isJump()){
                        continue;
                    }
                    Value judVal = brInst.getJudVal();
                    if(!(judVal instanceof ConstInteger constInt)){
                        continue;
                    }
                    BasicBlock removedNxtBb;
                    if(constInt.getValue() == 1){
                        removedNxtBb = brInst.getFalseBlock();
                        ArrayList<Phi> phis = UtilFunc.getPhiInBb(removedNxtBb);
                        int idx = removedNxtBb.getPreBlocks().indexOf(bb);
                        for(Phi phi : phis){
                            phi.removeOperand(idx);
                        }
                        brInst.turnToJump(brInst.getTrueBlock());
                    }
                    else{
                        removedNxtBb = brInst.getTrueBlock();
                        ArrayList<Phi> phis = UtilFunc.getPhiInBb(removedNxtBb);
                        int idx = removedNxtBb.getPreBlocks().indexOf(bb);
                        for(Phi phi : phis){
                            phi.removeOperand(idx);
                        }
                        brInst.turnToJump(brInst.getFalseBlock());
                    }
                    bb.removeNxtBlock(removedNxtBb);
                    removedNxtBb.removePreBlock(bb);
                }
            }
        }
    }

    private void foldCalculateInst(IRModule module){
        for(Function function : module.functions()){
            for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()){
                BasicBlock bb = bbNode.getValue();
                IList.INode<Instruction, BasicBlock> itInstNode = bb.getInsts().getHead();
                while (itInstNode != null){
                    Instruction inst = itInstNode.getValue();
                    itInstNode = itInstNode.getNext();

                    //  simplifyInst得到的Inst有两种情况：
                    //  一种是原指令，说明无法优化
                    //  另一种为简化后的Inst/Value，这种情况我们直接replaceUsedWith
                    Value value = simplifyInst(inst);
                    if(!inst.equals(value)){
                        inst.replaceUsedWith(value);
                        inst.removeSelf();
                    }
                }
            }
        }
    }


    private Value simplifyInst(Instruction inst){
        if(inst instanceof BinaryInst binaryInst){
            return simplifyBinaryInst(binaryInst);
        }
        else if(inst instanceof ConversionInst conversionInst){
            return simplifyConversionInst(conversionInst);
        }
        return inst;
    }

    private Value simplifyConversionInst(ConversionInst conversionInst){
        OP op = conversionInst.getOp();
        if(op.equals(OP.Zext)){
            return simplifyZextInst(conversionInst);
        }
        else if(op.equals(OP.Ftoi)){
            return simplifyFtoiInst(conversionInst);
        }
        else if(op.equals(OP.Itof)){
            return simplifyItofInst(conversionInst);
        }
        return conversionInst;
    }

    private Value simplifyBinaryInst(BinaryInst binaryInst){
        OP op = binaryInst.getOp();
        if(op == OP.Add || op == OP.Fadd){
            return simplifyAddInst(binaryInst);
        }
        else if(op == OP.Sub || op == OP.Fsub){
            return simplifySubInst(binaryInst);
        }
        else if(op == OP.Mul || op == OP.Fmul){
            return simplifyMulInst(binaryInst);
        }
        else if(op == OP.Div || op == OP.Fdiv){
            return simplifyDivInst(binaryInst);
        }
        else if(op == OP.Mod || op == OP.Fmod){
            return simplifyModInst(binaryInst);
        }
        else if(op == OP.Eq || op == OP.FEq
                || op == OP.Ne || op == OP.FNe
                || op == OP.Le || op == OP.FLe
                || op == OP.Lt || op == OP.FLt
                || op == OP.Ge || op == OP.FGe
                || op == OP.Gt || op == OP.FGt){
            return simplifyCmpInst(binaryInst);
        }
        else return binaryInst;
    }

    private Value simplifyCmpInst(BinaryInst cmpInst){
        //  1. fold常量
        Value tmp = foldConstant(cmpInst);
        if (tmp != null) {
            return tmp;
        }

        if(cmpInst.getOp() == OP.Eq) {
            Value left = cmpInst.getLeftVal();
            Value right = cmpInst.getRightVal();

            //  2. a == a
            if (left.equals(right)) {
                return f.buildNumber(0);
            }
        }

        return cmpInst;
    }

    private Value simplifyAddInst(BinaryInst addInst){
        //  1. fold常量
        Value tmp = foldConstant(addInst);
        if (tmp != null) {
            return tmp;
        }

        boolean isInt = addInst.getType().isIntegerTy();
        Value left = addInst.getLeftVal();
        Value right = addInst.getRightVal();

        //  2. +0优化
        if(isInt) {
            if (left instanceof ConstInteger && ((ConstInteger) left).getValue() == 0) {
                return right;
            }
            if (right instanceof ConstInteger && ((ConstInteger) right).getValue() == 0) {
                return left;
            }
        }
        else {
            if (left instanceof ConstFloat && ((ConstFloat) left).getValue() == 0) {
                return right;
            }
            if (right instanceof ConstFloat && ((ConstFloat) right).getValue() == 0) {
                return left;
            }
        }

        //  3. left + right == 0
        //  原理是constInt - x + x = 0
        if (left instanceof Instruction leftInst) {
            if (tryAddToConst(right, leftInst)) {
                return isInt ? CurConstInt : CurConstFloat;
            }
        }
        if (right instanceof Instruction rightInst) {
            if (tryAddToConst(left, rightInst)) {
                return isInt ? CurConstInt : CurConstFloat;
            }
        }

        return addInst;
    }

    private Value simplifySubInst(BinaryInst subInst){
        //  1. fold常量
        Value tmp = foldConstant(subInst);
        if (tmp != null) {
            return tmp;
        }

        boolean isInt = subInst.getType().isIntegerTy();
        Value left = subInst.getLeftVal();
        Value right = subInst.getRightVal();

        // 2、 -0 优化
        if (isInt) {
            if (right instanceof ConstInteger && ((ConstInteger) right).getValue() == 0) {
                return left;
            }
        }
        else {
            // FIXME 精度问题
            if (right instanceof ConstFloat && ((ConstFloat) right).getValue() == 0) {
                return left;
            }
        }

        // 3、 left == right
        if (left.equals(right)) {
            return isInt ? f.buildNumber(0) : f.buildNumber((float) 0.0);
        }

        return subInst;

    }

    private Value simplifyMulInst(BinaryInst mulInst){
        //  1. fold常量
        Value tmp = foldConstant(mulInst);
        if (tmp != null) {
            return tmp;
        }

        boolean isInt = mulInst.getType().isIntegerTy();
        Value left = mulInst.getLeftVal();
        Value right = mulInst.getRightVal();

        // 2、*0 *1
        if (isInt) {
            if (left instanceof ConstInteger) {
                int val = ((ConstInteger) left).getValue();
                if (val == 0) {
                    return f.buildNumber(0);
                }
                else if (val == 1) {
                    return right;
                }
            }
            if (right instanceof ConstInteger) {
                int val = ((ConstInteger) right).getValue();
                if (val == 0) {
                    return f.buildNumber(0);
                }
                else if (val == 1) {
                    return left;
                }
            }
        }
        else {
            if (left instanceof ConstFloat) {
                float val = ((ConstFloat) left).getValue();
                if (val == 0) {
                    return f.buildNumber((float) 0.0);
                }
                else if (val == 1) {
                    return right;
                }
            }
            if (right instanceof ConstFloat) {
                float val = ((ConstFloat) right).getValue();
                if (val == 0) {
                    return f.buildNumber((float) 0.0);
                }
                else if (val == 1) {
                    return left;
                }
            }
        }

        return mulInst;
    }

    private Value simplifyDivInst(BinaryInst divInst){
        //  1. fold常量
        Value tmp = foldConstant(divInst);
        if (tmp != null) {
            return tmp;
        }

        boolean isInt = divInst.getType().isIntegerTy();
        Value left = divInst.getLeftVal();
        Value right = divInst.getRightVal();

        // /1   |   0/
        if (isInt) {
            if (right instanceof ConstInteger && ((ConstInteger) right).getValue() == 1) {
                return left;
            }
            if (left instanceof ConstInteger && ((ConstInteger) left).getValue() == 0) {
                return f.buildNumber(0);
            }
        }
        else {
            if (right instanceof ConstFloat && ((ConstFloat) right).getValue() == 1) {
                return left;
            }
            if (left instanceof ConstFloat && ((ConstFloat) left).getValue() == 0) {
                return f.buildNumber((float) 0.0);
            }
        }

        // a = a * b / b;
        //  先不考虑浮点数（精度误差）
        if(isInt){
            if(left instanceof BinaryInst binInst && binInst.getOp().equals(OP.Mul)){
                Value binLeftValue = binInst.getLeftVal();
                Value binRightValue = binInst.getRightVal();
                if(binLeftValue.equals(right)){
                    return binRightValue;
                }
                else if(binRightValue.equals(right)){
                    return binLeftValue;
                }
            }
        }
        return divInst;
    }

    private Value simplifyModInst(BinaryInst modInst){
        //  1. fold常量
        Value tmp = foldConstant(modInst);
        if (tmp != null) {
            return tmp;
        }

        boolean isInt = modInst.getType().isIntegerTy();
        Value left = modInst.getLeftVal();
        Value right = modInst.getRightVal();

        // 2、 a % a == 0; 0 % a = 0; a % 1 = 0;
        if(isInt) {
            if (left.equals(right)
                    || (left instanceof ConstInteger constIntLeft && constIntLeft.getValue() == 0)
                    || (right instanceof ConstInteger constInteger && constInteger.getValue() == 1)) {
                return f.buildNumber(0);
            }
        }
        else{
            if (left.equals(right)
                    || (left instanceof ConstFloat constFloatLeft && constFloatLeft.getValue() == 0.0)
                    || (right instanceof ConstFloat constFloatRight && constFloatRight.getValue() == 1.0)) {
                return f.buildNumber((float) 0.0);
            }
        }
        return modInst;
    }

    private Value simplifyZextInst(ConversionInst zextInst){
        Value value = zextInst.getValue();
        if (value instanceof ConstInteger constValue) {
            return new ConstInteger(constValue.getValue(), IntegerType.I32);
        }
        return zextInst;
    }

    private Value simplifyFtoiInst(ConversionInst ftoiInst){
        Value value = ftoiInst.getValue();
        if (value instanceof ConstFloat constFloat) {
            return new ConstInteger((int) constFloat.getValue(), IntegerType.I32);
        }
        return ftoiInst;
    }

    private Value simplifyItofInst(ConversionInst itofInst){
        Value value = itofInst.getValue();
        if (value instanceof ConstInteger constInteger) {
            return new ConstFloat((float) constInteger.getValue());
        }
        return itofInst;
    }

    private boolean tryAddToConst(Value value, Instruction inst){
        if (inst.getOp() == OP.Sub) {
            Value left = inst.getOperands().get(0);
            Value right = inst.getOperands().get(1);
            if (left instanceof ConstInteger constInteger) {
                CurConstInt = constInteger;
                return right.equals(value);
            }
        }
        if (inst.getOp() == OP.Fsub) {
            Value left = inst.getOperands().get(0);
            Value right = inst.getOperands().get(1);
            if (left instanceof ConstFloat constFloat) {
                CurConstFloat = constFloat;
                return right.equals(value);
            }
        }
        return false;
    }

    /*
    * 将binaryInst化简为常数
    * 如果可以返回常数
    * 否则返回null
    *
    * */
    private Value foldConstant(BinaryInst inst){
        OP op = inst.getOp();
        Value left = inst.getLeftVal();
        Value right = inst.getRightVal();
        if(left instanceof ConstInteger && right instanceof ConstInteger){
            int leftVal = ((ConstInteger) left).getValue();
            int rightVal = ((ConstInteger) right).getValue();
            return new ConstInteger(calculate(leftVal, rightVal, op), inst.getType());
        }
        else if(left instanceof ConstFloat && right instanceof ConstFloat){
            float leftVal = ((ConstFloat) left).getValue();
            float rightVal = ((ConstFloat) right).getValue();
            //  特例：fcmp等返回的是整数值
            if(inst.getOp().isCmpOP()){
                return new ConstInteger((int) calculate(leftVal, rightVal, op), inst.getType());
            }
            return new ConstFloat(calculate(leftVal, rightVal, op));
        }
        return null;
    }


    private int calculate(int a, int b, OP op){
        if(op == OP.Add){
            return a + b;
        }
        else if(op == OP.Sub) {
            return a - b;
        }
        else if(op == OP.Mul){
            return a * b;
        }
        else if(op == OP.Div){
            return a / b;
        }
        else if(op == OP.Mod){
            return a % b;
        }
        else if(op == OP.Lt){
            return a < b ? 1 : 0;
        }
        else if(op == OP.Le){
            return a <= b ? 1 : 0;
        }
        else if(op == OP.Gt){
            return a > b ? 1 : 0;
        }
        else if(op == OP.Ge){
            return a >= b ? 1 : 0;
        }
        else if(op == OP.Eq){
            return a == b ? 1 : 0;
        }
        else if(op == OP.Ne){
            return a != b ? 1 : 0;
        }
        else return 0;
    }
    private float calculate(float a, float b, OP op){
        if(op == OP.Fadd){
            return a + b;
        }
        else if(op == OP.Fsub) {
            return a - b;
        }
        else if(op == OP.Fmul){
            return a * b;
        }
        else if(op == OP.Fdiv){
            return a / b;
        }
        else if(op == OP.Fmod){
            return a % b;
        }
        else if(op == OP.FLt){
            return a < b ? 1 : 0;
        }
        else if(op == OP.FLe){
            return a <= b ? 1 : 0;
        }
        else if(op == OP.FGt){
            return a > b ? 1 : 0;
        }
        else if(op == OP.FGe){
            return a >= b ? 1 : 0;
        }
        else if(op == OP.FEq){
            return a == b ? 1 : 0;
        }
        else if(op == OP.FNe){
            return a != b ? 1 : 0;
        }
        else return 0;
    }
    @Override
    public String getName() {
        return "ConstFold";
    }
}
