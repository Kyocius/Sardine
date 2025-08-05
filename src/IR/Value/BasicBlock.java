package IR.Value;

import IR.Type.VoidType;
import IR.Value.Instructions.*;
import Pass.IR.Utils.IRLoop;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import static Pass.IR.Utils.UtilFunc.getPhiInBb;

public class BasicBlock extends Value{
    private ArrayList<BasicBlock> preBlocks;
    private ArrayList<BasicBlock> nxtBlocks;
    public final ArrayList<Value> Use=new ArrayList<>();
    private final IList<Instruction, BasicBlock> insts;
    public static int blockNum = 0;
    private final IList.INode<BasicBlock, Function> node;
    private ArrayList<BasicBlock> idoms;
    private BasicBlock idominator;
    private IRLoop loop;
    private int domLV;
    public LinkedHashSet<BasicBlock> accessible;
    public BasicBlock(){
        super("block" + ++blockNum, VoidType.voidType);
        this.insts = new IList<>(this);
        this.preBlocks = new ArrayList<>();
        this.nxtBlocks = new ArrayList<>();
        this.node = new IList.INode<>(this);
    }

    public BasicBlock(Function function){
        super("block" + ++blockNum, VoidType.voidType);
        this.insts = new IList<>(this);
        this.preBlocks = new ArrayList<>();
        this.nxtBlocks = new ArrayList<>();
        this.node = new IList.INode<>(this);
    }

    public IList.INode<BasicBlock, Function> getNode(){
        return node;
    }


    public BinaryInst getBrCond(){
        Instruction inst = getLastInst();
        if(!(inst instanceof BrInst brInst)){
            return null;
        }
        if(brInst.isJump()){
            return null;
        }
        return (BinaryInst) brInst.getJudVal();
    }

    public void setLoop(IRLoop loop){
        this.loop = loop;
    }

    public IRLoop getLoop(){
        return loop;
    }

    public void addInst(Instruction inst){
        inst.getNode().insertListEnd(insts);
    }

    public void addInstToHead(Instruction inst){
        inst.getNode().insertListHead(insts);
    }

    public IList<Instruction, BasicBlock> getInsts() {
        return insts;
    }

    public ArrayList<BasicBlock> getPreBlocks() {
        return preBlocks;
    }

    public ArrayList<BasicBlock> getNxtBlocks() {
        return nxtBlocks;
    }

    public void removeLastInst(){
        Instruction lastInst = getLastInst();
        lastInst.removeSelf();
    }

    private boolean arrayEq(ArrayList<BasicBlock> oldPreBlocks, ArrayList<BasicBlock> preBlocks){
        for(BasicBlock oldPreBlock : oldPreBlocks){
            if(!preBlocks.contains(oldPreBlock)){
                return false;
            }
        }
        for(BasicBlock preBlock : preBlocks){
            if(!oldPreBlocks.contains(preBlock)){
                return false;
            }
        }
        return true;
    }

    public void setPreBlocks(ArrayList<BasicBlock> preBlocks){
        if(!this.preBlocks.isEmpty() && arrayEq(this.preBlocks, preBlocks)){
            return;
        }
        if (!this.preBlocks.isEmpty()) {
            ArrayList<Phi> phis = getPhiInBb(this);
            for (Phi phi : phis) {
                phi.fixPreBlocks(this.preBlocks, preBlocks);
            }
        }
        this.preBlocks = preBlocks;
    }

    public void setNxtBlocks(ArrayList<BasicBlock> nxtBlocks){
        if(this.nxtBlocks.size() != 0 && arrayEq(this.nxtBlocks, nxtBlocks)){
            return;
        }
        this.nxtBlocks = nxtBlocks;
    }

    public void setPreBlock(BasicBlock bb){
        if(!preBlocks.contains(bb)) {
            preBlocks.add(bb);
        }
    }
    public void setNxtBlock(BasicBlock bb){
        if(!nxtBlocks.contains(bb)) {
            nxtBlocks.add(bb);
        }
    }
    public void removePreBlock(BasicBlock bb){
        preBlocks.remove(bb);
    }

    public void removeNxtBlock(BasicBlock bb){
        nxtBlocks.remove(bb);
    }

    public Instruction getFirstInst(){
        return insts.getHeadValue();
    }

    public Instruction getLastInst(){
        return insts.getTailValue();
    }

    public void setIdoms(ArrayList<BasicBlock> idoms){
        this.idoms = idoms;
    }

    public void setDomLV(int domLV){
        this.domLV = domLV;
    }

    public void setIdominator(BasicBlock idominator){
        this.idominator = idominator;
    }

    public BasicBlock getIdominator(){
        return idominator;
    }
    public ArrayList<BasicBlock> getIdoms(){
        return idoms;
    }
    public int getDomLV(){
        return domLV;
    }


    //  removeSelf用于彻底将该基本块删除，包括与其相关的phi也会处理
    public void removeSelf(){
        //  检查phi
        for(BasicBlock nxtBb : nxtBlocks) {
            ArrayList<Phi> phis = getPhiInBb(nxtBb);
            int idx = nxtBb.getPreBlocks().indexOf(this);
            for(Phi phi : phis){
                phi.removeOperand(idx);
            }
        }

        //  解除block中user对别的块中value的使用
        for(IList.INode<Instruction, BasicBlock> instNode : insts){
            Instruction inst = instNode.getValue();
            inst.removeUseFromOperands();
        }

        //  删除前驱后继关系
        for(BasicBlock preBb : preBlocks){
            preBb.getNxtBlocks().remove(this);
        }
        for(BasicBlock nxtBb : nxtBlocks){
            nxtBb.getPreBlocks().remove(this);
        }
        node.removeFromList();
    }

    public void removeInstsAndSelf(){
        for(IList.INode<Instruction, BasicBlock> instNode : insts){
            Instruction inst = instNode.getValue();
            inst.removeUseFromOperands();
        }
        node.removeFromList();
    }

    public void turnBrBlock(BasicBlock oldBlock, BasicBlock newBlock){
        if(insts.getTailValue() instanceof BrInst brInst){
            if(brInst.isJump()){
                brInst.setJumpBlock(newBlock);
            }
            else {
                if(brInst.getTrueBlock().equals(oldBlock)){
                    brInst.setTrueBlock(newBlock);
                }
                if(brInst.getFalseBlock().equals(oldBlock)){
                    brInst.setFalseBlock(newBlock);
                }
            }
        }
    }

    public Function getParentFunc() {
        return node.getParent().getValue();
    }

    public int depth=0;

    public int getLoopDepth() {
        try {
            return getParentFunc().getLoopDepth(this);
        }catch (NullPointerException n )
        {
            return depth;//removephi中新增的bb，手动添加loop
        }
    }



    @Override
    public String toString(){
        return this.getName();
    }

    public void insertBefore(BasicBlock bb){
        node.insertBefore(bb.getNode());
    }

    public void insertAfter(BasicBlock bb){
        node.insertAfter(bb.getNode());
    }
}
