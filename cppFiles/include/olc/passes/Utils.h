#pragma once

#include <unordered_map>
#include <unordered_set>
#include <vector>

#include <olc/ir/IR.h>

namespace olc {

inline std::vector<BasicBlock *> getFunctionRPO(Function *func) {
  std::vector<BasicBlock *> rpo;
  std::set<BasicBlock *> visited;

  std::function<void(BasicBlock *)> visit = [&](BasicBlock *bb) {
    if (visited.count(bb))
      return;
    visited.insert(bb);
    for (auto *succ : bb->successors)
      visit(succ);
    rpo.push_back(bb);
  };

  visit(func->getEntryBlock());

  std::reverse(rpo.begin(), rpo.end());

  return rpo;
}

inline std::unordered_map<BasicBlock *, std::unordered_set<BasicBlock *>>
getFunctionDomTree(Function *func) {
  std::unordered_map<BasicBlock *, std::unordered_set<BasicBlock *>> childs;
  for (auto *bb : func->basicBlocks) {
    if (bb->idom)
      childs[bb->idom].insert(bb);
  }
  return childs;
}

} // namespace olc
