
#include <olc/ir/IR.h>

#include <iostream>
#include <map>

namespace olc {

struct AssemblyWriter {
  std::ostream &os;

  class NameManager {
    std::map<Instruction *, std::string> nameMap;
    // naive counter naming
    size_t counter = 0;

  public:
    void add(Instruction *instr) {
      nameMap[instr] = std::to_string(counter++);
    }

    std::string const &operator[](Instruction *instr) const {
      return nameMap.at(instr);
    }

    void reset() {
      nameMap.clear();
      counter = 0;
    }
  } nameManager;

  AssemblyWriter(std::ostream &os) : os(os) {}

  void prepareNamesForFunc(Function *);

  void printModule(Module *);
  void printGlobal(GlobalVariable *);
  void printFunc(Function *);
  void printBasicBlock(BasicBlock *);
  void printInstr(Instruction *);
};

} // namespace olc
