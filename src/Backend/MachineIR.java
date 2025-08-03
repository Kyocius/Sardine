package Backend;

import IR.GlobalVariable;
import java.util.*;

// 枚举定义
enum AsmPredicate {
    AL, // All the time
    EQ,
    NE,
    LT,
    LE,
    GT,
    GE
}

enum AsmType {
    I32,
    F32
}

// 抽象基类
abstract class AsmValue {
    enum Tag {
        VREG,
        PREG,
        IMM
    }

    protected Tag tag;

    public AsmValue(Tag tag) {
        this.tag = tag;
    }

    public Tag getTag() {
        return tag;
    }
}

// 寄存器基类
abstract class AsmReg extends AsmValue {
    protected AsmType type;
    protected int id;

    public AsmReg(Tag tag, AsmType type, int id) {
        super(tag);
        this.type = type;
        this.id = id;
    }

    public AsmType getType() {
        return type;
    }

    public int getId() {
        return id;
    }

    public abstract String abiName();

    // AArch64: 64位寄存器名称
    public abstract String abiName64();

    // AArch64: 32位寄存器名称
    public abstract String abiName32();

    public static VReg makeVReg(AsmType type) {
        return new VReg(type, generateVRegId());
    }

    public static PReg makePReg(AsmType type, int id) {
        return new PReg(type, id);
    }

    // AArch64特殊寄存器: x30 (lr), x31 (sp), x29 (frame pointer)
    public static PReg lr() { return makePReg(AsmType.I32, 30); }  // x30
    public static PReg sp() { return makePReg(AsmType.I32, 31); }  // x31
    public static PReg fp() { return makePReg(AsmType.I32, 29); }  // x29 frame pointer

    private static int vregCounter = 0;
    private static int generateVRegId() {
        return vregCounter++;
    }
}

// 虚拟寄存器
class VReg extends AsmReg {
    public VReg(AsmType type, int id) {
        super(Tag.VREG, type, id);
    }

    @Override
    public String abiName() {
        return abiName64(); // 默认使用64位命名
    }

    @Override
    public String abiName64() {
        if (type == AsmType.F32) {
            return "%d" + id; // AArch64: 虚拟浮点寄存器使用双精度命名
        } else {
            return "%x" + id; // AArch64: 虚拟整数寄存器使用64位命名
        }
    }

    @Override
    public String abiName32() {
        if (type == AsmType.F32) {
            return "%s" + id; // AArch64: 单精度浮点
        } else {
            return "%w" + id; // AArch64: 32位整数视图
        }
    }
}

// 物理寄存器
class PReg extends AsmReg {
    public PReg(AsmType type, int id) {
        super(Tag.PREG, type, id);
    }

    @Override
    public String abiName() {
        return abiName64(); // 默认使用64位命名
    }

    @Override
    public String abiName64() {
        if (id < 0 || id > 31) {
            throw new AssertionError("Invalid reg!");
        }

        if (type == AsmType.F32) {
            return "d" + id; // AArch64: d0-d31 双精度浮点
        } else {
            switch (id) {
                case 29: return "x29"; // frame pointer
                case 30: return "lr";  // link register
                case 31: return "sp";  // stack pointer
                default: return "x" + id; // AArch64: x0-x30
            }
        }
    }

    @Override
    public String abiName32() {
        if (id < 0 || id > 31) {
            throw new AssertionError("Invalid reg!");
        }

        if (type == AsmType.F32) {
            return "s" + id; // AArch64: s0-s31 单精度浮点
        } else {
            switch (id) {
                case 29: return "w29"; // frame pointer 32位视图
                case 30: return "lr";  // link register (没有32位视图)
                case 31: return "sp";  // stack pointer (没有32位视图)
                default: return "w" + id; // AArch64: w0-w30
            }
        }
    }
}

// 立即数
class AsmImm extends AsmValue {
    private long hexValue;

    public AsmImm(long hexValue) {
        super(Tag.IMM);
        this.hexValue = hexValue;
    }

