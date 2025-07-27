package Pass.IR;

import Driver.Config;
import IR.IRBuildFactory;
import IR.IRModule;
import IR.Value.*;
import IR.Value.Instructions.*;
import Pass.IR.Utils.*;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

//  Loop Invariant Code Motion
public class LICM implements Pass.IRPass {

    IRBuildFactory f = IRBuildFactory.getInstance();
    LoadStoreDataflowAnalysis lsAnalysis;

    //  1. load: TODO
    //  2. call: TODO
    //  3. store: TODO
    @Override
    public void run(IRModule module) {
        lsAnalysis = new LoadStoreDataflowAnalysis();
        lsAnalysis.runAnalysis(module, false);
        InterproceduralAnalysis.run(module);
        for (Function function : module.functions()) {
            if (function.isLibFunction()) continue;
            LoopAnalysis.runLoopInfo(function);
            ArrayList<IRLoop> dfsOrderLoops = new ArrayList<>();
            for(IRLoop loop : function.getTopLoops()){
                dfsOrderLoops.addAll(getDFSLoops(loop));
            }

            for(IRLoop loop : dfsOrderLoops){
                LICMForLoop(loop);
            }
        }
    }

    private boolean canBeAnalysisByLSAnalysis(ArrayList<LoadInst> allloads){
        for(var load : allloads){
            if(!lsAnalysis.loadConflict.containsKey(load)){
                return false;
            }
        }
        return true;
    }

    private void LICMForLoop(IRLoop loop) {
        //  1. LoadInst: 没有可能涉及该指针的Store
        ArrayList<StoreInst> storeInsts = new ArrayList<>();
        ArrayList<LoadInst> loadInsts = new ArrayList<>();
        HashSet<Value> allInstSet = new HashSet<>();
        HashSet<CallInst> callInsts = new HashSet<>();
        boolean sideFunc = false;
        for (BasicBlock bb : loop.getBbs()) {
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction inst = instNode.getValue();
                allInstSet.add(inst);
                if (inst instanceof LoadInst loadInst) loadInsts.add(loadInst);
                else if (inst instanceof StoreInst storeInst) storeInsts.add(storeInst);
                else if (inst instanceof CallInst callInst) {
                    Function function = callInst.getFunction();
                    if (function.isLibFunction()) continue;
                    else if (function.isStoreGV() || function.isStoreArg()) sideFunc = true;
                    callInsts.add(callInst);
                }
            }
        }


        BasicBlock head = loop.getHead();
        if (head.getIdominator() == null) return ;
        BasicBlock preHead = head.getIdominator();
        BrInst preHeadBrInst = (BrInst) preHead.getLastInst();

        if(Config.enhancedLICM && canBeAnalysisByLSAnalysis(loadInsts)){
            for(var load : loadInsts){
                if(allInstSet.contains(load.getPointer()))continue;
                boolean canelect = true;
                for(var store : lsAnalysis.loadConflict.get(load)){
                    if(storeInsts.contains(store)){
                        canelect = false;
                        break;
                    }
                }
                var conflictCallOpes = lsAnalysis.loadCallopeConflict.get(load);
                for(var call : callInsts){
                    for(var ope : call.getOperands()){
                        if(conflictCallOpes.contains(ope)){
                            canelect = false;
                            break;
                        }
                    }
                    if(!canelect)break;
                }
                if(!canelect)continue;
                load.removeFromBb();
                load.insertBefore(preHeadBrInst);
            }
        }else{
            //if (loop.getLatchBlocks().size() != 1) return ;
            //BasicBlock latch = loop.getLatchBlocks().get(0);
            //int latchIdx = head.getPreBlocks().indexOf(latch);
            //int preHeadIdx = 1 - latchIdx;
//
            //if (sideFunc) return ;
//
            //ArrayList<LoadInst> mayInvLoadInst = new ArrayList<>();
            //for (LoadInst loadInst : loadInsts) {
            //    Value ptr = loadInst.getPointer();
            //    if (!allInstSet.contains(ptr)) mayInvLoadInst.add(loadInst);
            //}
//
            ////  先写个简单的应付一下
            //boolean hasArg = false;
            //HashSet<Value> mayUsedPtr = new HashSet<>();
            //for (StoreInst storeInst : storeInsts) {
            //    Value ptr = storeInst.getPointer();
            //    Value root = AliasAnalysis.getRoot(ptr);
            //    if (root instanceof GlobalVar gv) mayUsedPtr.add(gv);
            //    else if (root instanceof AllocInst alloc) mayUsedPtr.add(alloc);
            //    else if (root instanceof Argument) hasArg = true;
            //}
            //if (hasArg) return ;
            //for (LoadInst loadInst : mayInvLoadInst) {
            //    Value ptr = loadInst.getPointer();
            //    Value root = AliasAnalysis.getRoot(ptr);
            //    if (!mayUsedPtr.contains(root)) {
            //        loadInst.removeFromBb();
            //        loadInst.insertBefore(preHeadBrInst);
            //    }
            //}
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

    @Override
    public String getName() {
        return "LICM";
    }
}
