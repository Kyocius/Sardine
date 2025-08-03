#include <olc/backend/Liveness.h>

#include <map>
#include <stack>

namespace olc {

// Calc basic block liveness
LivenessBlockInfo::LivenessBlockInfo(AsmLabel *label) : label(label) {
  for (auto inst = label->Head; inst; inst = inst->Next) {
    for (auto *refuse : inst->getUses())
      if (auto *reg = dyn_cast_if_present<AsmReg>(*refuse))
        if (!defRegs.count(reg))
          useRegs.insert(reg);
    for (auto *refdef : inst->getDefs())
      if (auto *reg = cast<AsmReg>(*refdef))
        defRegs.insert(reg);
  }
}

// in = use U (out \ def)
bool LivenessBlockInfo::updateLiveIn() {
  VarSetT newInValues = outRegs;
  set_subtract(newInValues, defRegs);
  set_union(newInValues, useRegs);
  bool changed = newInValues.size() != inRegs.size();
  inRegs = std::move(newInValues);
  return changed;
}

// out = U_{succ} in_{succ}
void LivenessBlockInfo::updateLiveOut(BlockBuilderMapT const &infoMap) {
  for (auto succ : label->succs) {
    set_union(outRegs, infoMap.at(succ).inRegs);
  }
}

void InstOrderingManager::runOnFunction(AsmFunc *func) {
  std::stack<AsmLabel *> worklist;
  std::map<AsmLabel *, bool> visited;
  worklist.push(func->labels.front());
  while (!worklist.empty()) {
    AsmLabel *block = worklist.top();
    worklist.pop();
    if (visited[block])
      continue;
    visited[block] = true;
    runOnBlock(block);
    for (auto succ : block->succs) {
      worklist.push(succ);
    }
  }
}

void InstOrderingManager::runOnBlock(AsmLabel *label) {
  labelOrder.push_back(label);

  for (auto inst = label->Head; inst; inst = inst->Next) {
    auto count = instIDMap.size();
    instIDMap[inst] = 2 * count;
  }
}

void LivenessAnalysis::runOnFunction(AsmFunc *func) {
  instOrdering.runOnFunction(func);
  this->func = func;
  buildBlockInfoMap();
  calcLiveness();
}

void LivenessAnalysis::buildBlockInfoMap() {
  std::vector<AsmLabel *> worklist;
  std::map<AsmLabel *, bool> present;

  for (auto block : instOrdering.labelOrder) {
    auto &blockInfo = blockInfoMap.try_emplace(block, block).first->second;
    if (blockInfo.updateLiveIn()) {
      for (auto pred : block->preds) {
        if (!present[pred]) {
          worklist.push_back(pred);
          present[pred] = true;
        }
      }
    }
  }

  while (!worklist.empty()) {
    auto block = worklist.back();
    worklist.pop_back();
    present[block] = false;
    auto &blockInfo = blockInfoMap.at(block);
    blockInfo.updateLiveOut(blockInfoMap);
    if (blockInfo.updateLiveIn()) {
      for (auto pred : block->preds) {
        if (!present[pred]) {
          worklist.push_back(pred);
          present[pred] = true;
        }
      }
    }
  }
}

void LivenessAnalysis::calcLiveness() {
  auto inPoint = [&](AsmInst *inst) { return instOrdering.instIDMap.at(inst); };
  auto outPoint = [&](AsmInst *inst) {
    return instOrdering.instIDMap.at(inst) + 1;
  };

  // TODO: Handle arguments

  liveness.resize(instOrdering.instIDMap.size() * 2);

  bool changed = false;
  do {
    changed = false;
    for (auto blockIt = instOrdering.labelOrder.rbegin();
         blockIt != instOrdering.labelOrder.rend(); ++blockIt) {
      auto block = *blockIt;
      auto &blockInfo = blockInfoMap.at(block);

      for (auto *inst = block->Tail; inst; inst = inst->Prev) {
        auto &inset = liveness.at(inPoint(inst));
        auto &outset = liveness.at(outPoint(inst));

        if (inst != block->Tail) {
          auto &nextIn = liveness.at(inPoint(inst->Next));
          set_union(outset, nextIn);
        } else {
          outset = blockInfo.outRegs;
        }

        auto newInset = outset;

        // def
        for (auto *refdef : inst->getDefs())
          if (auto *var = cast<AsmReg>(*refdef))
            newInset.erase(var);

        if (auto *callInst = dyn_cast<AsmCallInst>(inst)) {
          for (auto *preg : callInst->callDefs)
            newInset.erase(preg);
        }

        // use
        for (auto *refuse : inst->getUses())
          if (auto *var = dyn_cast_if_present<AsmReg>(*refuse))
            newInset.insert(var);
        
        if (auto *callInst = dyn_cast<AsmCallInst>(inst)) {
          for (auto *preg : callInst->callUses)
            newInset.insert(preg);
        }

        if (newInset != inset) {
          changed = true;
          inset = std::move(newInset);
        }
      }
    }
  } while (changed);
}

} // namespace olc
