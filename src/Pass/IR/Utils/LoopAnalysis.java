package Pass.IR.Utils;

import IR.Value.BasicBlock;
import IR.Value.ConstInteger;
import IR.Value.Function;
import IR.Value.Instructions.*;
import IR.Value.Value;

import java.util.*;

public class LoopAnalysis {
    private static final LinkedHashMap<BasicBlock, IRLoop> loopMap = new LinkedHashMap<>();
    private static final ArrayList<IRLoop> loops = new ArrayList<>();
    private static final ArrayList<IRLoop> topLevelLoops = new ArrayList<>();

    public static void runLoopInfo(Function function){
        runMainLoopInfo(function);
        runOtherLoopInfo();
    }

    //  目前只考虑简单循环，即一层的while(i<n){i = i + const}这种
    public static void runLoopIndvarInfo(Function function){
        for(IRLoop loop : function.getAllLoops()){
            getIndVarForLoop(loop);
        }
    }

    private static void getIndVarForLoop(IRLoop loop){
        if(!loop.isSimpleLoop()){
            return ;
        }
        BasicBlock headBlock = loop.getHead();
        //  itVar, itEnd分别对应while(i<n)中的i和n
        //  itVar的phiValue一个是init值，一个是用于迭代的值
        Phi itVar = null;
        Value itEnd = null;
        BrInst headBr = (BrInst) headBlock.getLastInst();
        if (headBr.getJudVal() == null) return ;
        BinaryInst headBrCond = (BinaryInst) headBr.getJudVal();
        //  简单循环的headCond一定有一个值是phi，也就是迭代变量
        Value brLeft = headBrCond.getLeftVal();
        Value brRight = headBrCond.getRightVal();

        if(brLeft instanceof Phi phi){
            itVar = phi;
            itEnd = headBrCond.getRightVal();
        }
        else if(brRight instanceof Phi phi){
            itVar = phi;
            itEnd = headBrCond.getLeftVal();
        }
        else{
            return ;
        }

        assert itVar != null;
        if(!itVar.getParentbb().equals(headBlock)){
            return;
        }

        Value itAlu, itInit;
        //  headBlock有两个前驱，看latch是哪个，从而找到对应的PhiValue
        BasicBlock latchBlock = loop.getLatchBlocks().get(0);
        if (latchBlock.equals(headBlock.getPreBlocks().get(0))) {
            itAlu = itVar.getOperand(0);
            itInit = itVar.getOperand(1);
        }
        else {
            itAlu = itVar.getOperand(1);
            itInit = itVar.getOperand(0);
        }
        if (!(itAlu instanceof BinaryInst)) {
            return;
        }

        Value itStep;
        if (((BinaryInst) itAlu).getLeftVal().equals(itVar)) {
            itStep = ((BinaryInst) itAlu).getRightVal();
        }
        else if (((BinaryInst) itAlu).getRightVal().equals(itVar)) {
            itStep = ((BinaryInst) itAlu).getLeftVal();
        }
        else {
            return;
        }

        OP itAluOp = ((BinaryInst) itAlu).getOp();

        if(itAluOp.equals(OP.Add) || itAluOp.equals(OP.Fadd)
                || itAluOp.equals(OP.Sub) || itAluOp.equals(OP.Fsub)
                || itAluOp.equals(OP.Mul) || itAluOp.equals(OP.Fmul)){
            loop.setIndInfo(itVar, itEnd, itInit, itAlu, itStep, headBrCond);
        }
    }

    private static void runOtherLoopInfo(){
        getExitingAndExitBlocks();
        getLatchBlocks();
    }

    private static void getExitingAndExitBlocks() {
        for (IRLoop l : loops) {
            for (BasicBlock bb : l.getBbs()) {
                for (BasicBlock succ : bb.getNxtBlocks()) {
                    if (!l.getBbs().contains(succ)) {
                        l.addExitingBlock(bb);
                        l.addExitBlock(succ);
                    }
                }
            }
        }
    }

    private static void getLatchBlocks() {
        for (IRLoop l : loops) {
            for (BasicBlock preBb : l.getHead().getPreBlocks()) {
                if (l.getBbs().contains(preBb)) {
                    l.addLatchBlock(preBb);
                }
            }
        }
    }

