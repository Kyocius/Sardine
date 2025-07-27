package Pass.IR;

import IR.IRModule;
import IR.Value.ConstInteger;
import IR.Value.Function;
import IR.Value.GlobalVar;
import IR.Value.Instructions.AllocInst;
import IR.Value.Instructions.LoadInst;

import IR.Value.Instructions.PtrInst;
import Pass.Pass;

public class ConstArrayFetch implements Pass.IRPass {

    @Override
    public String getName() {
        return "ConstArrayFetch";
    }

    @Override
    public void run(IRModule module) {
        for (Function function : module.functions()) {
            for (var bbnode : function.getBbs()) {
                var bb = bbnode.getValue();
                for (var instnode : bb.getInsts()) {
                    var inst = instnode.getValue();
                    if (inst instanceof LoadInst li) {
                        var pt = li.getPointer();
                        if (pt instanceof PtrInst ptadd) {
                            if (ptadd.getTarget() instanceof GlobalVar globalVar &&
                                    globalVar.isConst() && ptadd.getOffset() instanceof ConstInteger cint) {
                                li.replaceUsedWith(globalVar.getValues().get(cint.getValue()));
                            } else if (ptadd.getTarget() instanceof AllocInst alloc && alloc.isConst()
                                    && ptadd.getOffset() instanceof ConstInteger cint) {
                                li.replaceUsedWith(alloc.getInitValues().get(cint.getValue()));
                            }
                        }
                    }
                }
            }
        }
        var dce = new DCE();
        dce.run(module);
    }

}
