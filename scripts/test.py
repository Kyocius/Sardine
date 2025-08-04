#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QEMU ARMv8-A 功能测试脚本
适用于Windows环境，QEMU运行在WSL中
"""

import os
import sys
import subprocess
import time
import json
from pathlib import Path
import tempfile


class QEMUARMv8TestRunner:
    def __init__(self):
        # 项目根目录
        self.project_root = Path(__file__).parent.parent

        # 测试用例目录
        self.testcases_dir = self.project_root / "testcases" / "single"
        # self.testcases_dir = self.project_root / "testcases" / "functional"

        # 结果输出目录
        # self.results_dir = self.project_root / "scripts" / "results"
        self.results_dir = self.project_root / "results"
        self.results_dir.mkdir(exist_ok=True)

        # Java编译器classpath (Windows使用分号分隔)
        self.java_classpath = "out"

        # 超时设置 (秒)
        self.timeout = 30

        # 统计信息
        self.total_tests = 0
        self.passed_tests = 0
        self.failed_tests = 0
        self.test_results = []

        # QEMU和交叉编译工具链设置
        self.qemu_cmd = "qemu-aarch64-static"  # 用户模式QEMU，更简单
        self.cross_as = "aarch64-linux-gnu-as"
        self.cross_gcc = "aarch64-linux-gnu-gcc"  # 使用GCC进行链接

        # ARM Cortex-A53 MPCore 配置
        self.qemu_cpu = "cortex-a53"

    def log(self, message, level="INFO"):
        """日志输出"""
        timestamp = time.strftime("%H:%M:%S")
        print(f"[{timestamp}] {level}: {message}")

    def check_dependencies(self):
        """检查依赖工具是否可用"""
        self.log("检查依赖工具...")

        # 检查Java
        try:
            subprocess.run(
                ["java", "-version"],
                capture_output=True,
                check=True,
                encoding="utf-8",
                errors="replace",
            )
            self.log("Java: 可用")
        except (subprocess.CalledProcessError, FileNotFoundError):
            self.log("Java未找到或不可用", "ERROR")
            return False

        # 检查libsysy_arm.a静态库
        libsysy_arm_path = self.project_root / "libsysy_arm.a"
        if libsysy_arm_path.exists():
            self.log("libsysy_arm.a: 找到")
        else:
            self.log(
                "libsysy_arm.a: 未找到，请确保libsysy_arm.a在项目根目录下", "ERROR"
            )
            return False

        # 检查WSL中的QEMU和交叉编译工具链
        wsl_commands = [
            f"which {self.qemu_cmd}",
            f"which {self.cross_as}",
            f"which {self.cross_gcc}",
        ]

        for cmd in wsl_commands:
            try:
                result = subprocess.run(
                    ["wsl", "bash", "-c", cmd],
                    capture_output=True,
                    text=True,
                    timeout=10,
                    encoding="utf-8",
                    errors="replace",
                )
                if result.returncode == 0:
                    tool_name = cmd.split()[-1]
                    self.log(f"{tool_name}: 可用")
                else:
                    tool_name = cmd.split()[-1]
                    self.log(f"{tool_name}: 未找到", "ERROR")
                    return False
            except (
                subprocess.CalledProcessError,
                subprocess.TimeoutExpired,
                FileNotFoundError,
            ):
                self.log("WSL或交叉编译工具链不可用", "ERROR")
                return False

        return True

    def find_test_files(self):
        """查找所有.sy测试文件"""
        self.log(f"在 {self.testcases_dir} 中查找测试文件...")

        test_files = list(self.testcases_dir.glob("*.sy"))
        test_files.sort()

        self.log(f"找到 {len(test_files)} 个测试文件")
        return test_files

    def compile_sy_to_asm(self, sy_file, asm_file):
        """编译.sy文件到ARM汇编"""
        cmd = [
            "java",
            "-classpath",
            self.java_classpath,
            "Compiler",
            str(sy_file),
            str(asm_file),
            "-arm",
        ]

        try:
            result = subprocess.run(
                cmd,
                cwd=self.project_root,
                capture_output=True,
                text=True,
                timeout=self.timeout,
                encoding="utf-8",
                errors="replace",
            )
            return result.returncode == 0, result.stderr
        except subprocess.TimeoutExpired:
            return False, "编译超时"
        except Exception as e:
            return False, f"编译错误: {str(e)}"

    def assemble_and_link(self, asm_file, exe_file):
        """汇编和链接生成可执行文件"""
        # 转换为WSL路径
        wsl_project_root = self.windows_to_wsl_path(str(self.project_root))
        wsl_asm_file = self.windows_to_wsl_path(str(asm_file))
        wsl_exe_file = self.windows_to_wsl_path(str(exe_file))

        # libsysy_arm.a静态库路径
        wsl_libsysy_arm = self.windows_to_wsl_path(
            str(self.project_root / "libsysy_arm.a")
        )

        try:
            # 汇编主程序
            obj_file = asm_file.with_suffix(".o")
            wsl_obj_file = self.windows_to_wsl_path(str(obj_file))

            as_cmd = f"cd {wsl_project_root} && {self.cross_as} -o {wsl_obj_file} {wsl_asm_file}"
            result = subprocess.run(
                ["wsl", "bash", "-c", as_cmd],
                capture_output=True,
                text=True,
                timeout=self.timeout,
                encoding="utf-8",
                errors="replace",
            )

            if result.returncode != 0:
                return False, f"汇编失败: {result.stderr}"

            # 链接，使用GCC和libsysy_arm.a静态库
            ld_cmd = f"cd {wsl_project_root} && {self.cross_gcc} -static -o {wsl_exe_file} {wsl_obj_file} {wsl_libsysy_arm}"
            result = subprocess.run(
                ["wsl", "bash", "-c", ld_cmd],
                capture_output=True,
                text=True,
                timeout=self.timeout,
                encoding="utf-8",
                errors="replace",
            )

            # 清理临时文件
            obj_file.unlink(missing_ok=True)

            return result.returncode == 0, result.stderr

        except subprocess.TimeoutExpired:
            return False, "汇编/链接超时"
        except Exception as e:
            return False, f"汇编/链接错误: {str(e)}"

    def windows_to_wsl_path(self, windows_path):
        """将Windows路径转换为WSL路径"""
        # 将反斜杠替换为正斜杠
        path = windows_path.replace("\\", "/")
        # 将C: 替换为 /mnt/c
        if path.startswith("C:/") or path.startswith("c:/"):
            path = "/mnt/c" + path[2:]
        elif path.startswith("C:\\") or path.startswith("c:\\"):
            path = "/mnt/c" + path[2:].replace("\\", "/")
        return path

    def run_qemu(self, exe_file, input_data=None):
        """在QEMU中运行可执行文件"""
        wsl_exe_file = self.windows_to_wsl_path(str(exe_file))

        # 构建QEMU命令
        qemu_cmd = f"{self.qemu_cmd} -cpu {self.qemu_cpu} {wsl_exe_file}"

        try:
            if input_data:
                result = subprocess.run(
                    ["wsl", "bash", "-c", qemu_cmd],
                    input=input_data,
                    capture_output=True,
                    text=True,
                    timeout=self.timeout,
                    encoding="utf-8",
                    errors="replace",
                )
            else:
                result = subprocess.run(
                    ["wsl", "bash", "-c", qemu_cmd],
                    capture_output=True,
                    text=True,
                    timeout=self.timeout,
                    encoding="utf-8",
                    errors="replace",
                )

            return result.returncode, result.stdout, result.stderr

        except subprocess.TimeoutExpired:
            return -1, "", "运行超时"
        except Exception as e:
            return -1, "", f"运行错误: {str(e)}"

    def load_expected_output(self, sy_file):
        """加载期望输出和输入数据"""
        out_file = sy_file.with_suffix(".out")
        in_file = sy_file.with_suffix(".in")
        input_data = None

        if in_file.exists():
            with open(in_file, "r", encoding="utf-8") as f:
                input_data = f.read()

        expected_output = ""
        if out_file.exists():
            with open(out_file, "r", encoding="utf-8") as f:
                expected_output = f.read().strip()
        return expected_output, input_data

    def compare_output(self, actual, expected):
        """比较实际输出和期望输出"""
        actual = actual.strip()
        expected = expected.strip()
        return actual == expected

    def run_single_test(self, sy_file):
        """运行单个测试用例"""
        test_name = sy_file.stem
        self.log(f"测试 {test_name}...")

        # 创建临时目录
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)

            # 创建保存.s文件的目录
            s_dir = self.results_dir / "asm_source"
            s_dir.mkdir(exist_ok=True)
            asm_file = s_dir / f"{test_name}.s"
            exe_file = temp_path / f"{test_name}"

            result = {
                "name": test_name,
                "status": "UNKNOWN",
                "compile_success": False,
                "link_success": False,
                "run_success": False,
                "output_match": False,
                "error_message": "",
                "actual_output": "",
                "expected_output": "",
                "execution_time": 0,
            }

            start_time = time.time()

            try:
                # 1. 编译.sy到汇编
                compile_success, compile_error = self.compile_sy_to_asm(
                    sy_file, asm_file
                )
                result["compile_success"] = compile_success

                if not compile_success:
                    result["status"] = "COMPILE_FAILED"
                    result["error_message"] = compile_error
                    return result

                # 2. 汇编和链接
                link_success, link_error = self.assemble_and_link(asm_file, exe_file)
                result["link_success"] = link_success

                if not link_success:
                    result["status"] = "LINK_FAILED"
                    result["error_message"] = link_error
                    return result

                # 3. 加载期望输出和输入
                expected_output, input_data = self.load_expected_output(sy_file)
                result["expected_output"] = expected_output

                # 4. 在QEMU中运行
                return_code, actual_stdout, run_error = self.run_qemu(
                    exe_file, input_data
                )

                # 拼接实际输出：stdout + "\n" + return_code
                # 注意：不要使用rstrip()，因为会移除末尾的重要空格
                actual_output = actual_stdout.rstrip("\n") + "\n" + str(return_code)
                result["actual_output"] = actual_output

                # 5. 比较输出
                output_match = self.compare_output(actual_output, expected_output)
                result["output_match"] = output_match

                if output_match:
                    result["status"] = "PASSED"
                else:
                    result["status"] = "OUTPUT_MISMATCH"
                    result["error_message"] = (
                        f"输出不匹配\n期望: {repr(expected_output)}\n实际: {repr(actual_output)}"
                    )

            except Exception as e:
                result["status"] = "EXCEPTION"
                result["error_message"] = str(e)

            finally:
                result["execution_time"] = time.time() - start_time

            return result

    def auto_compile_java(self):
        """自动编译Java源代码到out目录"""
        self.log("自动编译Java源代码...")
        out_dir = self.project_root / "out"
        if not out_dir.exists():
            out_dir.mkdir()
        # 收集所有java文件
        java_files = []
        for root, dirs, files in os.walk(self.project_root / "src"):
            for file in files:
                if file.endswith(".java"):
                    java_files.append(str(Path(root) / file))
        if not java_files:
            self.log("未找到Java源文件", "ERROR")
            return False
        classpath = f"lib/antlr4-runtime-4.13.1.jar;lib/argparse4j-0.9.0.jar"
        javac_cmd = [
            "javac",
            "-encoding",
            "UTF-8",
            # "-cp",
            # classpath,
            "-d",
            str(out_dir),
        ] + java_files
        try:
            result = subprocess.run(
                javac_cmd,
                cwd=self.project_root,
                capture_output=True,
                text=True,
                timeout=60,
                encoding="utf-8",
                errors="replace",
            )
            if result.returncode != 0:
                self.log(f"Java编译失败:\n{result.stderr}", "ERROR")
                return False
            self.log("Java编译成功")
            return True
        except subprocess.TimeoutExpired:
            self.log("Java编译超时", "ERROR")
            return False
        except Exception as e:
            self.log(f"Java编译异常: {str(e)}", "ERROR")
            return False

    def run_all_tests(self):
        """运行所有测试用例"""
        self.log("开始QEMU ARMv8-A功能测试...")
        # 自动编译Java
        if not self.auto_compile_java():
            self.log("Java自动编译失败，退出测试", "ERROR")
            return False
        # 检查依赖
        if not self.check_dependencies():
            self.log("依赖检查失败，退出测试", "ERROR")
            return False
        # 查找测试文件
        test_files = self.find_test_files()
        if not test_files:
            self.log("未找到测试文件", "ERROR")
            return False
        self.total_tests = len(test_files)
        # 运行测试
        for i, sy_file in enumerate(test_files, 1):
            self.log(f"进度: {i}/{self.total_tests}")
            result = self.run_single_test(sy_file)
            self.test_results.append(result)
            if result["status"] == "PASSED":
                self.passed_tests += 1
                self.log(f"✓ {result['name']}: 通过")
            else:
                self.failed_tests += 1
                self.log(
                    f"✗ {result['name']}: {result['status']} - {result['error_message']}",
                    "ERROR",
                )
        # 生成报告
        self.generate_report()
        return True

    def generate_report(self):
        """生成测试报告"""
        self.log("生成测试报告...")

        # 控制台输出总结
        self.log("=" * 60)
        self.log(f"测试完成: {self.total_tests} 个测试")
        self.log(f"通过: {self.passed_tests}")
        self.log(f"失败: {self.failed_tests}")
        self.log(f"成功率: {(self.passed_tests / self.total_tests * 100):.1f}%")
        self.log("=" * 60)

        # 保存详细报告到JSON文件
        timestamp = time.strftime("%Y%m%d_%H%M%S")
        report_file = self.results_dir / f"test_report_{timestamp}.json"

        report_data = {
            "timestamp": timestamp,
            "summary": {
                "total_tests": self.total_tests,
                "passed_tests": self.passed_tests,
                "failed_tests": self.failed_tests,
                "success_rate": self.passed_tests / self.total_tests * 100
                if self.total_tests > 0
                else 0,
            },
            "test_results": self.test_results,
        }

        with open(report_file, "w", encoding="utf-8") as f:
            json.dump(report_data, f, indent=2, ensure_ascii=False)

        self.log(f"详细报告已保存到: {report_file}")

        # 保存简化的文本报告
        txt_report_file = self.results_dir / f"test_summary_{timestamp}.txt"
        with open(txt_report_file, "w", encoding="utf-8") as f:
            f.write(f"QEMU ARMv8-A 功能测试报告\n")
            f.write(f"测试时间: {timestamp}\n")
            f.write(f"总测试数: {self.total_tests}\n")
            f.write(f"通过: {self.passed_tests}\n")
            f.write(f"失败: {self.failed_tests}\n")
            f.write(f"成功率: {(self.passed_tests / self.total_tests * 100):.1f}%\n\n")

            f.write("失败的测试:\n")
            for result in self.test_results:
                if result["status"] != "PASSED":
                    f.write(
                        f"  {result['name']}: {result['status']} - {result['error_message']}\n"
                    )


def main():
    """主函数"""
    if len(sys.argv) > 1 and sys.argv[1] in ["-h", "--help"]:
        print("QEMU ARMv8-A 功能测试脚本")
        print("用法: python test.py")
        print("要求:")
        print("  - Windows环境")
        print("  - WSL with QEMU and AArch64 cross-compilation tools")
        print("  - Java环境")
        print("  - libsysy_arm.a静态库（应放在项目根目录下）")
        return

    runner = QEMUARMv8TestRunner()
    success = runner.run_all_tests()

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
