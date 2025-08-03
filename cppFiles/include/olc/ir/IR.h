#pragma once

#include <cassert>
#include <iostream>
#include <list>
#include <set>
#include <string>
#include <variant>
#include <vector>

#include <olc/Support.h>
#include <olc/ir/Type.h>

namespace olc {

// 前向声明
struct Use;
struct User;
struct ConstantValue;
struct ConstantArray;
struct Function;
struct Instruction;

struct Value {
  enum class Tag {
    // constant
    ConstValue,
    ConstArray,
    Global,
    Function,

    // misc
    Undef,
    Param,
    BasicBlock,

    // instructions
    Add,
    Sub,
    Mul,
    Div,
    Mod,
    Lt,
    Le,
    Ge,
    Gt,
    Eq,
    Ne,
    Branch,
    Jump,
    Return,
    GetElementPtr,
    Load,
    Store,
    Call,
    Alloca,
    Phi,
    IntToFloat, // 新增
    FloatToInt, // 新增

    // anchors
    BeginConst = ConstValue,
    EndConst = Global,
    BeginInst = Add,
    EndInst = FloatToInt,
    BeginBinOp = Add,
    EndBinOp = Ne,
    BeginBooleanOp = Lt,
    EndBooleanOp = Ne,
  } tag;
  Value(Tag tag, Type *type) : tag(tag), type(type) {}

  std::list<Use> uses;

  void addUse(User *user, int index);

  void removeUse(User *user, int index);

  void replaceAllUseWith(Value *v);

  Type *getType() const { return type; };

  bool isDefVar() const;

protected:
  Type *type;
};

struct Undef : public Value {
  Undef() : Value(Tag::Undef, VoidType::get()) {}

  static bool classof(const Value *V) { return V->tag == Tag::Undef; }

  static Undef *get() {
    static Undef undef;
    return &undef;
  }
};

struct User : public Value {
  std::vector<Value *> operands;

  User(Tag tag, Type *type, std::vector<Value *> operands = {})
      : Value(tag, type), operands(std::move(operands)) {
    for (auto i = 0u; i < this->operands.size(); ++i)
      if (auto &operand = this->operands[i])
        operand->addUse(this, i);
  }

  static bool classof(const Value *V) { return isa<Instruction>(V); }

  Value *getOperand(unsigned i) const;
  void setOperand(unsigned i, Value *v);
  void addOperand(Value *v);
  size_t getNumOperands() const { return operands.size(); }
  void removeOperands(unsigned i1, unsigned i2);

private:
  void setOperandWithoutRemoveUse(unsigned i, Value *v);
  friend struct Value;
};

struct Use {
  User *user;
  int index;

  Use(User *u, int index) : user(u), index(index) {}
  bool operator==(Use const &rhs) const {
    return user == rhs.user && index == rhs.index;
  }
};

struct BasicBlock : Value {
  Function *parent;
  std::string label;
  IList<Instruction> instructions;

  std::list<BasicBlock *> predecessors;
  std::list<BasicBlock *> successors;

  BasicBlock *idom;
  std::set<BasicBlock *> dom, domFrontier;

  BasicBlock(Function *parent, std::string const &label)
      : Value(Tag::BasicBlock, LabelType::get()), parent(parent), label(label) {
  }

  static bool classof(const Value *V) { return V->tag == Tag::BasicBlock; }

  template <typename InstT, typename... Args> InstT *create(Args &&...args) {
    auto *inst = new InstT(this, std::forward<Args>(args)...);
    instructions.push_back(inst);
    return inst;
  }

  std::vector<Instruction *> remove_phi_from(BasicBlock *);
};

struct Argument : Value {
  std::string argName;

  Argument(Type *type, std::string const &argName)
      : Value(Tag::Param, type), argName(argName) {}

  static bool classof(const Value *V) { return V->tag == Tag::Param; }
};

struct Function : User {
  bool isBuiltin;
  std::string fnName;
  std::vector<Argument *> args;
  std::list<BasicBlock *> basicBlocks;

  Function(
      Type *retType, std::string const &fnName, std::vector<Argument *> args)
      : User(Tag::Function, createFuncType(retType, args)), fnName(fnName),
        args(std::move(args)), isBuiltin(false) {
    if (!isBuiltin)
      basicBlocks.push_back(new BasicBlock(this, "entry"));
  }

  static bool classof(const Value *V) { return V->tag == Tag::Function; }

