#pragma once

#include <olc/Support.h>

#include <olc/backend/MachineIR.h>
#include <olc/backend/RegAlloc.h>
#include <olc/ir/IR.h>

#include <map>

namespace olc {

struct ArmGen {

  std::ostream &os;
  AsmModule *module = nullptr;
  AsmFunc *curFunc = nullptr;
  std::unordered_map<AsmLabel *, int> labelMap;

  // When exceeding 1000, split the label and insert ltorg.
  size_t labelInstCounter = 0;
  // Generate a unique name for the new split label.
  size_t labelSplitCounter = 0;
  static constexpr size_t kMaxInstInLabel = 1000;

  std::unordered_map<std::string, std::string> i2fOpCode{
      {"add", "vadd.f32"},  {"sub", "vsub.f32"}, {"mul", "vmul.f32"},
      {"sdiv", "vdiv.f32"}, {"cmp", "vcmp.f32"}, {"mov", "vmov.f32"},
      {"ldr", "vldr.32"},   {"str", "vstr.32"},
  };

  ArmGen(std::ostream &os, AsmModule *module) : os(os), module(module) {}

  std::string getCondStr(AsmPredicate pred) {
    switch (pred) {
    case AsmPredicate::Al:
      return "al";
    case AsmPredicate::Eq:
      return "eq";
    case AsmPredicate::Ne:
      return "ne";
    case AsmPredicate::Lt:
      return "lt";
    case AsmPredicate::Le:
      return "le";
    case AsmPredicate::Gt:
      return "gt";
    case AsmPredicate::Ge:
      return "ge";
    default:
      olc_unreachable("Unknown condition");
    }
  }

  uint32_t reinterpretFloat(float x) {
    union {
      float f;
      uint32_t i;
    } u;
    u.f = x;
    return u.i;
  }

  void printArmInstr(
      const std::string &op, const std::vector<std::string> &operands = {},
      AsmPredicate pred = AsmPredicate::Al) {

    // indent for instructions
    os << "  ";

    os << op;
    if (pred != AsmPredicate::Al) {
      os << getCondStr(pred);
    }
    os << " ";
    for (int i = 0; i < operands.size(); ++i) {
      if (i > 0) {
        os << ", ";
      }
      os << operands[i];
    }
    os << '\n';

    if (++labelInstCounter > kMaxInstInLabel) {
      labelInstCounter = 0;
      auto name = "split_label_" + std::to_string(labelSplitCounter++);
      os << "  b " << name << "\n";
      os << ".ltorg\n";
      os << name << ":\n";
    }
  }

  enum class RegSaveType {
    Push,
    Pop,
  };

  void printStackMovement(RegSaveType type, AsmFunc *func) {
    int size = func->stackSize;
    std::string op = type == RegSaveType::Push ? "sub" : "add";
    if (AsmImm::match<AsmImm::Operand2>(size)) {
      printArmInstr(op, {"sp", "sp", "#" + std::to_string(size)});
    } else {
      // use lr as temp reg, which is covered by reg save store
      printArmInstr("ldr", {"lr", "=" + std::to_string(size)});
      printArmInstr(op, {"sp", "lr"});
    }
  }

  /// Print push or pop { ...calleeSavedRegs, lr }
  void printRegSaveStore(RegSaveType type, AsmFunc *func) {
    std::set<PReg *> pushIntRegs, vpushFloatRegs;

    for (auto *preg : func->usedCalleeSavedRegs) {
      if (preg->type == AsmType::I32) {
        pushIntRegs.insert(preg);
      } else {
        vpushFloatRegs.insert(preg);
      }
    }

    auto emitPushPop = [&] {
      std::string pushRegList;
      for (auto *reg : pushIntRegs) {
        if (!pushRegList.empty())
          pushRegList += ", ";
        pushRegList += reg->abiName();
      }
      // always save lr
      if (!pushRegList.empty())
        pushRegList += ", ";
      pushRegList += PReg::lr()->abiName();
      printArmInstr(
          type == RegSaveType::Push ? "push" : "pop",
          {"{" + pushRegList + "}"});
    };

    auto emitVPushVPop = [&] {
      if (!vpushFloatRegs.empty()) {
        std::string pushRegList;
        for (auto *reg : vpushFloatRegs) {
          if (!pushRegList.empty())
            pushRegList += ", ";
          pushRegList += reg->abiName();
        }
        printArmInstr(
            type == RegSaveType::Push ? "vpush" : "vpop",
            {"{" + pushRegList + "}"});
      }
    };

    if (type == RegSaveType::Push) {
      emitPushPop();
      emitVPushVPop();
    } else {
      emitVPushVPop();
      emitPushPop();
    }
  }

