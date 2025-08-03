#pragma once

#include <olc/ir/IR.h>
#include <olc/passes/Pass.h>
#include <olc/passes/Utils.h>

#include <algorithm>
#include <set>

namespace olc {

struct Loop {
  std::vector<BasicBlock *> bbs;
  Loop *parent;
  int depth;
  std::vector<Loop *> children;

  BasicBlock *getHeader() { return bbs.front(); }
};

struct FuncLoopInfo {
  std::vector<Loop *> roots;
  std::unordered_map<BasicBlock *, Loop *> belong;
};

class LoopAnalysis {
public:
  LoopAnalysis() {}

  FuncLoopInfo runOnFunction(Function *func) {
    FuncLoopInfo info;
    // Reversed map of bb->dom.
    auto domChildrenMap = getFunctionDomTree(func);

    std::vector<Loop *> allLoops;

    auto analyzeLoop = [&](BasicBlock *header) {
      std::vector<BasicBlock *> worklist;
      auto &dom = domChildrenMap[header];

      // Find all back edges as initial values.
      for (auto *pred : header->predecessors) {
        if (dom.find(pred) != dom.end()) {
          worklist.push_back(pred);
        }
      }

      if (worklist.empty())
        return;

      Loop *lp = new Loop{};
      allLoops.push_back(lp);
      lp->bbs.push_back(header);
      info.belong[header] = lp;
      while (!worklist.empty()) {
        BasicBlock *bb = worklist.back();
        worklist.pop_back();
        if (auto it = info.belong.find(bb); it != info.belong.end()) {
          // Already in some other loop, maintain the loop information
          // and continue.
          Loop *cur = it->second;
          while (cur->parent)
            cur = cur->parent;
          if (cur != lp) {
            cur->parent = lp;
            lp->children.push_back(cur);
            // For natural loops, we can only scan for one direction.
            // Since we start from the back edges, we should take along pred.
            for (auto *pred : bb->predecessors) {
              if (auto it2 = info.belong.find(pred);
                  it2 == info.belong.end() || it2->second != lp) {
                worklist.push_back(pred);
              }
            }
          }
        } else {
          // The first time, add it in the loop.
          lp->bbs.push_back(bb);
          info.belong[bb] = lp;
          for (auto *pred : bb->predecessors)
            worklist.push_back(pred);
        }
      }
    };
    std::function<void(BasicBlock * header)> dfsDomTree;
    dfsDomTree = [&](BasicBlock *header) {
      // Analyze the children first, because we want to build the nested loop
      // chain for once.
      for (auto *child : domChildrenMap[header]) {
        dfsDomTree(child);
      }
      analyzeLoop(header);
    };
    dfsDomTree(func->getEntryBlock());

    // Calculate roots and depth
    for (auto *lp : allLoops) {
      if (!lp->parent) {
        info.roots.push_back(lp);
        lp->depth = 1;
      } else {
        // Not in loop is 0, top-level loops is 1, and so on.
        lp->depth = 0;
        for (Loop *cur = lp; cur; cur = cur->parent)
          lp->depth++;
      }
    }

    // Reverse the order of basic blocks / nested loops in each loop.
    for (auto *lp : allLoops) {
      // Except the header.
      std::reverse(std::next(lp->bbs.begin()), lp->bbs.end());
      std::reverse(lp->children.begin(), lp->children.end());
    }

    return info;
  }
};

} // namespace olc
