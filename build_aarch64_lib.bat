@echo off
REM AArch64 静态库编译脚本 (Windows版本)
REM 用法: build_aarch64_lib.bat source1.c source2.c [更多源文件...]

if "%~1"=="" (
    echo 用法: %0 source1.c source2.c [更多源文件...]
    echo 示例: %0 lib1.c lib2.c
    exit /b 1
)

echo 正在调用 WSL 编译 AArch64 静态库...

REM 将所有参数传递给 WSL 中的脚本
wsl bash -c "cd /mnt/c/Users/kyoci/Desktop/beihang && chmod +x build_aarch64_lib.sh && ./build_aarch64_lib.sh %*"

if %errorlevel% neq 0 (
    echo 编译失败
    exit /b 1
)

echo 编译完成！静态库 mylib.a 已生成在根目录下。
