package Backend;

import IR.*;
import IR.Value.*;
import IR.Value.Instructions.*;
import Utils.DataStruct.IList;

import java.io.PrintStream;
import java.util.*;

public class ArmWriter {
    private final PrintStream os;
    private final Map<Value, Integer> stackMap;
    private final Map<BasicBlock, Integer> labelMap;
    private Function curFunction = null;
    private int stackSize;

    private String getCondTagStr(OP tag) {
        return switch (tag) {
            case Eq -> "eq";
            case Ne -> "ne";
            case Lt -> "lt";
            case Le -> "le";
            case Gt -> "gt";
            case Ge -> "ge";
            default -> throw new RuntimeException("Invalid tag");
        };
    }

    private boolean isNaiveLogicalOp(Instruction instr) {
        if (!(instr instanceof BinaryInst cmpInst)) return false;
        if (cmpInst.getOp() != OP.Ne && cmpInst.getOp() != OP.Eq)
            return false;
        Value rhs = cmpInst.getRightVal();
        if (!(rhs instanceof Const)) return false;
        return !(rhs instanceof ConstInteger constRhs) || constRhs.getValue() == 0;
    }

    public static class CallInfo {
        public List<Value> argsInIntReg = new ArrayList<>();
        public List<Value> argsInFloatReg = new ArrayList<>();
        public List<Value> argsOnStack = new ArrayList<>();
    }

    private <T extends Value> CallInfo arrangeCallInfo(List<T> args) {
        CallInfo info = new CallInfo();
        final int maxIntRegs = 8, maxFloatRegs = 8; // AArch64: x0-x7, d0-d7
        for (T arg : args) {
            if (arg.getType().isFloatTy()) {
                if (info.argsInFloatReg.size() < maxFloatRegs) {
                    info.argsInFloatReg.add(arg);
                } else {
                    info.argsOnStack.add(arg);
                }
            } else {
                if (info.argsInIntReg.size() < maxIntRegs) {
                    info.argsInIntReg.add(arg);
                } else {
                    info.argsOnStack.add(arg);
                }
            }
        }
        return info;
    }

    public ArmWriter(PrintStream os) {
        this.os = os;
        this.stackMap = new HashMap<>();
        this.labelMap = new HashMap<>();
    }

    // Helper method to remove @ prefix from variable names
    private String cleanName(String name) {
        return name.startsWith("@") ? name.substring(1) : name;
    }

    // RAII helper for reg alloc - Java version uses try-with-resources
    public class Reg implements AutoCloseable {
        public boolean isFloat;
        public int id;
        private final NaiveAllocator alloc;

        public String abiName() {
            assert id >= 0 : "Invalid reg!";
            if (isFloat) {
                return "d" + id;  // AArch64: d0-d31 for double precision
            } else {
                if (id == 29) return "fp"; // AArch64: x29 is frame pointer
                if (id == 30) return "lr";
                if (id == 31) return "sp";
                return "x" + id;  // AArch64: x0-x30 for 64-bit, w0-w30 for 32-bit
            }
        }

        public String abiName32() {
            assert id >= 0 : "Invalid reg!";
            if (isFloat) {
                return "s" + id;  // AArch64: s0-s31 for single precision
            } else {
                if (id == 29) return "fp"; // AArch64: x29 is frame pointer
                if (id == 30) return "lr";
                if (id == 31) return "sp";
                return "w" + id;  // AArch64: w0-w30 for 32-bit integers
            }
        }

        @Override
        public String toString() {
            return abiName();
        }

        public Reg(boolean isFloat, int id, NaiveAllocator alloc) {
            this.isFloat = isFloat;
            this.id = id;
            this.alloc = alloc;
        }

        @Override
        public void close() {
            if (id < 0) return;
            if (isFloat) {
                alloc.fltRegs.add(id);
            } else {
                alloc.intRegs.add(id);
            }
            id = -1;
        }
    }

    public class NaiveAllocator {
        public Set<Integer> intRegs = new TreeSet<>();
        public Set<Integer> fltRegs = new TreeSet<>();

        public NaiveAllocator() {
            reset();
        }

        public Reg allocIntReg() {
            if (!intRegs.isEmpty()) {
                int allocated = intRegs.iterator().next();
                intRegs.remove(allocated);
                return new Reg(false, allocated, this);
            } else {
                throw new RuntimeException("Reg Limit Exceeded");
            }
        }

        public Reg allocFloatReg() {
            if (!fltRegs.isEmpty()) {
                int allocated = fltRegs.iterator().next();
                fltRegs.remove(allocated);
                return new Reg(true, allocated, this);
            } else {
                throw new RuntimeException("Reg Limit Exceeded");
            }
        }

        public Reg claimIntReg(int reg) {
            if (intRegs.contains(reg)) {
                intRegs.remove(reg);
            } else {
                throw new RuntimeException("Reg conflict");
            }
            return new Reg(false, reg, this);
        }

        public Reg claimFloatReg(int reg) {
            if (fltRegs.contains(reg)) {
                fltRegs.remove(reg);
            } else {
                throw new RuntimeException("Reg conflict");
            }
            return new Reg(true, reg, this);
        }

        public Reg allocReg(Value val) {
            if (val.getType().isFloatTy()) {
                return allocFloatReg();
            } else {
                return allocIntReg();
            }
        }

