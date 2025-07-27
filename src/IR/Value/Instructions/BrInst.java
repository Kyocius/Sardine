package IR.Value.Instructions;

import IR.Type.IntegerType;
import IR.Type.VoidType;
import IR.Value.BasicBlock;
import IR.Value.Value;
import Utils.LLVMIRDump;

public class BrInst extends Instruction{

    private int type;
    private BasicBlock TrueBlock;
    private BasicBlock FalseBlock;
    private BasicBlock JumpBlock;

    public BrInst(BasicBlock jumpBb) {
        super("", VoidType.voidType, OP.Br);
        this.JumpBlock = jumpBb;
        this.type = 1;
    }

    public BrInst(Value value, BasicBlock trueBlock, BasicBlock falseBlock){
        super("", VoidType.voidType, OP.Br);
        addOperand(value);
        this.TrueBlock = trueBlock;
        this.FalseBlock = falseBlock;
        this.type = 2;
    }

    //  将br指令转换为jump指令
    public void turnToJump(BasicBlock jumpBlock){
        this.type = 1;
        this.JumpBlock = jumpBlock;
        this.TrueBlock = null;
        this.FalseBlock = null;
        this.removeUseFromOperands();
        this.operands.clear();
    }

    public void replaceBlock(BasicBlock oldBlock, BasicBlock newBlock) {
        if (TrueBlock == oldBlock) {
            TrueBlock = newBlock; this.type = 2;
        }
        else if (FalseBlock == oldBlock) {
            FalseBlock = newBlock; this.type = 2;
        }
        else if (JumpBlock == oldBlock) {
            JumpBlock = newBlock; this.type = 1;
            this.removeUseFromOperands();
            this.operands.clear();
        }
    }

    public Value getJudVal(){
        if (operands.isEmpty()) return null;
        return getOperand(0);
    }

    public BasicBlock getTrueBlock(){
        return TrueBlock;
    }

    public BasicBlock getFalseBlock(){
        return FalseBlock;
    }

    public BasicBlock getJumpBlock(){
        return JumpBlock;
    }

    public void setJumpBlock(BasicBlock jumpBlock){
        this.removeUseFromOperands();
        this.operands.clear();
        this.JumpBlock = jumpBlock;
        this.type = 1;
    }

    public void setTrueBlock(BasicBlock trueBlock){
        this.TrueBlock = trueBlock;
        this.type = 2;
    }

    public void setFalseBlock(BasicBlock falseBlock){
        this.FalseBlock = falseBlock;
        this.type = 2;
    }

    public boolean isJump(){
        return type == 1;
    }

    @Override
    public boolean hasName(){
        return false;
    }

    @Override
    public String getInstString(){
        StringBuilder sb = new StringBuilder();
        if(type == 2) {
            sb.append("br ");
            sb.append(getJudVal()).append(", ");
            sb.append("label %").append(getTrueBlock().getName()).append(", ");
            sb.append("label %").append(getFalseBlock().getName());
        }
        //  直接跳转
        else {
            sb.append("br label %");
            sb.append(getJumpBlock().getName());
        }
        return sb.toString();
    }

    @Override
    public String toLLVMString() {
        StringBuilder sb = new StringBuilder();
        sb.append("br ");
        if(type == 2) {
            assert getJudVal().getType().isIntegerTy();
            assert IntegerType.isI1((IntegerType) (getJudVal().getType()));
            sb.append(getJudVal().getType().toLLVMString() + " ").append(LLVMIRDump.getLLVMName(getJudVal().getName()))
                    .append(", label %").append(getTrueBlock().getName())
                    .append(", label %").append(getFalseBlock().getName());
        } else {
            sb.append("label %").append(getJumpBlock().getName());
        }
        return sb.toString();
    }
}
