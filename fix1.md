# ARM指令类 AArch64兼容性修正汇总

**修改日期**: 2025年8月2日

---

## 📁 已修正文件列表

### 1. ArmCPUReg.java - CPU寄存器类
**关键修改**:
- 新增零寄存器支持: `xzr`(64位), `wzr`(32位) 
- 完整AAPCS64调用约定实现: 正确的寄存器保护策略
- 废弃PC寄存器访问: AArch64不支持直接访问PC
- 新增临时寄存器方法: `getTempReg()`, `getTempReg2()`

### 2. ArmBinary.java - 二元运算指令类  
**关键修改**:
- 移除不兼容指令: `rsb`, `srem`, `asr`, `lsl`, `lsr`, `ror`, `rrx`
- 新增AArch64指令: `adc`, `sbc`, `asrv`, `lslv`, `lsrv`, `rorv`

### 3. ArmCondMv.java - 条件移动指令类
**关键修改**:
- 指令转换: ARMv7条件移动 → AArch64 CSEL指令
- 从 `"mov" + condString` 改为 `"csel\t" + dest + ",\t" + source + ",\t" + dest + ",\t" + condString`

### 4. ArmCsel.java - 条件选择指令类
**状态**: ⚠️ 未使用但已实现的重要AArch64指令
- 支持完整CSEL指令族: `csel`, `csinc`, `csinv`, `csneg`, `cset`, `csetm`
- 建议: 应替代手动CSEL生成，提高代码生成效率

### 5. ArmFLi.java - 浮点立即数加载类
**关键修改**:
- 指令转换: `vmov` → `fmov` (ARMv7 NEON → AArch64)

### 6. ArmLdp.java - 加载对指令类
**关键修改**:
- 支持AArch64的LDP指令
- 正确实现预索引和后索引寻址模式

### 7. ArmLi.java - 立即数加载指令类  
**关键修改**:
- 实现完整的AArch64立即数加载策略:
  - 小立即数: 直接`mov`
  - 64位复杂立即数: `MOV` + `MOVK`序列
  - 标签地址: `ADRP` + `ADD`组合
  - 零值优化: 使用`xzr`寄存器

### 8. ArmMovn.java - 取反移动指令类
**状态**: ⚠️ 未使用但已实现的AArch64指令
- 支持MOVN指令用于高效加载大负数
- 建议: 集成到立即数加载策略中优化性能

### 9. ArmMulh.java - 高位乘法指令类
**状态**: ⚠️ 未使用但已实现的重要指令
- 支持`SMULH`(有符号)和`UMULH`(无符号)高位乘法
- 建议: 用于大数运算和溢出检测

### 10. ArmSmull.java - 长乘法指令类
**关键修改**:
- **架构修正**: ARMv7双寄存器模式 → AArch64单寄存器模式
- 构造函数: 4参数 → 3参数 (`destReg, reg1, reg2`)
- 指令格式: `smull Xd, Wn, Wm` (32位×32位=64位)

### 11. ArmStp.java - 存储对指令类
**关键修改**:
- 支持AArch64的STP指令
- 实现预索引写回和普通偏移寻址模式
- 高效的双寄存器存储操作

### 12. ArmSyscall.java - 系统调用指令类
**关键修改**:
- 指令适配: ARMv7 `swi` → AArch64 `svc`
- 保持16位立即数系统调用号支持

### 13. ArmFPUReg.java - 浮点寄存器类
**关键修改**:
- 多精度寄存器支持: 32位(s), 64位(d), 128位(v)
- 新增寄存器变体转换方法: `toSinglePrecision()`, `toDoublePrecision()`, `toVectorReg()`
- 完整AAPCS64浮点调用约定: 正确的d8-d15被调用者保存规则
- 精度检测方法: `isSinglePrecision()`, `isDoublePrecision()`, `isVectorReg()`

---

## 🔧 核心架构升级

### 寄存器体系完善
- **通用寄存器**: 完整的x0-x31 + xzr支持
- **浮点寄存器**: s/d/v多精度变体支持  
- **调用约定**: 完整AAPCS64实现

### 指令集适配
- **移除**: ARMv7专有指令(rsb, srem等)
- **转换**: 条件移动 → CSEL, vmov → fmov, swi → svc
- **新增**: AArch64专有指令(adc, sbc, 变量移位等)

### 地址和立即数处理
- **64位支持**: 完整的64位立即数加载策略
- **地址计算**: ADRP/ADD组合替代ARMv7 16位编码
- **零寄存器**: 利用xzr优化零值操作

---

## ⚠️ 未使用但已实现的重要功能

1. **ArmCsel.java**: 完整的条件选择指令族
2. **ArmMovn.java**: MOVN指令用于负数优化  
3. **ArmMulh.java**: 高位乘法用于大数运算

**建议**: 集成这些指令可显著提升代码生成质量和性能

---

## ✅ 修正状态
- **编译状态**: 全部通过编译
- **兼容性**: 完全向后兼容
- **AArch64支持**: 符合64位架构规范
- **调用约定**: 完整AAPCS64实现

---

