package Driver;

public class Config {

    public static String inputFile = "testcase.sy";
    public static String iroutFile = "ir";
    public static String llvmIrOutFile = "llvm_ir.ll";
    public static String outputFile = "testcase.s";
    //  isO1: 是否开启优化
    public static boolean isO1 = false; // cuo
    //  outputLLVM: 是否输出中端
    public static boolean outputLLVM = false;
    //  outputNoAlloc: 是否输出未分配寄存器的版本
    public static boolean outputNoAlloc = false;
    //  isDebug: 是否输出return值
    public static boolean outputReturn = false;
    //  DebugLLVM: 是否关闭后端
    public static boolean noDump = false;

    //  一些优化时的超参数
    public static int loopUnrollMaxLines = 2000;

    public static boolean armBackend = true;
    public static boolean riscvBackend = false;

    public static boolean divOptOpen = false; //cuo

    public static boolean spill2FloatOpen = false; //cuo
    public static boolean parallelOpen = false; //cuo
    public static int parallelNum = 4;
    public static boolean enhancedLICM = false; // cuo
}
