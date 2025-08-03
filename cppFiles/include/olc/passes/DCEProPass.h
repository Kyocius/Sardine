#pragma once

#include <olc/ir/IR.h>
#include <olc/passes/Pass.h>
#include <set>

namespace olc {

class DCEProPass : public FunctionPass {
public:
  DCEProPass() : FunctionPass(reinterpret_cast<void *>(0x12345679)) {}

  bool runOnFunction(Function &function) override {
    if (function.isBuiltin)
      return false;

    while (true) {
      bool changed = false;
      for (auto &bb : function.getBasicBlocks()) {
        for (auto *inst = bb->instructions.Tail; inst;) {
          if (inst->uses.empty() && !hasSideEffect(inst)) {
            changed = true;
            auto *nextInst = inst->Prev;
            inst->erase();
            inst = nextInst;
          } else {
            inst = inst->Prev;
          }
        }
      }
      if (!changed)
        break;
    }

    return false;
  }

  bool hasSideEffect(Instruction *inst) const {
    // 判断指令是否有副作用
    switch (inst->tag) {
    case Value::Tag::Store:
    case Value::Tag::Call:
    case Value::Tag::Return:
    case Value::Tag::Jump:
    case Value::Tag::Branch:
      return true;
    default:
      return false;
    }
  }

  std::string getName() const override { return "DeadCodeEliminationPass"; }

private:
  static const void *ID;
};

} // namespace olc