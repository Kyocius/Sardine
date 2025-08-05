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
    // 检查过程中是否发现错误
    private boolean hasFault = false;
    // 标记当前检查的是前驱关系还是后继关系
    private boolean isPrev = true;

    /**
     * 检查 IRModule 中所有函数的基本块前驱和后继关系，并将结果写入文件。
     * @param irModule 需要检查的 IR 模块
     * @param check_name 检查结果文件名（不带扩展名）
     * @throws IOException 文件写入异常
     */
    public void check(IRModule irModule, String check_name) throws IOException {
        // 创建输出文件
        out = new BufferedWriter(new FileWriter(check_name + ".txt"));
        hasFault = false;
        // 遍历所有函数，分别检查前驱和后继关系
        for (Function function : irModule.functions()) {
            isPrev = true;      // 标记当前为前驱检查
            checkPrev(function);
            isPrev = false;     // 标记当前为后继检查
            checkSucc(function);
        }
        // 根据检查结果写入文件
        if (!hasFault) {
            out.write("Block Check Pass!\n");
        } else {
            out.write("have fault\n");
        }
        out.close(); // 关闭文件
    }

    /**
     * 检查函数的每个基本块的前驱关系是否正确。
     * 对每个基本块，遍历其所有前驱块，判断跳转指令的目标是否为当前块。
     * 如果发现前驱块的跳转目标不是当前块，则记录错误信息。
     *
     * @param function 需要检查的函数
     * @throws IOException 文件写入异常
     */
    private void checkPrev(Function function) throws IOException {
        BasicBlock prevBlock = null;
        // 遍历函数的所有基本块
        for (IList.INode<BasicBlock, Function> basicBlockFunctionINode : function.getBbs()) {
            BasicBlock block = basicBlockFunctionINode.getValue();
            // 检查每个前驱块
            for (BasicBlock predBlock : block.getPreBlocks()) {
                // 如果前驱块是上一个块，检查跳转指令的目标
                if (predBlock == prevBlock) {
                    if (prevBlock != null && prevBlock.getLastInst() instanceof BrInst
                            && ((BrInst) prevBlock.getLastInst()).isJump()
                            && ((BrInst) prevBlock.getLastInst()).getJumpBlock() != block) {
                        printFalseInfo(block, predBlock);
                    }
                // 如果前驱块的最后一条指令是跳转指令，检查跳转目标
                } else if (predBlock.getLastInst() instanceof BrInst br) {
                    if (br.isJump()) {
                        if (br.getJumpBlock() != block) {
                            printFalseInfo(block, predBlock);
                        }
                    } else {
                        // 检查真假分支目标是否包含当前块
                        if (br.getFalseBlock() != block && br.getTrueBlock() != block) {
                            printFalseInfo(block, predBlock);
                        }
                    }
                }
            }
            prevBlock = block;
        }
    }

    /**
     * 检查函数的每个基本块的后继关系是否正确。
     * 对每个基本块，遍历其所有后继块，判断跳转指令的目标是否为后继块。
     * 如果发现后继块的跳转目标不是预期块，则记录错误信息。
     *
     * @param function 需要检查的函数
     * @throws IOException 文件写入异常
     */
    private void checkSucc(Function function) throws IOException {
        BasicBlock prevBlock;
        Iterator<IList.INode<BasicBlock, Function>> iterator = function.getBbs().iterator();
        // 获取第一个基本块
        if (iterator.hasNext()) {
            prevBlock = iterator.next().getValue();
        } else {
            return;
        }
        // 遍历后续基本块
        while (iterator.hasNext()) {
            BasicBlock block = iterator.next().getValue();
            // 检查前一个块的所有后继块
            for (BasicBlock succBlock : prevBlock.getNxtBlocks()) {
                if (succBlock == block) {
                    // 如果后继块是当前块，检查跳转指令的目标
                    if (prevBlock.getLastInst() instanceof BrInst &&
                            ((BrInst) prevBlock.getLastInst()).isJump()
                            && ((BrInst) prevBlock.getLastInst()).getJumpBlock() != succBlock) {
                        printFalseInfo(prevBlock, block);
                    }
                } else {
                    // 检查分支跳转指令的真假分支目标
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
                        // 如果不是跳转指令，直接记录错误
                        printFalseInfo(prevBlock, block);
                    }
                }
            }
            prevBlock = block;
        }
    }

    /**
     * 记录基本块前驱或后继关系错误信息到输出文件。
     * @param blockIn 当前检查的基本块
     * @param toBlock 错误的前驱或后继基本块
     * @throws IOException 文件写入异常
     */
    private void printFalseInfo(BasicBlock blockIn, BasicBlock toBlock) throws IOException {
        hasFault = true; // 标记发现错误
        if (isPrev) {
            // 前驱关系错误
            out.write(blockIn.getName() + " has wrong prevBlock " + toBlock.getName() + "\n");
        } else {
            // 后继关系错误
            out.write(blockIn.getName() + " has wrong succBlock " + toBlock.getName() + "\n");
        }
    }
}
