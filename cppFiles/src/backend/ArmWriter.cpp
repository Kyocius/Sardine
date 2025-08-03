#include <olc/backend/ArmWriter.h>
#include <optional>

namespace olc {

ArmWriter::Reg::~Reg() {
  if (id < 0)
    return;
  if (isFloat) {
    alloc->fltRegs.insert(id);
  } else {
    alloc->intRegs.insert(id);
  }
}

ArmWriter::Reg::Reg(Reg &&other)
    : isFloat(other.isFloat), id(other.id), alloc(other.alloc) {
  other.id = -1;
}

ArmWriter::Reg &ArmWriter::Reg::operator=(Reg &&other) {
  isFloat = other.isFloat;
  id = other.id;
  alloc = other.alloc;
  other.id = -1;
  return *this;
}

void ArmWriter::printModule(Module *module) {
  os << ".data\n";
  for (auto &global : module->globals) {
    printGlobalData(global);
  }

  os << ".bss\n";
  for (auto &global : module->globals) {
    printGlobalBss(global);
  }

  os << ".text\n";
  for (auto &func : module->functions) {
    os << ".global " << func->fnName << '\n';
  }
  for (auto &func : module->functions) {
    printFunc(func);
  }
}

void ArmWriter::printGlobalData(GlobalVariable *global) {
  if (!global->getInitializer())
    return;

  os << global->getName() << ":\n";
  std::function<void(Constant *)> printConstData = [&](Constant *val) {
    assert(val && "Invalid initializer");
    if (auto *arr = dyn_cast<ConstantArray>(val)) {
      for (auto *elt : arr->values) {
        printConstData(elt);
      }
      size_t totalCount = cast<ArrayType>(arr->getType())->getSize();
      // 不足的部分自动填充0
      if (totalCount > arr->values.size()) {
        os << ".zero " << 4 * (totalCount - arr->values.size()) << '\n';
      }
      os << ".size " << global->getName() << ", " << totalCount * 4 << '\n';
    } else if (auto *constVal = dyn_cast<ConstantValue>(val)) {
      if (constVal->isInt()) {
        os << ".word " << constVal->getInt() << '\n';
      } else {
        os << ".word " << reinterpretFloat(constVal->getFloat()) << '\n';
      }
    } else {
      olc_unreachable("NYI");
    }
  };
  printConstData(global->getInitializer());
}

void ArmWriter::printGlobalBss(GlobalVariable *global) {
  if (global->getInitializer()) {
    return;
  }
  os << global->getName() << ":\n";
  unsigned size = 4;
  if (auto *arrTy = dyn_cast<ArrayType>(global->getAllocatedType())) {
    assert(!arrTy->getArrayEltType()->isArrayTy() && "invalid");
    size = 4 * arrTy->getSize();
  }
  os << ".skip " << size << '\n';
}

void ArmWriter::printFunc(Function *function) {
  if (function->isBuiltin)
    return;

  curFunction = function;
  os << function->fnName << ":\n";

  CallInfo funcCallInfo = arrangeCallInfo(function->args);

  // 保护好参数寄存器，直到它们存入栈中
  std::vector<Reg> argIntRegs, argFloatRegs;
  for (unsigned i = 0; i < funcCallInfo.argsInIntReg.size(); i++) {
    argIntRegs.emplace_back(regAlloc.claimIntReg(i));
  }
  for (unsigned i = 0; i < funcCallInfo.argsInFloatReg.size(); i++) {
    argFloatRegs.emplace_back(regAlloc.claimFloatReg(i));
  }

  // 计算栈空间
  stackSize = 0;
  stackMap.clear();

  // 给函数内 call 指令的栈参数分配 stack slot
  // 后续在 Call CodeGen 中直接引用 [sp, #0] 开始的一段栈空间
  int callStackSize = 0;
  for (auto &bb : function->basicBlocks) {
    for (auto *instr = bb->instructions.Head; instr; instr = instr->Next) {
      if (auto *callInst = dyn_cast<CallInst>(instr)) {
        CallInfo callInfo = arrangeCallInfo(callInst->getArgs());
        callStackSize =
            std::max<int>(callInfo.argsOnStack.size(), callStackSize);
      }
    }
  }
  stackSize += callStackSize * 4;

  // 给本函数的寄存器参数分配 stack slot
  for (auto *arg : funcCallInfo.argsInIntReg) {
    stackMap[arg] = stackSize;
    stackSize += 4;
  }
  for (auto *arg : funcCallInfo.argsInFloatReg) {
    stackMap[arg] = stackSize;
    stackSize += 4;
  }

  // 给指令结果分配 stack slot
  for (auto *bb : function->basicBlocks) {
    for (auto *instr = bb->instructions.Head; instr; instr = instr->Next) {
      if (instr->tag == Value::Tag::Load)
        continue;
      if (instr->tag >= Value::Tag::BeginBooleanOp &&
          instr->tag <= Value::Tag::EndBooleanOp) {
        // For all br use, do not allocate stack. The codegen is in BranchInst.
        bool allBrUse = std::all_of(
            instr->uses.begin(), instr->uses.end(),
            [](const auto &use) { return isa<BranchInst>(use.user); });
        if (allBrUse)
          continue;
      }
      stackMap[instr] = stackSize;
      if (auto *alloca = dyn_cast<AllocaInst>(instr)) {
        if (auto *arrTy = dyn_cast<ArrayType>(alloca->getAllocatedType())) {
          assert(!arrTy->getArrayEltType()->isArrayTy() && "invalid");
          stackSize += 4 * arrTy->getSize();
        } else {
          stackSize += 4;
        }
      } else {
        stackSize += 4;
      }
    }
  }

  printArmInstr("push", {"{lr}"});
  const int pushSize = 4;

  // 对齐到 8
  if ((stackSize + pushSize) % 8 != 0) {
    stackSize += 4;
  }

  printArmInstr("sub", {"sp", "sp", getImme(stackSize, 8)});

  // 保存寄存器参数到栈
  for (unsigned i = 0; i < funcCallInfo.argsInIntReg.size(); i++) {
    storeRegToMemorySlot(argIntRegs.at(i), funcCallInfo.argsInIntReg.at(i));
  }
  for (unsigned i = 0; i < funcCallInfo.argsInFloatReg.size(); i++) {
    storeRegToMemorySlot(argFloatRegs.at(i), funcCallInfo.argsInFloatReg.at(i));
  }
  argIntRegs.clear();
  argFloatRegs.clear();

  // 栈上参数已经被调用方分配，直接插入 stack slot
  int argsOffset = stackSize + pushSize;
  for (unsigned i = 0; i < funcCallInfo.argsOnStack.size(); i++) {
    stackMap[funcCallInfo.argsOnStack[i]] = argsOffset;
    argsOffset += 4;
  }

  for (auto &bb : function->basicBlocks) {
    printBasicBlock(bb);
  }

  os << '\n';
}

void ArmWriter::printBasicBlock(BasicBlock *basicBlock) {
  os << getLabel(basicBlock) << ":\n";
  for (auto *instr = basicBlock->instructions.Head; instr;
       instr = instr->Next) {
    printInstr(instr);
  }
}

void ArmWriter::printInstr(Instruction *instr) {
  switch (instr->tag) {
  case Value::Tag::Alloca:
  case Value::Tag::Load: {
    // Do nothing for naive regalloc
    break;
  }
  case Value::Tag::GetElementPtr: {
    auto *gep = cast<GetElementPtrInst>(instr);
    auto reg_ptr = loadToReg(gep->getPointer());
    if (auto *constIndex = dyn_cast<ConstantValue>(gep->getIndex())) {
      int offset = 4 * constIndex->getInt();
      printArmInstr("add", {reg_ptr, reg_ptr, getImme(offset, 8)});
    } else {
      std::string reg_offset = loadToReg(gep->getIndex());
      printArmInstr("add", {reg_ptr, reg_ptr, reg_offset, "lsl #2"});
    }
    storeRegToMemorySlot(reg_ptr, instr);
    break;
  }
  case Value::Tag::Store: {
    // TODO: consider float register
    auto *storeInst = cast<StoreInst>(instr);
    auto reg_val = loadToReg(storeInst->getValue());
    storeRegToAddress(reg_val, storeInst->getPointer());
    break;
  }
  case Value::Tag::Add:
    printBinInstr("add", instr);
    break;
  case Value::Tag::Sub:
    printBinInstr("sub", instr);
    break;
  case Value::Tag::Mul:
    printBinInstr("mul", instr);
    break;
  case Value::Tag::Div: {
    if (instr->getType()->isIntegerTy()) {
      auto reg_lhs = regAlloc.claimIntReg(0);
      loadToSpecificReg(reg_lhs, instr->getOperand(0));
      auto reg_rhs = regAlloc.claimIntReg(1);
      loadToSpecificReg(reg_rhs, instr->getOperand(1));
      printArmInstr("bl", {"__aeabi_idiv"});
      storeRegToMemorySlot(reg_lhs, instr);
    } else {
      auto reg_lhs = loadToReg(instr->getOperand(0));
      auto reg_rhs = loadToReg(instr->getOperand(1));
      printArmInstr("vdiv.f32", {reg_lhs, reg_lhs, reg_rhs});
      storeRegToMemorySlot(reg_lhs, instr);
    }
    break;
  }
  case Value::Tag::Mod: {
    auto reg_lhs = regAlloc.claimIntReg(0);
    loadToSpecificReg(reg_lhs, instr->getOperand(0));
    auto reg_rhs = regAlloc.claimIntReg(1);
    loadToSpecificReg(reg_rhs, instr->getOperand(1));
    printArmInstr("bl", {"__aeabi_idivmod"});
    // 假设idivmod将除法结果放在R0，模结果放在R1
    printArmInstr("mov", {reg_rhs, "r1"}); // 将模结果移动到目标寄存器
    storeRegToMemorySlot(reg_rhs, instr);
    break;
  }
  case Value::Tag::IntToFloat: {
    auto *i2fInst = cast<IntToFloatInst>(instr);
    auto ireg = loadToReg(instr->getOperand(0));
    auto freg = regAlloc.allocFloatReg();
    printArmInstr("vmov.f32", {freg, ireg});
    printArmInstr("vcvt.f32.s32", {freg, freg});
    storeRegToMemorySlot(freg, instr);
    break;
  }
  case Value::Tag::FloatToInt: {
    auto *f2iInst = cast<FloatToIntInst>(instr);
    auto freg = loadToReg(instr->getOperand(0));
    printArmInstr("vcvt.s32.f32", {freg, freg});
    auto ireg = regAlloc.allocIntReg();
    printArmInstr("vmov.f32", {ireg, freg});
    storeRegToMemorySlot(ireg, instr);
    break;
  }
  case Value::Tag::Lt:
  case Value::Tag::Le:
  case Value::Tag::Ge:
  case Value::Tag::Gt:
  case Value::Tag::Eq:
  case Value::Tag::Ne: {
    // For all br use, do nothing. The codegen is in BranchInst.
    bool allBrUse = std::all_of(
        instr->uses.begin(), instr->uses.end(),
        [](const auto &use) { return isa<BranchInst>(use.user); });
    if (allBrUse)
      break;
    auto *cmpInst = cast<BinaryInst>(instr);
    if (isNaiveLogicalOp(cmpInst)) {
      auto reg_lhs = loadToReg(cmpInst->getLHS());
      if (cmpInst->tag == Value::Tag::Ne) {
        // Value Ne 0, Binarization
        printArmInstr("cmp", {reg_lhs, "#0"});
        printArmInstr("movne", {reg_lhs, "#1"});
        storeRegToMemorySlot(reg_lhs, instr);
      } else if (cmpInst->tag == Value::Tag::Eq) {
        // Value Eq 0, Logical negation
        printArmInstr("clz", {reg_lhs, reg_lhs});
        printArmInstr("lsrs", {reg_lhs, reg_lhs, "#5"});
        storeRegToMemorySlot(reg_lhs, instr);
      } else {
        olc_unreachable("Invalid tag");
      }
    } else {
      if (cmpInst->getLHS()->getType()->isFloatTy()) {
        auto reg_lhs = loadToReg(cmpInst->getLHS());
        auto reg_rhs = loadToReg(cmpInst->getRHS());
        printArmInstr("cmp", {reg_lhs, reg_rhs});
        // 从协处理器中转移条件标志
        printArmInstr("vmrs", {"APSR_nzcv", "FPSCR"});
        auto reg_res = regAlloc.allocIntReg();
        printArmInstr("mov" + getCondTagStr(cmpInst->tag), {reg_res, "#1"});
        printArmInstr(
            "mov" + getCondTagStr(getNotCond(cmpInst->tag)), {reg_res, "#0"});
        storeRegToMemorySlot(reg_lhs, instr);
      } else {
        auto reg_lhs = loadToReg(cmpInst->getLHS());
        auto reg_rhs = loadToReg(cmpInst->getRHS());
        printArmInstr("cmp", {reg_lhs, reg_rhs});
        printArmInstr("mov" + getCondTagStr(cmpInst->tag), {reg_lhs, "#1"});
        printArmInstr(
            "mov" + getCondTagStr(getNotCond(cmpInst->tag)), {reg_lhs, "#0"});
        storeRegToMemorySlot(reg_lhs, instr);
      }
    }
    break;
  }
  case Value::Tag::Branch: {
    auto *brInst = cast<BranchInst>(instr);
    auto *cond = dyn_cast<BinaryInst>(brInst->getCondition());
    assert(cond && cond->isCmpOp() && "Branch condition must be a cmp op");
    auto condTag = getCondTagStr(cond->tag);
    printCmpInstr(cond);
    printArmInstr(
        "b" + condTag, {getLabel(cast<BranchInst>(instr)->getTrueBlock())});
    printArmInstr("b", {getLabel(cast<BranchInst>(instr)->getFalseBlock())});
    break;
    break;
  }
  case Value::Tag::Jump:
    printArmInstr("b", {getLabel(cast<JumpInst>(instr)->getTargetBlock())});
    break;
  case Value::Tag::Return: {
    auto *retInst = cast<ReturnInst>(instr);
    std::optional<Reg> reg_ret;
    if (retInst->getNumOperands() == 1) {
      reg_ret = retInst->getReturnValue()->getType()->isIntegerTy()
                    ? regAlloc.claimIntReg(0)
                    : regAlloc.claimFloatReg(0);
      assignToSpecificReg(*reg_ret, retInst->getReturnValue());
    }
    printArmInstr("add", {"sp", "sp", getImme(stackSize, 8)});
    printArmInstr("pop", {"{lr}"});
    printArmInstr("bx", {"lr"});
    break;
  }
  case Value::Tag::Call: {
    auto *callInst = cast<CallInst>(instr);
    auto args = callInst->getArgs();
    auto &&[argsInIntRegs, argsInFloatRegs, argsOnStack] =
        arrangeCallInfo(args);

    // 认领寄存器
    std::unordered_map<int, Reg> intRegs, floatRegs;
    auto claimReg = [&](bool isFloat, int i) {
      if (isFloat) {
        if (floatRegs.find(i) == floatRegs.end()) {
          floatRegs.emplace(i, regAlloc.claimFloatReg(i));
        }
      } else {
        if (intRegs.find(i) == intRegs.end()) {
          intRegs.emplace(i, regAlloc.claimIntReg(i));
        }
      }
    };

    // 返回值 r0 / s0
    claimReg(callInst->getType()->isFloatTy(), 0);
    // 参数 r0-r3 / s0-s15
    for (unsigned i = 0; i < argsInIntRegs.size(); i++) {
      claimReg(false, i);
    }
    for (unsigned i = 0; i < argsInFloatRegs.size(); i++) {
      claimReg(true, i);
    }

    // 寄存器入参
    for (unsigned i = 0; i < argsInIntRegs.size(); i++) {
      assignToSpecificReg(intRegs.at(i), argsInIntRegs[i]);
    }
    for (unsigned i = 0; i < argsInFloatRegs.size(); i++) {
      assignToSpecificReg(floatRegs.at(i), argsInFloatRegs[i]);
    }

    // 其它入参压栈
    int argsOffset = 0;
    for (auto *argOnStack : argsOnStack) {
      auto reg_arg = loadToReg(argOnStack);
      printArmInstr(
          "str", {reg_arg, "[sp, #" + std::to_string(argsOffset) + "]"});
      argsOffset += 4;
    }
    printArmInstr("bl", {callInst->getCallee()->fnName});

    // 返回值存储到栈槽位
    if (callInst->getType()->isVoidTy()) {
      // do nothing
    } else {
      auto &reg_ret =
          callInst->getType()->isFloatTy() ? floatRegs.at(0) : intRegs.at(0);
      storeRegToMemorySlot(reg_ret, instr);
    }

    break;
  }
  default:
    std::cerr << "NYI Instruction tag: " << static_cast<int>(instr->tag)
              << "\n";
    olc_unreachable("NYI");
    break;
  }
}

static std::unordered_map<std::string, std::string> intInstr2FltInstrOpCode{
    {"add", "vadd.f32"}, {"sub", "vsub.f32"}, {"mul", "vmul.f32"},
    {"div", "vdiv.f32"}, {"cmp", "vcmp.f32"}, {"mov", "vmov.f32"},
    {"ldr", "vldr.32"},  {"str", "vstr.32"},
};

void ArmWriter::printArmInstr(
    const std::string &op, const std::vector<std::string> &operands) {

  // indent for instructions
  os << "  ";

  auto isFloatRegName = [](std::string const &name) {
    return name != "sp" && name.at(0) == 's';
  };

  if (intInstr2FltInstrOpCode.count(op) && isFloatRegName(operands.at(0))) {
    os << intInstr2FltInstrOpCode[op];
  } else {
    os << op;
  }
  os << " ";
  for (int i = 0; i < operands.size(); ++i) {
    if (i > 0) {
      os << ", ";
    }
    os << operands[i];
  }
  os << '\n';
}

void ArmWriter::printBinInstr(const std::string &op, Instruction *instr) {
  auto reg_lhs = loadToReg(instr->getOperand(0));
  auto reg_rhs = loadToReg(instr->getOperand(1));
  printArmInstr(op, {reg_lhs, reg_lhs, reg_rhs});
  storeRegToMemorySlot(reg_lhs, instr);
}

void ArmWriter::printCmpInstr(BinaryInst *instr) {
  // TODO: utilize Operand2 to handle immediate
  auto reg_lhs = loadToReg(instr->getOperand(0));
  auto reg_rhs = loadToReg(instr->getOperand(1));
  printArmInstr("cmp", {reg_lhs, reg_rhs});
  if (reg_lhs.isFloat) {
    // 从协处理器中转移条件标志
    printArmInstr("vmrs", {"APSR_nzcv", "FPSCR"});
  }
}

std::string ArmWriter::getStackOper(Value *val) {
  if (auto *ld = dyn_cast<LoadInst>(val))
    val = ld->getPointer();
  return "[sp, " + getImme(stackMap[val]) + "]";
}

std::string ArmWriter::getImme(uint32_t imm, int maxBits) {
  if (imm < (1 << maxBits)) {
    return "#" + std::to_string(imm);
  } else {
    auto reg = regAlloc.allocIntReg();
    printArmInstr("movw", {reg, "#" + std::to_string(imm & 0xffff)});
    printArmInstr("movt", {reg, "#" + std::to_string(imm >> 16)});
    return reg.abiName();
  }
}

std::string ArmWriter::getImme(float imm) {
  // reinterpret and load int
  union {
    float f;
    uint32_t i;
  } u;
  u.f = imm;
  auto reg_int = regAlloc.allocIntReg();
  printArmInstr("movw", {reg_int, "#" + std::to_string(u.i & 0xffff)});
  if (u.i >> 16)
    printArmInstr("movt", {reg_int, "#" + std::to_string(u.i >> 16)});
  auto reg_flt = regAlloc.allocFloatReg();
  printArmInstr("vmov.f32", {reg_flt, reg_int});
  return reg_flt.abiName();
}

std::string ArmWriter::getLabel(BasicBlock *bb) {
  if (labelMap.find(bb) == labelMap.end()) {
    labelMap[bb] = labelMap.size();
  }
  return "." + bb->label + "_" + std::to_string(labelMap[bb]);
}

} // namespace olc