  BasicBlock *getEntryBlock() { return basicBlocks.front(); }

  void addBasicBlock(BasicBlock *bb) { basicBlocks.push_back(bb); }

  const std::list<BasicBlock *> &getBasicBlocks() const { return basicBlocks; }

  Type *getReturnType() const {
    return cast<FunctionType>(getType())->getRetType();
  }

  unsigned getArgNo(Argument *arg) {
    for (unsigned i = 0; i < args.size(); i++) {
      if (args[i] == arg)
        return i;
    }
    olc_unreachable("Unknown argument");
  }

private:
  static FunctionType *
  createFuncType(Type *retType, std::vector<Argument *> const &args) {
    std::vector<Type *> argTypes;
    for (auto *arg : args) {
      argTypes.push_back(arg->getType());
    }
    return FunctionType::get(retType, argTypes);
  }
};

struct Instruction : User, IListNode<Instruction> {
  BasicBlock *parent;
  Instruction(
      BasicBlock *bb, Type *type, Tag tag, std::vector<Value *> operands = {})
      : User(tag, type, std::move(operands)), parent(bb) {}

  static bool classof(const Value *V) {
    return V->tag >= Tag::BeginInst && V->tag <= Tag::EndInst;
  }

  void erase() {
    assert(uses.empty());
    // Unlink all operands.
    for (auto i = 0u; i < operands.size(); ++i)
      setOperand(i, nullptr);
    parent->instructions.remove(this);
  }

  bool isPHI() const { return tag == Tag::Phi; }
};

struct BinaryInst : Instruction {
  BinaryInst(BasicBlock *bb, Tag tag, Value *lhs, Value *rhs)
      : Instruction(bb, inferType(tag, lhs, rhs), tag, {lhs, rhs}) {}

  static bool classof(const Value *V) {
    return V->tag >= Tag::BeginBinOp && V->tag <= Tag::EndBinOp;
  }

  Value *getLHS() const { return getOperand(0); }
  Value *getRHS() const { return getOperand(1); }

  bool isCmpOp() const {
    return tag >= Tag::BeginBooleanOp && tag <= Tag::EndBooleanOp;
  }

  bool isCommutable() const {
    return tag == Tag::Add || tag == Tag::Mul || tag == Tag::Eq ||
           tag == Tag::Ne;
  }

  bool isCommutableCmp() const {
    return tag == Tag::Lt || tag == Tag::Le || tag == Tag::Ge || tag == Tag::Gt;
  }

private:
  Type *inferType(Tag tag, Value *lhs, Value *rhs) {
    assert(tag >= Tag::BeginBinOp && tag <= Tag::EndBinOp);
    assert(lhs->getType() == rhs->getType() && "Type mismatch");
    assert(
        !lhs->getType()->isPointerTy() && "Pointers not allowed in binary op");
    if (tag >= Tag::BeginBooleanOp && tag <= Tag::EndBooleanOp)
      // i32 type for bool
      return IntegerType::get();
    return lhs->getType();
  }
};

struct CallInst : Instruction {
  CallInst(BasicBlock *bb, Function *callee, std::vector<Value *> args)
      : Instruction(bb, callee->getReturnType(), Tag::Call, {}) {
    addOperand(callee);
    for (auto *arg : args) {
      addOperand(arg);
    }
  }

  static bool classof(const Value *V) { return V->tag == Tag::Call; }

  Function *getCallee() const { return cast<Function>(getOperand(0)); }

  std::vector<Value *> getArgs() const {
    return std::vector<Value *>(operands.begin() + 1, operands.end());
  }
};

struct BranchInst : Instruction {
  BranchInst(
      BasicBlock *bb, Value *cond, BasicBlock *ifTrue, BasicBlock *ifFalse)
      : Instruction(bb, VoidType::get(), Tag::Branch, {cond, ifTrue, ifFalse}) {
  }

  static bool classof(const Value *V) { return V->tag == Tag::Branch; }

  Value *getCondition() const { return getOperand(0); }
  BasicBlock *getTrueBlock() const { return cast<BasicBlock>(getOperand(1)); }
  BasicBlock *getFalseBlock() const { return cast<BasicBlock>(getOperand(2)); }
};

struct JumpInst : Instruction {
  JumpInst(BasicBlock *bb, BasicBlock *target)
      : Instruction(bb, VoidType::get(), Tag::Jump, {target}) {}

  static bool classof(const Value *V) { return V->tag == Tag::Jump; }

