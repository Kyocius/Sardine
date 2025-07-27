package Pass.IR;

import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import Pass.IR.Utils.BranchPosibility;
import Pass.IR.Utils.DomAnalysis;
import Pass.IR.Utils.LoopAnalysis;
import Pass.IR.Utils.UtilFunc;
import Pass.Pass;
import Utils.DataStruct.Pair;

import java.util.*;

//根据Loop分析，让第一个body块和head相邻
public class BlockReorder implements Pass.IRPass {
    public static int reorderCNT = 0;
    @Override
    public String getName() {
        return "BlockReorder";
    }

    private LinkedHashMap<Pair<BasicBlock, BasicBlock>, Double> probs;

    @Override
    public void run(IRModule module) {
        probs = BranchPosibility.run(module);
        for (Function function : module.functions()) {
            if (function.isLibFunction()) continue;
            reorderBlockForFunc(function);
        }
//        System.err.println("reorder block number:");
//        System.err.println(reorderCNT);
    }

    private void reorderBlockForFunc(Function function){
//        for(var loop : function.getAllLoops()){
//            var head = loop.getHead();
//            if(loop.getBbs().size()<=1){
//                continue;
//            }
//            BasicBlock bodyHead = null;
//            if(loop.getBbs().contains(head.getNxtBlocks().get(0))){
//                bodyHead = head.getNxtBlocks().get(0);
//            }else{
//                bodyHead = head.getNxtBlocks().get(1);
//            }
//            if(!(head.getNode().getNext() != null && head.getNode().getNext().equals(bodyHead.getNode()))){
//                BlockReorder.reorderCNT ++;
//                bodyHead.getNode().removeFromList();
//                bodyHead.insertAfter(head);
//            }
//        }
        DomAnalysis.run(function);
        LoopAnalysis.runLoopInfo(function);
        var entry = function.getBbEntry();
        LinkedHashSet<BasicBlock> allbb = new LinkedHashSet<>();
        for(var bbnode : function.getBbs()){
            allbb.add(bbnode.getValue());
        }
        var res = reorderDomTree(entry, function);
        for(var bb : allbb){
            bb.getNode().removeFromList();
        }
        for(var bb : res){
            function.getBbs().add(bb.getNode());
        }
        UtilFunc.makeCFG(function);
    }

    private ArrayList<BasicBlock> reorderDomTree(BasicBlock domer, Function function){
        var idoms = function.getIdoms();
        var res = new ArrayList<BasicBlock>();
        res.add(domer);
        ArrayList<BasicBlock> waitingList = new ArrayList<>();
        for(var succ : domer.getNxtBlocks()){
            if(!idoms.get(domer).contains(succ) || succ.equals(domer)){
                continue;
            }
            waitingList.add(succ);
        }

        Comparator<BasicBlock> compByProb= Comparator.comparingInt(bb -> {
            return (int) (probs.get(new Pair<>(domer, bb)) * 1000);
        });

        List<BasicBlock> waitListSorted = new ArrayList<>(waitingList).stream().sorted(compByProb).toList();
        for(var bb : waitListSorted){
            res.addAll(reorderDomTree(bb, function));
        }
//        ArrayList<BasicBlock> finaladdList = new ArrayList<>();
//        for(var succ : waitingList){
//            if((domer.getLoop() != null && domer.getLoop().getSubLoops().contains(succ.getLoop()))||
//                    (domer.getLoop() == null && succ.getLoop()!=null)){
//                res.addAll(reorderDomTree(succ, function));
//            }else{
//                finaladdList.add(succ);
//            }
//        }
//        for(var bb : finaladdList){
//            res.addAll(reorderDomTree(bb, function));
//        }
        for(var otherBeDomed : idoms.get(domer)){
            if(!domer.getNxtBlocks().contains(otherBeDomed)){
                res.addAll(reorderDomTree(otherBeDomed, function));
            }
        }
        return res;
    }
}
