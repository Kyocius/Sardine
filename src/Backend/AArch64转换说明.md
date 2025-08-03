# ARM 32位 到 AArch64 ARM v8-A 转换说明

## 主要架构变化

### 1. 寄存器系统
**ARM 32位**:
- 通用寄存器: r0-r15 (32位)
- 浮点寄存器: s0-s31 (单精度), d0-d15 (双精度)
- 4个整数参数寄存器: r0-r3
- 16个浮点参数寄存器: s0-s15

**AArch64**:
- 通用寄存器: x0-x30 (64位), w0-w30 (32位视图)
- 浮点寄存器: s0-s31 (单精度), d0-d31 (双精度)
- 8个整数参数寄存器: x0-x7
- 8个浮点参数寄存器: d0-d7
- 特殊寄存器: x30 (lr), x31 (sp), x29 (frame pointer)

### 2. 指令集变化

#### 立即数加载
**ARM 32位**:
```armv7
movw r0, #0x1234        ; 加载低16位
movt r0, #0x5678        ; 加载高16位
```

**AArch64**:
```aarch64
movz x0, #0x1234        ; 清零并加载
movk x0, #0x5678, lsl #16  ; 保持其他位并加载
```

#### 内存操作
**ARM 32位**:
```armv7
ldr r0, [sp, #offset]
str r0, [sp, #offset]
```

**AArch64**:
```aarch64
ldr w0, [sp, #offset]   ; 32位加载
ldr x0, [sp, #offset]   ; 64位加载
str w0, [sp, #offset]   ; 32位存储
```

#### 算术运算
**ARM 32位**:
```armv7
add r0, r1, r2
bl __aeabi_idiv         ; 除法需要调用库函数
```

**AArch64**:
```aarch64
add w0, w1, w2          ; 32位加法
sdiv w0, w1, w2         ; 硬件除法指令
msub w0, w3, w2, w1     ; w0 = w1 - (w3 * w2) 用于模运算
```

#### 浮点运算
**ARM 32位**:
```armv7
vadd.f32 s0, s1, s2
vmov.f32 s0, r1
vcvt.f32.s32 s0, s0
```

**AArch64**:
```aarch64
fadd s0, s1, s2
fmov s0, w1
scvtf s0, w0           ; 整数转浮点
fcvtzs w0, s0          ; 浮点转整数
```

#### 比较和条件执行
**ARM 32位**:
```armv7
cmp r0, r1
moveq r2, #1
movne r2, #0
```

**AArch64**:
```aarch64
cmp w0, w1
cset w2, eq            ; 根据条件设置寄存器
```

#### 分支指令
**ARM 32位**:
```armv7
beq label
bne label
```

**AArch64**:
```aarch64
b.eq label
b.ne label
```

### 3. 函数调用约定

#### 参数传递
**ARM 32位**:
- 整数: r0-r3, 其余压栈
- 浮点: s0-s15, 其余压栈
- 栈对齐: 8字节

**AArch64**:
- 整数: x0-x7, 其余压栈
- 浮点: d0-d7, 其余压栈
- 栈对齐: 16字节

#### 函数序言/尾声
**ARM 32位**:
```armv7
push {lr}
sub sp, sp, #stacksize
; 函数体
add sp, sp, #stacksize
pop {lr}
bx lr
```

**AArch64**:
```aarch64
stp lr, x29, [sp, #-16]!    ; 保存链接寄存器和帧指针
mov x29, sp                  ; 设置帧指针
sub sp, sp, #stacksize       ; 分配栈空间
; 函数体
add sp, sp, #stacksize       ; 恢复栈指针
ldp lr, x29, [sp], #16       ; 恢复寄存器
ret                          ; 返回
```

### 4. 全局变量访问
**ARM 32位**:
```armv7
movw r0, #:lower16:global_var
movt r0, #:upper16:global_var
ldr r1, [r0]
```

**AArch64**:
```aarch64
adrp x0, global_var@PAGE
ldr w1, [x0, global_var@PAGEOFF]
```

### 5. 栈布局变化
**ARM 32位**: 4字节对齐，4字节栈槽
**AArch64**: 16字节对齐，8字节栈槽

### 6. 条件码处理
**ARM 32位**: 大多数指令可以条件执行
**AArch64**: 只有分支指令支持条件，使用cset/csel等指令

## 代码转换要点

1. **寄存器命名**: r0→w0/x0, s0→s0/d0
2. **指令重命名**: 移除.f32后缀，使用标准浮点指令
3. **立即数处理**: 使用movz/movk替代movw/movt
4. **除法运算**: 使用硬件sdiv替代软件调用
5. **模运算**: 使用msub指令组合实现
6. **条件执行**: 使用cset替代条件mov
7. **内存对齐**: 确保16字节栈对齐
8. **参数传递**: 支持更多寄存器参数

这个转换保持了原有的寄存器分配策略，但适配了AArch64的指令集和调用约定。
