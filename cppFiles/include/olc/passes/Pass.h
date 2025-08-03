#pragma once

#include <olc/ir/IR.h>
#include <string>

namespace olc {

class PassManager;
class ModulePass;
class FunctionPass;

class AnalysisUsage {
public:
  // 声明当前通道依赖的分析通道
  void addRequired(const void *ID);
  // 声明当前通道保留的分析结果
  void addPreserved(const void *ID);
  // 声明当前通道不修改任何分析结果
  void setPreservesAll();
};

class Pass {
public:
  // enum class Tag { ModulePass, FunctionPass } tag;
  const void *ID;

  // 构造函数
  Pass(const void *ID) : ID(ID) {}

  virtual ~Pass() = default;

  virtual bool runOnFunction(Function &function) = 0;

  // 返回通道名称
  virtual std::string getName() const = 0;

  // 用于声明分析依赖和保留信息
  virtual void getAnalysisUsage(AnalysisUsage &AU) const {
    //
  }

  // 初始化通道
  virtual bool doInitialization(Module &module) { return false; }

  // 清理通道
  virtual bool doFinalization(Module &module) { return false; }

  // 获取通道 ID
  const void *getPassID() const { return ID; }
};

class ModulePass : public Pass {
public:
  ModulePass(const void *ID) : Pass(ID) {}

  virtual bool runOnModule(Module &module) = 0;

  std::string getName() const override = 0;

protected:
  bool skipModule(Module &M) const;
};

class FunctionPass : public Pass {
public:
  FunctionPass(const void *ID) : Pass(ID) {}

  // 强制 out-of-line 虚方法
  ~FunctionPass() override = default;

  virtual bool runOnFunction(Function &function) override = 0;

  std::string getName() const override = 0;

protected:
  bool skipFunction(Function &F) const {
    // 实现跳过函数的逻辑
    return false; // 默认不跳过
  }
};

} // namespace olc