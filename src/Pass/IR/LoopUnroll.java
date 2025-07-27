package Pass.IR;

import Driver.Config;
import IR.IRBuildFactory;
import IR.IRModule;
import IR.Type.IntegerType;
import IR.Value.*;
import IR.Value.Instructions.*;
import Pass.IR.Utils.CloneHelper;
import Pass.IR.Utils.IRLoop;
import Pass.IR.Utils.LoopAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;
import Utils.DataStruct.Pair;

import java.util.*;

import static Pass.IR.Utils.UtilFunc.*;

public class LoopUnroll implements Pass.IRPass {

    private final IRBuildFactory f = IRBuildFactory.getInstance();

    private BasicBlock head;
    private BasicBlock latch;
    private BasicBlock exit;
    private BasicBlock headNext;

    private boolean change;

    @Override
    public void run(IRModule module) {
        for(Function function : module.functions()){
            if (function.isLibFunction()) continue;
            LoopAnalysis.runLoopInfo(function);
            LoopAnalysis.runLoopIndvarInfo(function);
            change = false;
            ConstLoopUnroll(function);
            if(change){
                makeCFG(function);
            }
            DynamicLoopUnroll(function);
        }
    }

    private void DynamicLoopUnroll(Function function) {
        ArrayList<IRLoop> dfsOrderLoops = new ArrayList<>();
        for(IRLoop loop : function.getTopLoops()){
            dfsOrderLoops.addAll(getDFSLoops(loop));
        }

        for(IRLoop loop : dfsOrderLoops){
            DynamicLoopUnrollForLoop(loop);
        }
    }

    //  int i = 0; while (i < n) xxx; i = i + 1;
    private void DynamicLoopUnrollForLoop(IRLoop loop) {
        if (!loop.isSetIndVar()) {
            return;
        }

        Value itInit = loop.getItInit(), itStep = loop.getItStep(), itEnd = loop.getItEnd();

        BinaryInst itAlu = (BinaryInst) loop.getItAlu();

        if (!(itInit instanceof ConstInteger) || !(itStep instanceof ConstInteger)) return ;
        if (((ConstInteger) itInit).getValue() != 0 || ((ConstInteger) itStep).getValue() != 1) return ;
        if (itAlu.getOp() != OP.Add) return ;
        if (loop.getHeadBrCond().getOp() != OP.Lt) return ;

        //  初始化该循环的各种block
        if(!initAllBlock(loop)){
            return ;
        }

        if (loop.getBbs().size() != 2) return ;
        if (head.getPreBlocks().size() != 2) return ;

        //  i = i + 1后不能再有指令用到i
        boolean useItVar = false;
        BinaryInst stepInst = (BinaryInst) loop.getItAlu();
        IList.INode<Instruction, BasicBlock> itNode = stepInst.getNode().getNext();
        while (itNode != null) {
            Instruction inst = itNode.getValue();
            for (Value value : inst.getUseValues()) {
                if (value == stepInst) {
                    useItVar = true; break;
                }
            }
            itNode = itNode.getNext();
        }
        if (useItVar) return ;

        //  head中只有phi, brCond和br
        int cnt = 0;
        boolean ok = true;
        for (IList.INode<Instruction, BasicBlock> instNode : head.getInsts()) {
            Instruction inst = instNode.getValue();
            if (!(inst instanceof Phi phi)) {
                if (cnt == 0 && inst != loop.getHeadBrCond()) {
                    ok = false; break;
                }
                else if (cnt == 1 && !(inst instanceof BrInst)) {
                    ok = false; break;
                }
                else if (cnt > 1){
                    ok = false; break;
                }
                cnt++;
            }
        }
        if (!ok) return ;

        if (itEnd instanceof Instruction inst) {
            BasicBlock nBb = inst.getParentbb();
            if (nBb == head) return ;
        }
        if (!exit.getPreBlocks().contains(head)) return ;
        //  能到这就肯定会被展开了
        change = true;
        DoDynamicLoopUnRoll(loop);
    }

