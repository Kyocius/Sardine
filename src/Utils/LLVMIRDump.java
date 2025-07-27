package Utils;

import Driver.Config;
import IR.IRModule;
import IR.Type.FloatType;
import IR.Type.IntegerType;
import IR.Value.Argument;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.GlobalVar;
import IR.Value.Instructions.Instruction;
import Utils.DataStruct.IList;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class LLVMIRDump {
    private static BufferedWriter out;

    public static String getLLVMName(String name) {
        //出于规避llvm 纯数字寄存器所需排序，而增加的寄存器标号修改
        if(name.charAt(0) == '%') {
            if(name.endsWith("_param")) {
                return name;
            } else {
                StringBuilder sb = new StringBuilder(name);
                sb.insert(1, 'a');
                return sb.toString();
            }
        } else if(name.charAt(0) == '@'){
            return "%" + name.substring(1) + "_global";
        } else {
            return name;
        }
    }

    private static void LLVMDumpGlobalVar(GlobalVar globalVar) throws IOException {
        out.write(globalVar.toLLVMString());
        out.write("\n");
    }

    private static void LLVMDumpInstruction(Instruction inst) throws IOException {
        out.write(inst.toLLVMString() + "\n");
    }

    private static void LLVMDumpBasicBlock(BasicBlock bb, int i, ArrayList<String> bitcasts)
            throws IOException {
        String bbName = bb.getName();
        out.write(bbName + ":\n");
        if(i == 0) {
            for (String bitcast : bitcasts) {
                out.write("\t");
                out.write(bitcast);
                out.write("\n");
            }
        }
        IList<Instruction, BasicBlock> insts = bb.getInsts();
        for (IList.INode<Instruction, BasicBlock> instNode : insts) {
            out.write("\t");
            LLVMDumpInstruction(instNode.getValue());
        }
    }

    private static void LLVMDumpFunction(Function function,
                                         ArrayList<String> bitcasts) throws IOException {
        out.write("define dso_local ");
        if (function.getType() == IntegerType.I32) {
            out.write("i32 ");
        } else if (function.getType() == FloatType.F32) {
            out.write("float ");
        } else out.write("void ");
        out.write(function.getName() + "(");
        ArrayList<Argument> args = function.getArgs();
        for (int i = 0; i < args.size(); i++) {
            out.write(args.get(i).toLLVMString());
            if (i != args.size() - 1) out.write(", ");
        }
        out.write(") {\n");
        IList<BasicBlock, Function> basicBlocks = function.getBbs();
        int i = 0;
        for (IList.INode<BasicBlock, Function> bbNode : basicBlocks) {
            LLVMDumpBasicBlock(bbNode.getValue(), i, bitcasts);
            i++;
        }
        out.write("}\n");
    }

    public static void LLVMDumpModule(IRModule irModule) throws IOException {
        out = new BufferedWriter(new FileWriter(Config.llvmIrOutFile));
        ArrayList<GlobalVar> globalVars = irModule.globalVars();
        ArrayList<String> bitcasts = new ArrayList<>();
        for (GlobalVar globalVar : globalVars) {
            LLVMDumpGlobalVar(globalVar);
            bitcasts.add(globalVar.getBitcast());
        }

        out.write("declare i32 @getint()\n" +
                "declare i32 @getch()\n" +
                "declare float @getfloat()\n" +
                "declare i32 @getarray(i32*)\n" +
                "declare i32 @getfarray(float*)\n" +
                "declare void @putint(i32)\n" +
                "declare void @putch(i32)\n" +
                "declare void @putfloat(float)\n" +
                "declare void @putarray(i32, i32*)\n" +
                "declare void @putfarray(i32, float*)\n" +
                "declare void @putf(i8*, ...)\n" +
                "declare void @_sysy_starttime(i32)\n" +
                "declare void @_sysy_stoptime(i32)\n" +
                "declare void @memset(i32*, i32, i32)\n\n");

        ArrayList<Function> functions = irModule.functions();
        for (Function function : functions) {
            if(!function.isLibFunction()) {
                LLVMDumpFunction(function, bitcasts);
                out.write("\n");
            }
        }
        out.close();
    }
}
