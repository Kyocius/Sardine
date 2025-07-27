package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Type.PointerType;
import IR.Value.*;
import IR.Value.Instructions.*;
import Pass.IR.Utils.LoopAnalysis;
import Pass.Pass;
import Utils.DataStruct.IList;
import Utils.IRDump;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import static Pass.IR.Utils.UtilFunc.makeCFG;

public class LoopUnchangeElect implements Pass.IRPass {
    private boolean change = false;
    @Override
    public String getName() {
        return "LoopUnchangeElect";
    }

    @Override
    public void run(IRModule module) {
        for(Function function : module.functions()){
            if (function.isLibFunction()) continue;
            LoopAnalysis.runLoopInfo(function);
            LoopAnalysis.runLoopIndvarInfo(function);
            change = false;
            LoopConstElect(function);
//            LoopPtrElect(function);
        }
        var dce = new DCE();
        dce.run(module);
        var gcm = new GCM();
        gcm.run(module);
    }

    // 处理 loop中如下三值相加模式
    // loop{
    //     tmp1 = add loopoutValue1, loopinValue
    //     tmp2 = add tmp1, loopoutValue2
    // }
    // 改写为
    // loop{
    //     tmp1 = add loopoutValue1, loopinValue
    //     newtmp = add loopoutValue1, loopoutValue2
    //     tmp2 = add newtmp, loopinValue
    // } 使得tmp1循环无关，可以被GCM提出循环。此处理论上没有风险，只需要无脑变换tmp2、添加newtmp并保留tmp1。交给GVN和GCM完成后续操作。
    public void LoopConstElect(Function f){
        for(var loop : f.getAllLoops()){
            if(!loop.isSimpleLoop()){
                continue;
            }
            var visitList = new ArrayList<Instruction>();

            var headBB = loop.getHead();
            var allInsts = new LinkedHashSet<Value>();
            for(var instnode : headBB.getInsts()){
                allInsts.add(instnode.getValue());
                visitList.add(instnode.getValue());
            }

            var bodyBB = loop.getLatchBlocks().get(0);
            for(var instnode : bodyBB.getInsts()){
                var inst = instnode.getValue();
                allInsts.add(inst);
                visitList.add(inst);
            }

            for(var inst: visitList){
                Value loopOutValue=null;
                Value loopInValue=null;
                if(inst instanceof BinaryInst && inst.getOp() == OP.Add){
                    if(!allInsts.contains(inst.getOperand(0))){
                        loopOutValue = inst.getOperand(0);
                        loopInValue = inst.getOperand(1);
                    } else if (!allInsts.contains(inst.getOperand(1))) {
                        loopOutValue = inst.getOperand(1);
                        loopInValue = inst.getOperand(0);
                    }
                }
                if(loopInValue == null || loopOutValue == null){
                    continue;
                }
                // tmp2 = invalue + outvalue
                // 继续判断invalue
                Value loopOutvalue2 = null;
                Value loopInValue2 = null;
                if(loopInValue instanceof BinaryInst ininst && ininst.getOp() == OP.Add){
                    if(!allInsts.contains(ininst.getOperand(0))){
                        loopOutvalue2 = ininst.getOperand(0);
                        loopInValue2 = ininst.getOperand(1);
                    } else if(!allInsts.contains(ininst.getOperand(1))){
                        loopOutvalue2 = ininst.getOperand(1);
                        loopInValue2 = ininst.getOperand(0);
                    }
                }
                if(loopInValue2 == null || loopOutvalue2 == null){
                    continue;
                }
                // invalue = invalue2 + outvalue2
                // 进行变换
                var fac = IRBuildFactory.getInstance();
                var electadd = fac.buildBinaryInst(loopOutValue, loopOutvalue2, OP.Add,
                        ((BinaryInst) loopInValue).getParentbb());
                electadd.removeFromBb();
                electadd.insertAfter((Instruction) loopInValue);
                inst.replaceOperand(loopInValue, electadd);
                inst.replaceOperand(loopOutValue, loopInValue2);
                change = true;
            }
        }
    }

    public void LoopPtrElect(Function f){
        for(var loop : f.getAllLoops()){
            if(!loop.isSimpleLoop()){
                continue;
            }
            var visitList = new ArrayList<Instruction>();

            var headBB = loop.getHead();
            var allInsts = new LinkedHashSet<Value>();
            for(var instnode : headBB.getInsts()){
                allInsts.add(instnode.getValue());
                visitList.add(instnode.getValue());
            }

            var bodyBB = loop.getLatchBlocks().get(0);
            for(var instnode : bodyBB.getInsts()){
                var inst = instnode.getValue();
                allInsts.add(inst);
                visitList.add(inst);
            }

            boolean hasCall = false;
            for(var inst : allInsts){
                if(inst instanceof CallInst){
                    hasCall = true;
                    break;
                }
            }
            if(hasCall)continue;

            for(var inst: visitList){
                Value pointer;
                if(inst instanceof PtrInst&& inst.getOperand(0).getType() instanceof PointerType){
                    pointer = inst.getOperand(0);
                }else{
                    continue;
                }
                var builder = IRBuildFactory.getInstance();
                var newPtradd = builder.getPtrInst(pointer, builder.buildNumber(0));

                BasicBlock inputbb = null;
                for(var bb : headBB.getPreBlocks()){
                    if(bb != loop.getLatchBlocks().get(0)){
                        inputbb = bb;
                    }
                }
                assert inputbb != null;
                newPtradd.insertBefore(inputbb.getLastInst());

                inst.replaceOperand(pointer, newPtradd);
                change = true;
            }
        }
    }
}