    private static void runMainLoopInfo(Function function){
        DomAnalysis.run(function);
        //  idoms是该基本块直接支配的基本块
        LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> idoms =  function.getIdoms();
        //  dom是该基本块被哪些基本块支配
        LinkedHashMap<BasicBlock, LinkedHashSet<BasicBlock>> domer = function.getDomer();
        loopMap.clear();
        loops.clear();
        topLevelLoops.clear();

        // 后序遍历domTree
        ArrayList<BasicBlock> postOrder = DomAnalysis.getDomPostOrder(function);
        for(BasicBlock headBb : postOrder){
            Stack<BasicBlock> backEdges = new Stack<>();
            // 如果一个结点支配其前继，则将这个边识别为backedge
            for (BasicBlock backEdge : headBb.getPreBlocks()) {
                if (domer.getOrDefault(backEdge, new LinkedHashSet<>()).contains(headBb)) {
                    backEdges.push(backEdge);
                }
            }

            //  沿着reverse cfg的找到所有循环体
            if (!backEdges.isEmpty()) {
                //  初始化一个loop，以backedge的端点为loop header
                IRLoop loop = new IRLoop(headBb);
                //  构建该loop本身，同时与其中的subLoop建立关系
                discoverAndMapSubloop(loop, backEdges);
            }
        }

        //   完善循环嵌套关系 populateLoopsDFS
        //   Add a single Block to its ancestor loops in PostOrder. If the block is a
        //   subloop header, add the subloop to its parent in PostOrder, then reverse the
        //   Block and Subloop vectors of the now complete subloop to achieve RPO.
        for(BasicBlock bb : postOrder){
            insertIntoLoop(bb);
        }
        computeAllLoops();

        function.setLoopInfo(loopMap);
        function.setTopLoops(topLevelLoops);
        function.setAllLoops(loops);
    }

    private static void insertIntoLoop(BasicBlock bb){
        IRLoop subloop = loopMap.get(bb);
        //  只有当遍历完subloop里的所有bb后才会遍历到head
        if(subloop != null && subloop.getHead().equals(bb)){
            if(subloop.hasParent()){
                subloop.getParentLoop().addSubLoop(subloop);
            }
            else topLevelLoops.add(subloop);
            // For convenience, Blocks and Subloops are inserted in postorder. Reverse
            // the lists, except for the loop header, which is always at the beginning.
            subloop.reverseBlock1();
            Collections.reverse(subloop.getSubLoops());
            subloop = subloop.getParentLoop();
        }
        while(subloop != null){
            subloop.addBlock(bb);
            subloop = subloop.getParentLoop();
        }
    }

    private static void discoverAndMapSubloop(IRLoop loop, Stack<BasicBlock> backEdges){
        //  倒着往前遍历
        while (!backEdges.isEmpty()) {
            BasicBlock edge = backEdges.pop();
            IRLoop subLoop = loopMap.get(edge);
            //  如果该block还没匹配loop,放进该loop中
            if (subLoop == null) {
                loopMap.put(edge, loop);
                if (edge.equals(loop.getHead())) {
                    continue;
                }
                //  将该block所有前继也放进stack，有种bfs的意思
                for (BasicBlock edgePred : edge.getPreBlocks()) {
                    backEdges.push(edgePred);
                }
            }
            //  如果该block已经有subloop，找到该subloop最外层的循环
            else {
                while (subLoop.hasParent()) {
                    subLoop = subLoop.getParentLoop();
                }
                if (subLoop == loop) {
                    continue;
                }
                //  设置循环嵌套关系
                subLoop.setParentLoop(loop);
                //  将subloop header的前继节点放进stack
                for (BasicBlock subHeadPred : subLoop.getHead().getPreBlocks()) {
                    if (!Objects.equals(loopMap.get(subHeadPred), subLoop)) {
                        backEdges.push(subHeadPred);
                    }
                }
            }
        }
    }

    private static void computeAllLoops(){
        loops.clear();
        Stack<IRLoop> stack = new Stack<>();
        stack.addAll(topLevelLoops);
        loops.addAll(topLevelLoops);
        while (!stack.isEmpty()) {
            var l = stack.pop();
            if (!l.getSubLoops().isEmpty()) {
                stack.addAll(l.getSubLoops());
                loops.addAll(l.getSubLoops());
            }
        }
    }

}
