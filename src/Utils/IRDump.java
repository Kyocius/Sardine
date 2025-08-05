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
import java.io.StringWriter;
import java.util.ArrayList;

public class IRDump {
    private static BufferedWriter out;

    private static void DumpGlobalVar(GlobalVar globalVar) throws IOException {
        out.write(globalVar.toString());
        out.write("\n");
    }

    private static void DumpInstruction(Instruction inst) throws IOException {
        out.write(inst.getInstString() + "\n");
    }

    private static void DumpBasicBlock(BasicBlock bb) throws IOException {
        String bbName = bb.getName();
        out.write(bbName + ":\n");
        IList<Instruction, BasicBlock> insts = bb.getInsts();
        for (IList.INode<Instruction, BasicBlock> instNode : insts) {
            out.write("\t");
            DumpInstruction(instNode.getValue());
        }
    }

    private static void DumpFunction(Function function) throws IOException {
        if (function.getType() == IntegerType.I32) {
            out.write("int ");
        } else if (function.getType() == FloatType.F32) {
            out.write("float ");
        } else out.write("void ");

        out.write(function.getName() + "(");

        ArrayList<Argument> args = function.getArgs();
        for (int i = 0; i < args.size(); i++) {
            out.write(args.get(i).toString());
            if (i != args.size() - 1) out.write(", ");
        }

        if (!function.isLibFunction()) {
            out.write(") {\n");
            IList<BasicBlock, Function> basicBlocks = function.getBbs();

            for (IList.INode<BasicBlock, Function> bbNode : basicBlocks) {
                DumpBasicBlock(bbNode.getValue());
            }
            out.write("}\n");
        } else {
            out.write(")");
        }

    }

    public static void DumpModule(IRModule irModule) throws IOException {
        DumpModule(irModule, "");
    }

    /**
     * 将 IRModule 的内容导出到指定后缀的文本文件中。
     * @param irModule 要导出的 IRModule 实例
     * @param suffix 文件名后缀
     * @throws IOException 文件写入异常
     */
    public static void DumpModule(IRModule irModule, String suffix) throws IOException {
        // 创建输出文件的 BufferedWriter
        out = new BufferedWriter(new FileWriter(Config.iroutFile + suffix + ".txt"));
        ArrayList<GlobalVar> globalVars = irModule.globalVars();
        // 导出所有全局变量
        for (GlobalVar globalVar : globalVars) {
            DumpGlobalVar(globalVar);
        }

        ArrayList<Function> functions = irModule.functions();
        // 导出所有函数
        for (Function function : functions) {
            DumpFunction(function);
            out.write("\n");
        }
        // 关闭输出流
        out.close();
    }

    public static String getModuleDump(IRModule irModule) throws IOException {
        var sw = new StringWriter();
        out = new BufferedWriter(sw);
        ArrayList<GlobalVar> globalVars = irModule.globalVars();
        for (GlobalVar globalVar : globalVars) {
            DumpGlobalVar(globalVar);
        }

        ArrayList<Function> functions = irModule.functions();
        for (Function function : functions) {
            DumpFunction(function);
            out.write("\n");
        }
        out.close();
        return sw.toString();
    }
}
