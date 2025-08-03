package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Type.Type;
import IR.Use;
import IR.Value.*;
import IR.Value.Instructions.*;
import Pass.IR.Utils.CloneHelper;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.*;

import static Pass.IR.Utils.UtilFunc.buildCallRelation;

public class FuncInLine implements Pass.IRPass {

    @Override
    public String getName() {
        return "FuncInLine";
    }

    private IRModule module;
    private boolean changed;
    private final IRBuildFactory f = IRBuildFactory.getInstance();

    @Override
    public void run(IRModule module) {
        this.module = module;
        simpleInline();
    }

    //  simpleInline只基础版的函数内联，不做递归展开
    private void simpleInline(){
        ArrayList<Function> tobeProcessed = new ArrayList<>();
        changed = true;
        //  dfs找到端点函数并内联，直至没有函数可以内联
        //  将找到的端点函数放入tobeProcessed里
        while (changed) {
            changed = false;
            buildCallRelation(module);
            for(Function function : module.functions()){
                if (function.isLibFunction()) {
                    continue;
                }
                if (isInlinable(function)) {
                    tobeProcessed.add(function);
                }
            }

            for(Function function : tobeProcessed) {
                inlineFunction(function);
            }
            tobeProcessed.clear();
        }
        boolean uselessChanged = true;
        while(uselessChanged){
            buildCallRelation(module);
            uselessChanged = removeUseLessFunction(module);
        }
    }

    private boolean removeUseLessFunction(IRModule module){
        boolean changed = false;
        ArrayList<Function> deleteFuncs = new ArrayList<>();
        for(Function function : module.functions()){
            if(function.getCallerList().isEmpty() && !function.getName().equals("@main")){
                deleteFuncs.add(function);
                changed = true;
            }
            else if(function.getCallerList().size() == 1 && function.getCallerList().get(0).equals(function)
                    && !function.getName().equals("@main")){
                deleteFuncs.add(function);
                changed = true;
            }
        }

        //  删除函数时要把他们用的globalvar的use一起删掉
        for(GlobalVar globalVar : module.globalVars()){
            ArrayList<Use> tmpUseList = new ArrayList<>(globalVar.getUseList());
            for(Use use : tmpUseList){
                Instruction userInst = (Instruction) use.getUser();
                if(deleteFuncs.contains(userInst.getParentbb().getParentFunc())){
                    globalVar.removeUseByUser(userInst);
                }
            }
        }
        for(Function deleteFunc : deleteFuncs){
            module.functions().remove(deleteFunc);
        }
        return changed;
    }

    //  inlineFunction
    private void inlineFunction(Function function){
        if(function.getCallerList().isEmpty()){
            return;
        }

        changed = true;
        //  内联每一处调用该function的地方
        //  1. 找到该函数callers对应的callInst
        ArrayList<Instruction> toBeReplaced = new ArrayList<>();
        for(Function caller : function.getCallerList()){
            if(caller.equals(function)){
                continue;
            }
            for(IList.INode<BasicBlock, Function> bbNode : caller.getBbs()){
                BasicBlock bb = bbNode.getValue();
                for(IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()){
                    Instruction inst = instNode.getValue();
                    if(inst instanceof CallInst callInst){
                        if(callInst.getFunction().getName().equals(function.getName())){
                            toBeReplaced.add(inst);
                        }
                    }
                }
            }
        }
        //  2. 处理callInst的替换
        for(Instruction inst : toBeReplaced){
            inlineOneCall((CallInst) inst);
        }
        //  3. 删除该函数的调用列表
        function.getCallerList().clear();
    }

