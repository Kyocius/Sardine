#include <olc/passes/ConstantFoldingPass.h>

namespace olc {
ConstantValue *
ConstFold(BinaryInst *binInst, ConstantValue *lhs, ConstantValue *rhs) {
  ConstantValue *result = nullptr;
  switch (binInst->tag) {
  case Value::Tag::Add:
    if (lhs->isInt()) {
      result = new ConstantValue(lhs->getInt() + rhs->getInt());
    } else {
      result = new ConstantValue(lhs->getFloat() + rhs->getFloat());
    }
    break;
  case Value::Tag::Sub: {
    if (lhs->isInt()) {
      result = new ConstantValue(lhs->getInt() - rhs->getInt());
    } else {
      result = new ConstantValue(lhs->getFloat() - rhs->getFloat());
    }
    break;
  }
  case Value::Tag::Mul: {
    if (lhs->isInt()) {
      result = new ConstantValue(lhs->getInt() * rhs->getInt());
    } else {
      result = new ConstantValue(lhs->getFloat() * rhs->getFloat());
    }
    break;
  }
  case Value::Tag::Div: {
    if (lhs->isInt()) {
      result = new ConstantValue(lhs->getInt() / rhs->getInt());
    } else {
      result = new ConstantValue(lhs->getFloat() / rhs->getFloat());
    }
    break;
  }
  case Value::Tag::Mod: {
    result = new ConstantValue(lhs->getInt() % rhs->getInt());
    break;
  }
  case Value::Tag::Lt: {
    if (lhs->isInt()) {
      result = new ConstantValue(lhs->getInt() < rhs->getInt());
    } else {
      result = new ConstantValue(lhs->getFloat() < rhs->getFloat());
    }
    break;
  }
  case Value::Tag::Le: {
    if (lhs->isInt()) {
      result = new ConstantValue(lhs->getInt() <= rhs->getInt());
    } else {
      result = new ConstantValue(lhs->getFloat() <= rhs->getFloat());
    }
    break;
  }
  case Value::Tag::Gt: {
    if (lhs->isInt()) {
      result = new ConstantValue(lhs->getInt() > rhs->getInt());
    } else {
      result = new ConstantValue(lhs->getFloat() > rhs->getFloat());
    }
    break;
  }
  case Value::Tag::Ge: {
    if (lhs->isInt()) {
      result = new ConstantValue(lhs->getInt() >= rhs->getInt());
    } else {
      result = new ConstantValue(lhs->getFloat() >= rhs->getFloat());
    }
    break;
  }
  case Value::Tag::Eq: {
    if (lhs->isInt()) {
      result = new ConstantValue(lhs->getInt() == rhs->getInt());
    } else {
      result = new ConstantValue(lhs->getFloat() == rhs->getFloat());
    }
    break;
  }
  case Value::Tag::Ne: {
    if (lhs->isInt()) {
      result = new ConstantValue(lhs->getInt() != rhs->getInt());
    } else {
      result = new ConstantValue(lhs->getFloat() != rhs->getFloat());
    }
    break;
  }
  default:
    break;
  }
  return result;
}
} // namespace olc