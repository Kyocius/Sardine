package Pass.IR;

import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.Instruction;
import IR.Value.Instructions.Phi;
import IR.Value.Value;
import Pass.Pass;
import Utils.DataStruct.IList;

public class RemoveUselessPhi implements Pass.IRPass {

    @Override
    public void run(IRModule module){
        for(Function function : module.functions()) {
            boolean change = true;
            while(change) {
                change = false;
                for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                    BasicBlock bb = bbNode.getValue();
                    IList.INode<Instruction, BasicBlock> itInstNode = bb.getInsts().getHead();
                    while (itInstNode != null) {
                        Instruction inst = itInstNode.getValue();
                        itInstNode = itInstNode.getNext();
                        if (inst instanceof Phi phi) {
                            //  第一种情况，每个useValue都一样(包含只有一个useValue)
                            Value commonVal = phi.getOperand(0);
                            boolean isAllEqual = true;
                            for (Value value : phi.getUseValues()) {
                                 if (!commonVal.equals(value)) {
                                    isAllEqual = false;
                                    break;
                                }
                            }
                            if (isAllEqual) {
                                phi.replaceUsedWith(commonVal);
                                phi.removeSelf();
                                change = true;
                                continue;
                            }
                            //  第二种情况：该phi中的value是本身
                            int cnt = 0; Value init = null;
                            for (Value value : phi.getUseValues()) {
                                if (value != phi) {
                                    cnt++; init = value;
                                }
                            }
                            if (cnt == 1 && init != null) {
                                phi.replaceUsedWith(init);
                                phi.removeSelf();
                                change = true;
                                continue;
                            }

                            //  第三种情况：该phi和下个phi一模一样
                            if (itInstNode != null && itInstNode.getValue() instanceof Phi nxtPhi) {
                                if (phi.getUseValues().size() != nxtPhi.getUseValues().size()) {
                                    continue;
                                }
                                boolean isEqualToNxt = true;
                                for (int i = 0; i < phi.getOperands().size(); i++) {
                                    if (!phi.getOperand(i).equals(nxtPhi.getOperand(i))) {
                                        isEqualToNxt = false;
                                        break;
                                    }
                                }
                                if (isEqualToNxt) {
                                    phi.replaceUsedWith(nxtPhi);
                                    phi.removeSelf();
                                    change = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    @Override
    public String getName() {
        return "RemoveUselessPhi";
    }
}
