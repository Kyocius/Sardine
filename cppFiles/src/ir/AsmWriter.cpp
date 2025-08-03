#include <olc/ir/AsmWriter.h>

namespace olc {

void AssemblyWriter::printModule(Module *module) {
  for (auto &global : module->globals) {
    printGlobal(global);
  }
  os << "\n";
  for (auto &func : module->functions) {
    printFunc(func);
    os << "\n";
  }
}

void AssemblyWriter::printGlobal(GlobalVariable *global) {
  os << "@" << global->getName() << " = global ";
  global->getAllocatedType()->print(os);
  os << ", ";
  if (auto *initializer = global->getInitializer())
    initializer->print(os);
  else
    os << "zeroinitializer";
  os << "\n";
}

void AssemblyWriter::prepareNamesForFunc(Function *function) {
  nameManager.reset();
  for (auto &bb : function->basicBlocks) {
    for (auto *instr = bb->instructions.Head; instr; instr = instr->Next) {
      nameManager.add(instr);
    }
  }
}

void AssemblyWriter::printFunc(Function *function) {

  if (function->isBuiltin) {
    os << "declare builtin @" << function->fnName;
  } else {
    os << "define @" << function->fnName;
  }

  // arguments
  os << "(";
  for (unsigned i = 0; i < function->args.size(); i++) {
    if (i > 0)
      os << ", ";
    function->args[i]->getType()->print(os);
    os << " %" << function->args[i]->argName;
  }

  os << ") -> ";
  function->getReturnType()->print(os);

  if (function->isBuiltin) {
    os << "\n";
  } else {
    prepareNamesForFunc(function);
    os << " {\n";
    for (auto &bb : function->basicBlocks) {
      printBasicBlock(bb);
    }
    os << "}\n";
  }
}

void AssemblyWriter::printBasicBlock(BasicBlock *basicBlock) {
  os << basicBlock->label << ":\n";
  if (!basicBlock->predecessors.empty()) {
    os << "; predecessors:";
    for (auto *pred : basicBlock->predecessors) {
      os << " " << pred->label;
    }
    os << "\n";
  }
  if (!basicBlock->successors.empty()) {
    os << "; successors:";
    for (auto *succ : basicBlock->successors) {
      os << " " << succ->label;
    }
    os << "\n";
  }

  for (auto *instr = basicBlock->instructions.Head; instr;
       instr = instr->Next) {
    printInstr(instr);
  }
}

constexpr char const *kInstTagToOpName[] = {
    "add", "sub", "mul",  "div",    "mod",
    "lt",  "le",  "ge",   "gt",     "eq",
    "ne",  "br",  "jmp",  "ret",    "getelementptr",
    "ld",  "st",  "call", "alloca", "phi",
    "i2f", "f2i"};

static_assert(
    sizeof(kInstTagToOpName) / sizeof(kInstTagToOpName[0]) ==
        static_cast<int>(Value::Tag::EndInst) -
            static_cast<int>(Value::Tag::BeginInst) + 1,
    "kInstTagToOpName size mismatch");

void AssemblyWriter::printInstr(Instruction *instruction) {
  auto *opName = kInstTagToOpName
      [static_cast<int>(instruction->tag) -
       static_cast<int>(Value::Tag::BeginInst)];

  // indent for instructions
  os << "  ";
  if (!instruction->getType()->isVoidTy())
    os << "%" << nameManager[instruction] << " = ";

  os << opName;

  if (auto *instr = dyn_cast<AllocaInst>(instruction)) {
    os << " ";
    instr->getAllocatedType()->print(os);
  }

  for (unsigned i = 0; i < instruction->getNumOperands(); i++) {
    auto &op = instruction->operands[i];
    if (!isa<CallInst>(instruction)) {
      if (i > 0)
        os << ",";
      os << " ";
    } else {
      if (i > 1)
        os << ",";
      if (i != 1)
        os << " ";
    }
    if (isa<PhiInst>(instruction) && i % 2 == 0) {
      os << "[";
    }
    if (op == Undef::get()) {
      os << "undef";
    } else if (auto *constVal = dyn_cast<Constant>(op)) {
      constVal->print(os);
    } else if (auto *instr = dyn_cast<Instruction>(op)) {
      os << "%" << nameManager[instr];
    } else if (auto *arg = dyn_cast<Argument>(op)) {
      os << "%" << arg->argName;
    } else if (auto *bb = dyn_cast<BasicBlock>(op)) {
      os << "label %" << bb->label;
    } else if (auto *func = dyn_cast<Function>(op)) {
      os << "@" << func->fnName;
      os << "(";
    } else {
      olc_unreachable("NYI");
    }
    if (isa<PhiInst>(instruction) && i % 2 == 1) {
      os << "]";
    }
  }

  if (auto *instr = dyn_cast<CallInst>(instruction)) {
    os << ")";
  }

  os << "\n";
}
} // namespace olc
