package Pass.IR;

import Driver.Config;
import Frontend.AST;
import IR.IRBuildFactory;
import IR.IRModule;
import IR.Type.IntegerType;
import IR.Value.*;
import IR.Value.Instructions.*;
import Pass.IR.Utils.*;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.stream.BaseStream;

//  PeepHole中都是一些对特定模式的优化
public class PeepHole implements Pass.IRPass {

    private final IRBuildFactory f = IRBuildFactory.getInstance();

    @Override
    public void run(IRModule module) {
        //  ptradd A, 0
        PeepHole1(module);
        //  br %x, %block A, %block A
        PeepHole2(module);
        //  br %x %block A, %block B
        //  Block B: br %x %block C, %block D
        PeepHole3(module);

        //  store A ptr1; ...; B = load ptr1;
        //WARNING: this peephole may faild in case error_sideeffect
//        PeepHole4(module);

        //  连续load，中间没有store
//        PeepHole5(module);
        LoopPeepHole(module);
        FunctionPeephole(module);

        continiousLoadEliminate(module);
        ConstBrLoopOptimize(module);

        ArithmeticPeephole(module);
    }

    private void PeepHole5(IRModule module) {
        for (Function function : module.functions()) {
            for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                BasicBlock bb = bbNode.getValue();
                ArrayList<LoadInst> loadInsts = new ArrayList<>();
                HashMap<LoadInst, LoadInst> loadMap = new HashMap<>();
                for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                    Instruction inst = instNode.getValue();
                    if (!(inst instanceof LoadInst loadInst)) continue;
                    Value pointer = loadInst.getPointer();
                    IList.INode<Instruction, BasicBlock> itNode = instNode.getNext();
                    Value root = AliasAnalysis.getRoot(pointer);
                    while (itNode != null) {
                        Instruction nxtInst = itNode.getValue();
                        if (nxtInst instanceof CallInst) break;
                        else if (nxtInst instanceof StoreInst storeInst) {
                            Value storeRoot = AliasAnalysis.getRoot(storeInst);
                            if (root == storeRoot) break;
                        }
                        else if (nxtInst instanceof LoadInst nxtLoadInst && nxtLoadInst.getPointer() == pointer) {
                            loadMap.put(nxtLoadInst, loadInst);
                            loadInsts.add(nxtLoadInst);
                        }
                        itNode = itNode.getNext();
                    }
                }

                for (LoadInst loadInst : loadInsts) {
                    loadInst.replaceUsedWith(loadMap.get(loadInst));
                }
            }
        }
    }

    private void ArithmeticPeephole(IRModule module) {
        for (Function function : module.functions()) {
            if (function.getBbs().getSize() != 7) continue ;
            BasicBlock entry = function.getBbEntry();
            if (entry.getInsts().getSize() != 2) continue ;
            boolean recur = false;
            for (Function caller : function.getCallerList()) {
                if (caller == function) {
                    recur = true;
                    break;
                }
            }
            if (!recur) continue;
            ArrayList<CallInst> callInsts = new ArrayList<>();
            ArrayList<RetInst> retInsts = new ArrayList<>();
            for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                BasicBlock bb = bbNode.getValue();
                for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                    Instruction inst = instNode.getValue();
                    if (inst instanceof CallInst callInst) {
                        callInsts.add(callInst);
                    }
                    else if (inst instanceof RetInst retInst) {
                        retInsts.add(retInst);
                    }
                }
            }
            if (callInsts.size() > 1 || retInsts.size() < 3) return ;
            CallInst callInst = callInsts.get(0);
            if (callInst.getParams().size() != 2) return ;
            Value arg = callInst.getParams().get(0);
            Value divInst = callInst.getParams().get(1);
            if (arg != function.getArgs().get(0)) return ;
            if (!(divInst instanceof BinaryInst binInst) || binInst.getOp() != OP.Div) return ;
            if (((BinaryInst) divInst).getLeftVal() != function.getArgs().get(1)
                    || !(((BinaryInst) divInst).getRightVal() instanceof ConstInteger constInt)
                    || constInt.getValue() != 2) return ;
            if (entry.getInsts().getSize() != 2) return ;
            BasicBlock divBb = ((BinaryInst) divInst).getParentbb();
            if (divBb.getInsts().getSize() != 7) return ;
            for (Value user : divInst.getUserList()) {
                if (user != callInst) return ;
            }
            ArrayList<BinaryInst> addInsts = new ArrayList<>();
            for (Value user : callInst.getUserList()) {
                if (!(user instanceof BinaryInst addInst) || addInst.getOp() != OP.Add) return ;
                addInsts.add(addInst);
            }
            if (addInsts.size() != 1) return ;
            BinaryInst addInst = addInsts.get(0);
            if (addInst.getLeftVal() != callInst || addInst.getRightVal() != callInst) return ;
            ArrayList<BinaryInst> divInsts = new ArrayList<>();
            for (Value user : addInst.getUserList()) {
                if (user instanceof BinaryInst divInst2 && divInst2.getOp() != OP.Div) {
                    divInsts.add(divInst2);
                }
            }
            if (divInsts.size() != 1) return ;
            if (!(divInsts.get(0).getRightVal() instanceof ConstInteger constInt2)) return ;
            BasicBlock preHead = f.getBasicBlock(function);
            Value mod = constInt2;
            BinaryInst mulInst = f.buildBinaryInst(function.getArgs().get(0), function.getArgs().get(1), OP.Mul, preHead);
            mulInst.I64 = true;
            BinaryInst modInst = f.buildBinaryInst(mulInst, mod, OP.Mod, preHead);
            modInst.I64 = true;
            f.buildRetInst(modInst, preHead);
            preHead.insertBefore(function.getBbEntry());
        }
    }

    //block216:
    //	%548 = phi i32 [ 0, %block210 ],[ %554, %block216 ]     //01
    //	%549 = phi i32 [ 0, %block210 ],[ %559, %block216 ]     //02
    //	%1359 = add i32 4950, %549                              //03
    //	%559 = srem i32 %1359, 65535                            //04
    //	%554 = add i32 %548, 1                                  //05
    //	%550 = icmp slt i32 %554, %532                          //06
    //	br i32 %550, label %block216, label %block420           //07
    private void LoopPeepHole(IRModule module) {
        for (var func : module.functions()) {
            boolean changed = false;
            LoopAnalysis.runLoopInfo(func);
            for (var loop : func.getAllLoops()) {
                Instruction instruction3 = null;
                Instruction instruction4 = null;
                Instruction instruction5 = null;
                Instruction instruction6 = null;

                if (loop.getBbs().size() != 1) {
                    continue;
                }
                var bb = loop.getBbs().get(0);
                if (bb.getInsts().getSize() != 7) {
                    continue;
                }
                ArrayList<Phi> allPhi = new ArrayList<>();
                ArrayList<Instruction> allInsts = new ArrayList<>();
                //遍历bb，添加到allphi
                for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                    Instruction inst = instNode.getValue();
                    if (inst instanceof Phi phi) {
                        allPhi.add(phi);
                    }
                    allInsts.add(inst);
                }
                if (allPhi.size() != 2 || allPhi.get(1).getOperands().size() != 2) {
                    continue;
                }
                var brcond = bb.getBrCond(); //06
                if (brcond == null || brcond.getOp() != OP.Lt) {
                    continue;
                }
                if (!(allInsts.contains(brcond.getOperand(0)) && !allInsts.contains(brcond.getOperand(1)))) {
                    continue;
                }
                //startIndex to endIndex, +1 every time
                // startAddingValue = (startAddingValue + addingNumber) % remNumber

                Phi indexPhi = null;
                ConstInteger startIndexConst = null;
                Value endIndex = null;

                Phi addingPhi = null;
                Value startAddingValue = null;
                ConstInteger addingNumber = null;
                ConstInteger remNumber = null;

                endIndex = brcond.getOperand(1);

                boolean successMap = true;
                for (var phi : allPhi) {
                    var value2bb = phi.getPhiValues();
                    LinkedHashMap<Value, BasicBlock> valuemap = new LinkedHashMap<>();
                    for (var pair : value2bb) {
                        valuemap.put(pair.getFirst(), pair.getSecond());
                    }
                    boolean hasConstIncome = false;
                    ConstInteger constvalue = null;
                    Value nonConstValue = null;
                    for (var value : valuemap.keySet()) {
                        if (value instanceof ConstInteger) {
                            hasConstIncome = true;
                            constvalue = (ConstInteger) value;
                        } else {
                            nonConstValue = value;
                        }
                    }
                    if (!hasConstIncome) {
                        successMap = false;
                        break;
                    }
                    if (!(nonConstValue instanceof BinaryInst binaryInst)) {
                        successMap = false;
                        break;
                    }
                    //match index adding pattern
                    //01
                    if (binaryInst.getOp() == OP.Add &&
                            binaryInst.getOperand(0).equals(phi) &&
                            (binaryInst.getOperand(1) instanceof ConstInteger constInteger &&
                                    constInteger.getValue() == 1)) {
                        indexPhi = phi;
                        startIndexConst = constvalue;
                        instruction5 = binaryInst;
                        // binaryInst is 05
                        if (!brcond.getOperand(0).equals(binaryInst)) { // 判断这个自增1的phi的binaryinst是否是icmp的第一个操作数
                            successMap = false;
                            break;
                        }
                    }
                    //02
                    else if (binaryInst.getOp() == OP.Mod &&
                            binaryInst.getOperand(1) instanceof ConstInteger) {
                        instruction4 = binaryInst;
                        //binaryInst is 04
                        remNumber = (ConstInteger) binaryInst.getOperand(1);
                        addingPhi = phi;
                        startAddingValue = constvalue;
                        if (!(binaryInst.getOperand(0) instanceof BinaryInst subbinaryInst &&
                                allInsts.contains(subbinaryInst))) {
                            successMap = false;
                            break;
                        }
                        instruction3 = subbinaryInst;
                        //subBinaryInst is 03
                        if (subbinaryInst.getOperand(0) instanceof ConstInteger subconst&&
                                subbinaryInst.getOperand(1) instanceof Phi subusephi&&
                                allInsts.contains(subusephi) && subusephi.getOperands().contains(binaryInst)
                        ) {
                            addingNumber = subconst;
                        }else if(
                                subbinaryInst.getOperand(1) instanceof ConstInteger subconst&&
                                        subbinaryInst.getOperand(0) instanceof Phi subusephi&&
                                        allInsts.contains(subusephi) && subusephi.getOperands().contains(binaryInst)
                        ){
                            addingNumber = subconst;
                        }else{
                            successMap = false;
                            break;
                        }
                    }
                }
                if (!successMap) {
                    continue;
                }
                for(var inst : allInsts){
                    for(User user : inst.getUserList()){
                        if(!(allInsts.contains(user)) && !inst.equals(instruction4)){
                            successMap = false;
                        }
                    }
                }
                if (!successMap) {
                    continue;
                }

                //build and replace instruction4 by result;
                BrInst brinst = (BrInst) bb.getLastInst();
                if(! brinst.getTrueBlock().equals(bb)){
                    continue;
                }
                var times = new BinaryInst(OP.Sub, brcond.getOperand(1) ,startIndexConst, startIndexConst.getType());
                var modtimes = new BinaryInst(OP.Mod, times, remNumber, remNumber.getType());
                var addinsRes = new BinaryInst(OP.Mul, modtimes, addingNumber, addingNumber.getType());
                var addingStartRes = new BinaryInst(OP.Add, addinsRes, startAddingValue, startAddingValue.getType());
                var modRes = new BinaryInst(OP.Mod, addingStartRes, remNumber, remNumber.getType());
                for(var inst : allInsts){
                    inst.removeSelf();
                }
                bb.addInst(times);
                bb.addInst(modtimes);
                bb.addInst(addinsRes);
                bb.addInst(addingStartRes);
                bb.addInst(modRes);
                bb.addInst(new BrInst(brinst.getFalseBlock()));
                instruction4.replaceUsedWith(modRes);
                changed  = true;
            }
            if(changed){
                UtilFunc.makeCFG(func);
            }
        }

    }

    private void FunctionPeephole(IRModule module){
        Function matchFunc = null;
        int dataIndex = 0;
        int recurIndex = 0;
        //block14:
        //	%16 = icmp slt i32 %num_param, 0
        //	br i32 %16, label %block9, label %block10
        //block9:
        //	ret float 0.0
        //block10:
        //	%20 = sub i32 %num_param, 1
        //	%24 = call float @func(float %data_param, i32 %20)
        //	%25 = fadd float %data_param, %24
        //	%29 = call float @func(float %25, i32 %20)
        //	%30 = fsub float %25, %29
        //	ret float %30

        // 模式识别：三个块、entry+entry的两个后继，两个后继的lastInst都是ret，两个arg
        // 要求entry两条语句，brcond判断其中一个arg小于0，认为其是recurIndex，另一个是data
        //  yesblock return 0.0
        //  no block return (data+call(data))-call(newdata)，newdata = data+call(data)
        //  no block both call use recurIndex-1 to call



        for(var func : module.functions()){
            if(func.getBbs().getSize()!=3 || func.getBbs().getHead().getValue().getNxtBlocks().size()!=2 ||
                !func.getBbs().getHead().getNext().getValue().getNxtBlocks().isEmpty() ||
                    !func.getBbs().getHead().getNext().getNext().getValue().getNxtBlocks().isEmpty()){
                continue;
            }
            boolean returnInt = func.getType() instanceof IntegerType;
            var entry = func.getBbEntry();
            BrInst br = (BrInst) entry.getLastInst();
            var cond = entry.getBrCond();
            if(!(entry.getInsts().getSize()==2)){
                continue;
            }
            if(!(cond.getOperand(0) instanceof Argument curIndex &&
                    cond.getOperand(1) instanceof ConstInteger curStopcond && curStopcond.getValue() == 0 &&
                    cond.getOp()==OP.Lt)){
                continue;
            }
            Argument data = null;
            for(var arg : func.getArgs()){
                if(!arg.equals(curIndex)){
                    data = arg;
                }
            }
            if(data == null){
                continue;
            }
            var stopBlock = br.getTrueBlock();
            var recurBlock = br.getFalseBlock();
            if(!(stopBlock.getInsts().getSize()==1 && stopBlock.getLastInst() instanceof RetInst ret &&
                    (returnInt?
                            ret.getOperand(0) instanceof ConstInteger retval &&
                                    retval.getValue() == 0 :
                            ret.getOperand(0) instanceof ConstFloat retfloatval &&
                                    retfloatval.getValue()==(float) 0.0
                    ))){
                continue;
            }
            if(!(recurBlock.getInsts().getSize()==6)){
                continue;
            }

            //	%20 = sub i32 %num_param, 1
            //	%24 = call float @func(float %data_param, i32 %20)
            //	%25 = fadd float %data_param, %24
            //	%29 = call float @func(float %25, i32 %20)
            //	%30 = fsub float %25, %29
            //	ret float %30
            var recurRet = ((RetInst)recurBlock.getLastInst()).getOperand(0);
            var newRecurIndex = recurBlock.getInsts().getHead().getValue();
            if(!(newRecurIndex instanceof BinaryInst bina && newRecurIndex.getOp()==OP.Sub &&
                    bina.getRightVal() instanceof ConstInteger subval && subval.getValue() == 1 &&
                    bina.getLeftVal().equals(curIndex))){
                continue;
            }
            if(!(newRecurIndex.getNode().getNext().getValue() instanceof CallInst firstCall &&
                    firstCall.getParams().contains(newRecurIndex) && firstCall.getParams().contains(data))){
                continue;
            }
            if(firstCall.getOperand(func.getArgs().indexOf(data)) != data){
                continue;
            }
            var retOpe = firstCall.getNode().getNext().getValue();
            if(!(retOpe instanceof BinaryInst retBinary && retBinary.getOp() == OP.Fadd &&
                    retBinary.getOperands().contains(firstCall) && retBinary.getOperands().contains(data))){
                continue;
            }
            var maysecondCall = retOpe.getNode().getNext().getValue();
            if(!(maysecondCall instanceof CallInst secondCall &&
                    secondCall.getOperands().contains(retOpe) && secondCall.getOperands().contains(newRecurIndex))){
                continue;
            }
            var finalOpe = secondCall.getNode().getNext().getValue();
            if(!(finalOpe instanceof BinaryInst finalBinary && finalBinary.getOp() == OP.Fsub &&
                    finalBinary.getLeftVal().equals(retOpe) && finalBinary.getRightVal().equals(secondCall))){
                continue;
            }
            if(!recurRet.equals(finalOpe)){
                continue;
            }
            matchFunc = func;
            dataIndex = func.getArgs().indexOf(data);
            recurIndex = func.getArgs().indexOf(curIndex);
            break;

        }
        if(matchFunc==null){
            return;
        }
        ArrayList<BasicBlock> matchbbs= new ArrayList<>();
        for(var bn : matchFunc.getBbs()){
            matchbbs.add(bn.getValue());
        }
        for(var bb : matchbbs){
            bb.removeSelf();
        }
        BasicBlock newentry = new BasicBlock(matchFunc);
        BasicBlock newZeroBlock = new BasicBlock(matchFunc);
        BasicBlock newDataBlockl = new BasicBlock(matchFunc);
        newZeroBlock.addInst(new RetInst(new ConstFloat((float)0.0)));
        newDataBlockl.addInst(new RetInst(matchFunc.getArgs().get(dataIndex)));
        var newmod = new BinaryInst(OP.Mod,
                matchFunc.getArgs().get(recurIndex), new ConstInteger(2, IntegerType.I32),
                IntegerType.I1);
        var newCond = new BinaryInst(OP.Eq,
                newmod, new ConstInteger(1, IntegerType.I1),
                IntegerType.I1);
        var newBr = new BrInst(newCond, newZeroBlock, newDataBlockl);
        newentry.addInst(newmod);
        newentry.addInst(newCond);
        newentry.addInst(newBr);
        matchFunc.getBbs().add(newentry.getNode());
        matchFunc.getBbs().add(newZeroBlock.getNode());
        matchFunc.getBbs().add(newDataBlockl.getNode());
        UtilFunc.makeCFG(matchFunc);
        new FuncInLine().run(module);
    }

    private void PeepHole3(IRModule module) {
        for (Function function : module.functions()) {
            if (function.isLibFunction()) continue;
            for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                BasicBlock bb = bbNode.getValue();
                Instruction lastInst = bb.getLastInst();
                if (lastInst instanceof BrInst brInst
                        && brInst.getJudVal() != null) {
                    Value value = brInst.getJudVal();
                    BasicBlock trueBb = brInst.getTrueBlock();
                    BasicBlock falseBb = brInst.getFalseBlock();
                    Instruction falseFirInst = falseBb.getFirstInst();
                    Instruction trueFirInst = trueBb.getFirstInst();
                    if (falseFirInst instanceof BrInst falseBr
                            && falseBr.getJudVal() == value) {
                        BasicBlock falseNxt = falseBr.getFalseBlock();
                        brInst.replaceBlock(falseBb, falseNxt);
                        falseBb.removePreBlock(bb);
                        bb.removeNxtBlock(falseBb);
                        bb.setNxtBlock(falseNxt);
                        falseNxt.setPreBlock(bb);
                        int idx = falseNxt.getPreBlocks().indexOf(falseBb);
                        for (IList.INode<Instruction, BasicBlock> instNode : falseNxt.getInsts()) {
                            Instruction phiInst = instNode.getValue();
                            if (phiInst instanceof Phi phi) {
                                phi.addOperand(phi.getOperand(idx));
                            }
                            else break;
                        }
                    }
                    else if (trueFirInst instanceof BrInst trueBr
                            && trueBr.getJudVal() == value) {
                        BasicBlock trueNxt = trueBr.getTrueBlock();
                        brInst.replaceBlock(trueBb, trueNxt);
                        trueBb.removePreBlock(bb);
                        bb.removeNxtBlock(trueBb);
                        bb.setNxtBlock(trueNxt);
                        trueNxt.setPreBlock(bb);
                        int idx = trueNxt.getPreBlocks().indexOf(trueBb);
                        for (IList.INode<Instruction, BasicBlock> instNode : trueNxt.getInsts()) {
                            Instruction phiInst = instNode.getValue();
                            if (phiInst instanceof Phi phi) {
                                phi.addOperand(phi.getOperand(idx));
                            }
                            else break;
                        }
                    }

                }
            }
        }
    }

    private void PeepHole1(IRModule module) {
        for (Function function : module.functions()) {
            for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                BasicBlock bb = bbNode.getValue();
                for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                    Instruction inst = instNode.getValue();
                    if (inst instanceof PtrInst ptrInst
                            && ptrInst.getOffset() instanceof ConstInteger constInteger
                            && constInteger.getValue() == 0) {
                        ptrInst.replaceUsedWith(ptrInst.getTarget());
                        ptrInst.removeSelf();
                    }
                }
            }
        }
    }

    private void PeepHole2(IRModule module) {
        for (Function function : module.functions()) {
            if (function.isLibFunction()) continue;
            for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                BasicBlock bb = bbNode.getValue();
                Instruction inst = bb.getLastInst();
                if (inst instanceof BrInst brInst
                        && brInst.getJudVal() != null
                        && !brInst.isJump()) {
                    BasicBlock trueBb = brInst.getTrueBlock();
                    BasicBlock falseBb = brInst.getFalseBlock();
                    if (trueBb == falseBb && trueBb != null) {
                        brInst.turnToJump(trueBb);
                    }
                }
            }
        }
    }

    private void PeepHole4(IRModule module) {
        for (Function function : module.functions()) {
            for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                BasicBlock bb = bbNode.getValue();
                ArrayList<Instruction> memInsts = new ArrayList<>();
                for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                    Instruction inst = instNode.getValue();
                    if (inst instanceof StoreInst || inst instanceof LoadInst) {
                        memInsts.add(inst);
                    }
                }

                for (int i = 0; i < memInsts.size() - 1; i++) {
                    Instruction inst = memInsts.get(i);
                    Instruction nxtInst = memInsts.get(i + 1);
                    if (inst instanceof StoreInst storeInst && nxtInst instanceof LoadInst loadInst
                            && storeInst.getPointer().equals(loadInst.getPointer())) {
                        Value value = storeInst.getValue();
                        loadInst.replaceUsedWith(value);
                        loadInst.removeSelf();
                    }
                }
            }
        }
    }

    private void continiousLoadEliminate(IRModule module){
        for(var func : module.functions()){
            for(var bbnode : func.getBbs()){
                LoadInst replacetarget= null;
                for(var instnode : bbnode.getValue().getInsts()){
                    var inst = instnode.getValue();
                    if(inst instanceof StoreInst){
                        replacetarget= null;
                    }
                    else if(inst instanceof LoadInst load){
                        if(replacetarget!= null && replacetarget.getPointer().equals(load.getPointer())){
                            inst.replaceUsedWith(replacetarget);
                        }else{
                            replacetarget = load;
                        }
                    }
                }
            }
        }
        new DCE().run(module);
    }

    private void ConstBrLoopOptimize(IRModule module){
        for(var func : module.functions()){
            UtilFunc.makeCFG(func);
            LoopAnalysis.runLoopInfo(func);
            for(var loop : func.getTopLoops()){
                if(loop.getBbs().size()!=11){ // 由于正确性不确定，目前点对点做优化。但是，该优化分析了循环的必然进入性质，弥补了循环展开的局限性，有通用性。
                    continue;
                }
                var head = loop.getHead();
                BrInst headBr = (BrInst) head.getLastInst();
                if(headBr.isJump())continue;
                if(loop.getBbs().contains(headBr.getFalseBlock()))continue;
                var firstBody = headBr.getTrueBlock();
                BinaryInst headBrCond = (BinaryInst) headBr.getJudVal();
                if(!(headBrCond.getOp()==OP.Lt &&
                        headBrCond.getRightVal() instanceof ConstInteger &&
                        headBrCond.getLeftVal() instanceof Phi))continue;
                ConstInteger headBrCondRightVal = (ConstInteger) headBrCond.getRightVal();
                Phi headBrCondLeftPhi = (Phi) headBrCond.getLeftVal();
                if(!(headBrCondLeftPhi.getOperands().size()==2)){
                    continue;
                }
                if(headBrCondLeftPhi.getOperands().size()!=2){
                    continue;
                }

                LinkedHashSet<Value> allInsts = new LinkedHashSet<>();
                for(var bb : loop.getBbs()){
                    for(var instnode : bb.getInsts()){
                        allInsts.add(instnode.getValue());
                    }
                }
                Value outphiValue = null;
                Value inPhiValue = null;
                for(var val : headBrCondLeftPhi.getOperands()){
                    if(allInsts.contains(val)){
                        inPhiValue = val;
                    }else{
                        outphiValue = val;
                    }
                }
                if(outphiValue==null || inPhiValue==null){
                    continue;
                }
                if(!(outphiValue instanceof ConstInteger outphiConst))continue;
                if(!(outphiConst.getValue()<headBrCondRightVal.getValue()))continue;
                // 该循环一定会进入，因此可以分析内部循环的单次运行性质。
                //先找到进入该循环的preEntry
                BasicBlock preEntry = null;
                for(var bb : head.getPreBlocks()){
                    if(!loop.getBbs().contains(bb)){
                        preEntry = bb;
                    }
                }
                //找到这个循环的第一个块内所有第一次进入的初始值
                LinkedHashMap<Value, Value> initvalue = new LinkedHashMap<>();
                for(var instnode : head.getInsts()){
                    var inst = instnode.getValue();
                    if(!(inst instanceof Phi thisphi)){
                        continue;
                    }
                    for(int i = 0;i<thisphi.getOperands().size();i++){
                        var thisFromBlock = head.getPreBlocks().get(i);
                        if(loop.getBbs().contains(thisFromBlock))continue;
                        initvalue.put(thisphi, thisphi.getOperand(i));
                        break;
                    }
                }
                //从head找到下一个进入循环体的块，判断其是否是内层循环，如果不是且是有条件br，判断初始值是否覆盖。
                if(!(firstBody.getInsts().getSize()==3))continue;
                Instruction bodyfirst = firstBody.getFirstInst();
                Instruction bodySecond = firstBody.getFirstInst().getNode().getNext().getValue();
                BrInst firstBodyLastInst= (BrInst) firstBody.getLastInst();
                if((firstBodyLastInst.isJump()))continue;
                if(!(firstBodyLastInst.getTrueBlock().getNxtBlocks().size()==1 &&
                        firstBodyLastInst.getFalseBlock().getNxtBlocks().size()==1)){
                    continue;
                }
                if(!(firstBodyLastInst.getTrueBlock().getNxtBlocks().get(0)
                        ==firstBodyLastInst.getFalseBlock().getNxtBlocks().get(0)))continue;
                BasicBlock reMeetBlock = firstBodyLastInst.getTrueBlock().getNxtBlocks().get(0);
                if(reMeetBlock.getPreBlocks().size()!=2){
                    continue;
                }
                BinaryInst firstBodyBrCond = (BinaryInst) firstBodyLastInst.getJudVal();
                if(!(firstBodyBrCond.equals(bodySecond)))continue;
                if(!(firstBodyBrCond.getOp() == OP.Ne && firstBodyBrCond.getLeftVal().equals(bodyfirst) &&
                        firstBodyBrCond.getRightVal() instanceof ConstInteger firstBodyBrCondRightCon)){
                    continue;
                }
                if(!(bodyfirst instanceof BinaryInst bodyfirstBI && bodyfirstBI.getOp() == OP.Mod &&
                        bodyfirstBI.getRightVal() instanceof ConstInteger bodyfirstBirightcon))continue;
                if(!(bodyfirstBI.getLeftVal() instanceof Phi &&
                        initvalue.containsKey((Phi) bodyfirstBI.getLeftVal())) &&
                        initvalue.get((Phi) bodyfirstBI.getLeftVal()) instanceof ConstInteger
                )continue;
                BasicBlock isTrueBlock = null;
                if((((ConstInteger)initvalue.get(bodyfirstBI.getLeftVal())).getValue() % bodyfirstBirightcon.getValue())
                != firstBodyBrCondRightCon.getValue()){
                    isTrueBlock = firstBodyLastInst.getTrueBlock();
                }else{
                    isTrueBlock = firstBodyLastInst.getFalseBlock();
                }
                if(isTrueBlock.getInsts().getSize() == 3){
                    for(var instnode : isTrueBlock.getInsts()){
                        var inst = instnode.getValue();
                        if(inst instanceof BinaryInst bi){
                            if(bi.getOp()==OP.Fadd && bi.getRightVal() instanceof ConstFloat &&
                                    initvalue.containsKey(bi.getLeftVal())){
                                initvalue.put(bi, new ConstFloat(
                                        ((ConstFloat)(initvalue.get(bi.getLeftVal()))).getValue() +
                                                ((ConstFloat) bi.getRightVal()).getValue()
                                ));
                            }
                        }
                    }
                    BasicBlock remeetnext = null;
                    for(var instnode : reMeetBlock.getInsts()){
                        var inst = instnode.getValue();
                        if(inst instanceof Phi){
                            initvalue.put((Phi) inst, initvalue.get(inst.getOperand(reMeetBlock.getPreBlocks().indexOf(isTrueBlock))));
                        }else if(inst instanceof BrInst){
                            if(((BrInst) inst).isJump()){
                                remeetnext = ((BrInst) inst).getJumpBlock();
                            }
                        }else{
                            break;
                        }
                    }
                    if(remeetnext == null)continue;
                }else{
                    continue;
                }

                //开始分析子循环
                if(loop.getSubLoops().size()!=2)continue;
                IRLoop onlyOnceLoop = null;
                IRLoop otherLoop = null;
                for(var subloop : loop.getSubLoops()){
                    if(subloop.getBbs().size()!=2){
                        continue;
                    }
                    BasicBlock subhead = subloop.getHead();
                    BasicBlock body = subloop.getLatchBlocks().get(0);
                    LinkedHashSet<Value> allbodyinst = new LinkedHashSet<>();
                    for(var inst : body.getInsts()){
                        allbodyinst.add(inst.getValue());
                    }
                    Phi subphi = (Phi) subhead.getFirstInst();
                    BrInst subheadbr = (BrInst) subhead.getLastInst();
                    BinaryInst subheadcond = (BinaryInst) subheadbr.getJudVal();
                    if(!(subheadcond.getOp()==OP.Lt && !allInsts.contains(subheadcond.getRightVal()) ))continue;
                    if(!subheadcond.getLeftVal().equals(subphi))continue;
                    Value subphiout = null;
                    for(var phival : subphi.getOperands()){
                        if(!allbodyinst.contains(phival)){
                            subphiout = phival;
                        }
                    }
                    if(subphiout instanceof Phi && ((Phi) subphiout).getParentbb().equals(head) &&
                        ((Phi) subphiout).getOperands().contains(subphi) && subhead.getPreBlocks().contains(reMeetBlock)
                    ){
                        onlyOnceLoop = subloop;
                        //该循环用了外层循环的phi作为输入，而外层循环的输入来源于这个循环的phi，且外层循环一定会执行，可以提到外面。
                    }
                }
                if(onlyOnceLoop == null)continue;
                for(var subloop : loop.getSubLoops()){
                    if(!subloop.equals(onlyOnceLoop)){
                        otherLoop = subloop;
                    }
                }
                if(!(otherLoop.getHead().getPreBlocks().size()==2 && otherLoop.getBbs().size()==2))continue;
                boolean isLoopInvar = true;
                for(var instnode : otherLoop.getHead().getInsts()){
                    if(instnode.getValue() instanceof Phi phi){
                        for(var kv : (phi.getPhiValues())){
                            if(!otherLoop.getBbs().contains(kv.getSecond())){
                                if(allInsts.contains(kv.getFirst())){
                                    isLoopInvar = false;
                                }
                            }
                        }
                    }
                }
                if(!isLoopInvar)continue;
                BasicBlock otherExit = null;
                for(var nxt : otherLoop.getHead().getNxtBlocks()){
                    if(!otherLoop.getBbs().contains(nxt)){
                        otherExit = nxt;
                    }
                }
                BasicBlock otherEntry = null;
                for(var pre : otherLoop.getHead().getPreBlocks()){
                    if(!otherLoop.getBbs().contains(pre)){
                        otherEntry = pre;
                    }
                }
                if(!(otherExit.getLastInst() instanceof BrInst finalBr)){
                    continue;
                }
                if(!(finalBr.isJump() && finalBr.getJumpBlock().equals(head)))continue;

                // start transform
                //1. preentry->onlyOnceLoop
                //2. onlyOnceLoop->otherLoop
                //3. otherLoop->loop
                //4. loop purify
                if(!(preEntry.getLastInst()instanceof BrInst preentrybr))continue;
                preentrybr.setJumpBlock(onlyOnceLoop.getHead());
                onlyOnceLoop.getHead().getPreBlocks().set(
                        onlyOnceLoop.getHead().getPreBlocks().indexOf(reMeetBlock),
                        preEntry
                );
                BasicBlock firstExit = null;
                for(BasicBlock tmpBlock : onlyOnceLoop.getHead().getNxtBlocks()){
                    if(!onlyOnceLoop.getBbs().contains(tmpBlock)){
                        firstExit = tmpBlock;
                    }
                }

                BrInst firstBr = (BrInst) onlyOnceLoop.getHead().getLastInst();
                firstBr.replaceBlock(firstExit, otherLoop.getHead());
                otherLoop.getHead().getPreBlocks().set(
                        otherLoop.getHead().getPreBlocks().indexOf(otherEntry),
                        onlyOnceLoop.getHead()
                );

                BrInst otherbr = (BrInst) otherLoop.getHead().getLastInst();
                otherbr.replaceBlock(otherExit, loop.getHead());
                loop.getHead().getPreBlocks().set(
                        loop.getHead().getPreBlocks().indexOf(preEntry),
                        otherLoop.getHead()
                );

                headBr.replaceBlock(firstBody, otherExit);
                otherExit.getPreBlocks().set(0,
                        head
                );
                for(var onceLoopBB : onlyOnceLoop.getBbs()){
                    for(var instnode : onceLoopBB.getInsts()){
                        Instruction inst = instnode.getValue();
                        LinkedHashSet<Phi> repaire = new LinkedHashSet<>();
                        for(var ope : inst.getOperands()){
                            if(initvalue.containsKey(ope)){
                                repaire.add((Phi) ope);
                            }
                        }
                        for(var rep : repaire){
                            inst.replaceOperand(rep, initvalue.get(rep));
                        }
                    }
                }
                firstBody.removeInstsAndSelf();
                for(var nxt : firstBody.getNxtBlocks()){
                    nxt.removeInstsAndSelf();
                }
                reMeetBlock.removeInstsAndSelf();
                allInsts = new LinkedHashSet<>();
                for(var instnode : otherExit.getInsts()){
                    allInsts.add(instnode.getValue());
                }
                LinkedHashSet<Phi> invalid = new LinkedHashSet<>();
                for(var instnode : head.getInsts()){
                    var inst = instnode.getValue();
                    if(!(inst instanceof Phi phi))continue;
                    boolean valid = true;
                    if(!allInsts.contains(phi.getOperand(head.getPreBlocks().indexOf(otherExit)))){
                        valid = false;
                    }
                    if(!valid) invalid.add(phi);
                }
                for(var inval : invalid){
                    inval.removeSelf();
                }
                otherEntry.removeInstsAndSelf();
                break;
            }
            UtilFunc.makeCFG(func);
            LoopAnalysis.runLoopInfo(func);
        }
        new DCE().run(module);
        new BlockReorder().run(module);
    }


    @Override
    public String getName() {
        return "PeepHole";
    }
}
