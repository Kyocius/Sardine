package Pass.IR.Utils;

import IR.Value.BasicBlock;
import IR.Value.Instructions.BinaryInst;
import IR.Value.Instructions.Instruction;
import IR.Value.Instructions.OP;
import IR.Value.Value;
import Utils.DataStruct.IList;

import java.util.*;

public class IRLoop {
    // 循环头块
    private final BasicBlock header;
    // 子循环列表
    private final ArrayList<IRLoop> subLoops = new ArrayList<>();
    // 循环内所有基本块
    private final ArrayList<BasicBlock> bbs = new ArrayList<>();
    // 跳出循环的块（exiting block）
    private final LinkedHashSet<BasicBlock> exitingBlocks = new LinkedHashSet<>();
    // 循环退出后第一个到达的块（exit block）
    private final LinkedHashSet<BasicBlock> exitBlocks = new LinkedHashSet<>();
    // 跳转到循环头的块（latch block）
    private final ArrayList<BasicBlock> latchBlocks = new ArrayList<>();
    // 父循环
    private IRLoop parentLoop = null;
    // 归纳变量相关信息
    private Value itVar = null;      // 归纳变量
    private Value itEnd = null;      // 归纳变量终止值
    private Value itInit = null;     // 归纳变量初始值
    private Value itAlu = null;      // 归纳变量运算结果
    private Value itStep = null;     // 归纳变量步长
    private BinaryInst headBrCond = null; // 循环头的分支条件
    private boolean setIndVar = false;    // 是否设置了归纳变量
    private boolean setUnrolled = false;  // 是否已展开
    private int times;                    // 循环次数
    // 循环折叠相关信息
    private boolean canLoopFold = false;  // 是否可以循环折叠
    private Value phiEnterValue;          // phi节点进入值

    public IRLoop(BasicBlock header) {
        this.header = header;
        bbs.add(header);
    }

    /**
     * 获取循环内所有基本块列表
     * @return 循环内的基本块列表
     */
    public ArrayList<BasicBlock> getBbs(){
        return bbs;
    }

    /**
     * 添加跳出循环的基本块（exiting block）
     * @param bb 跳出循环的基本块
     */
    public void addExitingBlock(BasicBlock bb) {
        exitingBlocks.add(bb);
    }

    /**
     * 添加循环退出后的第一个到达的块（exit block）
     * @param bb 循环退出后的基本块
     */
    public void addExitBlock(BasicBlock bb) {
        exitBlocks.add(bb);
    }

    /**
     * 获取循环退出后的第一个到达的块（exit block）集合
     * @return exitBlocks 循环退出后的基本块集合
     */
    public LinkedHashSet<BasicBlock> getExitBlocks(){
        return exitBlocks;
    }

    /**
     * 添加跳转到循环头的块（latch block）
     * @param latchBb 跳转到循环头的基本块
     */
    public void addLatchBlock(BasicBlock latchBb){
        latchBlocks.add(latchBb);
    }

    /**
     * 获取循环头块
     * @return 循环头的基本块
     */
    public BasicBlock getHead() {
        return header;
    }

    /**
     * 判断当前循环是否有父循环
     * @return 如果有父循环则返回true，否则返回false
     */
    public boolean hasParent() {
        return parentLoop != null;
    }

    /**
     * 获取父循环
     * @return 父循环，如果没有则为null
     */
    public IRLoop getParentLoop() {
        return parentLoop;
    }

    /**
     * 设置父循环
     * @param parentLoop 父循环对象
     */
    public void setParentLoop(IRLoop parentLoop) {
        this.parentLoop = parentLoop;
    }

    /**
     * 添加子循环
     * @param subLoop 子循环对象
     */
    public void addSubLoop(IRLoop subLoop){
        subLoops.add(subLoop);
    }

    public void reverseBlock1() {
        Collections.reverse(bbs);
        BasicBlock bb = bbs.get(bbs.size() - 1);
        bbs.add(0, bb);
        bbs.remove(bbs.size() - 1);
    }

    /**
     * 获取子循环列表
     * @return 子循环的列表
     */
    public ArrayList<IRLoop> getSubLoops() {
        return subLoops;
    }

