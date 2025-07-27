package Pass.IR.Utils;

import IR.IRModule;
import IR.Value.*;
import IR.Value.Instructions.LoadInst;
import IR.Value.Instructions.PtrInst;
import Utils.DataStruct.IList;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class AccessAnalysis {

    public static void run(IRModule module) {
        module.functions().forEach(DomAnalysis::run);
        module.functions().forEach(f -> {
            f.getBbs().forEach(bbnode -> {
                var bb = bbnode.getValue();
                bb.accessible = new LinkedHashSet<>();
            });
        });

        module.functions().forEach(f -> {
            f.getBbs().forEach(bbnode -> {
                var bb = bbnode.getValue();
                for(var domer : f.getDomer().get(bb)){
                    domer.accessible.add(bb);
                }
            });
        });
        module.functions().forEach(f -> {
            f.getBbs().forEach(bbnode -> {
                var bb = bbnode.getValue();
                bb.accessible.remove(bb);
                bb.accessible.addAll(bb.getNxtBlocks());
            });
        });
        module.functions().forEach(f -> {
            if(f.isLibFunction()){
                return;
            }
            var changed = true;

            LinkedHashMap<BasicBlock, LinkedHashSet<BasicBlock>> added = new LinkedHashMap<>();
            f.getBbs().forEach(bbnode -> added.put(bbnode.getValue(),
                    new LinkedHashSet<>() {{
                        add(bbnode.getValue());
                    }}));

            while (changed) {
                changed = false;
                for (IList.INode<BasicBlock, Function> node : f.getBbs()) {
                    var block = node.getValue();
                    LinkedHashSet<BasicBlock> tmp = new LinkedHashSet<>(block.accessible);
                    for (BasicBlock accblock : tmp) {
                        if (!added.get(block).contains(accblock)) {
                            block.accessible.addAll(accblock.accessible);
                            added.get(block).add(accblock);
                        }
                    }
                    if (!(block.accessible.size() == tmp.size())) {
                        changed = true;
                    }
                }
            }

        });
    }
}