    private void DoDynamicLoopUnRoll(IRLoop loop) {
        Value itEnd = loop.getItEnd(), itVar = loop.getItVar();
        BinaryInst stepInst = (BinaryInst) loop.getItAlu();

        Function function = head.getParentFunc();
        BasicBlock preHead = f.getBasicBlock(function);
        BasicBlock preExitHead = f.getBasicBlock(function);
        BasicBlock preExitLatch = f.getBasicBlock(function);
        int latchIdx = head.getPreBlocks().indexOf(latch);

        //  Initialize: move StepInst and set step_size to 4
        IList.INode<Instruction, BasicBlock> stepNode = stepInst.getNode();
        IList.INode<Instruction, BasicBlock> brNode = latch.getLastInst().getNode();
        stepNode.removeFromList();
        stepNode.insertBefore(brNode);
        if (stepInst.getLeftVal() instanceof ConstInteger) {
            stepInst.replaceOperand(0, new ConstInteger(4, IntegerType.I32));
        }
        else stepInst.replaceOperand(1, new ConstInteger(4, IntegerType.I32));

        //  Initialize: 维护head中的phi，用来知道哪些变量在循环中进行了迭代
        HashMap<Phi, Value> headPhiMap = new HashMap<>();
        HashMap<Value, Phi> valueToPhiMap = new HashMap<>();
        for (IList.INode<Instruction, BasicBlock> instNode : head.getInsts()) {
            Instruction inst = instNode.getValue();
            if (inst instanceof Phi phi) {
                if (phi == itVar) continue;
                Value value = phi.getOperand(latchIdx);
                headPhiMap.put(phi, value);
                valueToPhiMap.put(value, phi);
            }
            else break;
        }

        //  1. make preHead, preExitHead, preExitLatch;
        BinaryInst andInst = f.buildBinaryInst(itEnd, new ConstInteger(2147483640, IntegerType.I32), OP.And, preHead);
        BinaryInst cmpInst = f.buildBinaryInst(itEnd, new ConstInteger(3, IntegerType.I32), OP.Gt, preHead);
        f.buildBrInst(cmpInst, head, preExitHead, preHead);
        for (BasicBlock preBb : head.getPreBlocks()) {
            if (preBb != latch) {
                preBb.removeNxtBlock(head); preBb.setNxtBlock(preHead);
                preHead.setPreBlock(preBb);
                preHead.setNxtBlock(head);
                int idx = head.getPreBlocks().indexOf(preBb);
                head.getPreBlocks().set(idx, preHead);
                BrInst brInst = (BrInst) preBb.getLastInst();
                brInst.replaceBlock(head, preHead);
            }
        }
        preHead.insertBefore(head);
        preExitHead.insertAfter(latch);
        head.removeNxtBlock(exit);
        head.setNxtBlock(preExitHead);
        BrInst brInst = (BrInst) head.getLastInst();
        brInst.setFalseBlock(preExitHead);
        preExitHead.setPreBlock(preHead);
        preExitHead.setPreBlock(head);
        preExitHead.setNxtBlock(exit);
        exit.getPreBlocks().set(exit.getPreBlocks().indexOf(head), preExitHead);
        //  copy head to preExitHead
        HashMap<Phi, Phi> preExitToHeadPhiMap = new HashMap<>();
        HashMap<Phi, Phi> headToPreExitPhiMap = new HashMap<>();
        Value exitItVar = null;
        CloneHelper c0 = new CloneHelper();
        for (IList.INode<Instruction, BasicBlock> instNode : head.getInsts()) {
            Instruction inst = instNode.getValue();
            if (inst == loop.getHeadBrCond()) break;
            if (inst instanceof Phi phi) {
                ArrayList<Value> values = new ArrayList<>();
                values.add(phi.getOperand(1 - latchIdx));
                values.add(new ConstInteger(0, IntegerType.I32));
                values.add(phi.getOperand(latchIdx));
                Phi newPhi = f.buildPhi(preExitHead, phi.getType(), values);
                preExitToHeadPhiMap.put(newPhi, phi);
                headToPreExitPhiMap.put(phi, newPhi);
                c0.addValueMapping(phi, newPhi);
                if (phi == itVar) exitItVar = newPhi;
            }
        }

        assert exitItVar != null;
        BinaryInst newCmpInst = f.buildBinaryInst(exitItVar, itEnd, OP.Lt, preExitHead);
        f.buildBrInst(newCmpInst, preExitLatch, exit, preExitHead);
        //  copy latch to preExitLatch
        HashMap<Phi, Value> headPhiToPreExitLatch = new HashMap<>();
        for (IList.INode<Instruction, BasicBlock> instNode : latch.getInsts()) {
            Instruction inst = instNode.getValue();
            if (inst == stepInst || inst instanceof BrInst) continue;
            Instruction newInst = c0.copyInstruction(inst);
            c0.addValueMapping(inst, newInst);
            if (valueToPhiMap.containsKey(inst)) {
                Phi headPhi = valueToPhiMap.get(inst);
                headPhiToPreExitLatch.put(headPhi, newInst);
            }
            preExitLatch.addInst(newInst);
        }
        BinaryInst newItVar = f.buildBinaryInst(headToPreExitPhiMap.get(itVar), new ConstInteger(1, IntegerType.I32), OP.Add, preExitLatch);

        //  set phiValues(idx: 2) for preExitHead
        for (IList.INode<Instruction, BasicBlock> instNode : preExitHead.getInsts()) {
            Instruction inst = instNode.getValue();
            if (inst instanceof Phi phi) {
                Phi headPhi = preExitToHeadPhiMap.get(phi);
                if (headPhi == itVar) phi.replaceOperand(2, newItVar);
                else if (headPhiToPreExitLatch.containsKey(headPhi)) {
                    phi.replaceOperand(2, headPhiToPreExitLatch.get(headPhi));
                }
            }
        }

        f.buildBrInst(preExitHead, preExitLatch);
        preExitLatch.setNxtBlock(preExitHead);
        preExitHead.setPreBlock(preExitLatch);
        preExitLatch.insertAfter(preExitHead);


        //  2. 把边界值改成n & 2147483640
        BinaryInst brCond = loop.getHeadBrCond();
        if (brCond.getLeftVal() != itVar) {
            brCond.replaceOperand(0, andInst);
        }
        else brCond.replaceOperand(1, andInst);



        //  4. 插入指令，复制指令3遍
        CloneHelper c = new CloneHelper();
        Instruction firstOrInst = null;
        for (int time = 1; time <= 3; time++) {
            BinaryInst orInst = f.getBinaryInst(itVar, new ConstInteger(time, IntegerType.I32), OP.Or, IntegerType.I32);
            if (firstOrInst == null) firstOrInst = orInst;
            orInst.insertBefore(stepInst);
            c.addValueMapping(itVar, orInst);
            ArrayList<Instruction> newInsts = new ArrayList<>();
            for (IList.INode<Instruction, BasicBlock> instNode : latch.getInsts()) {
                Instruction inst = instNode.getValue();
                if (inst == firstOrInst) break;
                Instruction newInst = c.copyInstruction(inst);
                c.addValueMapping(inst, newInst);

                ArrayList<Pair<Integer, Value>> phiIdxs = new ArrayList<>();
                for (int i = 0; i < newInst.getOperands().size(); i++) {
                    Value operand = newInst.getOperand(i);
                    if (headPhiMap.containsKey(operand)) {
                        phiIdxs.add(new Pair<>(i, headPhiMap.get(operand)));
                    }
                }
                for (Pair<Integer, Value> pair : phiIdxs) {
                    int pos = pair.getFirst(); Value value = pair.getSecond();
                    newInst.replaceOperand(pos, value);
                }

                if (valueToPhiMap.containsKey(inst)) {
                    Phi phi = valueToPhiMap.get(inst);
                    headPhiMap.put(phi, newInst);
                }

                newInsts.add(newInst);
            }
            for (Instruction newInst : newInsts) {
                newInst.insertBefore(stepInst);
            }
        }

        //  5. Fix headPhiValues
        for (IList.INode<Instruction, BasicBlock> instNode : head.getInsts()) {
            Instruction inst = instNode.getValue();
            if (inst instanceof Phi phi && phi != itVar) {
                phi.replaceOperand(latchIdx, headPhiMap.get(phi));
            }
        }

        //  6. Fix preExitHead PhiValues
        for (IList.INode<Instruction, BasicBlock> instNode : preExitHead.getInsts()) {
            Instruction inst = instNode.getValue();
            if (inst instanceof Phi phi) {
                phi.replaceOperand(1, preExitToHeadPhiMap.get(phi));
            }
        }

        //  7. 修复Exit中的LCSSA
        for (IList.INode<Instruction, BasicBlock> instNode : exit.getInsts()) {
            Instruction inst = instNode.getValue();
            if (inst instanceof Phi phi) {
                int n = phi.getOperands().size();
                for (int i = 0; i < n; i++) {
                    Value value = phi.getOperand(i);
                    if (headToPreExitPhiMap.containsKey(value)) {
                        phi.replaceOperand(i, headToPreExitPhiMap.get(value));
                    }
                }
            }
        }
    }

