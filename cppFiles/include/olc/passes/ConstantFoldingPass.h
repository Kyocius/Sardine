#pragma once

#include <olc/ir/IR.h>
#include <olc/passes/Pass.h>
#include <unordered_map>

namespace olc {

ConstantValue *
ConstFold(BinaryInst *binInst, ConstantValue *lhs, ConstantValue *rhs);
class ConstantFoldingPass : public FunctionPass {
public:
  ConstantFoldingPass() : FunctionPass(reinterpret_cast<void *>(0xCF240816)) {}

  bool runOnFunction(Function &function) override {
    bool modified = false;
    std::unordered_map<Value *, ConstantValue *> memoryMap;

    // 遍历函数中的每一个基本块
    for (auto &block : function.getBasicBlocks()) {
      // 遍历基本块中的每一条指令
      for (auto *inst = block->instructions.Head; inst;) {
        auto *nextInst = inst->Next;
        // 检查是否是二元运算指令
        if (auto binInst = dyn_cast<BinaryInst>(inst)) {
          // 获取操作数
          if (auto lhs = dyn_cast<ConstantValue>(binInst->getLHS())) {
            if (auto rhs = dyn_cast<ConstantValue>(binInst->getRHS())) {
              // 计算结果
              ConstantValue *result = ConstFold(binInst, lhs, rhs);
              // 替换指令
              if (result) {
                inst->replaceAllUseWith(result);
                inst->erase();
                modified = true;
              }
            }
          }
        }
        // TODO: Other operantions.
        inst = nextInst;
      }
    }

    return modified;
  }

  std::string getName() const override { return "ConstantFoldingPass"; }

private:
  static const void *ID;
};

} // namespace olc