        public void reset() {
            intRegs.clear();
            fltRegs.clear();
            // AArch64: x0-x30 (x31 is SP), but we reserve some for special purposes
            for (int i = 0; i <= 18; ++i) { // x0-x18 available for allocation
                intRegs.add(i);
            }
            // AArch64: d0-d31, but we use d0-d15 for allocation
            for (int i = 0; i < 16; ++i) {
                fltRegs.add(i);
            }
        }
    }

    public NaiveAllocator regAlloc = new NaiveAllocator();

    public void loadToSpecificReg(Reg reg, Value val) {
        if (val instanceof Const) {
            if (val instanceof ConstInteger constVal) {
                long imm = constVal.getValue();
                if (imm >= 0 && imm <= 65535) {
                    // Use mov for small positive immediates
                    printAArch64Instr("mov", Arrays.asList(reg.abiName32(), "#" + imm));
                } else if (imm >= -65536 && imm < 0) {
                    // Use mov with negative immediate
                    printAArch64Instr("mov", Arrays.asList(reg.abiName32(), "#" + imm));
                } else {
                    // Use movz/movk for larger immediates
                    printAArch64Instr("movz", Arrays.asList(reg.abiName32(), "#" + (imm & 0xffff)));
                    if ((imm >> 16) != 0) {
                        printAArch64Instr("movk", Arrays.asList(reg.abiName32(), "#" + ((imm >> 16) & 0xffff), "lsl #16"));
                    }
                }
            } else {
                // Load float constant
                int floatBits = Float.floatToIntBits(((ConstFloat)val).getValue());

                try (Reg regInt = regAlloc.allocIntReg()) {
                    printAArch64Instr("movz", Arrays.asList(regInt.abiName32(), "#" + (floatBits & 0xffff)));
                    if ((floatBits >> 16) != 0) {
                        printAArch64Instr("movk", Arrays.asList(regInt.abiName32(), "#" + ((floatBits >> 16) & 0xffff), "lsl #16"));
                    }
                    printAArch64Instr("fmov", Arrays.asList(reg.abiName32(), regInt.abiName32()));
                }
            }
        } else {
            if (val instanceof LoadInst ld) {
                val = ld.getPointer();
                if (val instanceof GlobalVar gv) {
                    if (!reg.isFloat) {
                        String cleanGvName = cleanName(gv.getName());
                        printAArch64Instr("adrp", Arrays.asList(reg.abiName(), cleanGvName));
                        printAArch64Instr("ldr", Arrays.asList(reg.abiName32(), "[" + reg.abiName() + ", #:lo12:" + cleanGvName + "]"));
                    } else {
                        try (Reg regAddr = regAlloc.allocIntReg()) {
                            String cleanGvName = cleanName(gv.getName());
                            printAArch64Instr("adrp", Arrays.asList(regAddr.abiName(), cleanGvName));
                            printAArch64Instr("ldr", Arrays.asList(reg.abiName32(), "[" + regAddr.abiName() + ", #:lo12:" + cleanGvName + "]"));
                        }
                    }
                    return;
                } else if (val instanceof AllocInst) {
                    // For LoadInst from AllocInst, we need to load the value from the stack slot
                    int offset = stackMap.get(val);
                    if (offset <= 4095) {
                        String instName = reg.isFloat ? "ldr" : "ldr";
                        String regName = reg.isFloat ? reg.abiName32() : reg.abiName32();
                        printAArch64Instr(instName, Arrays.asList(regName, "[sp, #" + offset + "]"));
                    } else {
                        // Large offset, need register addressing
                        try (Reg regAddr = regAlloc.allocIntReg()) {
                            int low16 = offset & 0xFFFF;
                            int high16 = (offset >> 16) & 0xFFFF;
                            
                            if (high16 == 0) {
                                printAArch64Instr("mov", Arrays.asList(regAddr.abiName(), "#" + low16));
                            } else {
                                printAArch64Instr("movz", Arrays.asList(regAddr.abiName(), "#" + high16, "lsl #16"));
                                if (low16 != 0) {
                                    printAArch64Instr("movk", Arrays.asList(regAddr.abiName(), "#" + low16));
                                }
                            }
                            printAArch64Instr("add", Arrays.asList(regAddr.abiName(), "sp", regAddr.abiName()));
                            String instName = reg.isFloat ? "ldr" : "ldr";
                            String regName = reg.isFloat ? reg.abiName32() : reg.abiName32();
                            printAArch64Instr(instName, Arrays.asList(regName, "[" + regAddr.abiName() + "]"));
                        }
                    }
                    return;
                } else { // If non-static address, use memory slot
                    int offset = stackMap.get(val);
                    if (offset <= 4095) {
                        if (reg.isFloat) {
                            try (Reg regAddr = regAlloc.allocIntReg()) {
                                printAArch64Instr("ldr", Arrays.asList(regAddr.abiName(), "[sp, #" + offset + "]"));
                                printAArch64Instr("ldr", Arrays.asList(reg.abiName32(), "[" + regAddr.abiName() + "]"));
                            }
                        } else {
                            // Load pointer from memory slot and then load value
                            printAArch64Instr("ldr", Arrays.asList(reg.abiName(), "[sp, #" + offset + "]"));
                            printAArch64Instr("ldr", Arrays.asList(reg.abiName32(), "[" + reg.abiName() + "]"));
                        }
                    } else {
                        // Large offset, need register addressing for both steps
                        try (Reg regAddr = regAlloc.allocIntReg()) {
                            int low16 = offset & 0xFFFF;
                            int high16 = (offset >> 16) & 0xFFFF;
                            
                            if (high16 == 0) {
                                printAArch64Instr("mov", Arrays.asList(regAddr.abiName(), "#" + low16));
                            } else {
                                printAArch64Instr("movz", Arrays.asList(regAddr.abiName(), "#" + high16, "lsl #16"));
                                if (low16 != 0) {
                                    printAArch64Instr("movk", Arrays.asList(regAddr.abiName(), "#" + low16));
                                }
                            }
                            printAArch64Instr("add", Arrays.asList(regAddr.abiName(), "sp", regAddr.abiName()));
                            printAArch64Instr("ldr", Arrays.asList(regAddr.abiName(), "[" + regAddr.abiName() + "]"));
                            printAArch64Instr("ldr", Arrays.asList(reg.abiName32(), "[" + regAddr.abiName() + "]"));
                        }
                    }
                }
            } else {
                if (val instanceof GlobalVar gv) {
                    assert !reg.isFloat : "Address should not be float";
                    String cleanGvName = cleanName(gv.getName());
                    printAArch64Instr("adrp", Arrays.asList(reg.abiName(), cleanGvName));
                    printAArch64Instr("add", Arrays.asList(reg.abiName(), reg.abiName(), "#:lo12:" + cleanGvName));
                } else if (val instanceof AllocInst) {
                    assert !reg.isFloat : "Address should not be float";
                    int offset = stackMap.get(val);
                    if (offset <= 4095) {
                        printAArch64Instr("add", Arrays.asList(reg.abiName(), "sp", "#" + offset));
                    } else {
                        // Large offset, load into register first
                        if (offset <= 65535) {
                            printAArch64Instr("mov", Arrays.asList(reg.abiName(), "#" + offset));
                        } else {
                            printAArch64Instr("movz", Arrays.asList(reg.abiName(), "#" + (offset & 0xFFFF)));
                            if ((offset >> 16) != 0) {
                                printAArch64Instr("movk", Arrays.asList(reg.abiName(), "#" + (offset >> 16), "lsl #16"));
                            }
                        }
                        printAArch64Instr("add", Arrays.asList(reg.abiName(), "sp", reg.abiName()));
                    }
                } else { // If non-static address, use memory slot
                    // For non-pointer values, load them directly based on their type
                    int offset = stackMap.get(val);
                    if (offset <= 4095) {
                        if (val.getType().isPointerType()) {
                            printAArch64Instr("ldr", Arrays.asList(reg.abiName(), "[sp, #" + offset + "]"));
                        } else {
                            printAArch64Instr("ldr", Arrays.asList(reg.abiName32(), "[sp, #" + offset + "]"));
                        }
                    } else {
                        try (Reg regAddr = regAlloc.allocIntReg()) {
                            if (offset <= 65535) {
                                printAArch64Instr("mov", Arrays.asList(regAddr.abiName(), "#" + offset));
                            } else {
                                printAArch64Instr("movz", Arrays.asList(regAddr.abiName(), "#" + (offset & 0xFFFF)));
                                if ((offset >> 16) != 0) {
                                    printAArch64Instr("movk", Arrays.asList(regAddr.abiName(), "#" + (offset >> 16), "lsl #16"));
                                }
                            }
                            printAArch64Instr("add", Arrays.asList(regAddr.abiName(), "sp", regAddr.abiName()));
                            if (val.getType().isPointerType()) {
                                printAArch64Instr("ldr", Arrays.asList(reg.abiName(), "[" + regAddr.abiName() + "]"));
                            } else {
                                printAArch64Instr("ldr", Arrays.asList(reg.abiName32(), "[" + regAddr.abiName() + "]"));
                            }
                        }
                    }
                }
            }
        }
    }

