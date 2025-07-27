package Driver;

import Backend.Arm.tools.BackendPeepHole;
import Backend.Arm.tools.RegisterAllocator;
import Backend.Riscv.Optimization.BackPeepHole;
import Backend.Riscv.Optimization.ReOrdering;
import Backend.Riscv.Process.RegAllocator;
import Backend.Riscv.RiscvCodeGen;
import Frontend.AST;
import Frontend.Lexer;
import Frontend.Parser;
import Frontend.TokenList;
import IR.IRModule;
import IR.Visitor;
import Pass.PassManager;
import Utils.BlockChecker;
import Utils.IRDump;
import Utils.LLVMIRDump;
import Utils.UseValueChecker;
import Backend.Arm.ArmCodeGen;

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

        PassManager passManager = PassManager.getInstance();
        passManager.runIRPasses(irModule);
        if (!Config.noDump) {
            blockChecker.check(irModule, "block_check_pass");
            useValueChecker.check(irModule, "value_check_pass");
        }

        if (!Config.noDump) {
            IRDump.DumpModule(irModule, "_afterIr");
        }
        if (!Config.noDump && !Config.parallelOpen) {
            LLVMIRDump.LLVMDumpModule(irModule);
        }

        passManager.runMirPasses(irModule);
        if (!Config.noDump) IRDump.DumpModule(irModule,"_afterMir");

        if (Config.armBackend){
            ArmCodeGen armCodeGen = new ArmCodeGen(irModule);
            armCodeGen.run();
            if (!Config.noDump) armCodeGen.dump();

            BackendPeepHole backendPeepHole = new BackendPeepHole(armCodeGen.getArmModule());
            backendPeepHole.DataFlowRun();
            if (!Config.noDump) backendPeepHole.DataFlowDump();

            RegisterAllocator registerAllocator = new RegisterAllocator(armCodeGen.getArmModule());
            registerAllocator.run();
            if (!Config.noDump) registerAllocator.dump();

            backendPeepHole.run();
            try {
                var out = new BufferedWriter(new FileWriter(Config.outputFile));
                out.write(armCodeGen.getArmModule().toString());
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (Config.riscvBackend) {
            RiscvCodeGen riscvCodeGen = new RiscvCodeGen(irModule);
            riscvCodeGen.run();
            if (!Config.noDump) riscvCodeGen.dump();

            ReOrdering riscvReordering = new ReOrdering(riscvCodeGen.getRvModule());
            riscvReordering.run();
            if (!Config.noDump) riscvReordering.dump();

            BackPeepHole backPeepHole = new BackPeepHole(riscvCodeGen.getRvModule());
            backPeepHole.DataFlowRun();
            if (!Config.noDump) backPeepHole.DataFlowDump();

            RegAllocator regAllocator = new RegAllocator(riscvCodeGen.getRvModule());
            regAllocator.run();
            if (!Config.noDump) regAllocator.dump();

            backPeepHole.run();
            if (!Config.noDump) backPeepHole.dump();

            try {
                var out = new BufferedWriter(new FileWriter(Config.outputFile));
                out.write(riscvCodeGen.getRvModule().toString());
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void parseArgs(String[] args){
        for(String arg : args){
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
