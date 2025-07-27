package Pass.IR;

import IR.IRModule;
import IR.Type.IntegerType;
import IR.Type.PointerType;
import IR.Type.Type;
import IR.Use;
import IR.Value.*;
import IR.Value.Instructions.AllocInst;
import IR.Value.Instructions.Instruction;
import IR.Value.Instructions.LoadInst;
import IR.Value.Instructions.StoreInst;
import Pass.Pass;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class GlobalValueLocalize implements Pass.IRPass {

    @Override
    public String getName() {
        return "GlobalValueLocalize";
    }

    private final LinkedHashSet<GlobalVar> deleteGVs = new LinkedHashSet<>();

    @Override
    public void run(IRModule module) {
        ArrayList<GlobalVar> globalVars = module.globalVars();
        for(GlobalVar globalVar : globalVars){
            if(!globalVar.isArray()){
                localize(globalVar);
            }
        }

        for(GlobalVar globalVar : deleteGVs){
            globalVars.remove(globalVar);
        }
    }

    private void localize(GlobalVar globalVar){
        boolean hasStore = false;
        ArrayList<Function> useFuncs = new ArrayList<>();
        LinkedHashSet<Function> useFuncSet = new LinkedHashSet<>();
        ArrayList<Instruction> useInsts = new ArrayList<>();
        Type type = ((PointerType) globalVar.getType()).getEleType();
        for(Use use : globalVar.getUseList()){
            User user = use.getUser();
            if(user instanceof Instruction useInst){
                useInsts.add(useInst);
                Function function = useInst.getParentbb().getParentFunc();
                if(!useFuncSet.contains(function)){
                    useFuncs.add(function);
                    useFuncSet.add(function);
                }
                if(useInst instanceof StoreInst){
                    hasStore = true;
                }
            }
        }

        // 没有被store的全局变量直接改为初始的常数
        if (!hasStore) {
            deleteGVs.add(globalVar);
            for(Instruction useInst : useInsts){
                if(useInst instanceof LoadInst) {
                    Value init = globalVar.getValue();
                    Value newValue = null;
                    if (type.isIntegerTy()) {
                        int initVal = ((ConstInteger) init).getValue();
                        newValue = new ConstInteger(initVal, IntegerType.I32);
                    }
                    else if (type.isFloatTy()) {
                        float initVal = ((ConstFloat) init).getValue();
                        newValue = new ConstFloat(initVal);
                    }

                    useInst.replaceUsedWith(newValue);
                    useInst.removeSelf();
                }
            }
            return;
        }

        //  只在main函数中被调用的改为局部变量
        if(useFuncs.size() == 1 && useFuncs.get(0).getName().equals("@main")){
            Function function = useFuncs.get(0);
            BasicBlock bb = function.getBbEntry();
            if(type.isIntegerTy()){
                int initVal = ((ConstInteger) globalVar.getValue()).getValue();
                AllocInst allocInst = new AllocInst(new PointerType(type));
                StoreInst storeInst = new StoreInst(new ConstInteger(initVal, IntegerType.I32), allocInst);
                bb.addInstToHead(storeInst);
                bb.addInstToHead(allocInst);
                globalVar.replaceUsedWith(allocInst);
            }
            else if(type.isFloatTy()){
                float initVal = ((ConstFloat) globalVar.getValue()).getValue();
                AllocInst allocInst = new AllocInst(new PointerType(type));
                StoreInst storeInst = new StoreInst(new ConstFloat(initVal), allocInst);
                bb.addInstToHead(storeInst);
                bb.addInstToHead(allocInst);
                globalVar.replaceUsedWith(allocInst);
            }
            deleteGVs.add(globalVar);
        }
    }
}