    public Reg loadToReg(Value val) {
        Reg reg = regAlloc.allocReg(val);
        loadToSpecificReg(reg, val);
        return reg;
    }

    public void assignToSpecificReg(Reg reg, Value val) {
        if (val instanceof Argument arg) {
            int argNo = curFunction.getArgNo(arg);
            if (argNo < 8) { // AArch64: x0-x7 for integer args
                if (arg.getType().isFloatTy()) {
                    try (Reg regArg = regAlloc.claimFloatReg(argNo)) {
                        printAArch64Instr("fmov", Arrays.asList(reg.abiName32(), regArg.abiName32()));
                    }
                } else {
                    try (Reg regArg = regAlloc.claimIntReg(argNo)) {
                        printAArch64Instr("mov", Arrays.asList(reg.abiName32(), regArg.abiName32()));
                    }
                }
                return;
            }
        }
        // Otherwise, the value has a memory slot.
        loadToSpecificReg(reg, val);
    }

    public void storeRegToMemorySlot(Reg reg, Value val) {
        assert !(val instanceof AllocInst) : "Alloca has no memory slot.";
        int offset = stackMap.get(val);
        if (offset <= 4095) {
            // Small offset, can use direct addressing
            if (reg.isFloat) {
                printAArch64Instr("str", Arrays.asList(reg.abiName32(), "[sp, #" + offset + "]"));
            } else {
                // Use 64-bit register name for pointers, 32-bit for integers
                String regName = (val.getType().isPointerType()) ? reg.abiName() : reg.abiName32();
                printAArch64Instr("str", Arrays.asList(regName, "[sp, #" + offset + "]"));
            }
        } else {
            // Large offset, need to use register addressing
            try (Reg regAddr = regAlloc.allocIntReg()) {
                int low16 = offset & 0xFFFF;
                int high16 = (offset >> 16) & 0xFFFF;
                
                if (high16 == 0) {
                    printAArch64Instr("mov", Arrays.asList(regAddr.abiName(), "#" + low16));
                } else {
                    printAArch64Instr("movz", Arrays.asList(regAddr.abiName(), "#" + high16, "lsl #16"));
                    if (low16 != 0) {
                        printAArch64Instr("movk", Arrays.asList(regAddr.abiName(), "#" + low16));
                    }
                }
                printAArch64Instr("add", Arrays.asList(regAddr.abiName(), "sp", regAddr.abiName()));
                String regName = (val.getType().isPointerType()) ? reg.abiName() : reg.abiName32();
                printAArch64Instr("str", Arrays.asList(regName, "[" + regAddr.abiName() + "]"));
            }
        }
    }

