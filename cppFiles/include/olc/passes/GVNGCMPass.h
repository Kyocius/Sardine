#include <olc/ir/IR.h>
#include <olc/passes/LoopAnalysis.h>
#include <olc/passes/Pass.h>
#include <olc/passes/Utils.h>

#include <unordered_map>

namespace olc {

class GVNGCMPass : public FunctionPass {
public:
  GVNGCMPass() : FunctionPass(&ID) {}

  bool isWhiteListInst(Instruction *inst) {
    // TODO: call inst
    return isa<BinaryInst>(inst) || isa<GetElementPtrInst>(inst) ||
           isa<IntToFloatInst>(inst) || isa<FloatToIntInst>(inst);
  };

  bool runGVNOnFunc(Function &function) {
    bool changed = false;

    auto rpo = getFunctionRPO(&function);

    std::unordered_map<Value *, std::size_t> valueHash;

    std::function<size_t(Value *)> calcValueHash;
    calcValueHash = [&](Value *value) -> size_t {
      if (!value)
        return 0;
      if (auto it = valueHash.find(value); it != valueHash.end())
        return it->second;
      size_t seed = 19260817;
      hash_combine(seed, value->tag);

      if (auto *inst = dyn_cast<Instruction>(value)) {
        if (isWhiteListInst(inst)) {
          for (size_t i = 0; i < inst->getNumOperands(); i++) {
            auto *op = inst->getOperand(i);
            hash_combine(seed, calcValueHash(op));
          }
        } else {
          // Fallback to always unique.
          hash_combine(seed, inst);
        }
      } else if (auto *glob = dyn_cast<GlobalVariable>(value)) {
        hash_combine(seed, glob->getName());
      } else if (auto *arg = dyn_cast<Argument>(value)) {
        // Only ensure unique inside func itself.
        hash_combine(seed, arg);
      } else if (auto *konst = dyn_cast<ConstantValue>(value)) {
        if (konst->isInt()) {
          hash_combine(seed, 1e9 + 7);
          hash_combine(seed, konst->getInt());
        } else {
          hash_combine(seed, 998244353);
          hash_combine(seed, konst->getFloat());
        }
      } else {
        olc_unreachable("NYI");
      }
      return seed;
    };

    std::unordered_map<std::size_t, Value *> hashLeader;

    for (auto *bb : rpo) {
      for (auto *inst = bb->instructions.Head; inst;) {
        auto nextInst = inst->Next;

        if (!isWhiteListInst(inst)) {
          inst = nextInst;
          continue;
        }
        size_t hash = calcValueHash(inst);
        if (auto it = hashLeader.find(hash); it != hashLeader.end()) {
          inst->replaceAllUseWith(it->second);
          inst->erase();
          changed = true;
        } else {
          hashLeader[hash] = inst;
        }

        inst = nextInst;
      }
    }

    return changed;
  }

