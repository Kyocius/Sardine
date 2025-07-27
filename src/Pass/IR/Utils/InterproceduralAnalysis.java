package Pass.IR.Utils;

import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.GlobalVar;
import IR.Value.Instructions.*;
import IR.Value.Value;
import Utils.DataStruct.IList;

//  过程间分析
//  计算函数是否有副作用，以及函数是否使用全局变量
//  同时设置函数的一些属性，如canGVN等等
public class InterproceduralAnalysis{

    public static void run(IRModule module){
        /*  初始化默认所有函数无副作用（即在不考虑其返回值的情况下，是否对程序结果有影响）
        *   副作用主要包含两点：
        * 1. 向全局变量/参数指针写入数据
        * 2. 调用库函数/有副作用的函数
        *
        * */
        //  TODO: useGV暂时用不到，先注释了
        for(Function function : module.functions()){
            function.setMayHasSideEffect(false);
            function.setStoreGV(false);
            function.setStoreArg(false);
        }

        //  标记直接使用全局变量的函数
        for(Function function : module.functions()){
            for(IList.INode<BasicBlock, Function> bbNode : function.getBbs()){
                BasicBlock bb = bbNode.getValue();
                for(IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()){
                    Instruction inst = instNode.getValue();
                    if(inst instanceof StoreInst) {
                        Value pointer = ((StoreInst) inst).getPointer();
                        Value array = AliasAnalysis.getRoot(pointer);
                        if (!AliasAnalysis.isLocal(array)) {
                            function.setMayHasSideEffect(true);
                            if (array instanceof GlobalVar gv) function.setStoreGV(true);
                            else function.setStoreArg(true);
                        }
                    }
                    else if(inst instanceof CallInst){
                        if(((CallInst) inst).getFunction().isLibFunction()){
                            function.setMayHasSideEffect(true);
                        }
                    }
                }
            }
        }

        // 递归标记间接使用全局变量的函数
        for(Function function : module.functions()) {
            if(function.isMayHasSideEffect()) {
                markSideEffect(function);
            }
            if (function.isStoreGV()) {
                markStoreGV(function);
            }
            if (function.isStoreArg()) {
                markStoreArg(function);
            }
        }
    }

    private static void markSideEffect(Function function) {
        for (Function caller : function.getCallerList()) {
            if (!caller.isMayHasSideEffect()) {
                caller.setMayHasSideEffect(true);
                markSideEffect(caller);
            }
        }
    }

    private static void markStoreGV(Function function) {
        for (Function caller : function.getCallerList()) {
            if (!caller.isStoreGV()) {
                caller.setStoreGV(true);
                markStoreGV(caller);
            }
        }
    }

    private static void markStoreArg(Function function) {
        for (Function caller : function.getCallerList()) {
            if (!caller.isStoreArg()) {
                caller.setStoreArg(true);
                markStoreArg(caller);
            }
        }
    }


}

