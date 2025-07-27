package Pass.IR.Utils;

import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.CallInst;
import Utils.DataStruct.Pair;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class BranchPosibility {

    private static LinkedHashMap<Pair<BasicBlock, BasicBlock>, Double> probs;
    private static LinkedHashMap<Pair<BasicBlock, BasicBlock>, ArrayList<Double>> probsList;

    public static LinkedHashMap<Pair<BasicBlock, BasicBlock>, Double> run(IRModule module) {
        probs = new LinkedHashMap<>();
        probsList = new LinkedHashMap<>();
        for (var func : module.functions()) {
            if (func.isLibFunction()) {
                continue;
            }
            runForFunction(func);
        }
        return probs;
    }

    private static int checkCallNum(BasicBlock bb) {
        int callcnt = 0;
        for (var instnode : bb.getInsts()) {
            if (instnode.getValue() instanceof CallInst) {
                callcnt += 1;
            }
        }
        return callcnt;
    }

    private static boolean lessOneSucc(BasicBlock bb) {
        return bb.getNxtBlocks().size() <= 1;
    }

    private static double weightToProb1(int weight1, int weight2) {
        return (double) weight1 / ((double) weight1 + (double) weight2);
    }
    private static double weightToProb2(int weight1, int weight2) {
        return (double) weight2 / ((double) weight1 + (double) weight2);
    }

    private static void callPredictor(BasicBlock bb) {
        if (lessOneSucc(bb)) return;
        boolean firstIsOne = false;
        boolean secondIsOne = false;
        firstIsOne = (checkCallNum(bb.getNxtBlocks().get(0)) == 1);
        secondIsOne = (checkCallNum(bb.getNxtBlocks().get(1)) == 1);
        if(firstIsOne && !secondIsOne){
            probsList.get(new Pair<>(bb, bb.getNxtBlocks().get(0))).add(weightToProb1(10,1));
            probsList.get(new Pair<>(bb, bb.getNxtBlocks().get(1))).add(weightToProb2(10, 1));
        }else if(!firstIsOne && secondIsOne){
            probsList.get(new Pair<>(bb, bb.getNxtBlocks().get(1))).add(weightToProb1(10,1));
            probsList.get(new Pair<>(bb, bb.getNxtBlocks().get(0))).add(weightToProb2(10, 1));
        }
    }

    private static void setSuccProbs(ArrayList<Integer> weight, BasicBlock bb){
        ArrayList<Double> sumOneProbs = new ArrayList<>();
        double sumup = 0;
        for(var val : weight){
            sumup += val;
        }
        for(var val : weight){
            sumOneProbs.add(val/sumup);
        }
        int index = 0;
        for(var succ : bb.getNxtBlocks()){
            probsList.get(new Pair<>(bb, succ)).add(sumOneProbs.get(index));
            index+=1;
        }
    }

    private static boolean intoLoop(BasicBlock bb, BasicBlock nxt){
        var loop = bb.getLoop();
        var nxtloop = nxt.getLoop();
        if(loop != null && nxtloop != null){
            if(loop.getSubLoops().contains(nxtloop)){
                return true;
            }else{
                return false;
            }
        }else if(loop == null && nxtloop!= null){
            return true;
        }
        return false;
    }

    private static  boolean exitLoop(BasicBlock bb, BasicBlock nxt){
        var loop = bb.getLoop();
        var nxtloop = nxt.getLoop();
        if(nxtloop == null && loop != null){
            return true;
        }else if(nxtloop != null && loop != null){
            return nxtloop.getSubLoops().contains(loop);
        }else{
            return false;
        }
    }

    private static void loopPredictor(BasicBlock bb){
        if(lessOneSucc(bb))return;
        ArrayList<Integer> weightList = new ArrayList<>();
        for(int i= 0;i< 2;i++){
            var nxt = bb.getNxtBlocks().get(i);
            if(intoLoop(bb, nxt)){
                weightList.add(10);
            }else if(exitLoop(bb,nxt)){
                weightList.add(1);
            }else{
                weightList.add(5);
            }
        }
        setSuccProbs(weightList, bb);
    }

    private static void runForFunction(Function function) {
        LoopAnalysis.runLoopInfo(function);
        for (var bbnode : function.getBbs()) {
            for(var succ : bbnode.getValue().getNxtBlocks()){
                probsList.put(new Pair<>(bbnode.getValue(), succ), new ArrayList<>());
            }
        }
        for (var bbnode : function.getBbs()) {
            loopPredictor(bbnode.getValue());
            callPredictor(bbnode.getValue());
        }

        for (var bbnode : function.getBbs()) {
            if (bbnode.getValue().getNxtBlocks().isEmpty()) {
                continue;
            }
            if (bbnode.getValue().getNxtBlocks().size() == 1) {
                probs.put(new Pair<>(bbnode.getValue(), bbnode.getValue().getNxtBlocks().get(0)), 1.0);
                continue;
            }
            var bb = bbnode.getValue();
            LinkedHashMap<Pair<BasicBlock, BasicBlock>, Double> unifiedProbability = new LinkedHashMap<>();
            double sumup = 0.0001;
            for(var succ : bb.getNxtBlocks()) {
                double curprob = 0.5;
                for(var predictprob : probsList.get(new Pair<>(bb, succ))){
                    curprob = (curprob * predictprob)/((curprob*predictprob) + (1-curprob)*(1-predictprob));
                }
                unifiedProbability.put(new Pair<>(bb, succ), curprob);
            }
            for(int i= 0;i<2;i++){
                var path = new Pair<>(bb, bb.getNxtBlocks().get(i));
                unifiedProbability.put(path,
                        unifiedProbability.get(path)/sumup);
            }
            for(int i= 0;i<2;i++){
                var path = new Pair<>(bb, bb.getNxtBlocks().get(i));
                probs.put(path, unifiedProbability.get(path));
            }
        }
    }
}