    public long getHexValue() {
        return hexValue;
    }

    public String toAsm() {
        return "#" + hexValue;
    }
}

// 指令基类
abstract class AsmInst {
    enum Tag {
        ADD, SUB, MUL, DIV, MOD, LSL, LSR, ASR,
        RSB, SMMUL, AND, CMP, BRANCH, JUMP, RETURN,
        MOVE, LOAD, STORE, CALL, CVT, LOADGLOBAL, STRING
    }

    enum ShiftType {
        LSL, LSR, ASR
    }

    protected Tag tag;
    public AsmInst next;
    public AsmInst prev;

    public AsmInst(Tag tag) {
        this.tag = tag;
    }

    public Tag getTag() {
        return tag;
    }

    public abstract List<AsmValue> getDefs();
    public abstract List<AsmValue> getUses();
}

// 标签类
class AsmLabel {
    public String name;
    public List<AsmLabel> preds = new ArrayList<>();
    public List<AsmLabel> succs = new ArrayList<>();
    public AsmInst head;
    public AsmInst tail;
    public AsmInst terminatorBegin;

    public AsmLabel(String name) {
        this.name = name;
    }

    public void addInst(AsmInst inst) {
        if (head == null) {
            head = tail = inst;
        } else {
            tail.next = inst;
            inst.prev = tail;
            tail = inst;
        }
    }
}

// 二元指令
class AsmBinaryInst extends AsmInst {
    public AsmValue dst;
    public AsmValue lhs;
    public AsmValue rhs;
    public int shift = 0;
    public ShiftType shiftTag = ShiftType.LSL;

    public AsmBinaryInst(Tag tag) {
        super(tag);
    }

    @Override
    public List<AsmValue> getDefs() {
        return dst != null ? Arrays.asList(dst) : new ArrayList<>();
    }

    @Override
    public List<AsmValue> getUses() {
        List<AsmValue> uses = new ArrayList<>();
        if (lhs != null) uses.add(lhs);
        if (rhs != null) uses.add(rhs);
        return uses;
    }
}

// 比较指令
class AsmCompareInst extends AsmInst {
    public AsmValue lhs;
    public AsmValue rhs;

    public AsmCompareInst() {
        super(Tag.CMP);
    }

    @Override
    public List<AsmValue> getDefs() {
        return new ArrayList<>();
    }

    @Override
    public List<AsmValue> getUses() {
        List<AsmValue> uses = new ArrayList<>();
        if (lhs != null) uses.add(lhs);
        if (rhs != null) uses.add(rhs);
        return uses;
    }
}

// 分支指令
class AsmBranchInst extends AsmInst {
    public AsmPredicate pred = AsmPredicate.AL;
    public AsmLabel trueTarget;
    public AsmLabel falseTarget;

    public AsmBranchInst() {
        super(Tag.BRANCH);
    }

    @Override
    public List<AsmValue> getDefs() {
        return new ArrayList<>();
    }

    @Override
    public List<AsmValue> getUses() {
        return new ArrayList<>();
    }
}

// 跳转指令
class AsmJumpInst extends AsmInst {
    public AsmLabel target;

    public AsmJumpInst(AsmLabel target) {
        super(Tag.JUMP);
        this.target = target;
    }

    @Override
    public List<AsmValue> getDefs() {
        return new ArrayList<>();
    }

    @Override
    public List<AsmValue> getUses() {
        return new ArrayList<>();
    }
}

// 返回指令
class AsmReturnInst extends AsmInst {
    public AsmReturnInst() {
        super(Tag.RETURN);
    }

    @Override
    public List<AsmValue> getDefs() {
        return new ArrayList<>();
    }

    @Override
    public List<AsmValue> getUses() {
        return new ArrayList<>();
    }
}

// 移动指令
class AsmMoveInst extends AsmInst {
    public AsmValue src;
    public AsmValue dst;
    public AsmPredicate pred = AsmPredicate.AL;

    public AsmMoveInst() {
        super(Tag.MOVE);
    }

