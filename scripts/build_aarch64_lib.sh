#!/bin/bash

# AArch64 静态库编译脚本
# 用法: ./build_aarch64_lib.sh source1.c source2.c [更多源文件...]

if [ $# -eq 0 ]; then
    echo "用法: $0 source1.c source2.c [更多源文件...]"
    echo "示例: $0 lib1.c lib2.c"
    exit 1
fi

# 设置工具链
CC=aarch64-linux-gnu-gcc
AR=aarch64-linux-gnu-ar
RANLIB=aarch64-linux-gnu-ranlib

# 编译选项
CFLAGS="-O2 -fPIC -Wall -fno-stack-protector"

# 输出文件
LIB_NAME="mylib.a"

# 临时目录
TEMP_DIR=$(mktemp -d)
OBJ_FILES=""

echo "开始编译 AArch64 静态库..."
echo "源文件: $@"
echo "输出: $LIB_NAME"
echo "临时目录: $TEMP_DIR"

# 编译每个源文件为目标文件
for src_file in "$@"; do
    if [ ! -f "$src_file" ]; then
        echo "错误: 文件 $src_file 不存在"
        rm -rf "$TEMP_DIR"
        exit 1
    fi
    
    # 获取文件名（不含扩展名）
    basename=$(basename "$src_file" .c)
    obj_file="$TEMP_DIR/${basename}.o"
    
    echo "编译 $src_file -> $obj_file"
    $CC $CFLAGS -c "$src_file" -o "$obj_file"
    
    if [ $? -ne 0 ]; then
        echo "错误: 编译 $src_file 失败"
        rm -rf "$TEMP_DIR"
        exit 1
    fi
    
    OBJ_FILES="$OBJ_FILES $obj_file"
done

# 创建静态库
echo "创建静态库 $LIB_NAME"
$AR rcs "$LIB_NAME" $OBJ_FILES

if [ $? -ne 0 ]; then
    echo "错误: 创建静态库失败"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# 更新库索引
echo "更新库索引"
$RANLIB "$LIB_NAME"

# 清理临时文件
rm -rf "$TEMP_DIR"

# 验证生成的库
echo "验证生成的库:"
file "$LIB_NAME"
$AR -t "$LIB_NAME"

echo "成功创建 AArch64 静态库: $LIB_NAME"
