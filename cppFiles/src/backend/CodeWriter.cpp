#include <olc/backend/CodeWriter.h>
#include <olc/backend/MachineIR.h>

namespace olc {

void CodeWriter::printModule(AsmModule *module) {
  for (auto &global : module->globals) {
    printGlobal(global);
  }
  os << "\n";
  for (auto &func : module->funcs) {
    printFunc(func);
    os << "\n";
  }
}

void CodeWriter::printGlobal(GlobalVariable *global) {
  os << ".global " << global->getName() << "\n";
}

void CodeWriter::printFunc(AsmFunc *func) {
  if (func->isBuiltin) {
    return;
  } else {
    os << func->name << ":\n";
    for (auto &lable : func->labels) {
      printLabel(lable);
    }
  }
}

void CodeWriter::printLabel(AsmLabel *label) {
  os << label->name << ":\n";
  for (auto inst = label->Head; inst != nullptr; inst = inst->Next) {
    printInst(inst);
  }
}

std::string CodeWriter::to_string(AsmValue *value) {
  std::string res;
  if (auto *reg = dyn_cast<AsmReg>(value)) {
    if (auto *preg = dyn_cast<PReg>(reg)) {
      res = preg->abiName();
    } else if (auto *vreg = dyn_cast<VReg>(reg)) {
      res = vreg->abiName();
    }
  } else if (auto *imm = dyn_cast<AsmImm>(value)) {
    res = std::to_string(imm->hexValue);
  } else {
    olc_unreachable("Invalid asm value");
  }
  return res;
}

void CodeWriter::printInst(AsmInst *inst) {
  static std::vector<std::string> tags = {
      "add",  "sub",   "mul",  "div", "mod",        "lsl",   "lsr",    "asr",
      "rsb",  "smmul", "and",  "cmp", "branch",     "jump",  "return", "move",
      "load", "store", "call", "cvt", "loadglobal", "string"};

  std::string op = tags[(int)inst->tag];
  if (auto *binInst = dyn_cast<AsmBinaryInst>(inst)) {
    printCodeInstr(
        op, {to_string(binInst->dst), to_string(binInst->lhs),
             to_string(binInst->rhs)});

  } else if (auto *cmpInst = dyn_cast<AsmCompareInst>(inst)) {
    printCodeInstr(op, {to_string(cmpInst->lhs), to_string(cmpInst->rhs)});

  } else if (auto *branchInst = dyn_cast<AsmBranchInst>(inst)) {
    static std::vector<std::string> condTags = {"al", "eq", "ne", "lt",
                                                "le", "ge", "gt"};
    std::string cond = "b";
    if (branchInst->pred != AsmPredicate::Al) {
      cond += condTags[(int)branchInst->pred];
    }
    printCodeInstr(cond, {cond + " ." + branchInst->trueTarget->name});
    printCodeInstr("b", {cond + " ." + branchInst->falseTarget->name});
  } else if (auto *jumpInst = dyn_cast<AsmJumpInst>(inst)) {
    printCodeInstr("b", {". " + jumpInst->target->name});
  } else if (auto *retInst = dyn_cast<AsmReturnInst>(inst)) {
    printCodeInstr(op, {});
  } else if (auto *moveInst = dyn_cast<AsmMoveInst>(inst)) {
    printCodeInstr(op, {to_string(moveInst->dst), to_string(moveInst->src)});
  } else if (auto *loadInst = dyn_cast<AsmLoadInst>(inst)) {
    printCodeInstr(op, {to_string(loadInst->dst), to_string(loadInst->addr)});
  } else if (auto *storeInst = dyn_cast<AsmStoreInst>(inst)) {
    printCodeInstr(op, {to_string(storeInst->src), to_string(storeInst->addr)});
  } else if (auto *callInst = dyn_cast<AsmCallInst>(inst)) {
    printCodeInstr(op, {callInst->callee});
  } else if (auto *cvtInst = dyn_cast<AsmConvertInst>(inst)) {
    if (cvtInst->type == AsmConvertInst::CvtType::f2i) {
      op += ".f2i";
    } else if (cvtInst->type == AsmConvertInst::CvtType::i2f) {
      op += ".i2f";
    }
    printCodeInstr(op, {to_string(cvtInst->dst), to_string(cvtInst->src)});
  } else {
    os << "invalid instr here" << std::endl;
  }
}
} // namespace olc
