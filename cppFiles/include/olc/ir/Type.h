#pragma once

#include <iostream>
#include <unordered_map>

#include <olc/Support.h>

namespace olc {

struct Type {
  enum class Tag {
    Void,
    Integer,
    Float,
    Label,
    Pointer,
    Array,
    Function,
  } tag;

  Type(Tag tag) : tag(tag) {}

  virtual ~Type() = default;
  virtual void print(std::ostream &os) const = 0;

  bool isVoidTy() const { return tag == Tag::Void; }
  bool isIntegerTy() const { return tag == Tag::Integer; }
  bool isFloatTy() const { return tag == Tag::Float; }
  bool isPointerTy() const { return tag == Tag::Pointer; }
  bool isArrayTy() const { return tag == Tag::Array; }
  bool isLabelTy() const { return tag == Tag::Label; }
  bool isFunctionTy() const { return tag == Tag::Function; }
  Type *getPointerEltType() const;
  Type *getArrayEltType() const;
};

class VoidType : public Type {
  VoidType() : Type(Tag::Void) {}

public:
  static bool classof(const Type *T) { return T->tag == Tag::Void; }

  static VoidType *get();

  void print(std::ostream &os) const override { os << "void"; }
};

class IntegerType : public Type {
  IntegerType() : Type(Tag::Integer) {}

public:
  static bool classof(const Type *T) { return T->tag == Tag::Integer; }

  static IntegerType *get();

  void print(std::ostream &os) const override { os << "i32"; }
};

class FloatType : public Type {
  FloatType() : Type(Tag::Float) {}

public:
  static bool classof(const Type *T) { return T->tag == Tag::Float; }

  static FloatType *get();

  void print(std::ostream &os) const override { os << "f32"; }
};

class LabelType : public Type {
  LabelType() : Type(Tag::Label) {}

public:
  static bool classof(const Type *T) { return T->tag == Tag::Label; }

  static LabelType *get();

  void print(std::ostream &os) const override { os << "label"; }
};

class PointerType : public Type {
  Type *pointeeType;

  PointerType(Type *pointeeType)
      : Type(Tag::Pointer), pointeeType(pointeeType) {}

public:
  static bool classof(const Type *T) { return T->tag == Tag::Pointer; }

  static PointerType *get(Type *pointeeType);

  Type *getPointeeType() const { return pointeeType; }

  void print(std::ostream &os) const override {
    pointeeType->print(os);
    os << "*";
  }
};

class ArrayType : public Type {
  Type *elemType;
  size_t size;

  ArrayType(Type *elemType, size_t size)
      : Type(Tag::Array), elemType(elemType), size(size) {
    assert(size > 0 && "Array size must be greater than 0");
  }

public:
  static bool classof(const Type *T) { return T->tag == Tag::Array; }

  static ArrayType *get(Type *elemType, size_t size);

  Type *getElementType() const { return elemType; }

  size_t getSize() const { return size; }

  void print(std::ostream &os) const override {
    os << "[";
    elemType->print(os);
    os << " x " << size << "]";
  }
};

class FunctionType : public Type {
  std::vector<Type *> argTypes;
  Type *retType;

  FunctionType(Type *retType, std::vector<Type *> argTypes)
      : Type(Tag::Function), argTypes(std::move(argTypes)), retType(retType) {}

public:
  static bool classof(const Type *T) { return T->tag == Tag::Function; }

  static FunctionType *get(Type *retType, std::vector<Type *> argTypes);

  const std::vector<Type *> &getArgTypes() const { return argTypes; }
  Type *getRetType() const { return retType; }

  void print(std::ostream &os) const override {
    os << "func(";
    for (auto &paramType : argTypes) {
      paramType->print(os);
      os << ", ";
    }
    os << ") -> ";
    retType->print(os);
  }
};

} // namespace olc