  std::string getLabel(AsmLabel *label) {
    if (labelMap.find(label) == labelMap.end()) {
      labelMap[label] = labelMap.size();
    }
    return "." + label->name + "_" + std::to_string(labelMap[label]);
  }

  void runRegAlloc() {
    for (auto *func : module->funcs) {
      if (func->isBuiltin)
        continue;
      bool allocDone = false;
      while (!allocDone) {
        // 1. Run reg alloc on the func
        LinearScan regAlloc;
        regAlloc.runOnFunction(func);
        auto &regMap = regAlloc.regMap;
        std::map<AsmReg *, int> spillMap;
        for (auto *var : regAlloc.spills) {
          spillMap[var] = func->stackSize;
          func->stackSize += 4;
        }
        allocDone = spillMap.empty();

        auto generateSpillAddress = [](AsmAccess *access, int offset,
                                       AsmLabel *label, AsmType type) {
          access->addr = PReg::sp();
          bool isMatch = false;
          if (type == AsmType::I32)
            isMatch = AsmImm::match<AsmImm::Imm12bit>(offset);
          else
            isMatch = AsmImm::match<AsmImm::Imm8bitx4>(offset);

          if (isMatch) {
            access->offset = new AsmImm{AsmImm::getBitRepr(offset)};
          } else {
            auto *imm = new AsmImm{AsmImm::getBitRepr(offset)};
            auto *mov = new AsmMoveInst{};
            mov->dst = VReg::makeVReg(AsmType::I32);
            mov->src = imm;
            access->offset = mov->dst;
            label->push_before(access, mov);
          }
        };

        // 2. Remove unused insts, for better handling in step 3
        for (auto *label : func->labels) {
          for (auto *inst = label->Head; inst != nullptr;) {
            auto *next = inst->Next;
            assert(
                inst->getDefs().size() <= 1 && "Can not handle > 1 defs now");
            for (auto refdef : inst->getDefs()) {
              auto &def = *refdef;
              if (auto *vreg = dyn_cast<VReg>(def)) {
                // If not in both maps, it's an unused dead def.
                if (!regAlloc.regMap.count(vreg) &&
                    !regAlloc.spills.count(vreg)) {
                  label->remove(inst);
                  break;
                }
              }
            }
            inst = next;
          }
        }

        // 3. Spill all regs into vregs, continue to next iteration.
        // Note that we do not rewrite the vregs to pregs here, to reserve the
        // space of allocating new vregs used in spilling.
        if (!allocDone) {
          for (auto *label : func->labels) {
            for (auto *inst = label->Head; inst != nullptr; inst = inst->Next) {
              for (auto refdef : inst->getDefs()) {
                auto &def = *refdef;
                if (auto *vreg = dyn_cast<VReg>(def);
                    regAlloc.spills.count(vreg)) {
                  auto *reg_tmp = VReg::makeVReg(vreg->type);
                  def = reg_tmp;
                  int offset = spillMap.at(vreg);
                  auto storeSlotInst = new AsmStoreInst{};
                  label->push_after(inst, storeSlotInst);
                  storeSlotInst->src = reg_tmp;
                  generateSpillAddress(
                      storeSlotInst, offset, label, reg_tmp->type);
                }
              }

              for (auto refuse : inst->getUses()) {
                auto &use = *refuse;
                if (auto *vreg = dyn_cast_if_present<VReg>(use);
                    regAlloc.spills.count(vreg)) {
                  auto *reg_tmp = VReg::makeVReg(vreg->type);
                  use = reg_tmp;
                  int offset = spillMap.at(vreg);
                  auto loadSlotInst = new AsmLoadInst{};
                  label->push_before(inst, loadSlotInst);
                  loadSlotInst->dst = reg_tmp;
                  generateSpillAddress(
                      loadSlotInst, offset, label, reg_tmp->type);
                }
              }
            }
          }
        }

        // 4. Rewrite all defs and uses
        if (allocDone) {
          for (auto *label : func->labels) {
            for (auto *inst = label->Head; inst != nullptr; inst = inst->Next) {
              for (auto refdef : inst->getDefs()) {
                auto &def = *refdef;
                if (auto *vreg = dyn_cast<VReg>(def))
                  def = regMap.at(vreg);
              }
              for (auto refuse : inst->getUses()) {
                auto &use = *refuse;
                if (auto *vreg = dyn_cast_if_present<VReg>(use))
                  use = regMap.at(vreg);
              }
            }
          }
        }
      }
    }
  }

