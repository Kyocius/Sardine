package Backend;

import java.io.PrintStream;
import java.util.*;

public class CodeWriter {
    private PrintStream os;

    // 名称管理器类
    static class NameManager {
        private Map<AsmInst, String> nameMap = new HashMap<>();
        private int counter = 0; // 简单的计数器命名

        public String getName(AsmInst inst) {
            return nameMap.computeIfAbsent(inst, k -> "inst" + (counter++));
        }
    }

    private NameManager nameManager = new NameManager();

    public CodeWriter(PrintStream os) {
        this.os = os;
    }

    // 打印AArch64指令的辅助方法
    public void printAArch64Instr(String op, List<String> operands) {
        // 指令缩进
        os.print("  ");
        os.print(op);

        if (!operands.isEmpty()) {
            os.print(" ");
            for (int i = 0; i < operands.size(); i++) {
                if (i > 0) {
                    os.print(", ");
                }
                os.print(operands.get(i));
            }
        }
        os.println();
    }

    // 打印模块
    public void printModule(AsmModule module) {
        // AArch64汇编文件头部
        os.println(".arch armv8-a");
        os.println();

        os.println(".data");
        for (GlobalVariable global : module.globals) {
            printGlobal(global);
        }
        os.println();

        os.println(".text");
        for (AsmFunc func : module.funcs) {
            if (!func.isBuiltin) {
                os.println(".global " + func.name);
            }
        }
        os.println();

        for (AsmFunc func : module.funcs) {
            printFunc(func);
            os.println();
        }
    }

    // 打印全局变量
    public void printGlobal(GlobalVariable global) {
        os.println(".global " + global.getName());
        os.println(global.getName() + ":");
        // 简化的全局变量初始化，实际应该根据类型处理
        os.println("  .word 0");
    }

    // 打印函数
    public void printFunc(AsmFunc func) {
        if (func.isBuiltin) {
            return;
        } else {
            os.println(func.name + ":");
            // AArch64函数序言
            printAArch64Instr("stp", Arrays.asList("x29", "x30", "[sp, #-16]!"));
            printAArch64Instr("mov", Arrays.asList("x29", "sp"));

            for (AsmLabel label : func.labels) {
                printLabel(label);
            }

            // AArch64函数尾声（如果没有显式返回指令）
            printAArch64Instr("ldp", Arrays.asList("x29", "x30", "[sp], #16"));
            printAArch64Instr("ret", Arrays.asList());
        }
    }

    // 打印标签
    public void printLabel(AsmLabel label) {
        os.println(label.name + ":");
        for (AsmInst inst = label.head; inst != null; inst = inst.next) {
            printInst(inst);
        }
    }

    // 将AsmValue转换为AArch64字符串表示
    public String toAArch64String(AsmValue value, boolean use32bit) {
        if (value instanceof VReg) {
            VReg vreg = (VReg) value;
            return use32bit ? vreg.abiName32() : vreg.abiName64();
        } else if (value instanceof PReg) {
            PReg preg = (PReg) value;
            return use32bit ? preg.abiName32() : preg.abiName64();
        } else if (value instanceof AsmImm) {
            AsmImm imm = (AsmImm) value;
            return "#" + imm.getHexValue();
        } else {
            throw new RuntimeException("Invalid asm value");
        }
    }

