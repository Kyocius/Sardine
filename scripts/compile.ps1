# Tensei Compiler Build Script
# 使用方法: .\compile.ps1 [-Clean] [-Verbose]

param(
    [switch]$Clean = $false,
    [switch]$Verbose = $false
)

# 设置颜色输出
function Write-Status {
    param(
        [string]$Message,
        [string]$Status,
        [string]$Color = "White"
    )
    Write-Host "[$Status] " -ForegroundColor $Color -NoNewline
    Write-Host $Message
}

# 检查 Java 是否安装
function Test-JavaInstallation {
    try {
        $javaVersion = & java -version 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Status "找到 Java 环境" "INFO" "Green"
            if ($Verbose) {
                Write-Host "  Java 版本信息:" -ForegroundColor Gray
                $javaVersion | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
            }
            return $true
        }
    } catch {
        Write-Status "未找到 Java 环境，请确保 Java 已安装并在 PATH 中" "ERROR" "Red"
        return $false
    }
    return $false
}

# 清理编译输出
function Clear-CompileOutput {
    Write-Status "清理之前的编译输出..." "INFO" "Yellow"
    
    # 删除所有 .class 文件
    Get-ChildItem -Recurse -Filter "*.class" | Remove-Item -Force -ErrorAction SilentlyContinue
    
    # 清理临时文件
    @("out.ll", "compile_stderr.txt", "compile_stdout.txt") | ForEach-Object {
        if (Test-Path $_) { 
            Remove-Item $_ -Force 
            if ($Verbose) { Write-Host "  删除: $_" -ForegroundColor Gray }
        }
    }
    
    Write-Status "清理完成" "INFO" "Green"
}


# 编译 Java 源文件
function Build-JavaSources {
    Write-Status "编译 Java 源文件..." "INFO" "Cyan"
    
    # 获取所有 Java 源文件
    $sourceFiles = @()
    $sourceFiles += Get-ChildItem -Path "src" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
    $sourceFiles += Get-ChildItem -Path "frontend" -Filter "*.java" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
    
    if ($sourceFiles.Count -eq 0) {
        Write-Status "未找到 Java 源文件" "ERROR" "Red"
        return $false
    }
    
    Write-Status "找到 $($sourceFiles.Count) 个 Java 源文件" "INFO" "Green"
    
    # 设置 classpath
    # $classpath = ".;lib\antlr4-runtime-4.13.1.jar;lib\argparse4j-0.9.0.jar"
    
    # 创建 out 目录（如果不存在）
    if (!(Test-Path "out")) {
        New-Item -ItemType Directory -Path "out" -Force | Out-Null
        Write-Status "创建输出目录: out" "INFO" "Green"
    }
    
    # 编译命令
    $compileArgs = @(
        #"-cp", $classpath,
        "-d", "out",
        "-encoding", "UTF-8"
    )
    $compileArgs += $sourceFiles
    
    if ($Verbose) {
        # Write-Host "  Classpath: $classpath" -ForegroundColor Gray
        Write-Host "  编译 $($sourceFiles.Count) 个文件..." -ForegroundColor Gray
    }
    
    try {
        $process = Start-Process -FilePath "javac" -ArgumentList $compileArgs -Wait -PassThru -RedirectStandardError "javac_stderr.txt" -RedirectStandardOutput "javac_stdout.txt"
        
        if ($process.ExitCode -eq 0) {
            Write-Status "Java 编译成功" "INFO" "Green"
            
            # 检查是否生成了主类文件
            if (Test-Path "out\Compiler.class") {
                Write-Status "主编译器类已生成: out\Compiler.class" "INFO" "Green"
            }
            
            return $true
        } else {
            Write-Status "Java 编译失败" "ERROR" "Red"
            if (Test-Path "javac_stderr.txt") {
                $errorContent = Get-Content "javac_stderr.txt" -Raw
                if ($errorContent.Trim() -ne "") {
                    Write-Host "编译错误:" -ForegroundColor Red
                    Write-Host $errorContent -ForegroundColor Red
                }
            }
            return $false
        }
    } catch {
        Write-Status "编译过程中出现异常: $($_.Exception.Message)" "ERROR" "Red"
        return $false
    } finally {
        # 清理临时文件
        @("javac_stderr.txt", "javac_stdout.txt") | ForEach-Object {
            if (Test-Path $_) { Remove-Item $_ -Force }
        }
    }
}

# 验证编译结果
function Test-CompileResult {
    Write-Status "验证编译结果..." "INFO" "Cyan"
    
    # 检查主要的类文件是否存在
    $requiredClasses = @(
        "out\Compiler.class",
        "out\Driver\Driver.class"
    )
    
    $missingClasses = @()
    foreach ($class in $requiredClasses) {
        if (!(Test-Path $class)) {
            $missingClasses += $class
        }
    }
    
    if ($missingClasses.Count -eq 0) {
        Write-Status "编译验证成功，所有必要的类文件都已生成" "INFO" "Green"
        
        return $true
    } else {
        Write-Status "编译验证失败，缺少以下类文件:" "ERROR" "Red"
        $missingClasses | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
        return $false
    }
}

# 主编译流程
function Main {
    Write-Host "Sardine 编译器构建脚本" -ForegroundColor Cyan
    Write-Host ""  # 输出一个空行
    Write-Host ("=" * 50) -ForegroundColor Gray
    
    # 检查 Java 环境
    if (!(Test-JavaInstallation)) {
        exit 1
    }
    
    # 清理（如果需要）
    if ($Clean) {
        Clear-CompileOutput
    }
    
    # 编译 Java 源文件
    if (!(Build-JavaSources)) {
        Write-Status "编译失败!" "ERROR" "Red"
        exit 1
    }
    
    # 验证编译结果
    if (!(Test-CompileResult)) {
        Write-Status "编译验证失败!" "ERROR" "Red"
        exit 1
    }
    
    Write-Host ""  # 输出一个空行
    Write-Host ("=" * 50) -ForegroundColor Gray
    Write-Status "编译完成!" "SUCCESS" "Green"
}

# 执行主流程
Main