  void calcStackInfo() {
    for (auto *func : module->funcs) {
      if (func->isBuiltin)
        continue;

      // 1. scan used callee saved regs list

      auto processReg = [&](AsmValue *reg) {
        auto *preg = dyn_cast_if_present<PReg>(reg);
        if (!preg)
          return;
        if (preg->type == AsmType::I32) {
          if (preg->id >= 4 && preg->id <= 11) {
            func->usedCalleeSavedRegs.insert(preg);
          }
        } else {
          if (preg->id >= 16 && preg->id <= 31) {
            // olc_unreachable("float save NYI");
            func->usedCalleeSavedRegs.insert(preg);
          }
        }
      };

      for (auto *label : func->labels) {
        for (auto *inst = label->Head; inst; inst = inst->Next) {
          for (auto *refdef : inst->getDefs())
            processReg(*refdef);
          for (auto *refuse : inst->getUses())
            processReg(*refuse);
        }
      }

      // 2. replace stack arg offsets
      const int pushSize = 4 + 4 * func->usedCalleeSavedRegs.size();
      if ((func->stackSize + pushSize) % 8 != 0) {
        func->stackSize += 4;
      }

      for (auto *offsetInst : func->stackArgOffsets) {
        auto *offsetImm = cast<AsmImm>(offsetInst->src);
        offsetImm->hexValue += func->stackSize + pushSize;
      }
    }
  }

