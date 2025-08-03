// ref:
// https://github.com/antlr/antlr4/blob/master/runtime/Cpp/demo/Windows/antlr4-cpp-demo/main.cpp

// ref: https://tomassetti.me/getting-started-antlr-cpp/
// https://github.com/gabriele-tomassetti/antlr-cpp/blob/master/antlr.cpp

#include <fstream>
#include <getopt.h>
#include <iostream>
#include <sstream>

#include <antlr4-runtime.h>
#include <sysy2022Lexer.h>
#include <sysy2022Parser.h>
#include <sysy2022Visitor.h>

#include <olc/backend/ArmGen.h>
#include <olc/backend/ArmWriter.h>
#include <olc/backend/CodeGen.h>
#include <olc/backend/CodeWriter.h>
#include <olc/backend/Liveness.h>
#include <olc/backend/RegAlloc.h>
#include <olc/frontend/Visitor.h>
#include <olc/ir/AsmWriter.h>
#include <olc/ir/IR.h>
#include <olc/passes/PassManager.h>
#include <olc/passes/Passes.h>

using namespace antlr4;
using namespace olc;

int main(int argc, char *argv[]) {
  std::string outputFilename;
  std::string inputFilename;
  bool assemble = true;
  bool optimize = false;

  int opt;
  while ((opt = getopt(argc, argv, "So:O:")) != -1) {
    switch (opt) {
    case 'S':
      assemble = true;
      break;
    case 'o':
      outputFilename = optarg;
      break;
    case 'O':
      if (std::string(optarg) == "1") {
        optimize = true;
      }
      break;
    default:
      std::cerr << "Usage: olc -S -o <output file> <input file> [-O1]\n";
      return 1;
    }
  }

  if (optind < argc) {
    inputFilename = argv[optind];
  } else {
    inputFilename = "../test/data/test.sy";
    // std::cerr << "olc: fatal error: no input files\n";
    // return 1;
  }

  // if (outputFilename.empty()) {
  //   size_t lastDot = inputFilename.rfind('.');
  //   if (lastDot != std::string::npos) {
  //     outputFilename = inputFilename.substr(0, lastDot) + ".s";
  //   } else {
  //     outputFilename = inputFilename + ".s";
  //   }
  // }

  std::ifstream fin(inputFilename);
  if (!fin) {
    std::cout << "File not found: " << inputFilename << std::endl;
    return 1;
  }
  ANTLRInputStream input(fin);
  // ANTLRInputStream input("int main() { \n\treturn 0; \n}");

  sysy2022Lexer lexer(&input);
  CommonTokenStream tokens(&lexer);

  // 输出解析树
  // sysy2022Parser parser(&tokens);
  // tree::ParseTree *parse_tree = parser.compUnit();
  // auto s = parse_tree->toStringTree(&parser);
  // std::cout << "Parse Tree: " << s << std::endl;

  // 输出中间代码
  sysy2022Parser parser(&tokens);
  sysy2022Parser::CompUnitContext *tree = parser.compUnit();

  auto *mod = new Module{};
  SymbolTable symbolTable;
  ConstFoldVisitor constFolder(symbolTable);
  CodeGenASTVisitor visitor(mod, constFolder, symbolTable);
  visitor.visitCompUnit(tree);

  AssemblyWriter asmWriter{std::cerr};
  asmWriter.printModule(mod);
  std::cerr << "============\n";

  // ArmWriter armWriter{std::cerr};
  // armWriter.printModule(mod);

  PassManager pm;
  pm.addPass(new SimplifyCFGPass{});
  // pm.addPass(new ConstantFoldingPass{});
  pm.addPass(new DominanceAnalysis{});
  pm.addPass(new Mem2RegPass{});
  // pm.addPass(new ConstantFoldingPass{});
  // pm.addPass(new SCCPPass{});
  pm.addPass(new SimplifyCFGPass{});
  pm.addPass(new CanonicalizePass{});

  pm.addPass(new DCEProPass{});
  pm.addPass(new GVNGCMPass{});
  pm.addPass(new DCEProPass{});

  pm.run(*mod);
  asmWriter.printModule(mod);
  std::cerr << "============\n";

  CodeGen codegen{mod};
  codegen.run();
  auto *asmMod = codegen.asmModule;
  std::cerr << "============\n";

  std::stringstream ss;
  ArmGen armgen{ss, asmMod};
  armgen.run();

  if (assemble) {
    if (outputFilename.empty()) {
      std::cout << ss.str();
    } else {
      std::ofstream fout(outputFilename);
      fout << ss.str();
    }
  }
  std::cerr << "============\n";

  return 0;
}
