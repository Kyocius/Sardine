#pragma once

#include <olc/ir/IR.h>
#include <olc/passes/Pass.h>
#include <set>

namespace olc {

class SimplifyCFGPass : public FunctionPass {
public:
  SimplifyCFGPass() : FunctionPass(reinterpret_cast<void *>(0xDEADBEEF)) {}

  bool runOnFunction(Function &function) override {
    bool changed = false;
    auto *entry = function.getEntryBlock();
    for (auto it = function.basicBlocks.begin();
         it != function.basicBlocks.end();) {
      auto *bb = *it;
      // Skip entry block, remove blocks that have no predecessors
      if (bb != entry && bb->predecessors.empty()) {
        changed = true;
        // First remove all references to this block from other blocks
        for (auto *pred : bb->successors) {
          pred->predecessors.remove(bb);
        }

        it = function.basicBlocks.erase(it);
      } else {
        ++it;
      }
    }
    return changed;
  }

  std::string getName() const override { return "SimplifyCFGPass"; }

private:
  static const void *ID;
};


} // namespace olc