  void printGlobalData(GlobalVariable *global) {
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

  void printGlobalBss(GlobalVariable *global) {
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

  bool isEqualAsmValue(AsmValue *lhs, AsmValue *rhs) {
    if (!lhs || !rhs)
      return lhs == rhs;
    if (isa<AsmImm>(lhs))
      return AsmImm::isEqual(lhs, rhs);
    return lhs == rhs;
  }

  void peepHole() {
    for (auto *func : module->funcs) {
      if (func->isBuiltin)
        continue;
      for (auto *label : func->labels) {
        for (auto *inst = label->Head; inst; inst = inst->Next) {
          if (auto *movInst = dyn_cast<AsmMoveInst>(inst)) {
            if (movInst->src == movInst->dst) {
              // mov rx, rx
              // mov can be removed
              label->remove(movInst);
            }
          } else if (auto *ldInst = dyn_cast<AsmLoadInst>(inst)) {
            if (inst->Prev) {
              if (auto *stInst = dyn_cast<AsmStoreInst>(inst->Prev)) {
                if (stInst->src == ldInst->dst &&
                    stInst->addr == ldInst->addr &&
                    isEqualAsmValue(stInst->offset, ldInst->offset)) {
                  label->remove(ldInst);
                  // str rx, [ry]
                  // ldr rx, [ry]
                  // ldr can be removed
                } else if (
                    stInst->addr == ldInst->addr &&
                    isEqualAsmValue(stInst->offset, ldInst->offset)) {
                  auto *movInst = new AsmMoveInst{};
                  movInst->dst = ldInst->dst;
                  movInst->src = stInst->src;
                  label->push_before(inst, movInst);
                  label->remove(ldInst);
                  // label->remove(stInst);

                  // str rx, [ry]
                  // ldr rz, [ry]
                  // ->
                  // mov rx, rz
                }
              }
            }
          } else if (auto *stInst = dyn_cast<AsmStoreInst>(inst)) {
            if (inst->Prev) {
              if (auto *ldInst = dyn_cast<AsmLoadInst>(inst->Prev)) {
                if (ldInst->dst == stInst->src &&
                    ldInst->addr == stInst->addr &&
                    isEqualAsmValue(ldInst->offset, stInst->offset)) {
                  label->remove(stInst);
                  // ldr rx, [ry]
                  // str rx, [ry]
                  // str can be removed
                }
              }
            }
          }
        }
      }
    }
  }

  void run() {
    runRegAlloc();
    calcStackInfo();
    peepHole();

    os << ".arch armv7ve\n";

    os << ".data\n";
    for (auto &global : module->globals) {
      printGlobalData(global);
    }

    os << ".bss\n";
    for (auto &global : module->globals) {
      printGlobalBss(global);
    }

    os << ".text\n";
    for (auto *func : module->funcs) {
      os << ".global " << func->name << "\n";
    }

    for (auto *func : module->funcs) {
      if (func->isBuiltin)
        continue;
      os << func->name << ":\n";
      curFunc = func;

      // function prologue
      printRegSaveStore(RegSaveType::Push, func);
      printStackMovement(RegSaveType::Push, func);

      for (auto *label : func->labels) {
        labelInstCounter = 0;
        os << getLabel(label) << ":\n";
        for (auto *inst = label->Head; inst; inst = inst->Next) {
          if (auto *retInst = dyn_cast<AsmReturnInst>(inst)) {
            printStackMovement(RegSaveType::Pop, func);
            printRegSaveStore(RegSaveType::Pop, func);
            printArmInstr("bx", {"lr"});
            printArmInstr(".ltorg");
          } else if (auto *movInst = dyn_cast<AsmMoveInst>(inst)) {
            auto reg_dst = cast<PReg>(movInst->dst);
            if (auto *imm = dyn_cast<AsmImm>(movInst->src)) {
              assert(
                  reg_dst->type == AsmType::I32 && "F32 should load from IReg");
              union {
                uint32_t i;
                struct {
                  uint16_t lo;
                  uint16_t hi;
                };
              } u;
              u.i = imm->hexValue;
              printArmInstr(
                  "movw", {reg_dst->abiName(), std::to_string(u.lo)},
                  movInst->pred);
              if (u.hi > 0)
                printArmInstr(
                    "movt", {reg_dst->abiName(), std::to_string(u.hi)},
                    movInst->pred);
            } else if (auto *reg = dyn_cast<PReg>(movInst->src)) {
              std::string op;
              if (reg_dst->type == AsmType::F32 || reg->type == AsmType::F32) {
                op = "vmov.f32";
              } else {
                op = "mov";
              }
              printArmInstr(
                  op, {reg_dst->abiName(), reg->abiName()}, movInst->pred);
            } else {
              olc_unreachable("NYI");
            }
          } else if (auto *binInst = dyn_cast<AsmBinaryInst>(inst)) {
            std::string op;
            switch (binInst->tag) {
            case AsmInst::Tag::Add:
              op = "add";
              break;
            case AsmInst::Tag::Sub:
              op = "sub";
              break;
            case AsmInst::Tag::Mul:
              op = "mul";
              break;
            case AsmInst::Tag::Div:
              // if (cast<PReg>(binInst->dst)->type == AsmType::F32) {
              //   op = "vdiv.f32";
              // } else {
              //   op = "sdiv";
              // }
              op = "sdiv";
              break;
            case AsmInst::Tag::Mod:
              olc_unreachable("Should not produce MOD in IR");
            case AsmInst::Tag::Lsl:
              op = "lsl";
              break;
            case AsmInst::Tag::Lsr:
              op = "lsr";
              break;
            case AsmInst::Tag::Asr:
              op = "asr";
              break;
            case AsmInst::Tag::Rsb:
              op = "rsb";
              break;
            case AsmInst::Tag::Smmul:
              op = "smmul";
              break;
            case AsmInst::Tag::And:
              op = "and";
              break;
            default:
              olc_unreachable("Unknown");
            }
            if (cast<PReg>(binInst->dst)->type == AsmType::F32) {
              op = i2fOpCode[op];
            }
            auto reg_dst = cast<PReg>(binInst->dst);
            auto reg_lhs = cast<PReg>(binInst->lhs);
            std::vector<std::string> operands;
            if (auto reg_rhs = dyn_cast<PReg>(binInst->rhs)) {
              operands = {
                  reg_dst->abiName(), reg_lhs->abiName(), reg_rhs->abiName()};
            } else {
              auto imm = cast<AsmImm>(binInst->rhs);
              operands = {reg_dst->abiName(), reg_lhs->abiName(), imm->toAsm()};
            }
            if (binInst->shift != 0) {
              std::string shiftOp;
              switch (binInst->shiftTag) {
              case AsmInst::ShiftType::Lsl:
                shiftOp = "lsl";
                break;
              case AsmInst::ShiftType::Lsr:
                shiftOp = "lsr";
                break;
              case AsmInst::ShiftType::Asr:
                shiftOp = "asr";
                break;
              default:
                olc_unreachable("Unknown");
              }
              operands.push_back(
                  shiftOp + " #" + std::to_string(binInst->shift));
            }
            printArmInstr(op, operands);
          } else if (auto *ldInst = dyn_cast<AsmLoadInst>(inst)) {
            auto reg_dst = cast<PReg>(ldInst->dst);
            auto reg_base = cast<PReg>(ldInst->addr);
            std::string op;
            if (reg_dst->type == AsmType::F32) {
              op = "vldr.32";
            } else {
              op = "ldr";
            }
            if (ldInst->offset) {
              if (auto *offsetImm = dyn_cast<AsmImm>(ldInst->offset)) {
                printArmInstr(
                    op, {reg_dst->abiName(), "[" + reg_base->abiName() + ", " +
                                                 offsetImm->toAsm() + "]"});
              } else if (auto *offsetReg = dyn_cast<PReg>(ldInst->offset)) {
                printArmInstr(
                    op, {reg_dst->abiName(), "[" + reg_base->abiName() + ", " +
                                                 offsetReg->abiName() + "]"});
              } else {
                olc_unreachable("NYI");
              }
            } else {
              printArmInstr(
                  op, {reg_dst->abiName(), "[" + reg_base->abiName() + "]"});
            }
          } else if (auto *stInst = dyn_cast<AsmStoreInst>(inst)) {
            auto reg_src = cast<PReg>(stInst->src);
            auto reg_base = cast<PReg>(stInst->addr);
            std::string op;
            if (reg_src->type == AsmType::F32) {
              op = "vstr.32";
            } else {
              op = "str";
            }
            if (stInst->offset) {
              if (auto *offsetImm = dyn_cast<AsmImm>(stInst->offset)) {
                printArmInstr(
                    op, {reg_src->abiName(), "[" + reg_base->abiName() + ", " +
                                                 offsetImm->toAsm() + "]"});
              } else if (auto *offsetReg = dyn_cast<PReg>(stInst->offset)) {
                printArmInstr(
                    op, {reg_src->abiName(), "[" + reg_base->abiName() + ", " +
                                                 offsetReg->abiName() + "]"});
              } else {
                olc_unreachable("NYI");
              }
            } else {
              printArmInstr(
                  op, {reg_src->abiName(), "[" + reg_base->abiName() + "]"});
            }
          } else if (auto *callInst = dyn_cast<AsmCallInst>(inst)) {
            printArmInstr("bl", {callInst->callee});
          } else if (auto *brInst = dyn_cast<AsmBranchInst>(inst)) {
            if (brInst->pred != AsmPredicate::Al) {
              // 处理条件分支
              std::string condTag = getCondStr(brInst->pred);
              printArmInstr("b" + condTag, {getLabel(brInst->trueTarget)});
              printArmInstr("b", {getLabel(brInst->falseTarget)});
            } else {
              // 处理无条件跳转
              printArmInstr("b", {getLabel(brInst->trueTarget)});
            }
          } else if (auto *cmpInst = dyn_cast<AsmCompareInst>(inst)) {
            // 处理比较指令
            auto reg_lhs = cast<PReg>(cmpInst->lhs);
            std::string op;
            if (reg_lhs->type == AsmType::F32) {
              op = "vcmp.f32";
            } else {
              op = "cmp";
            }
            if (auto reg_rhs = dyn_cast<PReg>(cmpInst->rhs))
              printArmInstr(op, {reg_lhs->abiName(), reg_rhs->abiName()});
            else if (auto *imm_rhs = dyn_cast<AsmImm>(cmpInst->rhs)) {
              printArmInstr(op, {reg_lhs->abiName(), imm_rhs->toAsm()});
            } else {
              olc_unreachable("NYI");
            }

            if (reg_lhs->type == AsmType::F32) {
              // 从协处理器中转移条件标志
              printArmInstr("vmrs", {"APSR_nzcv", "FPSCR"});
            }

          } else if (auto *jmpInst = dyn_cast<AsmJumpInst>(inst)) {
            // 处理无条件跳转指令
            printArmInstr("b", {getLabel(jmpInst->target)});
            printArmInstr(".ltorg");
          } else if (auto *ldGlbInst = dyn_cast<AsmLoadGlobalInst>(inst)) {
            // 处理全局变量加载指令
            auto reg_dst = cast<PReg>(ldGlbInst->dst);
            printArmInstr(
                "ldr", {reg_dst->abiName(), "=" + ldGlbInst->var->getName()});
          } else if (auto *cvtInst = dyn_cast<AsmConvertInst>(inst)) {
            auto reg_dst = cast<PReg>(cvtInst->dst);
            auto reg_src = cast<PReg>(cvtInst->src);
            if (cvtInst->type == AsmConvertInst::CvtType::f2i) {
              printArmInstr(
                  "vcvt.s32.f32", {reg_dst->abiName(), reg_src->abiName()});
            } else if (cvtInst->type == AsmConvertInst::CvtType::i2f) {
              printArmInstr(
                  "vcvt.f32.s32", {reg_dst->abiName(), reg_src->abiName()});
            }
          } else {
            olc_unreachable("NYI");
          }
        }
      }
    }
  }
};

} // namespace olc
