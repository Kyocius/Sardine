#pragma once

#include <memory>
#include <olc/ir/IR.h>
#include <olc/passes/Pass.h>
#include <vector>

namespace olc {

class Pass;
class ModulePass;
class FunctionPass;

class PassManager {
public:
  // 添加 Pass 到 PassManager
  void addPass(Pass *P) { Passes.push_back(P); }

  // 运行所有 Pass
  void run(Module &M) {
    for (auto *P : Passes) {
      if (auto *MP = dynamic_cast<ModulePass *>(P)) {
        MP->doInitialization(M);
        MP->runOnModule(M);
        MP->doFinalization(M);
      } else if (auto *FP = dynamic_cast<FunctionPass *>(P)) {
        FP->doInitialization(M);
        for (auto *F : M.functions) {
          if (F->isBuiltin)
            continue;
          FP->runOnFunction(*F);
        }
        FP->doFinalization(M);
      }
    }
  }

private:
  std::vector<Pass *> Passes;
};

} // namespace olc