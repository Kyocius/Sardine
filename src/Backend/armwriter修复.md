ArmWriter修复总结
🛠️ 主要修复内容：
修复全局变量和函数名的@前缀问题：

添加了cleanName()辅助方法来移除变量名前的@符号
修复了.global声明、函数标签、全局变量标签
修复了全局变量访问中的@PAGE和@PAGEOFF
修复了BasicBlock标签名
修复函数调用问题：

将callInst.getOperand(0).getName()改为callInst.getFunction().getName()
将callInst.getOperands()改为callInst.getParams()
确保函数调用使用正确的函数名而不是数字
修复大栈大小的立即数限制问题：

AArch64的add/sub指令立即数限制为4095
对于大栈大小，使用寄存器加载立即数再进行运算
处理超过65535的大立即数，使用movz+movk指令
统一名称清理：

所有IR名称（函数、变量、基本块）都通过cleanName()方法统一处理
确保汇编代码中不会出现@符号
🎯 预期解决的错误：
✅ Error: junk at end of line, first unrecognized character is '@'
✅ Error: bad expression at operand 2 -- 'adrp x1,@b@PAGE'
✅ Error: invalid expression in the address at operand 2 -- 'ldr w1,[x1,@b@PAGEOFF]'
✅ Error: immediate value must be a multiple of 4 at operand 1 -- 'bl 1'
✅ 大立即数导致的栈操作错误