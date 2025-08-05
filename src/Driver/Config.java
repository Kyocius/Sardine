package Driver;

public class Config {

    public static String inputFile = "testcase.sy";
    public static String iroutFile = "ir";
    public static String llvmIrOutFile = "llvm_ir.ll";
    public static String outputFile = "testcase.s";

    //  isO1: 是否开启优化
    public static boolean isO1 = false;
    //  outputLLVM: 是否输出中端
    public static boolean outputLLVM = false;
    //  outputNoAlloc: 是否输出未分配寄存器的版本
    public static boolean outputNoAlloc = false;
    //  isDebug: 是否输出return值
    public static boolean outputReturn = false;
    //  DebugLLVM: 是否关闭后端
    public static boolean noDump = false;
    public static boolean armBackend = true;
}
