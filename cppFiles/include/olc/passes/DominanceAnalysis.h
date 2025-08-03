#pragma once

#include <olc/ir/IR.h>
#include <olc/passes/Pass.h>

#include <algorithm>
#include <set>

namespace olc {

class DominanceAnalysis : public FunctionPass {
public:
  DominanceAnalysis() : FunctionPass(reinterpret_cast<void *>(0x19260817)) {}

  bool runOnFunction(Function &function) override {
    auto *entry = function.getEntryBlock();
    entry->dom = {entry};
    entry->idom = nullptr;
    entry->domFrontier = {};
    std::set<BasicBlock *> universe = {};
    for (auto *block : function.basicBlocks) {
      universe.insert(block);
    }
    for (auto *block : function.basicBlocks) {
      if (block == entry)
        continue;
      block->dom = universe;
      block->idom = nullptr;
      block->domFrontier = {};
    }

    // calc dom
    bool changed = true;
    while (changed) {
      changed = false;
      for (auto *block : function.basicBlocks) {
        if (block == entry)
          continue;
        std::set<BasicBlock *> newDom = universe;
        for (auto *pred : block->predecessors) {
          set_intersect(newDom, pred->dom);
        }
        newDom.insert(block);
        if (newDom != block->dom) {
          block->dom = std::move(newDom);
          changed = true;
        }
      }
    }

    // calc idom
    for (auto *block : function.basicBlocks) {
      if (block == entry)
        continue;
      BasicBlock *idom = nullptr;
      for (auto *d : block->dom) {
        if (d == block)
          continue;
        // none of other dominator is more immediate than d
        if (std::none_of(
                block->dom.begin(), block->dom.end(), [&](BasicBlock *bb) {
                  return bb != block && bb != d && bb->dom.count(d);
                })) {
          idom = d;
          break;
        }
      }
      block->idom = idom;
    }

    // calc dom frontier
    for (auto *block : function.basicBlocks) {
      if (block == entry)
        continue;
      if (block->predecessors.size() <= 1)
        continue;
      for (auto *pred : block->predecessors) {
        auto *runner = pred;
        while (runner != block->idom) {
          runner->domFrontier.insert(block);
          runner = runner->idom;
        }
      }
    }

    return false;
  }

  std::string getName() const override { return "DominanceAnalysis"; }

private:
  static const void *ID;
};

} // namespace olc