    //  insertBlock用于承上启下，将callInst所在的基本块以callInst为分隔线一分为二
    //  之后copy出来的新blocks将插在oriBlock和insertBlock之间
    private void inlineOneCall(CallInst callInst){
        Function calledFunction = callInst.getFunction();
        BasicBlock oriBlock = callInst.getParentbb();
        Function oriFunction = oriBlock.getParentFunc();

        CloneHelper cloneHelper = new CloneHelper();
        Function tmpInlineFunction = cloneHelper.copyFunction(calledFunction, module.globalVars());

        //  构建insertBlock
        //  1. 创建insertBlock并将其插入在函数中
        BasicBlock insertBlock = f.getBasicBlock(oriFunction);
        insertBlock.insertAfter(oriBlock);

        //  2. 为insertBlock填写指令(即callInst后面的指令)
        //  同时删除oriBlock中的这些指令
        IList.INode<Instruction, BasicBlock> itInstNode = callInst.getNode().getNext();
        while (itInstNode != null){
            Instruction inst = itInstNode.getValue();
            itInstNode = itInstNode.getNext();
            inst.removeFromBb();
            insertBlock.addInst(inst);
        }

        //  3. 删除callInst指令并修正函数调用关系
        callInst.removeSelf();
        oriFunction.getCalleeList().remove(calledFunction);
        calledFunction.getCallerList().remove(oriFunction);

        //  4. 修正基本块之间前驱后继关系
        BasicBlock tmpBbEntry = tmpInlineFunction.getBbEntry();
        //  在为oriBlock重构nxtBlock之前，
        //  先把insertBlock和oriBlock的原后继建好关系
        for(BasicBlock bb : oriBlock.getNxtBlocks()){
            //  一般情况下 我们nxtBlock和preBlock都是在建立br指令的时候自动构建的
            //  但是这里的情况是insertBlock复制了oriBlock里的br指令
            //  并没有进行新的构建，因此需要手动设置一下nxtBlock和preBlock
            insertBlock.setNxtBlock(bb);
            for(int i = 0; i < bb.getPreBlocks().size(); i++){
                BasicBlock preBb = bb.getPreBlocks().get(i);
                if(preBb == oriBlock){
                    bb.getPreBlocks().set(i, insertBlock);
                }
            }
        }
        f.buildBrInst(tmpBbEntry, oriBlock);
        oriBlock.setNxtBlock(tmpBbEntry);
        tmpBbEntry.setPreBlock(oriBlock);

        // 将调用函数的形式参数换为为传入参数
        ArrayList<Argument> formalParameters = tmpInlineFunction.getArgs();
        ArrayList<Value> actualParameters = new ArrayList<>(callInst.getOperands());

        for (int i = 0; i < formalParameters.size(); i++) {
            Value formalParam = formalParameters.get(i);
            Value actualParam = actualParameters.get(i);
            formalParam.replaceUsedWith(actualParam);
        }

        //  处理ret和call指令
        //  将tmpInline函数中的基本块转移到原函数中
        ArrayList<Instruction> rets = new ArrayList<>();
        ArrayList<Instruction> calls = new ArrayList<>();
        ArrayList<BasicBlock> moveBB = new ArrayList<>();
        for (IList.INode<BasicBlock, Function> bbNode : tmpInlineFunction.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            moveBB.add(bb);
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof RetInst) {
                    rets.add(inst);
                }
                else if (inst instanceof CallInst) {
                    calls.add(inst);
                }
            }
        }
        //  要内联的函数内部可能因为不同的控制流有多个return指令
        //  如果只有一个ret指令显然我们呢直接替换就可以了
        //  否则我们应该将返回值转换成一个phi指令的形式
        Type retType = calledFunction.getType();
        if (retType.isIntegerTy() || retType.isFloatTy()) {
            if(rets.size() == 1){
                Instruction retInst = rets.get(0);
                callInst.replaceUsedWith(retInst.getOperand(0));
                BasicBlock nowBb = retInst.getParentbb();
                retInst.removeSelf();
                f.buildBrInst(insertBlock, nowBb);
                nowBb.setNxtBlock(insertBlock);
                insertBlock.setPreBlock(nowBb);
            }
            else {
                //  填写phi指令并修改ret指令为br指令
                //  这些含ret的基本块通过br汇总到insertBlock
                Phi phi = new Phi(retType, new ArrayList<>());
                callInst.replaceUsedWith(phi);
                for (Instruction retInst : rets) {
                    if(((RetInst) retInst).isVoid()){
                        continue;
                    }
                    phi.addOperand(retInst.getOperands().get(0));
                    BasicBlock nowBb = retInst.getParentbb();
                    retInst.removeSelf();
                    f.buildBrInst(insertBlock, nowBb);
                    nowBb.setNxtBlock(insertBlock);
                    insertBlock.setPreBlock(nowBb);
                }
                phi.insertToHead(insertBlock);
            }
        }
        else if (retType.isVoidTy()) {
            for (Instruction retInst : rets) {
                BasicBlock nowBb = retInst.getParentbb();
                retInst.removeFromBb();
                f.buildBrInst(insertBlock, nowBb);
                nowBb.setNxtBlock(insertBlock);
                insertBlock.setPreBlock(nowBb);
            }
        }

        for (BasicBlock bb : moveBB) {
            bb.insertBefore(insertBlock);
        }
        for (Instruction call : calls) {
            Function calledFunc = ((CallInst) call).getFunction();
            if(calledFunc.isLibFunction()){
                continue;
            }
            calledFunc.addCaller(oriFunction);
            oriFunction.addCallee(calledFunc);
        }

        module.functions().remove(tmpInlineFunction);
    }

    private boolean isInlinable(Function function){
        //  main函数或调用了其他函数 不能被内联
        if(function.getName().equals("@main")) return false;

        //  检查函数是否使用全局变量，如果使用则不内联
        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst instanceof LoadInst loadInst) {
                    if (!loadInst.getOperands().isEmpty() && loadInst.getOperands().get(0) instanceof GlobalVar) {
                        return false; // 使用全局变量，不内联
                    }
                }else if (inst instanceof StoreInst storeInst) {
                    if (storeInst.getOperands().size() > 1 && storeInst.getOperands().get(1) instanceof GlobalVar) {
                        return false; // 修改全局变量，不内联
                    }
                }
            }
        }
        
        //  设置一个内联阈值
//        int inst_num = 0;
//        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
//            BasicBlock bb = bbNode.getValue();
//            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
//                inst_num++;
//            }
//        }
//        if (inst_num > 20) return false;
        return function.getCalleeList().isEmpty();
    }


}
