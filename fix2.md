# ARM后端 AArch64兼容性修正汇总

**分析日期**: 2025年8月2日

---

## 📁 已修正文件列表

### 1. ArmImm.java - 立即数操作数类
**修改内容**:
- 数据类型升级: `int value` → `long value` (支持64位)
- 新增AArch64立即数编码检查方法:
  - `isValidFor12BitImm()` - 12位无符号立即数
  - `isValidFor12BitImmShifted()` - 12位+移位立即数  
  - `isValidForLogicalImm()` - 逻辑指令立即数
  - `isValidFor16BitImm()` - 16位立即数
- 保持向后兼容: 添加`getIntValue()`和`int`构造函数

### 2. ArmLabel.java - 标签操作数类
**修改内容**:
- 地址计算方式升级: ARMv7 16位编码 → AArch64 页地址+偏移
- 新增AArch64地址计算方法:
  - `page()` - 页地址引用 (`:pg_hi21:`)
  - `pageoff()` - 页内偏移 (`:lo12:`)
- 字段不可变性: `private String name` → `private final String name`
- 标记过时方法: `@Deprecated` `lo()` 和 `hi()`

### 3. ArmOperand.java - 操作数基类
**修改内容**:
- 字段不可变性: `private LinkedHashSet<...> users` → `private final LinkedHashSet<...> users`
- 完善JavaDoc文档: 明确标注为"架构无关的操作数基类"
- **AArch64兼容性**: ✅ 完全兼容，该类处理操作数使用关系，与具体指令架构无关

### 4. ArmStackFixer.java - 栈偏移修正类
**修改内容**:
- **关键兼容性修正**: `getValue()`返回类型 `int` → `long` (与ArmImm父类保持一致)
- **AArch64栈对齐**: 确保16字节栈对齐要求 `(stackSize + 15) & ~15`
- 代码优化: 简化`toString()`方法，移除不必要的`Integer.toString()`调用
- **栈帧计算**: 正确处理动态栈偏移和参数偏移的组合

### 5. ArmVirReg.java - 虚拟寄存器类
**修改内容**:
- **清理错误导入**: 移除RISC-V相关的`import Backend.Riscv.*`语句
- **字段不可变性**: 所有字段声明为`final` (`name`, `regType`, `index`)
- **构造函数修正**: 正确调用`super()`父类构造函数
- **AArch64寄存器类型**: 明确区分整数(`intType`)和浮点(`floatType`)寄存器
- **虚拟寄存器命名**: 生成`%int`前缀(整数)和`%float`前缀(浮点)的虚拟寄存器名

---

## 🔧 核心技术改进

### AArch64 vs ARMv7 对比
| 方面 | ARMv7 | AArch64 |
|------|-------|---------|
| 立即数精度 | 32位 (int) | 64位 (long) |
| 地址编码 | 16位×2片段 | 页地址(21位)+偏移(12位) |
| 重定位标记 | `:lower16:`, `:upper16:` | `:pg_hi21:`, `:lo12:` |
| 指令序列 | MOVW + MOVT | ADRP + ADD |
| 栈对齐 | 8字节对齐 | 16字节对齐 |

### AArch64栈管理特性
```assembly
# AArch64栈帧对齐示例
# 假设局部变量需要12字节
# 对齐到16字节: (12 + 15) & ~15 = 16
sub sp, sp, #16          # 分配16字节对齐的栈空间

# 栈偏移计算
ldr x0, [sp, #offset]    # 使用ArmStackFixer计算的偏移
```

### 典型代码示例
```assembly
# AArch64地址计算
adrp x0, global_var          # 加载页地址
add  x0, x0, :lo12:global_var # 计算最终地址

# AArch64立即数使用  
add x0, x1, #42              # 12位立即数
add x0, x1, #4096            # 12位+移位
```

---

## ✅ 修正状态
- **编译状态**: 通过编译，无警告或错误
- **兼容性**: 完全向后兼容
- **AArch64支持**: 符合64位架构要求
- **栈对齐**: 符合AArch64的16字节对齐规范

---

## 🔮 待添加文件
_(预留空间用于后续文件修改记录)_