  bool runGCMOnFunc(Function &function) {
    LoopAnalysis loopAnalysis;
    FuncLoopInfo loopInfo = loopAnalysis.runOnFunction(&function);

    std::set<Instruction *> visited;

    auto domChildsMap = getFunctionDomTree(&function);
    std::unordered_map<BasicBlock *, int> domLevelMap;
    std::function<void(BasicBlock *)> calcDomLevel;
    calcDomLevel = [&](BasicBlock *bb) {
      if (bb->idom) {
        domLevelMap[bb] = domLevelMap.at(bb->idom) + 1;
      } else {
        domLevelMap[bb] = 0;
      }
      for (auto *child : domChildsMap[bb]) {
        calcDomLevel(child);
      }
    };
    calcDomLevel(function.getEntryBlock());

    auto moveInstrToBB = [&](Instruction *inst, BasicBlock *bb) {
      inst->parent->instructions.remove(inst);
      inst->parent = bb;
      bb->instructions.push_before(bb->instructions.Tail, inst);
    };
    std::function<void(Instruction *)> scheduleEarly;
    scheduleEarly = [&](Instruction *inst) {
      // Others remain pinned
      if (!isWhiteListInst(inst))
        return;
      if (visited.count(inst))
        return;
      visited.insert(inst);
      // Never scheduled, place it in root.
      moveInstrToBB(inst, function.getEntryBlock());
      // Now recursively process operands.
      for (auto *op : inst->operands) {
        if (auto *opInst = dyn_cast<Instruction>(op)) {
          scheduleEarly(opInst);
          // Use the deepest dominator.
          if (domLevelMap.at(opInst->parent) > domLevelMap.at(inst->parent)) {
            moveInstrToBB(inst, opInst->parent);
          }
        }
      }
    };

    auto findLCA = [&](BasicBlock *a, BasicBlock *b) {
      while (a != b) {
        if (domLevelMap.at(a) > domLevelMap.at(b)) {
          a = a->idom;
        } else {
          b = b->idom;
        }
      }
      return a;
    };

    std::function<void(Instruction *)> scheduleLate;
    scheduleLate = [&](Instruction *inst) {
      // Others remain pinned
      if (!isWhiteListInst(inst))
        return;
      if (visited.count(inst))
        return;
      visited.insert(inst);

      // Now recursively process uses.
      BasicBlock *lca = nullptr;
      for (auto &use : inst->uses) {
        if (auto *useInst = dyn_cast<Instruction>(use.user)) {
          scheduleLate(useInst);

          BasicBlock *useBB = useInst->parent;
          if (auto *phiInst = dyn_cast<PhiInst>(useInst)) {
            // [value, block] pair in PhiInst
            useBB = cast<BasicBlock>(phiInst->getOperand(use.index + 1));
          }
          // Use the LCA of all uses.
          lca = lca ? findLCA(lca, useBB) : useBB;
        }
      }

      assert(lca && "Please run DCE first to ensure no dead code.");

      // Heuristically place it in the outermost loop level.
      auto getLoopLevel = [&](BasicBlock *bb) {
        if (auto it = loopInfo.belong.find(bb); it != loopInfo.belong.end())
          return it->second->depth;
        else
          return 0;
      };
      BasicBlock *best = lca;
      do {
        if (getLoopLevel(best) > getLoopLevel(lca))
          best = lca;

        if (lca == inst->parent)
          break;
        lca = lca->idom;
      } while (true);

      moveInstrToBB(inst, best);
      // Rearrange the inst with uses considered:
      // find its first use and insert it before that.
      for (auto *bestInst = best->instructions.Head; bestInst;
           bestInst = bestInst->Next) {
        if (isa<PhiInst>(bestInst))
          continue;
        for (auto &use : inst->uses) {
          if (use.user == bestInst) {
            best->instructions.remove(inst);
            best->instructions.push_before(bestInst, inst);
            return;
          }
        }
      }
    };

    // Collect all insts for convinient basicblock modification.
    std::vector<Instruction *> allInsts;
    for (auto *bb : function.basicBlocks)
      for (auto *inst = bb->instructions.Head; inst; inst = inst->Next)
        allInsts.push_back(inst);

    // Schedule now.
    for (auto *inst : allInsts)
      scheduleEarly(inst);
    visited.clear();
    for (auto *inst : allInsts)
      scheduleLate(inst);

    // Local schedule for better inst selection.
    for (auto *bb : function.basicBlocks) {
      // Fix the cmp insts to be just before the branch.
      for (auto *inst = bb->instructions.Head; inst; inst = inst->Next) {
        if (auto *brInst = dyn_cast<BranchInst>(inst)) {
          if (auto *cond = dyn_cast<BinaryInst>(brInst->getCondition());
              cond && cond->isCmpOp()) {
            auto *newCmp =
                new BinaryInst{bb, cond->tag, cond->getLHS(), cond->getRHS()};
            // Insert right before the branch.
            bb->instructions.push_before(inst, newCmp);
            brInst->setOperand(0, newCmp);
          }
        }
        // Otherwise no worries.
      }
    }
    return true;
  }

  bool runOnFunction(Function &function) override {
    bool changed = false;

    changed |= runGVNOnFunc(function);
    changed |= runGCMOnFunc(function);

    return changed;
  }

  std::string getName() const override { return "GVNGCMPass"; }

private:
  static const void *ID;
};

const void *GVNGCMPass::ID = reinterpret_cast<void *>(0xEAF1DC2C);

} // namespace olc