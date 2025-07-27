package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.Instruction;
import IR.Value.Instructions.Phi;
import IR.Value.User;
import IR.Value.Value;
import Pass.IR.Utils.DomAnalysis;
import Pass.IR.Utils.IRLoop;
import Pass.IR.Utils.LoopAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class LCSSA implements Pass.IRPass {

    private final IRBuildFactory f = IRBuildFactory.getInstance();

    @Override
    public void run(IRModule module) {
        for(Function function : module.functions()){
            if (function.isLibFunction()) continue;
            runLCSSAForFunc(function);
        }
    }

    private void runLCSSAForFunc(Function function){
        LoopAnalysis.runLoopInfo(function);
        DomAnalysis.run(function);
        for(IRLoop loop : function.getTopLoops()){
            addPhiForLoop(loop);
        }
    }

    private void addPhiForLoop(IRLoop loop){
        for (IRLoop subloop : loop.getSubLoops()) {
            addPhiForLoop(subloop);
        }

        LinkedHashSet<BasicBlock> exitBlocks = loop.getExitBlocks();
        if (exitBlocks.isEmpty()) {
            return;
        }

        for(BasicBlock bb : loop.getBbs()){
            for(IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()){
                Instruction inst = instNode.getValue();
                if (isUsedOutLoop(inst, loop)) {
                    addPhiAtExitBbs(inst, loop);
                }
            }
        }
    }

    //  在每次add一个phi的时候，用map存储
    LinkedHashMap<BasicBlock, Phi> exitPhiMap = new LinkedHashMap<>();
    private void addPhiAtExitBbs(Instruction inst, IRLoop loop){
        exitPhiMap.clear();
        BasicBlock instBb = inst.getParentbb();

        HashSet<Phi> newPhis = new HashSet<>();
        for(BasicBlock exitBb : loop.getExitBlocks()) {
            if (exitBb.getName().equals("block13")) {
                System.out.println(1);
            }
            LinkedHashSet<BasicBlock> domers = exitBb.getParentFunc().getDomer().get(exitBb);
            //  一个变量必须支配它的所有uses所在的基本块（user是phi时找对应前驱基本块）
            if(!exitPhiMap.containsKey(exitBb) && domers.contains(instBb)) {
                ArrayList<Value> phiValues = new ArrayList<>();
                for (int i = 0; i < exitBb.getPreBlocks().size(); i++) {
                    phiValues.add(inst);
                }
                Phi phi = f.getPhi(inst.getType(), phiValues);
                if (phi.getName().equals("%151")) {
                    System.out.println(1);
                }
                exitBb.addInstToHead(phi);
                exitPhiMap.put(exitBb, phi);
                newPhis.add(phi);
            }
        }

        //  Rename
        ArrayList<User> userList = new ArrayList<>(inst.getUserList());
        for(User user : userList){
            Instruction userInst = (Instruction) user;
            BasicBlock userBlock = getUserBlock(inst, userInst);
            if(userBlock == instBb || loop.getBbs().contains(userBlock) || newPhis.contains(user)){
                continue;
            }

            Phi phi = getValueInBlock(userBlock, loop);
            userInst.replaceOperand(inst, phi);
        }
    }

    //  由于每个exitBlock都要对应一个phi value
    //  所以一个循环外的userBlock需要该函数确定把目标inst替换成哪个phi value
    //  （草 这么简单的事情我居然想了一下午）
    private Phi getValueInBlock(BasicBlock userBlock, IRLoop loop){
        //  1. userBlock是exitBlock，直接替换
        if (exitPhiMap.get(userBlock) != null) {
            return exitPhiMap.get(userBlock);
        }
        //  2. 循环外不是exitBlock的，有两种情况
        //     要么该block被一个exitBlock支配，这种情况递归找到对应的exitBlock就可以
        //     要么该block不被任何一个exitBlock支配，此时我们需要新建phi指令
        //  我们通过查看该block的直接支配者是否在loop中来判断是哪种情况
        //  如果block的idominator在loop中，显然是后者(因为exitBlock不在循环中)
        //  反之我们不能确定，接着递归往上找就可以了
        BasicBlock idom = userBlock.getIdominator();
        if (!loop.getBbs().contains(idom)) {
            Phi value = getValueInBlock(idom, loop);
            exitPhiMap.put(userBlock, value);
            return value;
        }

        ArrayList<Value> values = new ArrayList<>();
        for (int i = 0; i < userBlock.getPreBlocks().size(); i++) {
            values.add(getValueInBlock(userBlock.getPreBlocks().get(i), loop));
        }
        Phi phi = f.getPhi(values.get(0).getType(), values);
        userBlock.addInstToHead(phi);
        exitPhiMap.put(userBlock, phi);

        return phi;
    }

    //  判断某指令inst是否在某loop外被使用
    private boolean isUsedOutLoop(Instruction inst, IRLoop loop){
        for(User user : inst.getUserList()){
            Instruction userInst = (Instruction) user;
            BasicBlock userBb = getUserBlock(inst, userInst);
            if(!loop.getBbs().contains(userBb)){
                return true;
            }
        }
        return false;
    }

    public BasicBlock getUserBlock(Instruction inst, Instruction userInst){
        BasicBlock userBb = userInst.getParentbb();
        if(userInst instanceof Phi phi){
            int idx = phi.getUseValues().indexOf(inst);
            userBb = phi.getParentbb().getPreBlocks().get(idx);
        }
        return userBb;
    }

    @Override
    public String getName() {
        return "LCSSA";
    }
}
