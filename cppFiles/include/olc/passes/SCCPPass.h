// ref:
// https://github.com/liuly0322/sysyf_compiler_codegen_opt/blob/main/SysYF_Pass_Student/src/Optimize/SCCP.cpp

#pragma once

#include <olc/Support.h>
#include <olc/ir/IR.h>
#include <olc/passes/ConstantFoldingPass.h>
#include <olc/passes/Pass.h>

#include <map>
#include <set>

namespace olc {

struct ValueState {
  enum State { BOT, CONST, TOP };

  State state;
  ConstantValue *value = nullptr;

  bool isbot() const { return state == BOT; }
  bool isconst() const { return state == CONST; }
  bool istop() const { return state == TOP; }
  void operator^=(const ValueState &rhs) {
    if (rhs.state < state) {
      state = rhs.state;
      value = rhs.value;
    } else if (rhs.state == state && state == CONST) {
      if (value->isInt()) {
        int x = value->getInt();
        int y = rhs.value->getInt();
        if (x != y) {
          state = BOT;
          value = nullptr;
        }
      } else {
        float x = value->getFloat();
        float y = rhs.value->getFloat();
        if (x != y) {
          state = BOT;
          value = nullptr;
        }
      }
    }
  }
  bool operator!=(const ValueState &rhs) const {
    if (state != rhs.state) {
      return true;
    }
    if (state == CONST) {
      if (value->isInt()) {
        int l = value->isInt();
        int r = rhs.value->getInt();
        return l != r;
      } else {
        float l = value->getFloat();
        float r = rhs.value->getFloat();
        return l != r;
      }
    }
    return false;
  }
};

class SCCPPass : public FunctionPass {
public:
  SCCPPass() : FunctionPass(reinterpret_cast<void *>(0x08191034)) {}

  bool runOnFunction(Function &function) override {
    stateMap.clear();
    marked.clear();
    cfg_worklist.clear();
    ssa_worklist.clear();
    remove_list.clear();

    if (function.isBuiltin)
      return false;
    bool modified = false;

    cfg_worklist.emplace_back(nullptr, function.getEntryBlock());
    for (auto &bb : function.getBasicBlocks())
      for (auto *inst = bb->instructions.Head; inst; inst = inst->Next)
        stateMap[inst] = {ValueState::State::TOP};

    unsigned i = 0, j = 0;
    while (i < cfg_worklist.size() || j < ssa_worklist.size()) {
      while (i < cfg_worklist.size()) {
        auto &item = cfg_worklist[i++];
        if (marked.find(item) != marked.end())
          continue;
        marked.insert(item);
        auto &[preBB, curBB] = item;

        for (auto *inst = curBB->instructions.Head; inst; inst = inst->Next) {
          visitInst(inst);
          // replaceConstant(inst);
        }
      }
      while (j < ssa_worklist.size()) {
        auto *inst = ssa_worklist[j++];
        auto *curBB = inst->parent;

        for (auto &preBB : curBB->predecessors) {
          if (marked.find({preBB, curBB}) != marked.end()) {
            visitInst(inst);
            break;
          }
        }
        // replaceConstant(inst);
      }
    }
    replaceConstants(&function);
    removeUselessBlocks(&function);
    return true;
  }

  std::string getName() const override { return "SCCPPass"; }

private:
  ValueState getValueState(Value *inst) {
    if (isa<ConstantValue>(inst)) {
      return {ValueState::State::CONST, cast<ConstantValue>(inst)};
    } else if (isa<Argument>(inst) || isa<Undef>(inst)) {
      return {ValueState::State::BOT};
    } else {
      return stateMap.at(inst);
    }
  }

