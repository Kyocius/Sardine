package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Type.IntegerType;
import IR.Use;
import IR.Value.*;
import IR.Value.Instructions.BinaryInst;
import IR.Value.Instructions.Instruction;
import IR.Value.Instructions.OP;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;

public class InstComb implements Pass.IRPass {
    private final IRBuildFactory f = IRBuildFactory.getInstance();

    @Override
    public void run(IRModule module) {
        for(Function function : module.functions()){
            constInstCombForFunc(function);
        }
        for(Function function : module.functions()){
            instCombForFunc(function);
        }
        for (Function function : module.functions()) {
            CmpInstCombForFunc(function);
        }
        for (Function function : module.functions()) {
            DivCombForFunc(function);
        }
    }

    private void DivCombForFunc(Function function) {
        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof BinaryInst binInst
                        && binInst.getOp() == OP.Div
                        && (binInst.getRightVal() instanceof ConstInteger constInt)
                        && (binInst.getLeftVal() instanceof BinaryInst left)
                        && left.getOp() == OP.Div
                        && left.getRightVal() instanceof ConstInteger constInt2
                        && constInt.getValue() * constInt2.getValue() < 1e9) {
                    inst.replaceOperand(0, left.getOperand(0));
                    inst.replaceOperand(1, new ConstInteger(constInt.getValue() * constInt2.getValue(), IntegerType.I32));
                }
            }
        }
    }

    //  i * const < const, i * i < const
    private void CmpInstCombForFunc(Function function) {
        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof BinaryInst binInst
                        && (binInst.getOp() == OP.Le || binInst.getOp() == OP.Lt || binInst.getOp() == OP.Ge || binInst.getOp() == OP.Gt)) {
                    CmpInstComb(binInst);
                }
            }
        }
    }

    private void CmpInstComb(BinaryInst cmpInst) {
        Value left = cmpInst.getLeftVal(), right = cmpInst.getRightVal();
        OP op = cmpInst.getOp();
        if (left instanceof BinaryInst binInst && right instanceof ConstInteger constInt) {
            Value binLeft = binInst.getLeftVal(), binRight = binInst.getRightVal();
            OP binOP = binInst.getOp();
            int val = constInt.getValue();
            if (binRight instanceof ConstInteger constInt2) {
                int val2 = constInt2.getValue();
                if (binOP == OP.Add) {
                    cmpInst.replaceOperand(0, binLeft);
                    cmpInst.replaceOperand(1, new ConstInteger(val - val2, constInt.getType()));
                }
                else if (binOP == OP.Sub) {
                    cmpInst.replaceOperand(0, binLeft);
                    cmpInst.replaceOperand(1, new ConstInteger(val + val2, constInt.getType()));
                }
                else if (binOP == OP.Mul) {
                    cmpInst.replaceOperand(0, binLeft);
                    cmpInst.replaceOperand(1, new ConstInteger(val / val2, constInt.getType()));
                }
            }
            else if (binLeft instanceof ConstInteger constInt2) {
                int val2 = constInt2.getValue();
                if (binOP == OP.Add) {
                    cmpInst.replaceOperand(0, binRight);
                    cmpInst.replaceOperand(1, new ConstInteger(val - val2, constInt.getType()));
                }
                else if (binOP == OP.Sub) {
                    cmpInst.replaceOperand(0, binRight);
                    cmpInst.replaceOperand(1, new ConstInteger(val2 - val, constInt.getType()));
                    if (op == OP.Le) cmpInst.setOp(OP.Ge);
                    else if (op == OP.Lt) cmpInst.setOp(OP.Gt);
                    else if (op == OP.Ge) cmpInst.setOp(OP.Le);
                    else if (op == OP.Gt) cmpInst.setOp(OP.Lt);
                }
                else if (binOP == OP.Mul) {
                    cmpInst.replaceOperand(0, binRight);
                    cmpInst.replaceOperand(1, new ConstInteger(val / val2, constInt.getType()));
                }
            }
            else if (binLeft == binRight) {
                if (binOP == OP.Add) {
                    cmpInst.replaceOperand(0, binLeft);
                    cmpInst.replaceOperand(1, new ConstInteger(val / 2, constInt.getType()));
                }
                else if (binOP == OP.Sub) {
                    cmpInst.replaceOperand(0, new ConstInteger(0, IntegerType.I32));
                }
                else if (binOP == OP.Mul) {
                    cmpInst.replaceOperand(0, binLeft);
                    cmpInst.replaceOperand(1, new ConstInteger((int) Math.sqrt(val), constInt.getType()));
                }
            }
            else return ;
        }
        else if (left instanceof ConstInteger constInt && right instanceof BinaryInst binInst) {
            cmpInst.replaceOperand(0, binInst);
            cmpInst.replaceOperand(1, constInt);
            if (op == OP.Le) cmpInst.setOp(OP.Ge);
            else if (op == OP.Lt) cmpInst.setOp(OP.Gt);
            else if (op == OP.Ge) cmpInst.setOp(OP.Le);
            else if (op == OP.Gt) cmpInst.setOp(OP.Lt);
            CmpInstComb(cmpInst);
        }
        else return ;
    }

    private void instCombForFunc(Function function){
        ArrayList<BinaryInst> twoTimesInst = new ArrayList<>();

        for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            IList.INode<Instruction, BasicBlock> itInstNode = bb.getInsts().getHead();
            while (itInstNode != null) {
                Instruction inst = itInstNode.getValue();
                itInstNode = itInstNode.getNext();
                //  TODO: 目前只考虑整数
                if(inst instanceof BinaryInst binInst
                        && binInst.getLeftVal().equals(binInst.getRightVal())
                        && binInst.getOp().equals(OP.Add)
                        && binInst.getType().isIntegerTy()
                        && !(binInst.getLeftVal() instanceof Const)){
                    twoTimesInst.add(binInst);
                }
            }
        }

        //  取出形如 %b = add %a %a的指令
        for(BinaryInst binInst : twoTimesInst){
            int nowFactor  = 2;
            Value baseValue = binInst.getLeftVal();
            //  change标识是否可以接着沿着def-use合并
            boolean change = true;
            Value nowBinInst = binInst;
            while (change){
                change = false;
                for(User user : nowBinInst.getUserList()){
                    Instruction userInst = (Instruction) user;
                    if(!(userInst instanceof BinaryInst userBinInst)){
                        continue;
                    }
                    if(!userBinInst.getLeftVal().equals(baseValue)
                            && !userBinInst.getRightVal().equals(baseValue)){
                        continue;
                    }

                    //  开始修改
                    nowFactor++;
                    setCombValueFactor(userBinInst, nowFactor, baseValue);
                    change = true;
                    //  迭代
                    nowBinInst = userBinInst;
                }
            }
        }
    }

    private void setCombValueFactor(BinaryInst binInst, int factor, Value baseValue){
        Value leftValue = binInst.getLeftVal();
        Value rightValue = binInst.getRightVal();
        if(leftValue.equals(baseValue)){
            binInst.replaceOperand(1, f.buildNumber(factor));
            binInst.setOp(OP.Mul);
        }
        else if(rightValue.equals(baseValue)){
            binInst.replaceOperand(0, f.buildNumber(factor));
            binInst.setOp(OP.Mul);
        }
    }

    private void constInstCombForFunc(Function function){
        for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()){
            BasicBlock bb = bbNode.getValue();
            IList.INode<Instruction, BasicBlock> itInstNode = bb.getInsts().getHead();
            while (itInstNode != null){
                Instruction inst = itInstNode.getValue();
                itInstNode = itInstNode.getNext();

                if(canCombAdd(inst)){
                    ArrayList<Use> tmpUseList = new ArrayList<>(inst.getUseList());
                    for(Use use : tmpUseList) {
                        BinaryInst userInst = (BinaryInst) use.getUser();
                        combAddToUser((BinaryInst) inst, userInst);
                    }
                    inst.removeSelf();
                }

                else if(canCombMul(inst)){
                    ArrayList<Use> tmpUseList = new ArrayList<>(inst.getUseList());
                    for(Use use : tmpUseList) {
                        BinaryInst userInst = (BinaryInst) use.getUser();
                        combMulToUser((BinaryInst) inst, userInst);
                    }
                    inst.removeSelf();
                }
            }
        }
    }

    private void combMulToUser(BinaryInst inst, BinaryInst userInst){
        Value leftA = inst.getLeftVal();
        Value rightA = inst.getRightVal();
        Value leftB = userInst.getLeftVal();
        Value rightB = userInst.getRightVal();

        int ConstInA = 0, ConstInB = 0;
        float ConstFloatA = 0, ConstFloatB = 0;
        boolean ConstInAIs0 = false, ConstInBIs0 = false;
        boolean isInt = inst.getType().isIntegerTy();

        if (leftA instanceof Const) {
            if(isInt) ConstInA = ((ConstInteger) leftA).getValue();
            else ConstFloatA = ((ConstFloat) leftA).getValue();
            ConstInAIs0 = true;
        }
        else {
            if(isInt) ConstInA = ((ConstInteger) rightA).getValue();
            else ConstFloatA = ((ConstFloat) rightA).getValue();
        }

        if (leftB instanceof Const) {
            if(isInt) ConstInB = ((ConstInteger) leftB).getValue();
            else ConstFloatB = ((ConstFloat) leftB).getValue();
            ConstInBIs0 = true;
        }
        else {
            if(isInt) ConstInB = ((ConstInteger) rightB).getValue();
            else ConstFloatB = ((ConstFloat) rightB).getValue();
        }

        ConstInteger constInteger = new ConstInteger(ConstInA * ConstInB, IntegerType.I32);
        ConstFloat constFloat = new ConstFloat(ConstFloatA * ConstFloatB);
        //  b = 3 * a; c = 2 * b;
        if(ConstInAIs0 && ConstInBIs0){
            if(isInt) userInst.replaceOperand(0, constInteger);
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, rightA);
        }
        else if(ConstInAIs0){
            if(isInt) userInst.replaceOperand(1, constInteger);
            else userInst.replaceOperand(1, constFloat);
            userInst.replaceOperand(0, rightA);
        }
        else if(ConstInBIs0){
            if(isInt) userInst.replaceOperand(0, constInteger);
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, leftA);
        }
        else {
            if(isInt) userInst.replaceOperand(1, constInteger);
            else userInst.replaceOperand(1, constFloat);
            userInst.replaceOperand(0, leftA);
        }
    }


    //  b = a + 1; c = b + 2;
    //  c = a + 3;
    private void combAddToUser(BinaryInst inst, BinaryInst userInst){
        Value leftA = inst.getLeftVal();
        Value rightA = inst.getRightVal();
        Value leftB = userInst.getLeftVal();
        Value rightB = userInst.getRightVal();

        int ConstInA = 0, ConstInB = 0;
        float ConstFloatA = 0, ConstFloatB = 0;
        boolean ConstInAIs0 = false, ConstInBIs0 = false;
        boolean isInt = inst.getType().isIntegerTy();
        boolean isAAdd = (inst.getOp() == OP.Add) || (inst.getOp() == OP.Fadd);
        boolean isBAdd = (userInst.getOp() == OP.Add) || (inst.getOp() == OP.Fadd);

        if (leftA instanceof Const) {
            if(isInt) ConstInA = ((ConstInteger) leftA).getValue();
            else ConstFloatA = ((ConstFloat) leftA).getValue();
            ConstInAIs0 = true;
        }
        else {
            if(isInt) ConstInA = ((ConstInteger) rightA).getValue();
            else ConstFloatA = ((ConstFloat) rightA).getValue();
        }

        if (leftB instanceof Const) {
            if(isInt) ConstInB = ((ConstInteger) leftB).getValue();
            else ConstFloatB = ((ConstFloat) leftB).getValue();
            ConstInBIs0 = true;
        }
        else {
            if(isInt) ConstInB = ((ConstInteger) rightB).getValue();
            else ConstFloatB = ((ConstFloat) rightB).getValue();
        }

        if (ConstInAIs0 && ConstInBIs0 && isAAdd && isBAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInA + ConstInB, IntegerType.I32);
            ConstFloat constfloat = new ConstFloat(ConstFloatA + ConstFloatB);
            if(isInt) userInst.replaceOperand(0, constantInt);
            else userInst.replaceOperand(0, constfloat);
            userInst.replaceOperand(1, rightA);
        }
        else if (ConstInAIs0 && ConstInBIs0 && isAAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInB - ConstInA, IntegerType.I32);
            ConstFloat constfloat = new ConstFloat(ConstFloatB - ConstFloatA);

            if(isInt) userInst.replaceOperand(0, constantInt);
            else userInst.replaceOperand(0, constfloat);
            userInst.replaceOperand(1, rightA);
        }
        else if (ConstInAIs0 && ConstInBIs0 && isBAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInB + ConstInA, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatB + ConstFloatA);
            if(isInt) userInst.replaceOperand(0, constantInt );
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, rightA);
            if(isInt) userInst.setOp(OP.Sub);
            else userInst.setOp(OP.Fsub);
        }
        else if (ConstInAIs0 && ConstInBIs0) {
            ConstInteger constantInt = new ConstInteger(ConstInB - ConstInA, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatB - ConstFloatA);
            if(isInt) userInst.replaceOperand(0, constantInt);
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, rightA);
            if(isInt) userInst.setOp(OP.Add);
            else userInst.setOp(OP.Fadd);
        }


        else if (ConstInAIs0 && isAAdd && isBAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInA + ConstInB, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatA + ConstFloatB);
            if(isInt) userInst.replaceOperand(1, constantInt);
            else userInst.replaceOperand(1, constFloat);
            userInst.replaceOperand(0, rightA);
        }
        else if (ConstInAIs0 && isAAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInA - ConstInB, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatA - ConstFloatB);
            if(isInt) userInst.replaceOperand(1, constantInt);
            else userInst.replaceOperand(1, constFloat);
            userInst.replaceOperand(0, rightA);
            if(isInt) userInst.setOp(OP.Add);
            else userInst.setOp(OP.Fadd);
        }
        //b = x - a; c = b + y; c = (x+y) - a
        else if (ConstInAIs0 && isBAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInA + ConstInB, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatA + ConstFloatB);
            if(isInt) userInst.replaceOperand(0, constantInt );
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, rightA);
            if(isInt) userInst.setOp(OP.Sub);
            else userInst.setOp(OP.Fsub);
            //A.remove();
        }
        //b = x - a; c = b - y ==> c = (x - y) - a
        else if (ConstInAIs0) {
            ConstInteger constantInt = new ConstInteger(ConstInA - ConstInB, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatA - ConstFloatB);
            if(isInt) userInst.replaceOperand(0, constantInt );
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, rightA);
        }
        else if (ConstInBIs0 && isAAdd && isBAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInA + ConstInB, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatA + ConstFloatB);
            if(isInt) userInst.replaceOperand(0, constantInt);
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, leftA);
        }
        else if (ConstInBIs0 && isAAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInB - ConstInA, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatB - ConstFloatA);
            if(isInt) userInst.replaceOperand(0, constantInt);
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, leftA);
        }
        else if (ConstInBIs0 && isBAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInB - ConstInA, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatB - ConstFloatA);
            if(isInt) userInst.replaceOperand(0, constantInt );
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, leftA);
        }
        else if (ConstInBIs0) {
            ConstInteger constantInt = new ConstInteger(ConstInB + ConstInA, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatB + ConstFloatA);
            if(isInt) userInst.replaceOperand(0, constantInt);
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, leftA);
        }

        else if (isAAdd && isBAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInA + ConstInB, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatA + ConstFloatB);
            if(isInt) userInst.replaceOperand(0, constantInt );
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, leftA);
        }
        else if (isAAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInA - ConstInB, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatA - ConstFloatB);
            if(isInt) userInst.replaceOperand(0, constantInt );
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, leftA);
            if(isInt) userInst.setOp(OP.Add);
            else userInst.setOp(OP.Fadd);
        }
        else if (isBAdd) {
            ConstInteger constantInt = new ConstInteger(ConstInB - ConstInA, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(ConstFloatB - ConstFloatA);
            if(isInt) userInst.replaceOperand(0, constantInt);
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, leftA);
        }

        //b = a - x; c = b - y; c = a - (x + y)
        else {
            ConstInteger constantInt = new ConstInteger(-ConstInB - ConstInA, IntegerType.I32);
            ConstFloat constFloat = new ConstFloat(-ConstFloatB - ConstFloatA);
            if(isInt) userInst.replaceOperand(0, constantInt );
            else userInst.replaceOperand(0, constFloat);
            userInst.replaceOperand(1, leftA);
            if(isInt) userInst.setOp(OP.Add);
            else userInst.setOp(OP.Fadd);
        }
    }

    private boolean canCombAdd(Instruction inst){
        if(!isCombAddAlu(inst)){
            return false;
        }
        for(Use use : inst.getUseList()){
            User user = use.getUser();
            if(!(user instanceof Instruction userInst && isCombAddAlu(userInst))){
                return false;
            }
        }
        return true;
    }

    private boolean canCombMul(Instruction inst){
        if(!isCombMulAlu(inst)){
            return false;
        }
        for(Use use : inst.getUseList()){
            User user = use.getUser();
            if(!(user instanceof Instruction userInst && isCombMulAlu(userInst))){
                return false;
            }
        }
        return true;
    }

    private boolean isCombAddAlu(Instruction inst){
        if(!(inst instanceof BinaryInst binaryInst)){
            return false;
        }
        OP op = binaryInst.getOp();
        if(!(op == OP.Add || op == OP.Sub)){
            return false;
        }
        return binaryInst.hasOneConst();
    }

    private boolean isCombMulAlu(Instruction inst){
        if(!(inst instanceof BinaryInst binaryInst)){
            return false;
        }
        OP op = binaryInst.getOp();
        if(!(op == OP.Mul)){
            return false;
        }
        return binaryInst.hasOneConst();
    }

    @Override
    public String getName() {
        return "InstComb";
    }
}
