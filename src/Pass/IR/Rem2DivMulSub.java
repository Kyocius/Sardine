package Pass.IR;

import IR.IRBuildFactory;
import IR.IRModule;
import IR.Type.IntegerType;
import IR.Value.BasicBlock;
import IR.Value.ConstInteger;
import IR.Value.Function;
import IR.Value.Instructions.BinaryInst;
import IR.Value.Instructions.Instruction;
import IR.Value.Instructions.OP;
import IR.Value.Value;
import Pass.Pass;
import Utils.DataStruct.IList;

import java.util.LinkedHashSet;

public class Rem2DivMulSub implements Pass.IRPass {

    private final IRBuildFactory f = IRBuildFactory.getInstance();

    @Override
    public void run(IRModule module) {
        LinkedHashSet<BinaryInst> rems = new LinkedHashSet<>();
        LinkedHashSet<BinaryInst> frems = new LinkedHashSet<>();

        for (Function function : module.functions()) {
            for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
                BasicBlock bb = bbNode.getValue();
                for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                    Instruction inst = instNode.getValue();
                    if (inst instanceof BinaryInst binaryInst) {
                        OP op = binaryInst.getOp();
                        if(op.equals(OP.Mod) && !binaryInst.I64){
                            rems.add(binaryInst);
                        }
                        else if(op.equals(OP.Fmod)){
                            frems.add(binaryInst);
                        }
                    }
                }
            }
        }

        for (BinaryInst rem : rems) {
            Value A = rem.getLeftVal();
            Value B = rem.getRightVal();
            if (B instanceof ConstInteger) {
                int val = ((ConstInteger) B).getValue();
                if (val > 0 && ((int) Math.pow(2, ((int) (Math.log(val) / Math.log(2))))) == val) {
                    continue;
                }
            }

            //  0 % a = 0; a % a = 0; a % 1 = 0;
            if((A instanceof ConstInteger constIntA && constIntA.getValue() == 0)
                    || A.equals(B)
                    || (B instanceof ConstInteger constIntB
                    && constIntB.getValue() == 1)){
                rem.replaceUsedWith(f.buildNumber(0));
                rem.removeSelf();
                return;
            }

            BinaryInst div = f.getBinaryInst(A, B, OP.Div, IntegerType.I32);
            BinaryInst mul = f.getBinaryInst(div, B, OP.Mul, IntegerType.I32);
            BinaryInst sub = f.getBinaryInst(A, mul, OP.Sub, IntegerType.I32);

            div.insertAfter(rem);
            mul.insertAfter(div);
            sub.insertAfter(mul);

            rem.replaceUsedWith(sub);
            rem.removeSelf();
        }

        for (BinaryInst frem: frems) {
            Value A = frem.getLeftVal();
            Value B = frem.getRightVal();
            BinaryInst fdiv = f.getBinaryInst(A, B, OP.Fdiv, IntegerType.I32);
            BinaryInst fmul = f.getBinaryInst(fdiv, B, OP.Fmul, IntegerType.I32);
            BinaryInst fsub = f.getBinaryInst(A, fmul, OP.Fsub, IntegerType.I32);

            fdiv.insertAfter(frem);
            fmul.insertAfter(fdiv);
            fsub.insertAfter(fmul);

            frem.replaceUsedWith(fsub);
            frem.removeSelf();
        }
    }

    @Override
    public String getName() {
        return null;
    }
}