    //  init/step/end 均为常数
    private void ConstLoopUnroll(Function function){
        ArrayList<IRLoop> dfsOrderLoops = new ArrayList<>();
        for(IRLoop loop : function.getTopLoops()){
            dfsOrderLoops.addAll(getDFSLoops(loop));
        }

        for(IRLoop loop : dfsOrderLoops){
            ConstLoopUnrollForLoop(loop);
        }
    }

    private ArrayList<IRLoop> getDFSLoops(IRLoop loop){
        ArrayList<IRLoop> allLoops = new ArrayList<>();
        for(IRLoop subLoop : loop.getSubLoops()){
            allLoops.addAll(getDFSLoops(subLoop));
        }
        allLoops.add(loop);
        return allLoops;
    }

    private void ConstLoopUnrollForLoop(IRLoop loop){
        if (!loop.isSetIndVar()) {
            return;
        }

        Value itInit = loop.getItInit();
        Value itStep = loop.getItStep();
        Value itEnd = loop.getItEnd();
        BinaryInst itAlu = (BinaryInst) loop.getItAlu();

        if (!(itInit instanceof Const) || !(itStep instanceof Const) || !(itEnd instanceof Const)) {
            return;
        }

        BinaryInst headBrCond = loop.getHeadBrCond();
        int init = ((ConstInteger) itInit).getValue();
        int step = ((ConstInteger) itStep).getValue();
        int end = ((ConstInteger) itEnd).getValue();
        if (step == 0) return ;

        //  aluOP是i = i + 1中的add
        //  cmpOP是i < n中的<
        OP aluOP = itAlu.getOp();
        OP cmpOP = headBrCond.getOp();
        int times = getConstLoopTimes(aluOP, cmpOP, init, step, end);

        if (times < 0) {
            return;
        }
        loop.setItTimes(times);
        if(!initCheck(loop)){
            return;
        }
        //  初始化该循环的各种block
        if(!initAllBlock(loop)){
            return ;
        }

        //  能到这就肯定会被展开了
        change = true;
        DoConstLoopUnRoll(loop);
    }

