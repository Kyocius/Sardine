#pragma once

#include <olc/ir/IR.h>
#include <olc/passes/Pass.h>

#include <algorithm>
#include <map>
#include <set>

namespace olc {

class Mem2RegPass : public FunctionPass {
public:
  Mem2RegPass() : FunctionPass(reinterpret_cast<void *>(0x29712683)) {}

  bool runOnFunction(Function &function) override {
    std::map<AllocaInst *, std::vector<StoreInst *>> allocaDefs;
    for (auto *bb : function.basicBlocks)
      for (auto *inst = bb->instructions.Head; inst; inst = inst->Next) {
        if (auto *store = dyn_cast<StoreInst>(inst)) {
          if (auto *alloca = dyn_cast<AllocaInst>(store->getPointer())) {
            if (!alloca->getAllocatedType()->isArrayTy())
              allocaDefs[alloca].push_back(store);
          }
        }
      }

    // 1. insert phi
    std::map<PhiInst *, AllocaInst *> phiMap;
    for (auto &&[alloca, defs] : allocaDefs) {
      std::vector<BasicBlock *> worklist;
      std::set<BasicBlock *> visited;
      for (auto *def : defs) {
        worklist.push_back(def->parent);
      }
      while (!worklist.empty()) {
        auto *bb = worklist.back();
        worklist.pop_back();
        for (auto *df : bb->domFrontier) {
          if (visited.count(df))
            continue;
          visited.insert(df);
          auto *newPhi = new PhiInst(df, alloca->getAllocatedType());
          df->instructions.push_front(newPhi);
          phiMap[newPhi] = alloca;
          worklist.push_back(df);
        }
      }
    }

    // 2. rename
    // Records the latest value of each alloca of each basic block.
    std::map<BasicBlock *, std::map<AllocaInst *, Value *>> renameMap;
    std::vector<BasicBlock *> worklist;
    worklist.push_back(function.getEntryBlock());

    while (!worklist.empty()) {
      auto *bb = worklist.back();
      worklist.pop_back();

      // Remove mem ops and update renameMap to phi refs.
      for (auto *inst = bb->instructions.Head; inst;) {
        auto *nextInst = inst->Next;
        if (auto *alloca = dyn_cast<AllocaInst>(inst);
            allocaDefs.count(alloca)) {
          bb->instructions.remove(inst);
        } else if (auto *load = dyn_cast<LoadInst>(inst)) {
          if (auto *alloca = dyn_cast<AllocaInst>(load->getPointer());
              allocaDefs.count(alloca)) {
            Value *newVal = renameMap[bb][alloca] ?: Undef::get();
            load->replaceAllUseWith(newVal);
            load->erase();
          }
        } else if (auto *store = dyn_cast<StoreInst>(inst)) {
          if (auto *alloca = dyn_cast<AllocaInst>(store->getPointer());
              allocaDefs.count(alloca)) {
            renameMap[bb][alloca] = store->getValue();
            store->erase();
          }
        } else if (auto *phi = dyn_cast<PhiInst>(inst)) {
          if (auto it = phiMap.find(phi); it != phiMap.end()) {
            renameMap[bb][it->second] = phi;
          }
        }
        inst = nextInst;
      }

      // Update all successors to the latest value of renameMap.
      for (auto *succ : bb->successors) {
        for (auto *inst = succ->instructions.Head; inst; inst = inst->Next) {
          if (auto *phi = dyn_cast<PhiInst>(inst)) {
            if (auto it = phiMap.find(phi); it != phiMap.end()) {
              if (auto itIncome = renameMap[bb].find(it->second);
                  itIncome != renameMap[bb].end())
                phi->setIncomingValueForBlock(itIncome->second, bb);
            }
          } else
            break;
        }
        if (renameMap.count(succ))
          continue;
        worklist.push_back(succ);
        renameMap[succ] = renameMap[bb];
      }
    }

    return true;
  }

  std::string getName() const override { return "Mem2RegPass"; }

private:
  static const void *ID;
};

} // namespace olc