    // 打印指令
    public void printInst(AsmInst inst) {
        // AArch64指令映射
        String[] aarch64Tags = {
            "add", "sub", "mul", "sdiv", "msub", "lsl", "lsr", "asr",
            "sub", "smull", "and", "cmp", "b", "b", "ret", "mov",
            "ldr", "str", "bl", "scvtf", "adrp", "string"
        };

        String op = aarch64Tags[inst.getTag().ordinal()];

        if (inst instanceof AsmBinaryInst) {
            AsmBinaryInst binInst = (AsmBinaryInst) inst;
            boolean isFloat = (binInst.dst instanceof AsmReg) &&
                             ((AsmReg)binInst.dst).getType() == AsmType.F32;

            if (isFloat) {
                // 浮点运算使用AArch64浮点指令
                String floatOp = getFLoatOp(op);
                printAArch64Instr(floatOp, Arrays.asList(
                    toAArch64String(binInst.dst, true),  // 使用s寄存器
                    toAArch64String(binInst.lhs, true),
                    toAArch64String(binInst.rhs, true)
                ));
            } else {
                // 整数运算
                if (inst.getTag() == AsmInst.Tag.MOD) {
                    // AArch64模运算需要组合指令：sdiv + msub
                    String tempReg = "w9"; // 临时寄存器
                    printAArch64Instr("sdiv", Arrays.asList(
                        tempReg,
                        toAArch64String(binInst.lhs, true),
                        toAArch64String(binInst.rhs, true)
                    ));
                    printAArch64Instr("msub", Arrays.asList(
                        toAArch64String(binInst.dst, true),
                        tempReg,
                        toAArch64String(binInst.rhs, true),
                        toAArch64String(binInst.lhs, true)
                    ));
                } else {
                    printAArch64Instr(op, Arrays.asList(
                        toAArch64String(binInst.dst, true),  // 使用w寄存器
                        toAArch64String(binInst.lhs, true),
                        toAArch64String(binInst.rhs, true)
                    ));
                }
            }

        } else if (inst instanceof AsmCompareInst) {
            AsmCompareInst cmpInst = (AsmCompareInst) inst;
            boolean isFloat = (cmpInst.lhs instanceof AsmReg) &&
                             ((AsmReg)cmpInst.lhs).getType() == AsmType.F32;

            String cmpOp = isFloat ? "fcmp" : "cmp";
            printAArch64Instr(cmpOp, Arrays.asList(
                toAArch64String(cmpInst.lhs, true),
                toAArch64String(cmpInst.rhs, true)
            ));

        } else if (inst instanceof AsmBranchInst) {
            AsmBranchInst branchInst = (AsmBranchInst) inst;
            String[] aarch64CondTags = {"", "eq", "ne", "lt", "le", "gt", "ge"};

            if (branchInst.pred != AsmPredicate.AL) {
                String cond = "b." + aarch64CondTags[branchInst.pred.ordinal()];
                printAArch64Instr(cond, Arrays.asList(branchInst.trueTarget.name));
            }
            if (branchInst.falseTarget != null) {
                printAArch64Instr("b", Arrays.asList(branchInst.falseTarget.name));
            }

        } else if (inst instanceof AsmJumpInst) {
            AsmJumpInst jumpInst = (AsmJumpInst) inst;
            printAArch64Instr("b", Arrays.asList(jumpInst.target.name));

        } else if (inst instanceof AsmReturnInst) {
            printAArch64Instr("ldp", Arrays.asList("x29", "x30", "[sp], #16"));
            printAArch64Instr("ret", Arrays.asList());

        } else if (inst instanceof AsmMoveInst) {
            AsmMoveInst moveInst = (AsmMoveInst) inst;
            boolean isFloat = (moveInst.dst instanceof AsmReg) &&
                             ((AsmReg)moveInst.dst).getType() == AsmType.F32;

            String moveOp = isFloat ? "fmov" : "mov";
            printAArch64Instr(moveOp, Arrays.asList(
                toAArch64String(moveInst.dst, true),
                toAArch64String(moveInst.src, true)
            ));

        } else if (inst instanceof AsmLoadInst) {
            AsmLoadInst loadInst = (AsmLoadInst) inst;
            printAArch64Instr("ldr", Arrays.asList(
                toAArch64String(loadInst.dst, true),
                "[" + toAArch64String(loadInst.addr, false) + "]"
            ));

        } else if (inst instanceof AsmStoreInst) {
            AsmStoreInst storeInst = (AsmStoreInst) inst;
            printAArch64Instr("str", Arrays.asList(
                toAArch64String(storeInst.src, true),
                "[" + toAArch64String(storeInst.addr, false) + "]"
            ));

        } else if (inst instanceof AsmCallInst) {
            AsmCallInst callInst = (AsmCallInst) inst;
            printAArch64Instr("bl", Arrays.asList(callInst.callee));

        } else if (inst instanceof AsmConvertInst) {
            AsmConvertInst cvtInst = (AsmConvertInst) inst;
            if (cvtInst.type == AsmConvertInst.CvtType.F2I) {
                // AArch64: 浮点转整数
                printAArch64Instr("fcvtzs", Arrays.asList(
                    toAArch64String(cvtInst.dst, true),
                    toAArch64String(cvtInst.src, true)
                ));
            } else if (cvtInst.type == AsmConvertInst.CvtType.I2F) {
                // AArch64: 整数转浮点
                printAArch64Instr("scvtf", Arrays.asList(
                    toAArch64String(cvtInst.dst, true),
                    toAArch64String(cvtInst.src, true)
                ));
            }

        } else if (inst instanceof AsmLoadGlobalInst) {
            AsmLoadGlobalInst loadGlobalInst = (AsmLoadGlobalInst) inst;
            // AArch64全局变量加载
            String varName = loadGlobalInst.var.getName();
            printAArch64Instr("adrp", Arrays.asList(
                toAArch64String(loadGlobalInst.dst, false),
                varName + "@PAGE"
            ));
            printAArch64Instr("add", Arrays.asList(
                toAArch64String(loadGlobalInst.dst, false),
                toAArch64String(loadGlobalInst.dst, false),
                varName + "@PAGEOFF"
            ));

        } else {
            os.println("  // Unsupported instruction: " + inst.getTag());
        }
    }

    // 获取浮点运算对应的AArch64指令
    private String getFLoatOp(String intOp) {
        switch (intOp) {
            case "add": return "fadd";
            case "sub": return "fsub";
            case "mul": return "fmul";
            case "sdiv": return "fdiv";
            case "cmp": return "fcmp";
            case "mov": return "fmov";
            default: return intOp;
        }
    }
}