  BasicBlock *getTargetBlock() const { return cast<BasicBlock>(getOperand(0)); }
};

struct ReturnInst : Instruction {
  ReturnInst(BasicBlock *bb, Value *val)
      : Instruction(bb, VoidType::get(), Tag::Return, {val}) {}

  ReturnInst(BasicBlock *bb) : Instruction(bb, VoidType::get(), Tag::Return) {}

  static bool classof(const Value *V) { return V->tag == Tag::Return; }

  Value *getReturnValue() const {
    if (getNumOperands() > 0)
      return getOperand(0);
    return nullptr;
  }
};

struct AllocaInst : Instruction {
  AllocaInst(BasicBlock *bb, Type *elementType)
      : Instruction(bb, PointerType::get(elementType), Tag::Alloca) {}

  static bool classof(const Value *V) { return V->tag == Tag::Alloca; }

  Type *getAllocatedType() const { return getType()->getPointerEltType(); }
  int getAllocatedSize() const {
    if (auto *arrTy = dyn_cast<ArrayType>(getAllocatedType())) {
      assert(!arrTy->getArrayEltType()->isArrayTy() && "invalid");
      return 4 * arrTy->getSize();
    } else {
      return 4;
    }
  }
};

struct StoreInst : Instruction {
  StoreInst(BasicBlock *bb, Value *val, Value *ptr)
      : Instruction(bb, VoidType::get(), Tag::Store, {val, ptr}) {
    assert(val->getType() == ptr->getType()->getPointerEltType());
  }

  static bool classof(const Value *V) { return V->tag == Tag::Store; }

  Value *getValue() const { return getOperand(0); }
  Value *getPointer() const { return getOperand(1); }
};

struct LoadInst : Instruction {
  LoadInst(BasicBlock *bb, Value *ptr)
      : Instruction(bb, ptr->getType()->getPointerEltType(), Tag::Load, {ptr}) {
  }

  static bool classof(const Value *V) { return V->tag == Tag::Load; }

  Value *getPointer() const { return getOperand(0); }
};

struct GetElementPtrInst : Instruction {
  GetElementPtrInst(BasicBlock *bb, Value *ptr, Value *idx)
      : Instruction(
            bb,
            PointerType::get(
                ptr->getType()->getPointerEltType()->getArrayEltType()),
            Tag::GetElementPtr, {ptr, cast<Value>(idx)}) {
    // assert(!type->isPointerTy() && "Should access into flat elements");
  }

  static bool classof(const Value *V) { return V->tag == Tag::GetElementPtr; }

  Value *getPointer() const { return getOperand(0); }
  Value *getIndex() const { return getOperand(1); }
};

struct IntToFloatInst : Instruction {
  IntToFloatInst(BasicBlock *bb, Value *intVal)
      : Instruction(bb, FloatType::get(), Tag::IntToFloat, {intVal}) {
    assert(intVal->getType()->isIntegerTy());
  }

  static bool classof(const Value *V) { return V->tag == Tag::IntToFloat; }

  Value *getIntValue() const { return getOperand(0); }
};

struct FloatToIntInst : Instruction {
  FloatToIntInst(BasicBlock *bb, Value *floatVal)
      : Instruction(bb, IntegerType::get(), Tag::FloatToInt, {floatVal}) {
    assert(floatVal->getType()->isFloatTy());
  }

  static bool classof(const Value *V) { return V->tag == Tag::FloatToInt; }

  Value *getFloatValue() const { return getOperand(0); }
};

struct PhiInst : Instruction {
  PhiInst(BasicBlock *bb, Type *type) : Instruction(bb, type, Tag::Phi, {}) {
    for (auto *pred : bb->predecessors) {
      addOperand(Undef::get());
      addOperand(pred);
    }
  }

  static bool classof(const Value *V) { return V->tag == Tag::Phi; }

  Value *getIncomingValueForBlock(BasicBlock *bb) const {
    for (unsigned i = 0; i < operands.size(); i += 2) {
      if (cast<BasicBlock>(getOperand(i + 1)) == bb)
        return getOperand(i);
    }
    olc_unreachable("No incoming value for block");
  }

  void setIncomingValueForBlock(Value *val, BasicBlock *bb) {
    for (unsigned i = 0; i < operands.size(); i += 2) {
      if (cast<BasicBlock>(getOperand(i + 1)) == bb) {
        setOperand(i, val);
        return;
      }
    }
    olc_unreachable("No incoming value for block");
  }

