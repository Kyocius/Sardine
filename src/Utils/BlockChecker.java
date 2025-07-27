package Utils;

import IR.IRModule;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.BrInst;
import Utils.DataStruct.IList;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;

public class BlockChecker {
    private BufferedWriter out;
    private boolean hasFault = false;
    private boolean isPrev = true;

    public void check(IRModule irModule, String check_name) throws IOException {
        out = new BufferedWriter(new FileWriter(check_name + ".txt"));
        hasFault = false;
        for (Function function : irModule.functions()) {
            isPrev = true;
            checkPrev(function);
            isPrev = false;
            checkSucc(function);
        }
        if (!hasFault) {
            out.write("Block Check Pass!\n");
        } else {
            out.write("have fault\n");
        }
        out.close();
    }

    private void checkPrev(Function function) throws IOException {
        BasicBlock prevBlock = null;
        for (IList.INode<BasicBlock, Function> basicBlockFunctionINode : function.getBbs()) {
            BasicBlock block = basicBlockFunctionINode.getValue();
            for (BasicBlock predBlock : block.getPreBlocks()) {
                if (predBlock == prevBlock) {
                    if (prevBlock != null && prevBlock.getLastInst() instanceof BrInst
                            && ((BrInst) prevBlock.getLastInst()).isJump()
                            && ((BrInst) prevBlock.getLastInst()).getJumpBlock() != block) {
                        printFalseInfo(block, predBlock);
                    }
                } else if (predBlock.getLastInst() instanceof BrInst br) {
                    if (br.isJump()) {
                        if (br.getJumpBlock() != block) {
                            printFalseInfo(block, predBlock);
                        }
                    } else {
                        if (br.getFalseBlock() != block && br.getTrueBlock() != block) {
                            printFalseInfo(block, predBlock);
                        }
                    }
                }
            }
            prevBlock = block;
        }
    }

    private void checkSucc(Function function) throws IOException {
        BasicBlock prevBlock;
        Iterator<IList.INode<BasicBlock, Function>> iterator = function.getBbs().iterator();
        if (iterator.hasNext()) {
            prevBlock = iterator.next().getValue();
        } else {
            return;
        }
        while (iterator.hasNext()) {
            BasicBlock block = iterator.next().getValue();
            for (BasicBlock succBlock : prevBlock.getNxtBlocks()) {
                if (succBlock == block) {
                    if (prevBlock.getLastInst() instanceof BrInst &&
                            ((BrInst) prevBlock.getLastInst()).isJump()
                            && ((BrInst) prevBlock.getLastInst()).getJumpBlock() != succBlock) {
                        printFalseInfo(prevBlock, block);
                    }
                } else {
                    if(prevBlock.getLastInst() instanceof BrInst br) {
                        if(br.isJump()) {
                            if(succBlock != br.getJumpBlock()) {
                                printFalseInfo(prevBlock, succBlock);
                            }
                        } else {
                            if(succBlock != br.getFalseBlock() && succBlock != br.getTrueBlock()) {
                                printFalseInfo(prevBlock, succBlock);
                            }
                        }
                    } else {
                        printFalseInfo(prevBlock, block);
                    }
                }
            }
            prevBlock = block;
        }
    }

    private void printFalseInfo(BasicBlock blockIn, BasicBlock toBlock) throws IOException {
        hasFault = true;
        if (isPrev) {
            out.write(blockIn.getName() + " has wrong prevBlock " + toBlock.getName() + "\n");
        } else {
            out.write(blockIn.getName() + " has wrong succBlock " + toBlock.getName() + "\n");
        }
    }
}
