#include <olc/ir/IR.h>
#include <olc/passes/Pass.h>
#include <olc/passes/Utils.h>

namespace olc {

class CanonicalizePass : public FunctionPass {
public:
  CanonicalizePass() : FunctionPass(&ID) {}

  // Op[C, V] -> Op'[V, C] ; Op = Add, Mul, Lt, Le, Ge, Gt, Eq, Ne
  // Sub[V, C] -> Add[V, -C]
  // Add[V, 0] -> V
  // Sub[V, 0] -> V
  Value *canonicalizeBinary(BinaryInst *inst) const {
    auto tag = inst->tag;
    auto *lhs = inst->getLHS(), *rhs = inst->getRHS();
    bool lhsConst = isa<ConstantValue>(lhs), rhsConst = isa<ConstantValue>(rhs);
    // assert((!lhsConst || !rhsConst) && "should run constfold first");
    if (lhsConst && !rhsConst) {
      if (inst->isCommutable()) {
        std::swap(lhs, rhs);
        std::swap(lhsConst, rhsConst);
      }
      if (inst->isCommutableCmp()) {
        std::swap(lhs, rhs);
        std::swap(lhsConst, rhsConst);
        switch (tag) {
        case Value::Tag::Lt:
          tag = Value::Tag::Gt;
          break;
        case Value::Tag::Le:
          tag = Value::Tag::Ge;
          break;
        case Value::Tag::Ge:
          tag = Value::Tag::Le;
          break;
        case Value::Tag::Gt:
          tag = Value::Tag::Lt;
          break;
        default:
          break;
        }
      }
    }

    if ((tag == Value::Tag::Add || tag == Value::Tag::Sub) && rhsConst &&
        cast<ConstantValue>(rhs)->isZero()) {
      return lhs;
    }

    inst->tag = tag;
    inst->setOperand(0, lhs);
    inst->setOperand(1, rhs);

    return inst;
  }

  Value *canonicalizePhi(PhiInst *inst) const {
    std::vector<Value *> incomingValues;
    for (unsigned i = 0; i < inst->getNumIncomingValues(); i++)
      // NOTE: To use with GCM, the Phi with undef should not be simplified.
      // if (auto val = inst->getIncomingValue(i); !isa<Undef>(val))
      if (auto val = inst->getIncomingValue(i); true)
        incomingValues.push_back(val);
    for (auto *val : incomingValues) {
      if (val != incomingValues.front()) {
        return inst;
      }
    }
    return incomingValues.front();
  }

  Value *canonicalize(Instruction *inst) const {
    if (auto *binInst = dyn_cast<BinaryInst>(inst)) {
      return canonicalizeBinary(binInst);
    } else if (auto *phiInst = dyn_cast<PhiInst>(inst)) {
      return canonicalizePhi(phiInst);
    }
    return inst;
  }

  bool runOnFunction(Function &function) override {
    bool changed = false;

    auto rpo = getFunctionRPO(&function);

    for (auto *bb : rpo) {
      for (auto *inst = bb->instructions.Head; inst;) {
        auto *instNext = inst->Next;

        if (auto *newInst = canonicalize(inst); newInst != inst) {
          inst->replaceAllUseWith(newInst);
          inst->erase();
          changed = true;
        }

        inst = instNext;
      }
    }

    return changed;
  }

  std::string getName() const override { return "CanonicalizePass"; }

private:
  static const void *ID;
};

const void *CanonicalizePass::ID = reinterpret_cast<void *>(0xCA7071C0);

} // namespace olc