    private int getConstLoopTimes(OP aluOP, OP cmpOP, int init, int step, int end){
        int times = -1;
        if(aluOP.equals(OP.Add)){
            //  while (i == n)
            if(cmpOP.equals(OP.Eq)){
                times = (init == end) ? 1 : 0;
            }
            //  while (i != n)
            else if(cmpOP.equals(OP.Ne)){
                times = ((end - init) % step == 0) ? (end - init) / step : -1;
            }
            //  while (i >=/<= n)
            else if(cmpOP.equals(OP.Ge) || cmpOP.equals(OP.Le)){
                times = (end - init) / step + 1;
            }
            //  while (i >/< n)
            else if(cmpOP.equals(OP.Gt) || cmpOP.equals(OP.Lt)){
                times = ((end - init) % step == 0)? (end - init) / step : (end - init) / step + 1;
            }
        }
        else if(aluOP.equals(OP.Sub)){
            if(cmpOP.equals(OP.Eq)){
                times = (init == end)? 1:0;
            }
            else if(cmpOP.equals(OP.Ne)){
                times = ((init - end) % step == 0)? (init - end) / step : -1;
            }
            else if(cmpOP.equals(OP.Ge) || cmpOP.equals(OP.Le)){
                times = (init - end) / step + 1;
            }
            else if(cmpOP.equals(OP.Gt) || cmpOP.equals(OP.Lt)){
                times = ((init - end) % step == 0)? (init - end) / step : (init - end) / step + 1;
            }
        }
        else if(aluOP.equals(OP.Mul)){
            double val = Math.log((double) end / init) / Math.log(step);
            boolean tag = init * Math.pow(step, val) == end;
            if(cmpOP.equals(OP.Eq)){
                times = (init == end)? 1:0;
            }
            else if(cmpOP.equals(OP.Ne)){
                times = tag ? (int) val : -1;
            }
            else if(cmpOP.equals(OP.Ge) || cmpOP.equals(OP.Le)){
                times = (int) val + 1;
            }
            else if(cmpOP.equals(OP.Gt) || cmpOP.equals(OP.Lt)){
                times = tag ? (int) val : (int) val + 1;
            }
        }
        return times;
    }

    private boolean initCheck(IRLoop loop){
        LinkedHashSet<BasicBlock> exits = new LinkedHashSet<>();
        LinkedHashSet<BasicBlock> allBB = new LinkedHashSet<>(loop.getBbs());

        for (IRLoop subLoop: loop.getSubLoops()) {
            checkDFS(subLoop, allBB, exits);
        }

        for (BasicBlock bb: exits) {
            if (!allBB.contains(bb)) {
                return false;
            }
        }

        int times = loop.getItTimes();
        int cnt = cntDFS(loop);
        return (long) cnt * times <= Config.loopUnrollMaxLines;
    }

