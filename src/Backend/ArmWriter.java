package Backend;

import IR.*;
import java.io.PrintStream;
import java.util.*;

public class ArmWriter {
    private PrintStream os;
    private Map<Value, Integer> stackMap;
    private Map<BasicBlock, Integer> labelMap;
    private Function curFunction = null;
    private int stackSize;
    private int floatCnt = 0;

    private String getCondTagStr(Value.Tag tag) {
        switch (tag) {
            case Eq:
                return "eq";
            case Ne:
                return "ne";
            case Lt:
                return "lt";
            case Le:
                return "le";
            case Gt:
                return "gt";
            case Ge:
                return "ge";
            default:
                throw new RuntimeException("Invalid tag");
        }
    }

    private Value.Tag getNotCond(Value.Tag tag) {
        switch (tag) {
            case Eq:
                return Value.Tag.Ne;
            case Ne:
                return Value.Tag.Eq;
            case Lt:
                return Value.Tag.Ge;
            case Le:
                return Value.Tag.Gt;
            case Gt:
                return Value.Tag.Le;
            case Ge:
                return Value.Tag.Lt;
            default:
                throw new RuntimeException("Invalid tag");
        }
    }

    private boolean isNaiveLogicalOp(Instruction instr) {
        if (!(instr instanceof BinaryInst)) return false;
        BinaryInst cmpInst = (BinaryInst) instr;
        if (cmpInst.tag != Value.Tag.Ne && cmpInst.tag != Value.Tag.Eq)
            return false;
        Value rhs = cmpInst.getRHS();
        if (!(rhs instanceof ConstantValue)) return false;
        ConstantValue constRhs = (ConstantValue) rhs;
        if (!constRhs.isInt() || constRhs.getInt() != 0)
            return false;
        return true;
    }

    private int reinterpretFloat(float x) {
        return Float.floatToIntBits(x);
    }

    public static class CallInfo {
        public List<Value> argsInIntReg = new ArrayList<>();
        public List<Value> argsInFloatReg = new ArrayList<>();
        public List<Value> argsOnStack = new ArrayList<>();
    }