    public void storeRegToAddress(Reg reg, Value ptr) {
        if (ptr instanceof AllocInst) {
            int offset = stackMap.get(ptr);
            if (offset <= 4095) {
                // 直接使用立即数偏移
                printAArch64Instr("str", Arrays.asList(reg.abiName32(), "[sp, #" + offset + "]"));
            } else {
                // 使用寄存器寻址处理大偏移
                try (Reg regAddr = regAlloc.allocIntReg()) {
                    // 使用movz/movk构造大偏移量
                    int low16 = offset & 0xFFFF;
                    int high16 = (offset >> 16) & 0xFFFF;
                    
                    if (high16 == 0) {
                        printAArch64Instr("mov", Arrays.asList(regAddr.abiName(), "#" + low16));
                    } else {
                        printAArch64Instr("movz", Arrays.asList(regAddr.abiName(), "#" + high16, "lsl #16"));
                        if (low16 != 0) {
                            printAArch64Instr("movk", Arrays.asList(regAddr.abiName(), "#" + low16));
                        }
                    }
                    printAArch64Instr("add", Arrays.asList(regAddr.abiName(), "sp", regAddr.abiName()));
                    printAArch64Instr("str", Arrays.asList(reg.abiName32(), "[" + regAddr.abiName() + "]"));
                }
            }
        } else {
            try (Reg regPtr = loadToReg(ptr)) {
                printAArch64Instr("str", Arrays.asList(reg.abiName32(), "[" + regPtr.abiName() + "]"));
            }
        }
    }

    private static final Map<String, String> intInstr2FltInstrOpCode = new HashMap<>();
    
    static {
        intInstr2FltInstrOpCode.put("add", "fadd");
        intInstr2FltInstrOpCode.put("sub", "fsub");
        intInstr2FltInstrOpCode.put("mul", "fmul");
        intInstr2FltInstrOpCode.put("div", "fdiv");
        intInstr2FltInstrOpCode.put("cmp", "fcmp");
        intInstr2FltInstrOpCode.put("mov", "fmov");
        // Note: ldr/str are handled differently in AArch64
    }

    public void printModule(IRModule irModule) {
        os.println(".data");
        for (GlobalVar global : irModule.globalVars()) {
            printGlobalData(global);
        }

        os.println(".bss");
        for (GlobalVar global : irModule.globalVars()) {
            printGlobalBss(global);
        }

        os.println(".text");
        for (Function func : irModule.functions()) {
            String funcName = cleanName(func.getName());
            os.println(".global " + funcName);
        }
        for (Function func : irModule.functions()) {
            printFunc(func);
        }
    }

    public void printGlobalData(GlobalVar global) {
        if (global.isZeroInit())
            return;

        String globalName = cleanName(global.getName());
        os.println(globalName + ":");
        printConstData(global);
    }

    /**
     * Print constant data for AArch64 using the IR model
     * Handles arrays, integers, and floats with proper zero-initialization optimization
     */
    private void printConstData(GlobalVar global) {
        if (global.isArray()) {
            // Handle array constants
            if (global.isZeroInit()) {
                // Zero-initialized array
                os.println("\t.zero\t" + (global.getSize() * 4));
            } else {
                // Array with explicit values
                ArrayList<Value> values = global.getValues();
                int lastNonZeroIndex = -1;
                
                for (int i = 0; i < values.size(); i++) {
                    Value val = values.get(i);
                    
                    if (val instanceof ConstInteger) {
                        int intVal = ((ConstInteger) val).getValue();
                        if (intVal != 0) {
                            // Output .zero for any gap since last non-zero
                            if (lastNonZeroIndex != i - 1) {
                                int zeroCount = i - 1 - lastNonZeroIndex;
                                if (zeroCount > 0) {
                                    os.println("\t.zero\t" + (4 * zeroCount));
                                }
                            }
                            lastNonZeroIndex = i;
                            os.println("\t.word\t" + intVal);
                        }
                    } else if (val instanceof ConstFloat) {
                        float floatVal = ((ConstFloat) val).getValue();
                        if (floatVal != 0.0f) {
                            // Output .zero for any gap since last non-zero
                            if (lastNonZeroIndex != i - 1) {
                                int zeroCount = i - 1 - lastNonZeroIndex;
                                if (zeroCount > 0) {
                                    os.println("\t.zero\t" + (4 * zeroCount));
                                }
                            }
                            lastNonZeroIndex = i;
                            // Use .word for 32-bit float representation in AArch64
                            os.println("\t.word\t" + Float.floatToIntBits(floatVal));
                        }
                    }
                }
                
                // Handle trailing zeros
                if (lastNonZeroIndex < values.size() - 1) {
                    int trailingZeros = values.size() - 1 - lastNonZeroIndex;
                    os.println("\t.zero\t" + (4 * trailingZeros));
                }
            }
        } else {
            // Handle single value (not array)
            Value val = global.getValue();
            if (val instanceof ConstInteger) {
                os.println("\t.word\t" + ((ConstInteger) val).getValue());
            } else if (val instanceof ConstFloat) {
                // Use .word for 32-bit float representation in AArch64
                os.println("\t.word\t" + Float.floatToIntBits(((ConstFloat) val).getValue()));
            } else {
                throw new RuntimeException("Unsupported constant type: " + val.getClass().getSimpleName());
            }
        }
    }