    private boolean initAllBlock(IRLoop loop){
        head = loop.getHead();
        //  Latch & Entering
        for (BasicBlock bb: head.getPreBlocks()) {
            if (loop.getLatchBlocks().contains(bb)) {
                latch = bb;
            }
        }

        //  Exit
        for (BasicBlock bb: loop.getExitBlocks()) {
            exit = bb;
        }
        //  HeadNext
        for (BasicBlock bb: head.getNxtBlocks()) {
            if (!bb.equals(exit)) {
                headNext = bb;
            }
        }

        assert headNext != null;
        return headNext.getPreBlocks().size() == 1;
    }

    private void DoConstLoopUnRoll(IRLoop loop){
        //  开始unroll
        //  phiLatchMap: 存放从latch到达head时各个phi所取的值
        //  beginToEnd: 存放head中的value到最后映射成了什么值，用于修复exit中的LCSSA
        LinkedHashMap<Value, Value> phiMap = new LinkedHashMap<>();
        LinkedHashMap<Value, Value> beginToEnd = new LinkedHashMap<>();
        Function function = head.getParentFunc();
        ArrayList<Phi> headPhiInsts = new ArrayList<>();
        for (IList.INode<Instruction, BasicBlock> instNode : head.getInsts()) {
            Instruction inst = instNode.getValue();
            if (!(inst instanceof Phi)) {
                break;
            }
            headPhiInsts.add((Phi) inst);
        }

        //  删除掉headPhi和latchBlock的联系，新建phiInLatchNxt
        int latchIndex = head.getPreBlocks().indexOf(latch);
        for (Phi phi : headPhiInsts) {
            Value newValue = phi.getUseValues().get(latchIndex);
            phi.removeOperand(latchIndex);
            phiMap.put(phi, newValue);
            beginToEnd.put(phi, newValue);
        }
        //  删除headBlock和latchBlock/exitBlock的前驱后继关系
        head.removePreBlock(latch);
        latch.removeNxtBlock(head);
        head.removeNxtBlock(exit);
        exit.removePreBlock(head);
        loop.removeBb(head);
        head.setLoop(loop.getParentLoop());
        BrInst brInst = (BrInst) head.getLastInst();
        brInst.turnToJump(headNext);
        latch.removeLastInst();

        //  得到复制Bb的dfs序
        LinkedHashSet<BasicBlock> allBbInLoop = new LinkedHashSet<>();
        getAllBBInLoop(loop, allBbInLoop);
        allBbInLoop.remove(head);
        ArrayList<BasicBlock> dfsBbInLoop = getDFSBBInLoop(headNext);

        //  更新该loop与parentLoop的关系(如果有的话)
        IRLoop parentLoop = loop.getParentLoop();
        if(parentLoop != null) {
            parentLoop.getSubLoops().remove(loop);
            for (BasicBlock bb : loop.getBbs()) {
                bb.setLoop(parentLoop);
                if (!parentLoop.getBbs().contains(bb)) {
                    parentLoop.addBlock(bb);
                }
            }
            for (IRLoop subLoop : loop.getSubLoops()) {
                subLoop.setParentLoop(parentLoop);
            }
        }
        else{
            function.getTopLoops().remove(loop);
        }

        //  开始unroll
        BasicBlock oldHeadNext = headNext;
        BasicBlock oldLatch = latch;
        CloneHelper cloneHelper = new CloneHelper();
        for(Value key : phiMap.keySet()){
            cloneHelper.addValueMapping(key, phiMap.get(key));
        }
        for (int time = 0; time < loop.getItTimes() - 1; time++) {
            //  copy allBlockInLoop and update dfsBbInLoop
            for(BasicBlock bb : dfsBbInLoop){
                BasicBlock newBb = f.buildBasicBlock(function);
                cloneHelper.addValueMapping(bb, newBb);
            }
            for(BasicBlock bb : dfsBbInLoop){
                BasicBlock newBb = (BasicBlock) cloneHelper.findValue(bb);
                cloneHelper.copyBlockToBlock(bb, newBb);
                newBb.setLoop(bb.getLoop());
                bb.getLoop().addBlock(newBb);
            }
            //  tmpDfsBbInLoop记录clone前的所有loop中的bb
            ArrayList<BasicBlock> tmpDfsBbInLoop = new ArrayList<>(dfsBbInLoop);
            dfsBbInLoop.clear();
            for(BasicBlock bb : tmpDfsBbInLoop){
                dfsBbInLoop.add((BasicBlock) cloneHelper.findValue(bb));
            }

            //  update newHeadNext, newLatch
            BasicBlock newHeadNext = (BasicBlock) cloneHelper.findValue(oldHeadNext);
            BasicBlock newLatch = (BasicBlock) cloneHelper.findValue(oldLatch);
            //  init allBbInLoop's preNxt
            makeCFG(dfsBbInLoop);
            //  init specialBb's preNxt(oldLatch->nxtBb = newHeadNext)
            oldLatch.setNxtBlock(newHeadNext);
            newHeadNext.setPreBlock(oldLatch);
            f.buildBrInst(newHeadNext, oldLatch);

            //  update phi
            ArrayList<Phi> phis = getAllPhiInBbs(tmpDfsBbInLoop);
            for (Phi phi : phis) {
                for (int i = 0; i < phi.getOperands().size(); i++) {
                    BasicBlock preBB = phi.getParentbb().getPreBlocks().get(i);
                    BasicBlock nowBB = (BasicBlock) cloneHelper.findValue(preBB);
                    Phi copyPhi = (Phi) cloneHelper.findValue(phi);
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
                    else copyValue = cloneHelper.findValue(value);
                    copyPhi.replaceOperand(index, copyValue);
                }
            }
            //  update beginToEnd
            for(Value key : beginToEnd.keySet()){
                Value value = beginToEnd.get(key);
                beginToEnd.put(key, cloneHelper.findValue(value));
            }

            //  update oldHeadNext, oldLatch
            oldHeadNext = newHeadNext;
            oldLatch = newLatch;
        }

        //  oldLatch->nextBlock := exit
        oldLatch.setNxtBlock(exit);
        exit.setPreBlock(oldLatch);
        f.buildBrInst(exit, oldLatch);

        //  修正exitBb中的LCSSA
        ArrayList<Phi> phiInExit = getPhiInBb(exit);
        for(Phi phi : phiInExit){
            for(Value value : phi.getUseValues()){
                if(value instanceof Instruction inst && inst.getParentbb().equals(head)){
                    phi.replaceOperand(value, beginToEnd.get(value));
                }
            }
        }

    }