    /**
     * 向循环内添加一个基本块
     * @param bb 要添加的基本块
     */
    public void addBlock(BasicBlock bb) {
        bbs.add(bb);
    }

    /**
     * 获取当前循环的嵌套深度
     * @return 当前循环的嵌套层数，最外层为1
     */
    public int getLoopDepth() {
        int depth = 0;
        IRLoop now = this;
        while (now != null) {
            depth++;
            now = now.parentLoop;
        }
        return depth;
    }

    /** 判断当前循环是否为“简单循环”
    simpleLoop满足以下几个条件：
    1. header有2个前驱（latch-head和正常运行-head）
    2. latchBlocks只有一个（唯一的latch块）
    3. exitingBlocks只有一个（唯一的exiting块）
    4. exitBlocks只有一个（唯一的exit块）
    5. exitingBlock必须是header本身 **/
    public boolean isSimpleLoop(){
        // 检查循环头的前驱数量是否为2
        if(header.getPreBlocks().size() != 2){
            return false;
        }
        // 检查latch块数量是否为1
        if(latchBlocks.size() != 1){
            return false;
        }
        // 检查exiting块数量是否为1
        if(exitingBlocks.size() != 1){
            return false;
        }
        // exiting块必须是header本身
        for(BasicBlock exitingBlock : exitingBlocks){
            if(exitingBlock != header){
                return false;
            }
        }
        // exit块数量是否为1
        return exitBlocks.size() == 1;
    }

    /**
     * 判断当前循环是否已经展开（unrolled）。
     * 展开循环通常指将循环体复制多份以减少循环次数，提高性能。
     * 该方法通过以下条件判断：
     * 1. 循环头的前驱数量为2；
     * 2. latch块数量为1；
     * 3. 循环头指令中存在逻辑与（OP.And）操作，通常用于展开后的条件判断。
     * @return 如果满足上述条件且存在逻辑与操作，则认为已展开，返回true；否则返回false。
     */
    public boolean isUnrolled() {
        if (header.getPreBlocks().size() != 2) return false;
        if (latchBlocks.size() != 1) return false;
        BasicBlock latch = latchBlocks.get(0);
        int latchIdx = header.getPreBlocks().indexOf(latch);
        BasicBlock preHead = header.getPreBlocks().get(1 - latchIdx);
        for (IList.INode<Instruction, BasicBlock> instNode : header.getInsts()) {
            Instruction inst = instNode.getValue();
            if (inst instanceof BinaryInst binInst && binInst.getOp() == OP.And) return true;
        }
        return false;
    }

    public ArrayList<BasicBlock> getLatchBlocks(){
        return latchBlocks;
    }

    public LinkedHashSet<BasicBlock> getExitingBlocks(){
        return exitingBlocks;
    }

    /**
     * 设置归纳变量相关信息
     * @param itVar 归纳变量
     * @param itEnd 归纳变量终止值
     * @param itInit 归纳变量初始值
     * @param itAlu 归纳变量运算结果
     * @param itStep 归纳变量步长
     * @param headBrCond 循环头的分支条件
     */
    public void setIndInfo(Value itVar, Value itEnd, Value itInit, Value itAlu, Value itStep, BinaryInst headBrCond){
        this.itVar = itVar;
        this.itEnd = itEnd;
        this.itInit = itInit;
        this.itAlu = itAlu;
        this.itStep = itStep;
        this.headBrCond = headBrCond;
        this.setIndVar = true;
    }

    public boolean isSetIndVar(){
        return setIndVar;
    }

    public void removeBb(BasicBlock bb){
        bbs.remove(bb);
    }

    public Value getItVar() {
        return itVar;
    }

    public Value getItEnd() {
        return itEnd;
    }

    public Value getItInit() {
        return itInit;
    }

    public Value getItAlu() {
        return itAlu;
    }

    public Value getItStep() {
        return itStep;
    }

    public BinaryInst getHeadBrCond() {
        return headBrCond;
    }

    public void setItTimes(int times){
        this.times = times;
    }

    public int getItTimes(){
        return times;
    }
}
