#include <olc/ir/IR.h>

namespace olc {

bool Value::isDefVar() const {
  if (auto *inst = dyn_cast<Instruction>(this)) {
    return !inst->getType()->isVoidTy();
  } else {
    return isa<Argument>(this);
  }
}

void Value::addUse(User *user, int index) { uses.push_back(Use(user, index)); }

void Value::removeUse(User *user, int index) {
  auto u = Use(user, index);
  uses.remove_if([=](Use const &use) { return use == u; });
}

void Value::replaceAllUseWith(Value *v) {
  for (auto u : uses) {
    // delay the remove to avoid invalidation of iterator
    u.user->setOperandWithoutRemoveUse(u.index, v);
  }
  // clear uses now
  uses.clear();
}

Value *User::getOperand(unsigned i) const {
  assert(i < getNumOperands());
  return operands[i];
}

void User::setOperand(unsigned i, Value *v) {
  assert(i < getNumOperands());
  if (Value *oldValue = operands[i])
    oldValue->removeUse(this, i);

  setOperandWithoutRemoveUse(i, v);
}

void User::setOperandWithoutRemoveUse(unsigned i, Value *v) {
  assert(i < getNumOperands());
  operands[i] = v;
  if (v)
    v->addUse(this, i);
}

void User::addOperand(Value *v) {
  auto newIndex = operands.size();
  operands.push_back(v);
  if (v) {
    v->addUse(this, newIndex);
  }
}

void User::removeOperands(unsigned i1, unsigned i2) {
  auto backup = std::vector<Value *>{};
  for (auto i = i2 + 1; i < operands.size(); i++) {
    backup.push_back(operands[i]);
  }
  for (auto i = i1; i < operands.size(); i++) {
    operands[i]->removeUse(this, i);
  }
  operands.erase(operands.begin() + i1, operands.begin() + operands.size());
  for (auto *op : backup) {
    addOperand(op);
  }
}

std::vector<Instruction *> BasicBlock::remove_phi_from(BasicBlock *block) {
  auto to_remove = std::vector<Instruction *>{};
  for (auto *inst = instructions.Head; inst; inst = inst->Next) {
    if (!inst->isPHI())
      break;
    for (unsigned i = 1; i < inst->getNumOperands();) {
      auto *op = inst->getOperand(i);
      if (op == block) {
        inst->removeOperands(i - 1, i);
      } else {
        i += 2;
      }
      if (inst->getNumOperands() == 2) {
        inst->replaceAllUseWith(inst->getOperand(0));
        to_remove.push_back(inst);
      }
    }
  }
  // for (auto *inst : to_remove) {
  //   instructions.remove(inst);
  // }
  return to_remove;
}

} // namespace olc
