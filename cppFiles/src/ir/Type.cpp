#include <olc/ir/Type.h>

namespace olc {

namespace {

inline constexpr int SEED = 19260817;

static struct TypeCache {
  std::unordered_map<std::size_t, Type *> types;

  Type *getType(std::size_t hash) const {
    auto it = types.find(hash);
    if (it == types.end())
      return nullptr;
    return it->second;
  }

  void addType(std::size_t hash, Type *type) { types[hash] = type; }

} typeCache;

} // namespace

Type *Type::getPointerEltType() const {
  assert(isPointerTy() && "Not a pointer type!");
  return cast<PointerType>(this)->getPointeeType();
}

Type *Type::getArrayEltType() const {
  assert(isArrayTy() && "Not an array type!");
  return cast<ArrayType>(this)->getElementType();
}

VoidType *VoidType::get() {
  auto hash = static_cast<std::size_t>(Tag::Void);
  // no other combine
  if (auto *res = cast_if_present<VoidType>(typeCache.getType(hash)))
    return res;

  auto *res = new VoidType();
  typeCache.addType(hash, res);
  return res;
}

IntegerType *IntegerType::get() {
  auto hash = static_cast<std::size_t>(Tag::Integer);
  // no other combine
  if (auto *res = cast_if_present<IntegerType>(typeCache.getType(hash)))
    return res;

  auto *res = new IntegerType();
  typeCache.addType(hash, res);
  return res;
}

FloatType *FloatType::get() {
  auto hash = static_cast<std::size_t>(Tag::Float);
  // no other combine
  if (auto *res = cast_if_present<FloatType>(typeCache.getType(hash)))
    return res;

  auto *res = new FloatType();
  typeCache.addType(hash, res);
  return res;
}

LabelType *LabelType::get() {
  auto hash = static_cast<std::size_t>(Tag::Label);
  // no other combine
  if (auto *res = cast_if_present<LabelType>(typeCache.getType(hash)))
    return res;

  auto *res = new LabelType();
  typeCache.addType(hash, res);
  return res;
}

PointerType *PointerType::get(Type *pointeeType) {
  std::size_t hash = SEED;
  hash_combine(hash, static_cast<std::size_t>(Tag::Pointer));
  hash_combine(hash, std::hash<Type *>{}(pointeeType));
  if (auto *res = cast_if_present<PointerType>(typeCache.getType(hash)))
    return res;

  auto *res = new PointerType(pointeeType);
  typeCache.addType(hash, res);
  return res;
}

ArrayType *ArrayType::get(Type *elemType, std::size_t size) {
  std::size_t hash = SEED;
  hash_combine(hash, static_cast<std::size_t>(Tag::Array));
  hash_combine(hash, std::hash<Type *>{}(elemType));
  hash_combine(hash, size);
  if (auto *res = cast_if_present<ArrayType>(typeCache.getType(hash)))
    return res;

  auto *res = new ArrayType(elemType, size);
  typeCache.addType(hash, res);
  return res;
}

FunctionType *FunctionType::get(Type *retType, std::vector<Type *> argTypes) {
  std::size_t hash = SEED;
  hash_combine(hash, static_cast<std::size_t>(Tag::Function));
  hash_combine(hash, std::hash<Type *>{}(retType));
  for (auto *paramType : argTypes)
    hash_combine(hash, std::hash<Type *>{}(paramType));
  if (auto *res = cast_if_present<FunctionType>(typeCache.getType(hash)))
    return res;

  auto *res = new FunctionType(retType, std::move(argTypes));
  typeCache.addType(hash, res);
  return res;
}

} // namespace olc