    public void printGlobalBss(GlobalVar global) {
        if (!global.isZeroInit()) {
            return;
        }
        String globalName = cleanName(global.getName());
        os.println(globalName + ":");
        int size = global.isArray() ? global.getSize() * 4 : 4;
        os.println("\t.skip\t" + size);
    }

    public void printFunc(Function function) {
        if (function.isLibFunc)
            return;

        curFunction = function;
        String funcName = cleanName(function.getName());
        os.println(funcName + ":");

        CallInfo funcCallInfo = arrangeCallInfo(function.getArgs());

        // 保护好参数寄存器，直到它们存入栈中
        List<Reg> argIntRegs = new ArrayList<>();
        List<Reg> argFloatRegs = new ArrayList<>();
        for (int i = 0; i < funcCallInfo.argsInIntReg.size(); i++) {
            argIntRegs.add(regAlloc.claimIntReg(i));
        }
        for (int i = 0; i < funcCallInfo.argsInFloatReg.size(); i++) {
            argFloatRegs.add(regAlloc.claimFloatReg(i));
        }

        // 计算栈空间
        stackSize = 0;
        stackMap.clear();

        // 给函数内 call 指令的栈参数分配 stack slot
        int callStackSize = 0;
        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction instr = instNode.getValue();
                if (instr instanceof CallInst callInst) {
                    CallInfo callInfo = arrangeCallInfo(callInst.getOperands());
                    callStackSize = Math.max(callInfo.argsOnStack.size() * 8, callStackSize); // 8 bytes per arg in AArch64
                }
            }
        }
        stackSize += callStackSize;

        // 给本函数的寄存器参数分配 stack slot
        for (Value arg : funcCallInfo.argsInIntReg) {
            stackMap.put(arg, stackSize);
            stackSize += 8;  // AArch64 uses 8-byte alignment
        }
        for (Value arg : funcCallInfo.argsInFloatReg) {
            stackMap.put(arg, stackSize);
            stackSize += 8;  // AArch64 uses 8-byte alignment
        }

        // 给指令结果分配 stack slot
        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            BasicBlock bb = bbNode.getValue();
            for (IList.INode<Instruction, BasicBlock> instNode : bb.getInsts()) {
                Instruction instr = instNode.getValue();
                if (instr.getOp() == OP.Load)
                    continue;
                // Check for comparison operations that are only used in branches
                boolean allBrUse = instr.getUseList().stream()
                    .allMatch(use -> use.getUser() instanceof BrInst);
                if (allBrUse && (instr.getOp() == OP.Eq || instr.getOp() == OP.Ne ||
                                instr.getOp() == OP.Lt || instr.getOp() == OP.Le ||
                                instr.getOp() == OP.Gt || instr.getOp() == OP.Ge))
                    continue;

                stackMap.put(instr, stackSize);
                if (instr instanceof AllocInst alloca) {
                    // Use IR model methods - arrays are handled through isArray() and getSize()
                    if (alloca.isArray()) {
                        // For arrays, use the size from AllocInst directly
                        stackSize += 4 * alloca.getSize();
                        // Align to 8 bytes for AArch64
                        if (stackSize % 8 != 0) {
                            stackSize = (stackSize + 7) & ~7;
                        }
                    } else {
                        stackSize += 8;  // AArch64 uses 8-byte slots
                    }
                } else {
                    stackSize += 8;  // AArch64 uses 8-byte slots
                }
            }
        }

        printAArch64Instr("stp", Arrays.asList("lr", "x29", "[sp, #-16]!"));  // Save lr and frame pointer
        printAArch64Instr("mov", Arrays.asList("x29", "sp"));  // Set frame pointer

        // AArch64 stack must be 16-byte aligned
        if (stackSize % 16 != 0) {
            stackSize = (stackSize + 15) & ~15;
        }

        if (stackSize > 0) {
            if (stackSize <= 4095) {
                printAArch64Instr("sub", Arrays.asList("sp", "sp", "#" + stackSize));
            } else {
                // For large stack sizes, use register
                try (Reg regTemp = regAlloc.allocIntReg()) {
                    if (stackSize <= 65535) {
                        printAArch64Instr("mov", Arrays.asList(regTemp.abiName(), "#" + stackSize));
                    } else {
                        printAArch64Instr("movz", Arrays.asList(regTemp.abiName(), "#" + (stackSize & 0xffff)));
                        if ((stackSize >> 16) != 0) {
                            printAArch64Instr("movk", Arrays.asList(regTemp.abiName(), "#" + ((stackSize >> 16) & 0xffff), "lsl #16"));
                        }
                    }
                    printAArch64Instr("sub", Arrays.asList("sp", "sp", regTemp.abiName()));
                }
            }
        }

        // 保存寄存器参数到栈
        for (int i = 0; i < funcCallInfo.argsInIntReg.size(); i++) {
            storeRegToMemorySlot(argIntRegs.get(i), funcCallInfo.argsInIntReg.get(i));
        }
        for (int i = 0; i < funcCallInfo.argsInFloatReg.size(); i++) {
            storeRegToMemorySlot(argFloatRegs.get(i), funcCallInfo.argsInFloatReg.get(i));
        }
        
        // Close all argument registers
        for (Reg reg : argIntRegs) {
            reg.close();
        }
        for (Reg reg : argFloatRegs) {
            reg.close();
        }

        // 栈上参数已经被调用方分配，直接插入 stack slot
        int argsOffset = stackSize + 16; // 16 bytes for saved lr and x29
        for (int i = 0; i < funcCallInfo.argsOnStack.size(); i++) {
            stackMap.put(funcCallInfo.argsOnStack.get(i), argsOffset);
            argsOffset += 8;  // AArch64 uses 8-byte slots
        }

        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            printBasicBlock(bbNode.getValue());
        }

        os.println();
    }

    public void printBasicBlock(BasicBlock basicBlock) {
        os.println(getLabel(basicBlock) + ":");
        for (IList.INode<Instruction, BasicBlock> instNode : basicBlock.getInsts()) {
            printInstr(instNode.getValue());
        }
    }

    public void printInstr(Instruction instr) {
        switch (instr.getOp()) {
            case Alloca:
            case Load: {
                // Do nothing for naive regalloc
                break;
            }
            case Ptradd: {
                PtrInst gep = (PtrInst) instr;
                try (Reg regPtr = loadToReg(gep.getTarget())) {
                    if (gep.getOffset() instanceof ConstInteger constIndex) {
                        int offset = 4 * constIndex.getValue();
                        if (offset >= 0 && offset < 4096) {
                            printAArch64Instr("add", Arrays.asList(regPtr.abiName(), regPtr.abiName(), "#" + offset));
                        } else {
                            try (Reg regOffset = regAlloc.allocIntReg()) {
                                printAArch64Instr("mov", Arrays.asList(regOffset.abiName(), "#" + offset));
                                printAArch64Instr("add", Arrays.asList(regPtr.abiName(), regPtr.abiName(), regOffset.abiName()));
                            }
                        }
                    } else {
                        try (Reg regOffset = loadToReg(gep.getOffset())) {
                            printAArch64Instr("add", Arrays.asList(regPtr.abiName(), regPtr.abiName(), regOffset.abiName(), "lsl #2"));
                        }
                    }
                    storeRegToMemorySlot(regPtr, instr);
                }
                break;
            }
            case Store: {
                StoreInst storeInst = (StoreInst) instr;
                try (Reg regVal = loadToReg(storeInst.getValue())) {
                    storeRegToAddress(regVal, storeInst.getPointer());
                }
                break;
            }
            case Add:
                printBinInstr("add", instr);
                break;
            case Sub:
                printBinInstr("sub", instr);
                break;
            case Mul:
                printBinInstr("mul", instr);
                break;
            case Div: {
                if (instr.getType().isIntegerTy()) {
                    try (Reg regLhs = loadToReg(instr.getOperand(0));
                         Reg regRhs = loadToReg(instr.getOperand(1))) {
                        printAArch64Instr("sdiv", Arrays.asList(regLhs.abiName32(), regLhs.abiName32(), regRhs.abiName32()));
                        storeRegToMemorySlot(regLhs, instr);
                    }
                } else {
                    try (Reg regLhs = loadToReg(instr.getOperand(0));
                         Reg regRhs = loadToReg(instr.getOperand(1))) {
                        printAArch64Instr("fdiv", Arrays.asList(regLhs.abiName32(), regLhs.abiName32(), regRhs.abiName32()));
                        storeRegToMemorySlot(regLhs, instr);
                    }
                }
                break;
            }
            case Mod: {
                try (Reg regLhs = loadToReg(instr.getOperand(0));
                     Reg regRhs = loadToReg(instr.getOperand(1));
                     Reg regDiv = regAlloc.allocIntReg()) {
                    // AArch64 doesn't have modulo instruction, compute using div
                    printAArch64Instr("sdiv", Arrays.asList(regDiv.abiName32(), regLhs.abiName32(), regRhs.abiName32()));
                    printAArch64Instr("msub", Arrays.asList(regLhs.abiName32(), regDiv.abiName32(), regRhs.abiName32(), regLhs.abiName32()));
                    storeRegToMemorySlot(regLhs, instr);
                }
                break;
            }
            case Itof: {
                try (Reg ireg = loadToReg(instr.getOperand(0));
                     Reg freg = regAlloc.allocFloatReg()) {
                    printAArch64Instr("scvtf", Arrays.asList(freg.abiName32(), ireg.abiName32()));
                    storeRegToMemorySlot(freg, instr);
                }
                break;
            }
            case Ftoi: {
                try (Reg freg = loadToReg(instr.getOperand(0));
                     Reg ireg = regAlloc.allocIntReg()) {
                    printAArch64Instr("fcvtzs", Arrays.asList(ireg.abiName32(), freg.abiName32()));
                    storeRegToMemorySlot(ireg, instr);
                }
                break;
            }
            case Lt:
            case Le:
            case Ge:
            case Gt:
            case Eq:
            case Ne: {
                // For all br use, do nothing. The codegen is in BranchInst.
                boolean allBrUse = instr.getUseList().stream()
                    .allMatch(use -> use.getUser() instanceof BrInst);
                if (allBrUse)
                    break;
                    
                BinaryInst cmpInst = (BinaryInst) instr;
                if (isNaiveLogicalOp(cmpInst)) {
                    try (Reg regLhs = loadToReg(cmpInst.getLeftVal())) {
                        if (cmpInst.getOp() == OP.Ne) {
                            printAArch64Instr("cmp", Arrays.asList(regLhs.abiName32(), "#0"));
                            printAArch64Instr("cset", Arrays.asList(regLhs.abiName32(), "ne"));
                            storeRegToMemorySlot(regLhs, instr);
                        } else if (cmpInst.getOp() == OP.Eq) {
                            printAArch64Instr("cmp", Arrays.asList(regLhs.abiName32(), "#0"));
                            printAArch64Instr("cset", Arrays.asList(regLhs.abiName32(), "eq"));
                            storeRegToMemorySlot(regLhs, instr);
                        } else {
                            throw new RuntimeException("Invalid tag");
                        }
                    }
                } else {
                    if (cmpInst.getLeftVal().getType().isFloatTy()) {
                        try (Reg regLhs = loadToReg(cmpInst.getLeftVal());
                             Reg regRhs = loadToReg(cmpInst.getRightVal());
                             Reg regRes = regAlloc.allocIntReg()) {
                            printAArch64Instr("fcmp", Arrays.asList(regLhs.abiName32(), regRhs.abiName32()));
                            printAArch64Instr("cset", Arrays.asList(regRes.abiName32(), getCondTagStr(cmpInst.getOp())));
                            storeRegToMemorySlot(regRes, instr);
                        }
                    } else {
                        try (Reg regLhs = loadToReg(cmpInst.getLeftVal());
                             Reg regRhs = loadToReg(cmpInst.getRightVal())) {
                            printAArch64Instr("cmp", Arrays.asList(regLhs.abiName32(), regRhs.abiName32()));
                            printAArch64Instr("cset", Arrays.asList(regLhs.abiName32(), getCondTagStr(cmpInst.getOp())));
                            storeRegToMemorySlot(regLhs, instr);
                        }
                    }
                }
                break;
            }
            case Br: {
                BrInst brInst = (BrInst) instr;
                if (brInst.getJudVal() != null) {
                    // Conditional branch
                    if (!(brInst.getJudVal() instanceof BinaryInst)) {
                        throw new RuntimeException("Branch condition must be a cmp op");
                    }
                    BinaryInst cond = (BinaryInst) brInst.getJudVal();
                    String condTag = getCondTagStr(cond.getOp());
                    printCmpInstr(cond);
                    printAArch64Instr("b." + condTag, List.of(getLabel(brInst.getTrueBlock())));
                    printAArch64Instr("b", List.of(getLabel(brInst.getFalseBlock())));
                } else {
                    // Unconditional jump
                    printAArch64Instr("b", List.of(getLabel(brInst.getJumpBlock())));
                }
                break;
            }
            case Ret: {
                RetInst retInst = (RetInst) instr;
                if (!retInst.getOperands().isEmpty()) {
                    Value retVal = retInst.getValue();
                    if (retVal.getType().isIntegerTy()) {
                        try (Reg regRet = regAlloc.claimIntReg(0)) {  // x0 for return value
                            assignToSpecificReg(regRet, retVal);
                        }
                    } else if (retVal.getType().isFloatTy()) {
                        try (Reg regRet = regAlloc.claimFloatReg(0)) { // d0 for float return value
                            assignToSpecificReg(regRet, retVal);
                        }
                    }
                }
                if (stackSize > 0) {
                    if (stackSize <= 4095) {
                        printAArch64Instr("add", Arrays.asList("sp", "sp", "#" + stackSize));
                    } else {
                        // For large stack sizes, use register
                        try (Reg regTemp = regAlloc.allocIntReg()) {
                            if (stackSize <= 65535) {
                                printAArch64Instr("mov", Arrays.asList(regTemp.abiName(), "#" + stackSize));
                            } else {
                                printAArch64Instr("movz", Arrays.asList(regTemp.abiName(), "#" + (stackSize & 0xffff)));
                                if ((stackSize >> 16) != 0) {
                                    printAArch64Instr("movk", Arrays.asList(regTemp.abiName(), "#" + ((stackSize >> 16) & 0xffff), "lsl #16"));
                                }
                            }
                            printAArch64Instr("add", Arrays.asList("sp", "sp", regTemp.abiName()));
                        }
                    }
                }
                printAArch64Instr("ldp", Arrays.asList("lr", "x29", "[sp], #16"));
                printAArch64Instr("ret", List.of());
                break;
            }
            case Call: {
                CallInst callInst = (CallInst) instr;
                List<Value> args = callInst.getParams();
                CallInfo callInfo = arrangeCallInfo(args);

                // 认领寄存器
                Map<Integer, Reg> intRegs = new HashMap<>();
                Map<Integer, Reg> floatRegs = new HashMap<>();

                // 返回值 x0 / d0
                boolean isRetFloat = callInst.getType().isFloatTy();
                if (isRetFloat) {
                    floatRegs.put(0, regAlloc.claimFloatReg(0));
                } else if (!callInst.getType().isVoidTy()) {
                    intRegs.put(0, regAlloc.claimIntReg(0));
                }

                // 参数 x0-x7 / d0-d7
                for (int i = 0; i < callInfo.argsInIntReg.size(); i++) {
                    if (!intRegs.containsKey(i)) {
                        intRegs.put(i, regAlloc.claimIntReg(i));
                    }
                }
                for (int i = 0; i < callInfo.argsInFloatReg.size(); i++) {
                    if (!floatRegs.containsKey(i)) {
                        floatRegs.put(i, regAlloc.claimFloatReg(i));
                    }
                }

                // 寄存器入参
                for (int i = 0; i < callInfo.argsInIntReg.size(); i++) {
                    assignToSpecificReg(intRegs.get(i), callInfo.argsInIntReg.get(i));
                }
                for (int i = 0; i < callInfo.argsInFloatReg.size(); i++) {
                    assignToSpecificReg(floatRegs.get(i), callInfo.argsInFloatReg.get(i));
                }

                // 其它入参压栈
                int argsOffset = 0;
                for (Value argOnStack : callInfo.argsOnStack) {
                    try (Reg regArg = loadToReg(argOnStack)) {
                        if (argOnStack.getType().isFloatTy()) {
                            printAArch64Instr("str", Arrays.asList(regArg.abiName32(), "[sp, #" + argsOffset + "]"));
                        } else {
                            printAArch64Instr("str", Arrays.asList(regArg.abiName(), "[sp, #" + argsOffset + "]"));
                        }
                    }
                    argsOffset += 8;  // AArch64 uses 8-byte stack slots
                }
                String callFuncName = cleanName(callInst.getFunction().getName());
                printAArch64Instr("bl", List.of(callFuncName));

                // 返回值存储到栈槽位
                if (!callInst.getType().isVoidTy()) {
                    Reg regRet = isRetFloat ? floatRegs.get(0) : intRegs.get(0);
                    storeRegToMemorySlot(regRet, instr);
                }

                // Close all claimed registers
                for (Reg reg : intRegs.values()) {
                    reg.close();
                }
                for (Reg reg : floatRegs.values()) {
                    reg.close();
                }
                break;
            }
            default:
                System.err.println("NYI Instruction op: " + instr.getOp().ordinal());
                throw new RuntimeException("NYI");
        }
    }

    public void printAArch64Instr(String op, List<String> operands) {
        // indent for instructions
        os.print("  ");

        boolean isFloatRegName = !operands.isEmpty() && 
            !operands.get(0).equals("sp") && 
            !operands.get(0).equals("lr") &&
            !operands.get(0).equals("x29") &&
            !operands.get(0).isEmpty() &&
            (operands.get(0).charAt(0) == 'd' || operands.get(0).charAt(0) == 's');

        if (intInstr2FltInstrOpCode.containsKey(op) && isFloatRegName) {
            os.print(intInstr2FltInstrOpCode.get(op));
        } else {
            os.print(op);
        }
        
        if (!operands.isEmpty()) {
            os.print(" ");
            for (int i = 0; i < operands.size(); ++i) {
                if (i > 0) {
                    os.print(", ");
                }
                os.print(operands.get(i));
            }
        }
        os.println();
    }

    public void printBinInstr(String op, Instruction instr) {
        try (Reg regLhs = loadToReg(instr.getOperand(0));
             Reg regRhs = loadToReg(instr.getOperand(1))) {
            if (regLhs.isFloat) {
                printAArch64Instr(intInstr2FltInstrOpCode.get(op), 
                    Arrays.asList(regLhs.abiName32(), regLhs.abiName32(), regRhs.abiName32()));
            } else {
                printAArch64Instr(op, Arrays.asList(regLhs.abiName32(), regLhs.abiName32(), regRhs.abiName32()));
            }
            storeRegToMemorySlot(regLhs, instr);
        }
    }

    public void printCmpInstr(BinaryInst instr) {
        try (Reg regLhs = loadToReg(instr.getOperand(0));
             Reg regRhs = loadToReg(instr.getOperand(1))) {
            if (regLhs.isFloat) {
                printAArch64Instr("fcmp", Arrays.asList(regLhs.abiName32(), regRhs.abiName32()));
            } else {
                printAArch64Instr("cmp", Arrays.asList(regLhs.abiName32(), regRhs.abiName32()));
            }
        }
    }

    public String getStackOper(Value val) {
        if (val instanceof LoadInst ld) {
            val = ld.getPointer();
        }
        int offset = stackMap.get(val);
        // Check if offset is within AArch64 immediate range
        if (offset <= 4095) {
            return "[sp, #" + offset + "]";
        } else {
            // For large offsets, we cannot use direct addressing
            // This should be handled by the caller using register addressing
            throw new RuntimeException("Stack offset too large for direct addressing: " + offset);
        }
    }

    public String getLabel(BasicBlock bb) {
        if (!labelMap.containsKey(bb)) {
            labelMap.put(bb, labelMap.size());
        }
        String cleanBbName = cleanName(bb.getName());
        return "." + cleanBbName + "_" + labelMap.get(bb);
    }
}