    private ArrayList<BasicBlock> getDFSBBInLoop(BasicBlock dfsEntry){
        LinkedHashSet<BasicBlock> visitedMap = new LinkedHashSet<>();
        Stack<BasicBlock> dfsStack = new Stack<>();
        ArrayList<BasicBlock> dfsBBInLoop = new ArrayList<>();
        dfsStack.push(dfsEntry);
        visitedMap.add(dfsEntry);
        while (!dfsStack.isEmpty()) {
            BasicBlock loopBlock = dfsStack.pop();
            dfsBBInLoop.add(loopBlock);
            if(!loopBlock.getNxtBlocks().isEmpty()) {
                for (BasicBlock basicBlock : loopBlock.getNxtBlocks()) {
                    if (!visitedMap.contains(basicBlock)) {
                        visitedMap.add(basicBlock);
                        dfsStack.push(basicBlock);
                    }
                }
            }
        }
        return dfsBBInLoop;
    }

    private void getAllBBInLoop(IRLoop loop, LinkedHashSet<BasicBlock> bbs) {
        bbs.addAll(loop.getBbs());
        for (IRLoop subLoop : loop.getSubLoops()) {
            getAllBBInLoop(subLoop, bbs);
        }
    }

    //  DFS获取该loop下的所有bb和exitbb
    private void checkDFS(IRLoop loop, LinkedHashSet<BasicBlock> allBB, LinkedHashSet<BasicBlock> exits) {
        allBB.addAll(loop.getBbs());
        exits.addAll(loop.getExitBlocks());
        for (IRLoop subLoop: loop.getSubLoops()) {
            checkDFS(subLoop, allBB, exits);
        }
    }

    private int cntDFS(IRLoop loop){
        int cnt = 0;
        for (BasicBlock bb: loop.getBbs()) {
            cnt += bb.getInsts().getSize();
        }
        for (IRLoop subLoop: loop.getSubLoops()) {
            cnt += cntDFS(subLoop);
        }
        return cnt;
    }

    @Override
    public String getName() {
        return "LoopUnroll";
    }
}