    private <T extends Value> CallInfo arrangeCallInfo(List<T> args) {
        CallInfo info = new CallInfo();
        final int maxIntRegs = 4, maxFloatRegs = 16;
        for (T rarg : args) {
            Value arg = rarg;
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

    // RAII helper for reg alloc - Java version uses try-with-resources
    public class Reg implements AutoCloseable {
        public boolean isFloat;
        public int id;
        private NaiveAllocator alloc;

        public String abiName() {
            assert id >= 0 : "Invalid reg!";
            if (isFloat) {
                return "s" + id;
            } else {
                return "r" + id;
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
            for (int i = 0; i <= 12; ++i) {
                intRegs.add(i);
            }
            for (int i = 0; i < 32; ++i) {
                fltRegs.add(i);
            }
        }
    }

    public NaiveAllocator regAlloc = new NaiveAllocator();

    public void loadToSpecificReg(Reg reg, Value val) {
        if (val instanceof ConstantValue) {
            ConstantValue constVal = (ConstantValue) val;
            if (constVal.isInt()) {
                long imm = constVal.getInt();
                if (imm < 4096) {
                    printArmInstr("mov", Arrays.asList(reg.toString(), "#" + imm));
                } else {
                    printArmInstr("movw", Arrays.asList(reg.toString(), "#" + (imm & 0xffff)));
                    if ((imm >> 16) != 0)
                        printArmInstr("movt", Arrays.asList(reg.toString(), "#" + (imm >> 16)));
                }
            } else {
                // reinterpret float as int
                int floatBits = Float.floatToIntBits(constVal.getFloat());
                try (Reg regInt = regAlloc.allocIntReg()) {
                    printArmInstr("movw", Arrays.asList(regInt.toString(), "#" + (floatBits & 0xffff)));
                    if ((floatBits >> 16) != 0)
                        printArmInstr("movt", Arrays.asList(regInt.toString(), "#" + (floatBits >> 16)));
                    printArmInstr("vmov.f32", Arrays.asList(reg.toString(), regInt.toString()));
                }
            }
        } else {
            if (val instanceof LoadInst) {
                LoadInst ld = (LoadInst) val;
                val = ld.getPointer();
                if (val instanceof GlobalVariable) {
                    GlobalVariable gv = (GlobalVariable) val;
                    if (!reg.isFloat) {
                        printArmInstr("movw", Arrays.asList(reg.toString(), "#:lower16:" + gv.getName()));
                        printArmInstr("movt", Arrays.asList(reg.toString(), "#:upper16:" + gv.getName()));
                        printArmInstr("ldr", Arrays.asList(reg.toString(), "[" + reg.abiName() + "]"));
                    } else {
                        try (Reg regAddr = regAlloc.allocIntReg()) {
                            printArmInstr("movw", Arrays.asList(regAddr.toString(), "#:lower16:" + gv.getName()));
                            printArmInstr("movt", Arrays.asList(regAddr.toString(), "#:upper16:" + gv.getName()));
                            printArmInstr("ldr", Arrays.asList(reg.toString(), "[" + regAddr.abiName() + "]"));
                        }
                    }
                } else if (val instanceof AllocaInst) {
                    if (reg.isFloat) {
                        try (Reg regAddr = regAlloc.allocIntReg()) {
                            printArmInstr("add", Arrays.asList(regAddr.toString(), "sp", getImme(stackMap.get(val), 8)));
                            printArmInstr("vldr.32", Arrays.asList(reg.toString(), "[" + regAddr.abiName() + "]"));
                        }
                    } else {
                        printArmInstr("ldr", Arrays.asList(reg.toString(), getStackOper(val)));
                    }
                } else { // If non-static address, use memory slot
                    if (reg.isFloat) {
                        // Load from memory slot
                        try (Reg regAddr = regAlloc.allocIntReg()) {
                            printArmInstr("ldr", Arrays.asList(regAddr.toString(), getStackOper(val)));
                            // Load from the loaded address
                            printArmInstr("vldr.32", Arrays.asList(reg.toString(), "[" + regAddr.abiName() + "]"));
                        }
                    } else {
                        // Load from memory slot
                        printArmInstr("ldr", Arrays.asList(reg.toString(), getStackOper(val)));
                        // Load from the loaded address
                        printArmInstr("ldr", Arrays.asList(reg.toString(), "[" + reg.abiName() + "]"));
                    }
                }
            } else {
                if (val instanceof GlobalVariable) {
                    GlobalVariable gv = (GlobalVariable) val;
                    assert !reg.isFloat : "Address should not be float";
                    printArmInstr("movw", Arrays.asList(reg.toString(), "#:lower16:" + gv.getName()));
                    printArmInstr("movt", Arrays.asList(reg.toString(), "#:upper16:" + gv.getName()));
                } else if (val instanceof AllocaInst) {
                    assert !reg.isFloat : "Address should not be float";
                    printArmInstr("add", Arrays.asList(reg.toString(), "sp", getImme(stackMap.get(val), 8)));
                } else { // If non-static address, use memory slot
                    if (reg.isFloat) {
                        try (Reg regAddr = regAlloc.allocIntReg()) {
                            printArmInstr("add", Arrays.asList(regAddr.toString(), "sp", getImme(stackMap.get(val), 8)));
                            printArmInstr("ldr", Arrays.asList(reg.toString(), "[" + regAddr.abiName() + "]"));
                        }
                    } else {
                        printArmInstr("ldr", Arrays.asList(reg.toString(), getStackOper(val)));
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
        if (val instanceof Argument) {
            Argument arg = (Argument) val;
            int argNo = curFunction.getArgNo(arg);
            if (argNo < 4) {
                assert !arg.getType().isFloatTy() : "NYI";
                try (Reg regArg = regAlloc.claimIntReg(argNo)) {
                    printArmInstr("mov", Arrays.asList(reg.toString(), regArg.toString()));
                }
                return;
            }
        }
        // Otherwise, the value has a memory slot.
        loadToSpecificReg(reg, val);
    }

    public void storeRegToMemorySlot(Reg reg, Value val) {
        assert !(val instanceof AllocaInst) : "Alloca has no memory slot.";
        if (reg.isFloat) {
            try (Reg regAddr = regAlloc.allocIntReg()) {
                printArmInstr("add", Arrays.asList(regAddr.toString(), "sp", getImme(stackMap.get(val), 8)));
                printArmInstr("vstr.32", Arrays.asList(reg.toString(), "[" + regAddr.abiName() + "]"));
            }
        } else {
            printArmInstr("str", Arrays.asList(reg.toString(), getStackOper(val)));
        }
    }

    public void storeRegToAddress(Reg reg, Value ptr) {
        if (ptr instanceof AllocaInst) {
            if (reg.isFloat) {
                try (Reg regAddr = regAlloc.allocIntReg()) {
                    printArmInstr("add", Arrays.asList(regAddr.toString(), "sp", getImme(stackMap.get(ptr), 8)));
                    printArmInstr("vstr.32", Arrays.asList(reg.toString(), "[" + regAddr.abiName() + "]"));
                }
            } else {
                printArmInstr("str", Arrays.asList(reg.toString(), getStackOper(ptr)));
            }
        } else {
            try (Reg regPtr = loadToReg(ptr)) {
                printArmInstr("str", Arrays.asList(reg.toString(), "[" + regPtr.abiName() + "]"));
            }
        }
    }

    private static final Map<String, String> intInstr2FltInstrOpCode = new HashMap<>();
    
    static {
        intInstr2FltInstrOpCode.put("add", "vadd.f32");
        intInstr2FltInstrOpCode.put("sub", "vsub.f32");
        intInstr2FltInstrOpCode.put("mul", "vmul.f32");
        intInstr2FltInstrOpCode.put("div", "vdiv.f32");
        intInstr2FltInstrOpCode.put("cmp", "vcmp.f32");
        intInstr2FltInstrOpCode.put("mov", "vmov.f32");
        intInstr2FltInstrOpCode.put("ldr", "vldr.32");
        intInstr2FltInstrOpCode.put("str", "vstr.32");
    }

    public void printModule(Module module) {
        os.println(".data");
        for (GlobalVariable global : module.getGlobals()) {
            printGlobalData(global);
        }

        os.println(".bss");
        for (GlobalVariable global : module.getGlobals()) {
            printGlobalBss(global);
        }

        os.println(".text");
        for (Function func : module.getFunctions()) {
            os.println(".global " + func.fnName);
        }
        for (Function func : module.getFunctions()) {
            printFunc(func);
        }
    }

    public void printGlobalData(GlobalVariable global) {
        if (global.getInitializer() == null)
            return;

        os.println(global.getName() + ":");
        printConstData(global.getInitializer(), global);
    }

    private void printConstData(Constant val, GlobalVariable global) {
        assert val != null : "Invalid initializer";
        if (val instanceof ConstantArray) {
            ConstantArray arr = (ConstantArray) val;
            for (Constant elt : arr.getValues()) {
                printConstData(elt, global);
            }
            int totalCount = ((ArrayType) arr.getType()).getSize();
            // 不足的部分自动填充0
            if (totalCount > arr.getValues().size()) {
                os.println(".zero " + (4 * (totalCount - arr.getValues().size())));
            }
            os.println(".size " + global.getName() + ", " + (totalCount * 4));
        } else if (val instanceof ConstantValue) {
            ConstantValue constVal = (ConstantValue) val;
            if (constVal.isInt()) {
                os.println(".word " + constVal.getInt());
            } else {
                os.println(".word " + reinterpretFloat(constVal.getFloat()));
            }
        } else {
            throw new RuntimeException("NYI");
        }
    }

    public void printGlobalBss(GlobalVariable global) {
        if (global.getInitializer() != null) {
            return;
        }
        os.println(global.getName() + ":");
        int size = 4;
        if (global.getAllocatedType() instanceof ArrayType) {
            ArrayType arrTy = (ArrayType) global.getAllocatedType();
            assert !(arrTy.getArrayEltType() instanceof ArrayType) : "invalid";
            size = 4 * arrTy.getSize();
        }
        os.println(".skip " + size);
    }

    public void printFunc(Function function) {
        if (function.isBuiltin)
            return;

        curFunction = function;
        os.println(function.fnName + ":");

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
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                if (instr instanceof CallInst) {
                    CallInst callInst = (CallInst) instr;
                    CallInfo callInfo = arrangeCallInfo(callInst.getArgs());
                    callStackSize = Math.max(callInfo.argsOnStack.size(), callStackSize);
                }
            }
        }
        stackSize += callStackSize * 4;

        // 给本函数的寄存器参数分配 stack slot
        for (Value arg : funcCallInfo.argsInIntReg) {
            stackMap.put(arg, stackSize);
            stackSize += 4;
        }
        for (Value arg : funcCallInfo.argsInFloatReg) {
            stackMap.put(arg, stackSize);
            stackSize += 4;
        }

        // 给指令结果分配 stack slot
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                if (instr.tag == Value.Tag.Load)
                    continue;
                if (instr.tag.ordinal() >= Value.Tag.BeginBooleanOp.ordinal() &&
                    instr.tag.ordinal() <= Value.Tag.EndBooleanOp.ordinal()) {
                    // For all br use, do not allocate stack. The codegen is in BranchInst.
                    boolean allBrUse = instr.getUses().stream()
                        .allMatch(use -> use.getUser() instanceof BranchInst);
                    if (allBrUse)
                        continue;
                }
                stackMap.put(instr, stackSize);
                if (instr instanceof AllocaInst) {
                    AllocaInst alloca = (AllocaInst) instr;
                    if (alloca.getAllocatedType() instanceof ArrayType) {
                        ArrayType arrTy = (ArrayType) alloca.getAllocatedType();
                        assert !(arrTy.getArrayEltType() instanceof ArrayType) : "invalid";
                        stackSize += 4 * arrTy.getSize();
                    } else {
                        stackSize += 4;
                    }
                } else {
                    stackSize += 4;
                }
            }
        }

        printArmInstr("push", Arrays.asList("{lr}"));
        final int pushSize = 4;

        // 对齐到 8
        if ((stackSize + pushSize) % 8 != 0) {
            stackSize += 4;
        }

        printArmInstr("sub", Arrays.asList("sp", "sp", getImme(stackSize, 8)));

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
        int argsOffset = stackSize + pushSize;
        for (int i = 0; i < funcCallInfo.argsOnStack.size(); i++) {
            stackMap.put(funcCallInfo.argsOnStack.get(i), argsOffset);
            argsOffset += 4;
        }

        for (BasicBlock bb : function.getBasicBlocks()) {
            printBasicBlock(bb);
        }

        os.println();
    }

    public void printBasicBlock(BasicBlock basicBlock) {
        os.println(getLabel(basicBlock) + ":");
        for (Instruction instr : basicBlock.getInstructions()) {
            printInstr(instr);
        }
    }

    public void printInstr(Instruction instr) {
        switch (instr.tag) {
            case Alloca:
            case Load: {
                // Do nothing for naive regalloc
                break;
            }
            case GetElementPtr: {
                GetElementPtrInst gep = (GetElementPtrInst) instr;
                try (Reg regPtr = loadToReg(gep.getPointer())) {
                    if (gep.getIndex() instanceof ConstantValue) {
                        ConstantValue constIndex = (ConstantValue) gep.getIndex();
                        int offset = 4 * constIndex.getInt();
                        printArmInstr("add", Arrays.asList(regPtr.toString(), regPtr.toString(), getImme(offset, 8)));
                    } else {
                        try (Reg regOffset = loadToReg(gep.getIndex())) {
                            printArmInstr("add", Arrays.asList(regPtr.toString(), regPtr.toString(), regOffset.toString(), "lsl #2"));
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
                    try (Reg regLhs = regAlloc.claimIntReg(0);
                         Reg regRhs = regAlloc.claimIntReg(1)) {
                        loadToSpecificReg(regLhs, instr.getOperand(0));
                        loadToSpecificReg(regRhs, instr.getOperand(1));
                        printArmInstr("bl", Arrays.asList("__aeabi_idiv"));
                        storeRegToMemorySlot(regLhs, instr);
                    }
                } else {
                    try (Reg regLhs = loadToReg(instr.getOperand(0));
                         Reg regRhs = loadToReg(instr.getOperand(1))) {
                        printArmInstr("vdiv.f32", Arrays.asList(regLhs.toString(), regLhs.toString(), regRhs.toString()));
                        storeRegToMemorySlot(regLhs, instr);
                    }
                }
                break;
            }
            case Mod: {
                try (Reg regLhs = regAlloc.claimIntReg(0);
                     Reg regRhs = regAlloc.claimIntReg(1)) {
                    loadToSpecificReg(regLhs, instr.getOperand(0));
                    loadToSpecificReg(regRhs, instr.getOperand(1));
                    printArmInstr("bl", Arrays.asList("__aeabi_idivmod"));
                    printArmInstr("mov", Arrays.asList(regRhs.toString(), "r1"));
                    storeRegToMemorySlot(regRhs, instr);
                }
                break;
            }
            case IntToFloat: {
                try (Reg ireg = loadToReg(instr.getOperand(0));
                     Reg freg = regAlloc.allocFloatReg()) {
                    printArmInstr("vmov.f32", Arrays.asList(freg.toString(), ireg.toString()));
                    printArmInstr("vcvt.f32.s32", Arrays.asList(freg.toString(), freg.toString()));
                    storeRegToMemorySlot(freg, instr);
                }
                break;
            }
            case FloatToInt: {
                try (Reg freg = loadToReg(instr.getOperand(0))) {
                    printArmInstr("vcvt.s32.f32", Arrays.asList(freg.toString(), freg.toString()));
                    try (Reg ireg = regAlloc.allocIntReg()) {
                        printArmInstr("vmov.f32", Arrays.asList(ireg.toString(), freg.toString()));
                        storeRegToMemorySlot(ireg, instr);
                    }
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
                boolean allBrUse = instr.getUses().stream()
                    .allMatch(use -> use.getUser() instanceof BranchInst);
                if (allBrUse)
                    break;
                    
                BinaryInst cmpInst = (BinaryInst) instr;
                if (isNaiveLogicalOp(cmpInst)) {
                    try (Reg regLhs = loadToReg(cmpInst.getLHS())) {
                        if (cmpInst.tag == Value.Tag.Ne) {
                            printArmInstr("cmp", Arrays.asList(regLhs.toString(), "#0"));
                            printArmInstr("movne", Arrays.asList(regLhs.toString(), "#1"));
                            storeRegToMemorySlot(regLhs, instr);
                        } else if (cmpInst.tag == Value.Tag.Eq) {
                            printArmInstr("clz", Arrays.asList(regLhs.toString(), regLhs.toString()));
                            printArmInstr("lsrs", Arrays.asList(regLhs.toString(), regLhs.toString(), "#5"));
                            storeRegToMemorySlot(regLhs, instr);
                        } else {
                            throw new RuntimeException("Invalid tag");
                        }
                    }
                } else {
                    if (cmpInst.getLHS().getType().isFloatTy()) {
                        try (Reg regLhs = loadToReg(cmpInst.getLHS());
                             Reg regRhs = loadToReg(cmpInst.getRHS())) {
                            printArmInstr("cmp", Arrays.asList(regLhs.toString(), regRhs.toString()));
                            printArmInstr("vmrs", Arrays.asList("APSR_nzcv", "FPSCR"));
                            try (Reg regRes = regAlloc.allocIntReg()) {
                                printArmInstr("mov" + getCondTagStr(cmpInst.tag), Arrays.asList(regRes.toString(), "#1"));
                                printArmInstr("mov" + getCondTagStr(getNotCond(cmpInst.tag)), Arrays.asList(regRes.toString(), "#0"));
                                storeRegToMemorySlot(regLhs, instr);
                            }
                        }
                    } else {
                        try (Reg regLhs = loadToReg(cmpInst.getLHS());
                             Reg regRhs = loadToReg(cmpInst.getRHS())) {
                            printArmInstr("cmp", Arrays.asList(regLhs.toString(), regRhs.toString()));
                            printArmInstr("mov" + getCondTagStr(cmpInst.tag), Arrays.asList(regLhs.toString(), "#1"));
                            printArmInstr("mov" + getCondTagStr(getNotCond(cmpInst.tag)), Arrays.asList(regLhs.toString(), "#0"));
                            storeRegToMemorySlot(regLhs, instr);
                        }
                    }
                }
                break;
            }
            case Branch: {
                BranchInst brInst = (BranchInst) instr;
                if (!(brInst.getCondition() instanceof BinaryInst)) {
                    throw new RuntimeException("Branch condition must be a cmp op");
                }
                BinaryInst cond = (BinaryInst) brInst.getCondition();
                assert cond.isCmpOp() : "Branch condition must be a cmp op";
                String condTag = getCondTagStr(cond.tag);
                printCmpInstr(cond);
                printArmInstr("b" + condTag, Arrays.asList(getLabel(brInst.getTrueBlock())));
                printArmInstr("b", Arrays.asList(getLabel(brInst.getFalseBlock())));
                break;
            }
            case Jump:
                JumpInst jumpInst = (JumpInst) instr;
                printArmInstr("b", Arrays.asList(getLabel(jumpInst.getTargetBlock())));
                break;
            case Return: {
                ReturnInst retInst = (ReturnInst) instr;
                if (retInst.getNumOperands() == 1) {
                    try (Reg regRet = retInst.getReturnValue().getType().isIntegerTy()
                            ? regAlloc.claimIntReg(0)
                            : regAlloc.claimFloatReg(0)) {
                        assignToSpecificReg(regRet, retInst.getReturnValue());
                    }
                }
                printArmInstr("add", Arrays.asList("sp", "sp", getImme(stackSize, 8)));
                printArmInstr("pop", Arrays.asList("{lr}"));
                printArmInstr("bx", Arrays.asList("lr"));
                break;
            }
            case Call: {
                CallInst callInst = (CallInst) instr;
                List<Value> args = callInst.getArgs();
                CallInfo callInfo = arrangeCallInfo(args);

                // 认领寄存器
                Map<Integer, Reg> intRegs = new HashMap<>();
                Map<Integer, Reg> floatRegs = new HashMap<>();

                // 返回值 r0 / s0
                boolean isRetFloat = callInst.getType().isFloatTy();
                if (isRetFloat) {
                    floatRegs.put(0, regAlloc.claimFloatReg(0));
                } else {
                    intRegs.put(0, regAlloc.claimIntReg(0));
                }

                // 参数 r0-r3 / s0-s15
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
                        printArmInstr("str", Arrays.asList(regArg.toString(), "[sp, #" + argsOffset + "]"));
                    }
                    argsOffset += 4;
                }
                printArmInstr("bl", Arrays.asList(callInst.getCallee().fnName));

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
                System.err.println("NYI Instruction tag: " + instr.tag.ordinal());
                throw new RuntimeException("NYI");
        }
    }

    public void printArmInstr(String op, List<String> operands) {
        // indent for instructions
        os.print("  ");

        boolean isFloatRegName = !operands.isEmpty() && 
            !operands.get(0).equals("sp") && 
            operands.get(0).length() > 0 && 
            operands.get(0).charAt(0) == 's';

        if (intInstr2FltInstrOpCode.containsKey(op) && isFloatRegName) {
            os.print(intInstr2FltInstrOpCode.get(op));
        } else {
            os.print(op);
        }
        os.print(" ");
        for (int i = 0; i < operands.size(); ++i) {
            if (i > 0) {
                os.print(", ");
            }
            os.print(operands.get(i));
        }
        os.println();
    }

    public void printBinInstr(String op, Instruction instr) {
        try (Reg regLhs = loadToReg(instr.getOperand(0));
             Reg regRhs = loadToReg(instr.getOperand(1))) {
            printArmInstr(op, Arrays.asList(regLhs.toString(), regLhs.toString(), regRhs.toString()));
            storeRegToMemorySlot(regLhs, instr);
        }
    }

    public void printCmpInstr(BinaryInst instr) {
        try (Reg regLhs = loadToReg(instr.getOperand(0));
             Reg regRhs = loadToReg(instr.getOperand(1))) {
            printArmInstr("cmp", Arrays.asList(regLhs.toString(), regRhs.toString()));
            if (regLhs.isFloat) {
                printArmInstr("vmrs", Arrays.asList("APSR_nzcv", "FPSCR"));
            }
        }
    }

    public String getStackOper(Value val) {
        if (val instanceof LoadInst) {
            LoadInst ld = (LoadInst) val;
            val = ld.getPointer();
        }
        return "[sp, " + getImme(stackMap.get(val)) + "]";
    }

    public String getImme(int imm, int maxBits) {
        if (imm < (1 << maxBits)) {
            return "#" + imm;
        } else {
            try (Reg reg = regAlloc.allocIntReg()) {
                printArmInstr("movw", Arrays.asList(reg.toString(), "#" + (imm & 0xffff)));
                printArmInstr("movt", Arrays.asList(reg.toString(), "#" + (imm >> 16)));
                return reg.abiName();
            }
        }
    }

    public String getImme(int imm) {
        return getImme(imm, 12);
    }

    public String getImme(float imm) {
        // reinterpret and load int
        int floatBits = Float.floatToIntBits(imm);
        try (Reg regInt = regAlloc.allocIntReg()) {
            printArmInstr("movw", Arrays.asList(regInt.toString(), "#" + (floatBits & 0xffff)));
            if ((floatBits >> 16) != 0)
                printArmInstr("movt", Arrays.asList(regInt.toString(), "#" + (floatBits >> 16)));
            try (Reg regFlt = regAlloc.allocFloatReg()) {
                printArmInstr("vmov.f32", Arrays.asList(regFlt.toString(), regInt.toString()));
                return regFlt.abiName();
            }
        }
    }

    public String getLabel(BasicBlock bb) {
        if (!labelMap.containsKey(bb)) {
            labelMap.put(bb, labelMap.size());
        }
        return "." + bb.getLabel() + "_" + labelMap.get(bb);
    }
}
