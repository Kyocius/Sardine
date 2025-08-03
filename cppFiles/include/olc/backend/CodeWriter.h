#pragma once

#include <iostream>
#include <map>
#include <olc/backend/MachineIR.h>

namespace olc {

struct CodeWriter {
  std::ostream &os;

  class NameManager {
    std::map<AsmInst *, std::string> nameMap;
    // naive counter naming
    size_t counter = 0;
  };

  CodeWriter(std::ostream &os) : os(os) {}

  void printCodeInstr(
      const std::string &op, const std::vector<std::string> &operands) {
    // indent for instructions
    os << "  ";

    os << op;
    os << " ";
    for (int i = 0; i < operands.size(); ++i) {
      if (i > 0) {
        os << ", ";
      }
      os << operands[i];
    }
    os << '\n';
  }

  void printModule(AsmModule *);
  void printGlobal(GlobalVariable *);
  void printFunc(AsmFunc *);
  void printLabel(AsmLabel *);
  void printInst(AsmInst *);
  std::string to_string(AsmValue *);
};
} // namespace olc