  void visitInst(Instruction *inst) {
    BasicBlock *bb = inst->parent;
    prev_state = getValueState(inst);
    cur_state = prev_state;

    if (auto *phiInst = dyn_cast<PhiInst>(inst)) {
      const int phi_size = phiInst->getNumOperands() / 2;
      for (int i = 0; i < phi_size; i++) {
        auto *pre_bb = cast<BasicBlock>(phiInst->getOperand(i * 2 + 1));
        if (marked.count({pre_bb, bb})) {
          auto *op = phiInst->getOperand(i * 2);
          auto opState = getValueState(op);
          cur_state ^= opState;
        }
      }
      // for (auto *op : phiInst->operands) {
      //   if (auto *lable = dyn_cast<BasicBlock>(op)) {
      //     bool flag = false;
      //     for (auto *prevBlock : phiInst->parent->predecessors) {
      //       if (prevBlock == op) {
      //         flag = true;
      //         break;
      //       }
      //     }
      //     if (!flag) {
      //       phiInst->parent->remove_phi_from(lable);
      //     }
      //   }
      // }
    } else if (auto *brInst = dyn_cast<BranchInst>(inst)) {
      if (brInst != bb->instructions.Tail)
        return;
      auto *trueBlock = brInst->getTrueBlock();
      auto *falseBlock = brInst->getFalseBlock();
      auto *constCond = getValueState(brInst->getCondition()).value;
      if (constCond) {
        if (constCond->getInt() == 1) {
          cfg_worklist.emplace_back(bb, trueBlock);
          // BranchToJmp(brInst, trueBlock, falseBlock);
        } else {
          cfg_worklist.emplace_back(bb, falseBlock);
          // BranchToJmp(brInst, falseBlock, trueBlock);
        }
      } else {
        cfg_worklist.emplace_back(bb, trueBlock);
        cfg_worklist.emplace_back(bb, falseBlock);
      }
    } else if (auto *jmpInst = dyn_cast<JumpInst>(inst)) {
      auto *target = jmpInst->getTargetBlock();
      cfg_worklist.emplace_back(bb, target);
    } else if (auto *binInst = dyn_cast<BinaryInst>(inst)) {
      auto lhs = getValueState(binInst->getLHS()).value;
      auto rhs = getValueState(binInst->getRHS()).value;
      if (lhs && rhs) {
        ConstantValue *result = ConstFold(binInst, lhs, rhs);
        cur_state = {ValueState::CONST, result};
      } else {
        cur_state = {ValueState::TOP};
        int num_operands = binInst->getNumOperands();
        for (int i = 0; i < num_operands; i++) {
          auto *op = binInst->getOperand(i);
          if (getValueState(op).isbot()) {
            cur_state = {ValueState::BOT};
            break;
          }
        }
      }
    } else {
      cur_state = {ValueState::BOT};
    }
    auto instTag = inst->tag;
    if (cur_state != prev_state) {
      stateMap[inst] = cur_state;
      for (auto use : inst->uses) {
        auto *user = dyn_cast<Instruction>(use.user);
        if (isa<LoadInst>(user) || isa<StoreInst>(user))
          continue;
        ssa_worklist.push_back(user);
      }
    }
  }

  void replaceConstants(Function *func) {
    for (auto *block : func->getBasicBlocks()) {
      for (auto *inst = block->instructions.Head; inst; inst = inst->Next) {
        if (auto *constant = getValueState(inst).value) {
          inst->replaceAllUseWith(constant);
          remove_list.push_back(inst);
        }
      }
    }
    for (auto *inst : remove_list) {
      inst->parent->instructions.remove(inst);
    }
    for (auto &bb : func->getBasicBlocks()) {
      if (bb->instructions.empty())
        continue;
      auto *branchInst = dyn_cast<BranchInst>(bb->instructions.Tail);
      if (branchInst) {
        auto *constCond = getValueState(branchInst->getCondition()).value;
        if (constCond) {
          auto *trueBlock = branchInst->getTrueBlock();
          auto *falseBlock = branchInst->getFalseBlock();
          if (constCond->getInt() == 1) {
            BranchToJmp(branchInst, trueBlock, falseBlock);
          } else {
            BranchToJmp(branchInst, falseBlock, trueBlock);
          }
        }
      }
    }
  }

  void BranchToJmp(
      BranchInst *branchInst, BasicBlock *trueBlock, BasicBlock *falseBlock) {
    auto *block = branchInst->parent;
    auto *jmpInst = new JumpInst(block, trueBlock);
    block->instructions.push_back(jmpInst);
    block->instructions.remove(branchInst);
    remove_list.push_back(branchInst);
    stateMap[jmpInst] = {ValueState::State::TOP};
    block->successors.remove(falseBlock);
    falseBlock->predecessors.remove(block);
    if (falseBlock->predecessors.empty()) {
      for (auto *succ : falseBlock->successors) {
        succ->predecessors.remove(falseBlock);
        succ->remove_phi_from(falseBlock);
      }
    }
  }

  void removeUselessBlocks(Function *func) {
    auto blocks = std::set<BasicBlock *>();
    for (auto mark : marked) {
      blocks.insert(mark.second);
    }
    for (auto it = func->basicBlocks.begin(); it != func->basicBlocks.end();) {
      auto block = *it;
      if (blocks.find(block) == blocks.end()) {
        for (auto succ : block->successors) {
          succ->predecessors.remove(block);
          succ->remove_phi_from(block);
        }
        it = func->basicBlocks.erase(it);
      } else {
        it++;
      }
    }
  }

  // void replaceConstant(Instruction *inst) {
  //   if (auto *phiInst = dyn_cast<PhiInst>(inst)) {
  //     return;
  //   }
  //   if (auto *constant = getValueState(inst).value) {
  //     inst->replaceAllUseWith(constant);
  //     remove_list.push_back(inst);
  //   }
  // }

  static const void *ID;

private:
  std::map<Value *, ValueState> stateMap;
  std::set<std::pair<BasicBlock *, BasicBlock *>> marked;
  std::vector<std::pair<BasicBlock *, BasicBlock *>> cfg_worklist;
  std::vector<Instruction *> ssa_worklist;
  std::vector<Instruction *> remove_list;

  ValueState prev_state;
  ValueState cur_state;
};

} // namespace olc