    @Override
    public List<AsmValue> getDefs() {
        return dst != null ? Arrays.asList(dst) : new ArrayList<>();
    }

    @Override
    public List<AsmValue> getUses() {
        return src != null ? Arrays.asList(src) : new ArrayList<>();
    }
}

// 访问指令基类
abstract class AsmAccess extends AsmInst {
    public AsmValue addr;
    public AsmValue offset;
    public int shift = 0;
    public AsmPredicate pred = AsmPredicate.AL;

    public AsmAccess(Tag tag) {
        super(tag);
    }
}

// 加载指令
class AsmLoadInst extends AsmAccess {
    public AsmValue dst;

    public AsmLoadInst() {
        super(Tag.LOAD);
    }

    @Override
    public List<AsmValue> getDefs() {
        return dst != null ? Arrays.asList(dst) : new ArrayList<>();
    }

    @Override
    public List<AsmValue> getUses() {
        List<AsmValue> uses = new ArrayList<>();
        if (addr != null) uses.add(addr);
        if (offset != null) uses.add(offset);
        return uses;
    }
}

// 存储指令
class AsmStoreInst extends AsmAccess {
    public AsmValue src;

    public AsmStoreInst() {
        super(Tag.STORE);
    }

    @Override
    public List<AsmValue> getDefs() {
        return new ArrayList<>();
    }

    @Override
    public List<AsmValue> getUses() {
        List<AsmValue> uses = new ArrayList<>();
        if (src != null) uses.add(src);
        if (addr != null) uses.add(addr);
        if (offset != null) uses.add(offset);
        return uses;
    }
}

// 调用指令
class AsmCallInst extends AsmInst {
    public String callee;
    public Set<PReg> callDefs = new HashSet<>();
    public Set<PReg> callUses = new HashSet<>();

    public AsmCallInst(String callee) {
        super(Tag.CALL);
        this.callee = callee;
    }

    @Override
    public List<AsmValue> getDefs() {
        return new ArrayList<>();
    }

    @Override
    public List<AsmValue> getUses() {
        return new ArrayList<>();
    }
}

// 全局变量加载指令
class AsmLoadGlobalInst extends AsmInst {
    public AsmValue dst;
    public GlobalVariable var;

    public AsmLoadGlobalInst() {
        super(Tag.LOADGLOBAL);
    }

    @Override
    public List<AsmValue> getDefs() {
        return dst != null ? Arrays.asList(dst) : new ArrayList<>();
    }

    @Override
    public List<AsmValue> getUses() {
        return new ArrayList<>();
    }
}

// 转换指令
class AsmConvertInst extends AsmInst {
    public enum CvtType { F2I, I2F }

    public AsmValue src;
    public AsmValue dst;
    public CvtType type;

    public AsmConvertInst(CvtType type) {
        super(Tag.CVT);
        this.type = type;
    }

    @Override
    public List<AsmValue> getDefs() {
        return dst != null ? Arrays.asList(dst) : new ArrayList<>();
    }

    @Override
    public List<AsmValue> getUses() {
        return src != null ? Arrays.asList(src) : new ArrayList<>();
    }
}

// 函数类
class AsmFunc {
    public String name;
    public List<AsmLabel> labels = new ArrayList<>();
    public boolean isBuiltin;

    // 其他状态
    public int stackSize = 0;
    public Set<PReg> usedCalleeSavedRegs = new HashSet<>();
    public List<AsmMoveInst> stackArgOffsets = new ArrayList<>();

    public AsmFunc(String name) {
        this.name = name;
    }
}

// 模块类
class AsmModule {
    public List<AsmFunc> funcs = new ArrayList<>();
    public List<GlobalVariable> globals = new ArrayList<>();

    public AsmFunc getFunction(String name) {
        for (AsmFunc func : funcs) {
            if (func.name.equals(name)) {
                return func;
            }
        }
        return null;
    }
}

public class MachineIR {
    // 这个类作为容器类，包含所有机器IR相关的定义
}
