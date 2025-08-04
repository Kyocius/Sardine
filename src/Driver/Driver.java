package Driver;

//import Backend.Arm.tools.BackendPeepHole;
//import Backend.Arm.tools.RegisterAllocator;

import Frontend.AST;
import Frontend.Lexer;
import Frontend.Parser;
import Frontend.TokenList;
import IR.IRModule;
import IR.Visitor;
//import Pass.PassManager;
import Utils.BlockChecker;
import Utils.IRDump;
import Utils.LLVMIRDump;
import Utils.UseValueChecker;
//import Backend.Arm.ArmCodeGen;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Driver {
    public void run(String[] args) throws IOException {
        //  解析参数
        parseArgs(args);
        //  开始编译流程
        TokenList tokenList = Lexer.getInstance().scanTokens();
        AST compAST = new Parser(tokenList).parseAST();
        IRModule irModule = new Visitor().visitAST(compAST);

        BlockChecker blockChecker = new BlockChecker();
        UseValueChecker useValueChecker = new UseValueChecker();
        if (!Config.noDump) {
            IRDump.DumpModule(irModule,"_ori");
            blockChecker.check(irModule, "block_check_front");
            useValueChecker.check(irModule, "value_check_front");
        }

        if (Config.armBackend){
            try {
                var fileOut = new java.io.FileOutputStream(Config.outputFile);
                var printStream = new java.io.PrintStream(fileOut);
                Backend.ArmWriter armWriter = new Backend.ArmWriter(printStream);
                armWriter.printModule(irModule);
                printStream.close();
                fileOut.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void parseArgs(String[] args){
        for(int i = 0; i < args.length; i++){
            String arg = args[i];
            if(arg.equals("-debug")) {
                Config.outputReturn = true;
                Config.outputNoAlloc = true;
                Config.outputLLVM = true;
            }
            else if(arg.equals("-debug-llvm")){
                Config.outputReturn = true;
                Config.outputLLVM = true;
            }
            else if(arg.equals("-test-llvm")){
                Config.outputReturn = true;
                Config.outputLLVM = true;
            }
            else if(arg.equals("-O1")) {
                Config.isO1 = true;
            }
            else if(arg.equals("-S")) {
                // 输出汇编文件，这是默认行为，无需特殊处理
            }
            else if(arg.equals("-o")) {
                // 下一个参数是输出文件名
                if(i + 1 < args.length) {
                    Config.outputFile = args[i + 1];
                    i++; // 跳过下一个参数，因为已经处理了
                }
            }
            else if(arg.contains(".s") && !arg.contains(".sy")){
                Config.outputFile = arg;
            }
            else if(arg.contains(".sy")){
                Config.inputFile = arg;
            } else if (arg.equals("-arm")) {
                Config.armBackend = true;
                Config.riscvBackend = false;
            } else if (arg.equals("-riscv")) {
                Config.riscvBackend = true;
                Config.armBackend = false;
            }
        }
    }
}