  BasicBlock *getIncomingBlock(unsigned i) const {
    return cast<BasicBlock>(getOperand(i * 2 + 1));
  }

  Value *getIncomingValue(unsigned i) const { return getOperand(i * 2); }

  unsigned getNumIncomingValues() const { return operands.size() / 2; }
};

struct Constant : Value {
  // just a base class, no actual features
  Constant(Value::Tag tag, Type *type) : Value(tag, type) {}

  virtual void print(std::ostream &os) const = 0;

  static bool classof(const Value *V) {
    return V->tag >= Tag::BeginConst && V->tag <= Tag::EndConst;
  }
};

struct ConstantValue : Constant {
  std::variant<int, float> value;

  ConstantValue(int value)
      : Constant(Tag::ConstValue, IntegerType::get()), value(value) {}

  ConstantValue(float value)
      : Constant(Tag::ConstValue, FloatType::get()), value(value) {}

  bool isInt() const { return value.index() == 0; }
  bool isFloat() const { return value.index() == 1; }
  int getInt() const { return std::get<int>(value); }
  float getFloat() const { return std::get<float>(value); }

  bool isZero() const {
    if (isInt())
      return getInt() == 0;
    else
      return getFloat() == 0.0;
  }

  void print(std::ostream &os) const override {
    getType()->print(os);
    if (isInt())
      os << " " << getInt();
    else
      os << " " << getFloat();
  }

  template <typename F> ConstantValue *map(F &&f) {
    std::variant<int, float> newVal;
    if (std::holds_alternative<int>(value))
      return new ConstantValue{
          static_cast<int>(std::forward<F>(f)(std::get<int>(value)))};
    else
      return new ConstantValue{
          static_cast<float>(std::forward<F>(f)(std::get<float>(value)))};
  }

  static bool classof(const Value *V) { return V->tag == Tag::ConstValue; }
};

struct ConstantArray : Constant {
  std::vector<Constant *> values;

  template <typename T> Constant *constructConst(T &&val) {
    using DecayT = std::decay_t<T>;
    if constexpr (
        std::is_same_v<DecayT, int> || std::is_same_v<DecayT, float>) {
      return new ConstantValue(val);
    } else {
      return cast<Constant>(val);
    }
  }

  template <typename... Args>
  ConstantArray(Type *type, Args &&...args)
      : ConstantArray(type, std::vector<Constant *>{constructConst(args)...}) {}

  ConstantArray(Type *type, std::vector<Constant *> values)
      : Constant(Tag::ConstArray, type), values(std::move(values)) {}

  void print(std::ostream &os) const override {
    os << "[";
    for (unsigned i = 0; i < values.size(); i++) {
      if (i > 0)
        os << ", ";
      values[i]->print(os);
    }
    os << "]";
  }

  static bool classof(const Value *V) { return V->tag == Tag::ConstArray; }
};

// TODO: model globals
// TODO: Constant, initval
struct GlobalVariable : Constant {
  Constant *initializer;

  // null initializer means zero init
  GlobalVariable(
      Type *type, std::string const &name, Constant *initializer = nullptr)
      : Constant(Tag::Global, PointerType::get(type)), initializer(initializer),
        name(name) {}

  static bool classof(const Value *V) { return V->tag == Tag::Global; }

  std::string getName() const { return name; }

  Constant *getInitializer() const { return initializer; }

  Type *getAllocatedType() const { return getType()->getPointerEltType(); }

  void print(std::ostream &os) const override { os << "@" << name; }

  bool isInt() const { return getAllocatedType()->isIntegerTy(); }
  bool isFloat() const { return getAllocatedType()->isFloatTy(); }

private:
  std::string name;
};

struct Module {
  std::list<Function *> functions;
  std::list<GlobalVariable *> globals;

  void addFunction(Function *fn) { functions.push_back(fn); }
  void addGlobal(GlobalVariable *gv) { globals.push_back(gv); }

  GlobalVariable *getGlobal(const std::string &name) const {
    for (auto *gv : globals) {
      if (gv->getName() == name) {
        return gv;
      }
    }
    olc_unreachable("Unknown global variable");
  }

  Function *getFunction(const std::string &name) const {
    for (auto *fn : functions) {
      if (fn->fnName == name) {
        return fn;
      }
    }
    olc_unreachable("Unknown function name");
  }
};

} // namespace olc
