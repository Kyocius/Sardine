
#pragma once

#include <algorithm>
#include <cmath>
#include <functional>
#include <iostream>
#include <map>
#include <vector>

#include <antlr4-runtime.h>
#include <sysy2022BaseVisitor.h>

#include <olc/ir/IR.h>
#include <olc/utils/SymbolTable.h>

#include <olc/debug.h>
#include <olc/frontend/ConstFolder.h>

using namespace olc;

class ConstFoldVisitor;

class CodeGenASTVisitor : public sysy2022BaseVisitor {
  // 符号表
  SymbolTable &symbolTable;
  // IR Value 表
  std::map<antlr4::ParserRuleContext *, Value *> valueMap;
  // 推导节点是否为左值
  std::map<antlr4::ParserRuleContext *, bool> isLValueMap;
  // 当前模块
  Module *curModule;
  // 当前函数
  Function *curFunction;
  // 当前基本块
  BasicBlock *curBasicBlock;

  ConstFoldVisitor &constFolder;

  // 标签计数
  int labelCnt = 0;
  // 当前条件块(for continue) 当前结束块(for break) 栈
  std::vector<BasicBlock *> condBBStack, endBBStack;
  // 是否提前退出基本块
  bool earlyExit = false;

  // 当前btrue(for or short circuit) 当前bfalse(for and short circuit) 栈
  std::vector<BasicBlock *> btrueStack, bfalseStack;

  Type *convertType(std::string const &typeStr) {
    if (typeStr == "int") {
      return IntegerType::get();
    } else if (typeStr == "float") {
      return FloatType::get();
    } else if (typeStr == "void") {
      return VoidType::get();
    }
    olc_unreachable("NYI");
    return nullptr;
  }

  Value *createLValue(antlr4::ParserRuleContext *ctx) {
    visit(ctx);
    assert(isLValueMap[ctx] && "not a lvalue");
    return valueMap.at(ctx);
  }

  Value *
  createRValue(antlr4::ParserRuleContext *ctx, Type *targetType = nullptr) {
    visit(ctx);
    auto *rVal = valueMap.at(ctx);
    if (isLValueMap[ctx]) {
      return curBasicBlock->create<LoadInst>(rVal);
    }
    if (targetType) {
      if (targetType->isFloatTy() && rVal->getType()->isIntegerTy()) {
        rVal = curBasicBlock->create<IntToFloatInst>(rVal);
      } else if (targetType->isIntegerTy() && rVal->getType()->isFloatTy()) {
        rVal = curBasicBlock->create<FloatToIntInst>(rVal);
      } else {
        ; // do nothing
      }
    }
    return rVal;
  }

  Value *createCondValue(antlr4::ParserRuleContext *ctx) {
    auto *expr = createRValue(ctx, nullptr);
    if (auto *binExpr = dyn_cast<BinaryInst>(expr);
        binExpr && binExpr->isCmpOp()) {
      return binExpr;
    } else {
      // 隐式类型转换
      if (expr->getType()->isIntegerTy()) {
        return curBasicBlock->create<BinaryInst>(
            Value::Tag::Ne, expr, new ConstantValue(0));
      } else {
        return curBasicBlock->create<BinaryInst>(
            Value::Tag::Ne, expr, new ConstantValue(0.f));
      }
    }
  }

  void typeConversion(Value **left, Value **right) {
    if ((*left)->getType()->isIntegerTy() &&
            (*right)->getType()->isIntegerTy() ||
        (*left)->getType()->isFloatTy() && (*right)->getType()->isFloatTy()) {
      return;
    }
    if ((*left)->getType()->isIntegerTy()) {
      *left = curBasicBlock->create<IntToFloatInst>(*left);
      return;
    }
    if ((*right)->getType()->isIntegerTy()) {
      *right = curBasicBlock->create<IntToFloatInst>(*right);
      return;
    }
  }

public:
  CodeGenASTVisitor(
      Module *module, ConstFoldVisitor &constFolder, SymbolTable &symbolTable)
      : curModule(module), curFunction(nullptr), constFolder(constFolder),
        symbolTable(symbolTable) {}

  virtual std::any
  visitCompUnit(sysy2022Parser::CompUnitContext *ctx) override {
    // insert builtins
    std::vector<Function *> builtins{
        new Function(IntegerType::get(), "getint", {}),
        new Function(IntegerType::get(), "getch", {}),
        new Function(
            IntegerType::get(), "getarray",
            {new Argument{PointerType::get(IntegerType::get()), "a"}}),
        new Function(FloatType::get(), "getfloat", {}),
        new Function(
            IntegerType::get(), "getfarray",
            {new Argument{PointerType::get(FloatType::get()), "a"}})

            ,
        new Function(
            VoidType::get(), "putint", {new Argument{IntegerType::get(), "a"}}),
        new Function(
            VoidType::get(), "putch", {new Argument{IntegerType::get(), "a"}}),
        new Function(
            VoidType::get(), "putarray",
            {new Argument{IntegerType::get(), "n"},
             new Argument{PointerType::get(IntegerType::get()), "a"}}),
        new Function(
            VoidType::get(), "putfloat", {new Argument{FloatType::get(), "a"}}),
        new Function(
            VoidType::get(), "putfarray",
            {new Argument{IntegerType::get(), "n"},
             new Argument{PointerType::get(FloatType::get()), "a"}}),
        // void __aeabi_memset(void *dest, size_t n, int c);
        new Function(
            VoidType::get(), "__aeabi_memset",
            {new Argument{PointerType::get(IntegerType::get()), "dest"},
             new Argument{IntegerType::get(), "n"},
             new Argument{IntegerType::get(), "c"}}),
        // void _sysy_starttime(int lineno);
        // void _sysy_stoptime(int lineno);
        new Function(
            VoidType::get(), "_sysy_starttime",
            {new Argument{IntegerType::get(), "lineno"}}),
        new Function(
            VoidType::get(), "_sysy_stoptime",
            {new Argument{IntegerType::get(), "lineno"}}),
    };

    for (auto *func : builtins) {
      func->isBuiltin = true;
      curModule->addFunction(func);
      symbolTable.insert(func->fnName, func);
    }

    return visitChildren(ctx);
  }

  //--------------------------------------------------------------------------
  // 声明部分
  //--------------------------------------------------------------------------
  virtual std::any visitVarDecl(sysy2022Parser::VarDeclContext *ctx) override {
    if (earlyExit)
      return {};

    Type *type = convertType(ctx->basicType->getText());
    bool isGlobal = (curFunction == nullptr); // 检查是否在全局作用域中

    for (const auto &varDef : ctx->varDef()) {
      std::string varName = varDef->ID()->getText();
      if (varDef->constExpr().size() > 0) {
        /* 数组定义 */
        size_t size = 1;
        std::vector<int> dimSizes;
        for (const auto &expr : varDef->constExpr()) {
          dimSizes.push_back(constFolder.resolveInt(expr));
          size *= dimSizes.back();
        }
        Type *arrayType = ArrayType::get(type, size);
        if (ctx->isConst || isGlobal) {
          // 全局数组 / 常量数组
          Constant *initializer = nullptr;
          if (varDef->initVal()) {
            std::vector<Constant *> values;
            std::function<void(sysy2022Parser::InitValContext *, int, int)> dfs;
            dfs = [&](sysy2022Parser::InitValContext *ctx, int dim, int len) {
              for (auto *val : ctx->initVal()) {
                if (val->expr()) {
                  values.push_back(constFolder.resolve(val->expr(), type));
                } else {
                  int match = 1, matchDim = dimSizes.size();
                  while (--matchDim > dim) {
                    if (values.size() % (match * dimSizes[matchDim]) == 0) {
                      match *= dimSizes[matchDim];
                    } else {
                      break;
                    }
                  }
                  if (matchDim == dimSizes.size() - 1) {
                    olc_unreachable("初始化列表错误");
                  }
                  dfs(val, dim + 1, values.size() + match);
                }
              }
              // 如果是填充到最后，省略末尾的0
              if (len == size)
                return;
              while (values.size() < len) {
                if (type->isFloatTy()) {
                  values.push_back(new ConstantValue(0.f));
                } else {
                  values.push_back(new ConstantValue(0));
                }
              }
            };
            dfs(varDef->initVal(), 0, size);
            bool allZero =
                std::all_of(values.begin(), values.end(), [&](Constant *val) {
                  if (auto *constVal = dyn_cast<ConstantValue>(val)) {
                    return constVal->isInt() && constVal->getInt() == 0;
                  }
                  return false;
                });
            if (!allZero)
              initializer =
                  cast<Constant>(new ConstantArray(arrayType, values));
            else
              initializer = nullptr;
          }
          GlobalVariable *globalVar =
              new GlobalVariable(arrayType, varName, initializer);
          curModule->addGlobal(globalVar);
          symbolTable.insert(varName, globalVar, dimSizes);
        } else {
          // 局部数组分配
          Value *allocaInst = curBasicBlock->create<AllocaInst>(arrayType);
          symbolTable.insert(varName, allocaInst, dimSizes);
          // 处理初始化表达式（如果有）
          if (varDef->initVal()) {
            std::vector<Value *> values(size, nullptr);
            int index = 0;

            bool allZero = true;
            std::function<void(sysy2022Parser::InitValContext *, int, int)> dfs;
            dfs = [&](sysy2022Parser::InitValContext *ctx, int dim, int len) {
              for (auto *val : ctx->initVal()) {
                if (val->expr()) {
                  values[index++] = createRValue(val->expr(), type);
                  auto *constVal = dyn_cast<ConstantValue>(values[index - 1]);
                  if (!constVal)
                    allZero = false;
                  else if (constVal->isInt() && constVal->getInt() != 0)
                    allZero = false;
                  else if (constVal->isFloat() && constVal->getFloat() != 0.f)
                    allZero = false;
                } else {
                  int match = 1, matchDim = dimSizes.size();
                  while (--matchDim > dim) {
                    if (index % (match * dimSizes[matchDim]) == 0) {
                      match *= dimSizes[matchDim];
                    } else {
                      break;
                    }
                  }
                  if (matchDim == dimSizes.size() - 1) {
                    olc_unreachable("初始化列表错误");
                  }
                  dfs(val, dim + 1, index + match);
                }
              }
              index = len;
            };
            dfs(varDef->initVal(), 0, size);
            // 初始化数组
            // 如果所有值为零，使用__aeabi_memset初始化
            if (allZero) {
              // void __aeabi_memset(void *dest, size_t n, int c);
              Function *memsetFunc =
                  cast<Function>(symbolTable.lookup("__aeabi_memset"));
              Value *basePtr = curBasicBlock->create<GetElementPtrInst>(
                  allocaInst, new ConstantValue(0));
              std::vector<Value *> args;
              args.push_back(basePtr);
              args.push_back(new ConstantValue((int)size * 4));
              args.push_back(new ConstantValue(0));
              curBasicBlock->create<CallInst>(memsetFunc, args);
            } else {
              // 否则正常初始化数组
              // 先遍历一遍将nullptr替换为0
              for (int i = 0; i < (int)size; ++i) {
                if (!values[i]) {
                  if (type->isFloatTy()) {
                    values[i] = new ConstantValue(0.f);
                  } else {
                    values[i] = new ConstantValue(0);
                  }
                }
              }
              for (int i = 0; i < (int)size; ++i) {
                Value *elementPtr = curBasicBlock->create<GetElementPtrInst>(
                    allocaInst, new ConstantValue(i));
                curBasicBlock->create<StoreInst>(values[i], elementPtr);
              }
            }
          }
        }
      } else {
        /* 变量定义 */
        if (ctx->isConst) {
          // 常量变量
          Constant *initializer = nullptr;
          if (varDef->initVal()) {
            initializer = constFolder.resolve(varDef->initVal(), type);
          }
          symbolTable.insert(varName, initializer);
        } else if (isGlobal) {
          // 初始化全局变量的值
          Constant *initializer = nullptr;
          if (varDef->initVal()) {
            initializer = constFolder.resolve(varDef->initVal(), type);
            // 判断全局变量是否为常量
          }
          GlobalVariable *globalVar =
              new GlobalVariable(type, varName, initializer);
          curModule->addGlobal(globalVar);
          symbolTable.insert(varName, globalVar);
        } else {
          // 局部变量分配
          Value *allocaInst = curBasicBlock->create<AllocaInst>(type);
          symbolTable.insert(varName, allocaInst);
          // 处理初始化表达式（如果有）
          if (varDef->initVal()) {
            Value *initVal = createRValue(varDef->initVal()->expr(), type);
            curBasicBlock->create<StoreInst>(initVal, allocaInst);
          }
        }
      }
    }
    return {};
  }

  virtual std::any visitVarDef(sysy2022Parser::VarDefContext *ctx) override {
    olc_unreachable("Never");
    return {};
  }

  virtual std::any visitInitVal(sysy2022Parser::InitValContext *ctx) override {
    olc_unreachable("Never");
    return {};
  }

  //--------------------------------------------------------------------------
  // 函数部分
  //--------------------------------------------------------------------------
  virtual std::any visitFuncDef(sysy2022Parser::FuncDefContext *ctx) override {
    labelCnt = 0;

    // 初始化函数
    auto *retType = convertType(ctx->funcType->getText());
    std::vector<Argument *> args;
    std::vector<std::vector<int>> argShapes;
    std::vector<bool> isArray;

    // 处理形参
    for (const auto &param : ctx->funcFParam()) {
      auto *type = convertType(param->basicType->getText());
      argShapes.push_back({});
      isArray.push_back(false);
      int size = 1;
      for (auto *dim : param->expr()) {
        argShapes.back().push_back(constFolder.resolveInt(dim));
        // type = ArrayType::get(type, argShapes.back().back());
        size *= argShapes.back().back();
      }
      if (param->isArrayRef) {
        type = ArrayType::get(type, size);
        type = PointerType::get(type);
        isArray.back() = true;
      }
      args.push_back(new Argument{type, param->ID()->getText()});
    }
    // 加到module中
    Function *function = new Function(retType, ctx->ID()->getText(), args);
    curModule->addFunction(function);
    curFunction = function;
    curBasicBlock = *function->basicBlocks.begin();

    // 函数名加到顶层符号表
    symbolTable.insert(ctx->ID()->getText(), function);

    // 进入新的作用域
    symbolTable.enterScope();
    // 参数加到符号表中 生成alloca和store指令
    for (unsigned i = 0; i < args.size(); i++) {
      auto &arg = args[i];
      if (!isArray[i]) {
        Value *allocaInst = curBasicBlock->create<AllocaInst>(arg->getType());
        curBasicBlock->create<StoreInst>(arg, allocaInst);
        symbolTable.insert(arg->argName, allocaInst, argShapes[i]);
      } else {
        // 数组参数
        argShapes[i].insert(argShapes[i].begin(), 0);
        symbolTable.insert(arg->argName, arg, argShapes[i]);
      }
    }

    // 处理函数体
    visit(ctx->block());

    // void函数 增加ret指令
    if (retType->isVoidTy() &&
        (curBasicBlock->instructions.empty() ||
         !isa<ReturnInst>(curBasicBlock->instructions.Tail))) {
      curBasicBlock->create<ReturnInst>();
    }

    // 若main函数没有ret, 则加上ret指令
    if (ctx->ID()->getText() == "main" &&
        (curBasicBlock->instructions.empty() ||
         !isa<ReturnInst>(curBasicBlock->instructions.Tail))) {
      curBasicBlock->create<ReturnInst>(new ConstantValue(0));
    }

    // 退出作用域
    symbolTable.exitScope();

    curFunction = nullptr;
    curBasicBlock = nullptr;
    earlyExit = false;

    return {};
  }

  virtual std::any
  visitFuncFParam(sysy2022Parser::FuncFParamContext *ctx) override {
    olc_unreachable("Never");
    return {};
  }

  //--------------------------------------------------------------------------
  // 语句部分
  //--------------------------------------------------------------------------
  virtual std::any visitBlock(sysy2022Parser::BlockContext *ctx) override {
    // 进入新的作用域
    symbolTable.enterScope();

    // 处理块项目
    visitChildren(ctx);

    // 退出作用域
    symbolTable.exitScope();

    return {};
  }

  virtual std::any
  visitAssignStmt(sysy2022Parser::AssignStmtContext *ctx) override {
    if (earlyExit)
      return {};

    // 获取左值
    auto *lVal = createLValue(ctx->lVal());
    // 获取右值
    auto *rVal =
        createRValue(ctx->expr(), lVal->getType()->getPointerEltType());
    // 创建指令
    curBasicBlock->create<StoreInst>(rVal, lVal);
    return {};
  }

  virtual std::any
  visitExprStmt(sysy2022Parser::ExprStmtContext *ctx) override {
    if (earlyExit)
      return {};

    if (ctx->expr())
      visit(ctx->expr());
    return {};
  }

  virtual std::any
  visitBlockStmt(sysy2022Parser::BlockStmtContext *ctx) override {
    if (earlyExit)
      return {};

    visit(ctx->block());
    return {};
  }

  virtual std::any visitIfStmt(sysy2022Parser::IfStmtContext *ctx) override {
    if (earlyExit)
      return {};

    // 创建基本块
    BasicBlock *btrue = nullptr, *bfalse = nullptr, *end = nullptr;
    btrue = new BasicBlock(curFunction, "btrue_" + std::to_string(labelCnt));
    if (ctx->stmt().size() == 2) {
      bfalse =
          new BasicBlock(curFunction, "bfalse_" + std::to_string(labelCnt));
    }
    end = new BasicBlock(curFunction, "endif_" + std::to_string(labelCnt++));

    btrueStack.push_back(btrue);
    bfalseStack.push_back(bfalse ? bfalse : end);

    // 获取条件
    auto *condInst = createCondValue(ctx->cond());

    // 创建指令 维护CFG
    curBasicBlock->create<BranchInst>(condInst, btrue, bfalse ? bfalse : end);
    curBasicBlock->successors.push_back(btrue);
    curBasicBlock->successors.push_back(bfalse ? bfalse : end);
    btrue->predecessors.push_back(curBasicBlock);
    bfalse ? bfalse->predecessors.push_back(curBasicBlock)
           : end->predecessors.push_back(curBasicBlock);

    // btrue
    curFunction->addBasicBlock(btrue);
    curBasicBlock = btrue;
    visit(ctx->stmt(0));
    if (!earlyExit) {
      // 创建指令 维护CFG
      curBasicBlock->create<JumpInst>(end);
      curBasicBlock->successors.push_back(end);
      end->predecessors.push_back(curBasicBlock);
    } else {
      earlyExit = false;
    }

    // bfalse
    if (bfalse) {
      curFunction->addBasicBlock(bfalse);
      curBasicBlock = bfalse;
      visit(ctx->stmt(1));
      if (!earlyExit) {
        // 创建指令 维护CFG
        curBasicBlock->create<JumpInst>(end);
        curBasicBlock->successors.push_back(end);
        end->predecessors.push_back(curBasicBlock);
      } else {
        earlyExit = false;
      }
    }

    // end
    curFunction->addBasicBlock(end);
    curBasicBlock = end;

    btrueStack.pop_back();
    bfalseStack.pop_back();

    return {};
  }

  virtual std::any
  visitWhileStmt(sysy2022Parser::WhileStmtContext *ctx) override {
    if (earlyExit)
      return {};

    // 创建基本块
    BasicBlock *cond = nullptr, *loop = nullptr, *end = nullptr;
    cond = new BasicBlock(curFunction, "cond_" + std::to_string(labelCnt));
    loop = new BasicBlock(curFunction, "loop_" + std::to_string(labelCnt));
    end = new BasicBlock(curFunction, "endloop_" + std::to_string(labelCnt++));
    condBBStack.push_back(cond);
    endBBStack.push_back(end);

    // 创建指令 维护CFG
    curBasicBlock->create<JumpInst>(cond);
    curBasicBlock->successors.push_back(cond);
    cond->predecessors.push_back(curBasicBlock);

    // cond
    curFunction->addBasicBlock(cond);
    curBasicBlock = cond;

    btrueStack.push_back(loop);
    bfalseStack.push_back(end);

    // 获取条件
    auto *condInst = createCondValue(ctx->cond());
    // 创建指令 维护CFG
    curBasicBlock->create<BranchInst>(condInst, loop, end);

    curBasicBlock->successors.push_back(loop);
    curBasicBlock->successors.push_back(end);
    loop->predecessors.push_back(curBasicBlock);
    end->predecessors.push_back(curBasicBlock);

    // loop
    curFunction->addBasicBlock(loop);
    curBasicBlock = loop;
    visit(ctx->stmt());
    if (!earlyExit) {
      // 创建指令 维护CFG
      curBasicBlock->create<JumpInst>(cond);
      curBasicBlock->successors.push_back(cond);
      cond->predecessors.push_back(curBasicBlock);
    } else {
      earlyExit = false;
    }

    // end
    curFunction->addBasicBlock(end);
    curBasicBlock = end;

    condBBStack.pop_back();
    endBBStack.pop_back();

    btrueStack.pop_back();
    bfalseStack.pop_back();

    return {};
  }

  virtual std::any
  visitBreakStmt(sysy2022Parser::BreakStmtContext *ctx) override {
    if (earlyExit)
      return {};

    earlyExit = true;
    auto *curEndBB = endBBStack.back();
    curBasicBlock->create<JumpInst>(curEndBB);
    if (find(
            curEndBB->predecessors.begin(), curEndBB->predecessors.end(),
            curBasicBlock) == curEndBB->predecessors.end()) {
      curEndBB->predecessors.push_back(curBasicBlock);
      curBasicBlock->successors.push_back(curEndBB);
    }
    return {};
  }

  virtual std::any
  visitContinueStmt(sysy2022Parser::ContinueStmtContext *ctx) override {
    if (earlyExit)
      return {};

    earlyExit = true;
    auto *curCondBB = condBBStack.back();
    curBasicBlock->create<JumpInst>(curCondBB);
    if (find(
            curCondBB->predecessors.begin(), curCondBB->predecessors.end(),
            curBasicBlock) == curCondBB->predecessors.end()) {
      curCondBB->predecessors.push_back(curBasicBlock);
      curBasicBlock->successors.push_back(curCondBB);
    }
    return {};
  }

  virtual std::any
  visitReturnStmt(sysy2022Parser::ReturnStmtContext *ctx) override {
    if (earlyExit)
      return {};

    earlyExit = true;
    if (ctx->expr()) {
      // 获取子操作数
      auto *retVal = createRValue(ctx->expr(), curFunction->getReturnType());
      // 创建指令
      Value *result = curBasicBlock->create<ReturnInst>(retVal);
    } else {
      Value *result = curBasicBlock->create<ReturnInst>();
    }
    return {};
  }

  //--------------------------------------------------------------------------
  // 表达式部分
  //--------------------------------------------------------------------------
  virtual std::any
  visitAddSubExpr(sysy2022Parser::AddSubExprContext *ctx) override {
    // 获取左操作数
    auto *left = createRValue(ctx->expr(0));
    // 获取右操作数
    auto *right = createRValue(ctx->expr(1));
    typeConversion(&left, &right);
    // 创建指令
    Value::Tag tag = Value::Tag::Undef;
    if (ctx->op->getText() == "+") {
      tag = Value::Tag::Add;
    } else {
      tag = Value::Tag::Sub;
    }
    Value *result = curBasicBlock->create<BinaryInst>(tag, left, right);
    // 返回值
    valueMap[ctx] = result;
    return {};
  }

  virtual std::any
  visitMulDivModExpr(sysy2022Parser::MulDivModExprContext *ctx) override {
    // 获取左操作数
    auto *left = createRValue(ctx->expr(0));
    // 获取右操作数
    auto *right = createRValue(ctx->expr(1));
    typeConversion(&left, &right);
    // 创建指令
    Value::Tag tag = Value::Tag::Undef;
    if (ctx->op->getText() == "*") {
      tag = Value::Tag::Mul;
    } else if (ctx->op->getText() == "/") {
      tag = Value::Tag::Div;
    } else {
      // Mod
      Value *quotient =
          curBasicBlock->create<BinaryInst>(Value::Tag::Div, left, right);
      Value *product =
          curBasicBlock->create<BinaryInst>(Value::Tag::Mul, quotient, right);
      Value *result =
          curBasicBlock->create<BinaryInst>(Value::Tag::Sub, left, product);
      valueMap[ctx] = result;
      return {};
    }
    Value *result = curBasicBlock->create<BinaryInst>(tag, left, right);
    // 返回值
    valueMap[ctx] = result;
    return {};
  }

  virtual std::any
  visitSubUnaryExpr(sysy2022Parser::SubUnaryExprContext *ctx) override {
    valueMap[ctx] = createRValue(ctx->unaryExpr());
    return {};
  }

  virtual std::any
  visitParenExpr(sysy2022Parser::ParenExprContext *ctx) override {
    visit(ctx->expr());
    valueMap[ctx] = valueMap.at(ctx->expr());
    isLValueMap[ctx] = isLValueMap[ctx->expr()];
    return {};
  }

  virtual std::any
  visitLValExpr(sysy2022Parser::LValExprContext *ctx) override {
    visit(ctx->lVal());
    valueMap[ctx] = valueMap.at(ctx->lVal());
    isLValueMap[ctx] = isLValueMap[ctx->lVal()];
    return {};
  }

  virtual std::any
  visitIntLiteral(sysy2022Parser::IntLiteralContext *ctx) override {
    std::string intStr = ctx->getText();
    int result = std::stoi(intStr.c_str(), nullptr, 0);
    valueMap[ctx] = new ConstantValue(result);
    return {};
  }

  virtual std::any
  visitFloatLiteral(sysy2022Parser::FloatLiteralContext *ctx) override {
    std::string floatStr = ctx->getText();
    float result = std::stof(floatStr.c_str());
    valueMap[ctx] = new ConstantValue(result);
    return {};
  }

  virtual std::any
  visitFuncCall(sysy2022Parser::FuncCallContext *ctx) override {
    // auto *callee = cast<Function>(symbolTable.lookup(ctx->ID()->getText()));
    Function *callee = nullptr;
    int lineno = ctx->ID()->getSymbol()->getLine();
    std::vector<Value *> args;
    if (ctx->ID()->getText() == "starttime" ||
        ctx->ID()->getText() == "stoptime") {
      // 宏替换
      std::string funcName = "_sysy_" + ctx->ID()->getText();
      callee = cast<Function>(symbolTable.lookup(funcName));
      args.push_back(new ConstantValue(lineno));
    } else {
      callee = cast<Function>(symbolTable.lookup(ctx->ID()->getText()));
      // 处理实参
      for (int i = 0; i < ctx->expr().size(); ++i) {
        // TODO: array param
        auto *arg = createRValue(ctx->expr(i), callee->args[i]->getType());
        args.push_back(arg);
      }
    }
    auto *callInst = curBasicBlock->create<CallInst>(callee, args);
    valueMap[ctx] = callInst;
    return {};
  }

  virtual std::any
  visitRecUnaryExpr(sysy2022Parser::RecUnaryExprContext *ctx) override {
    // 创建指令
    if (ctx->op->getText() == "+") {
      // do nothing
      valueMap[ctx] = createRValue(ctx->unaryExpr());
    } else if (ctx->op->getText() == "-") {
      // -x => 0 - x
      auto *subVal = createRValue(ctx->unaryExpr());
      Value *result = nullptr;
      if (subVal->getType()->isFloatTy()) {
        result = curBasicBlock->create<BinaryInst>(
            Value::Tag::Sub, new ConstantValue(0.f), subVal);
      } else {
        result = curBasicBlock->create<BinaryInst>(
            Value::Tag::Sub, new ConstantValue(0), subVal);
      }
      valueMap[ctx] = result;
    } else {
      auto *subVal = createRValue(ctx->unaryExpr(), IntegerType::get());
      Value *result = nullptr;
      if (subVal->getType()->isIntegerTy()) {
        result = curBasicBlock->create<BinaryInst>(
            Value::Tag::Eq, subVal, new ConstantValue(0));
      } else {
        result = curBasicBlock->create<BinaryInst>(
            Value::Tag::Eq, subVal, new ConstantValue(0.f));
      }
      valueMap[ctx] = result;
    }
    return {};
  }

  virtual std::any visitLVal(sysy2022Parser::LValContext *ctx) override {
    auto varName = ctx->ID()->getText();
    Value *lVal = symbolTable.lookup(varName);
    std::vector<int> shape = symbolTable.lookupShape(varName);

    if (!isa<GlobalVariable>(lVal) && isa<Constant>(lVal)) {
      // pass through the constant, i2f and f2i should be done in createRValue
      valueMap[ctx] = constFolder.resolve(ctx);
      isLValueMap[ctx] = false;
      return {};
    }

    if (shape.empty() && ctx->expr().empty()) {
      valueMap[ctx] = lVal;
      isLValueMap[ctx] = true;
      return {};
    }

    std::vector<Value *> indices;
    for (auto *expr : ctx->expr()) {
      indices.push_back(createRValue(expr, IntegerType::get()));
    }
    Value *offset = new ConstantValue(0);
    for (int i = indices.size(); i--;) {
      int stride = 1;
      for (int j = i + 1; j < shape.size(); j++) {
        stride *= shape[j];
      }
      offset = curBasicBlock->create<BinaryInst>(
          Value::Tag::Add,
          curBasicBlock->create<BinaryInst>(
              Value::Tag::Mul, indices[i], new ConstantValue(stride)),
          offset);
    }
    Value *elementPtr = curBasicBlock->create<GetElementPtrInst>(lVal, offset);
    valueMap[ctx] = elementPtr;
    if (indices.size() == shape.size()) {
      isLValueMap[ctx] = true;
    } else {
      isLValueMap[ctx] = false;
    }
    return {};
  }

  virtual std::any visitEqExpr(sysy2022Parser::EqExprContext *ctx) override {
    // 获取左操作数
    auto *left = createRValue(ctx->cond(0));
    // 获取右操作数
    auto *right = createRValue(ctx->cond(1));
    typeConversion(&left, &right);
    // 创建指令
    Value::Tag tag = Value::Tag::Undef;
    if (ctx->op->getText() == "==") {
      tag = Value::Tag::Eq;
    } else {
      tag = Value::Tag::Ne;
    }
    Value *result = curBasicBlock->create<BinaryInst>(tag, left, right);
    // 返回值
    valueMap[ctx] = result;
    return {};
  }

  virtual std::any
  visitBinaryExpr(sysy2022Parser::BinaryExprContext *ctx) override {
    valueMap[ctx] = createRValue(ctx->expr());
    return {};
  }

  virtual std::any visitAndExpr(sysy2022Parser::AndExprContext *ctx) override {
    // 创建基本块
    BasicBlock *bcondtrue =
        new BasicBlock(curFunction, "bcondtrue_" + std::to_string(labelCnt++));
    BasicBlock *bcondfalse = bfalseStack.back();

    // 获取左操作数
    auto *left = createCondValue(ctx->cond(0));

    // 创建指令 维护CFG
    // bcondtrue是指对于该条件分支成功与否
    curBasicBlock->create<BranchInst>(left, bcondtrue, bcondfalse);
    curBasicBlock->successors.push_back(bcondtrue);
    curBasicBlock->successors.push_back(bcondfalse);
    bcondtrue->predecessors.push_back(curBasicBlock);
    bcondfalse->predecessors.push_back(curBasicBlock);

    // bcondtrue
    // 获取右操作数
    curFunction->addBasicBlock(bcondtrue);
    curBasicBlock = bcondtrue;

    auto *right = createCondValue(ctx->cond(1));
    valueMap[ctx] = right;

    return {};
  }

  virtual std::any visitOrExpr(sysy2022Parser::OrExprContext *ctx) override {
    // 创建基本块
    BasicBlock *bcondtrue =
        new BasicBlock(curFunction, "bcondtrue_" + std::to_string(labelCnt++));
    BasicBlock *bcondfalse = btrueStack.back();

    bfalseStack.push_back(bcondtrue);

    // 获取左操作数
    auto *left = createCondValue(ctx->cond(0));

    bfalseStack.pop_back();

    // 创建指令 维护CFG
    // bcondtrue是指对于该条件分支成功与否
    curBasicBlock->create<BranchInst>(left, bcondfalse, bcondtrue);
    curBasicBlock->successors.push_back(bcondtrue);
    curBasicBlock->successors.push_back(bcondfalse);
    bcondtrue->predecessors.push_back(curBasicBlock);
    bcondfalse->predecessors.push_back(curBasicBlock);

    // bcondtrue
    // 获取右操作数
    curFunction->addBasicBlock(bcondtrue);
    curBasicBlock = bcondtrue;

    auto *right = createCondValue(ctx->cond(1));
    valueMap[ctx] = right;

    return {};
  }

  virtual std::any visitRelExpr(sysy2022Parser::RelExprContext *ctx) override {
    // 获取左操作数
    auto *left = createRValue(ctx->cond(0));
    // 获取右操作数
    auto *right = createRValue(ctx->cond(1));
    typeConversion(&left, &right);
    // 创建指令
    Value::Tag tag = Value::Tag::Undef;
    if (ctx->op->getText() == "<") {
      tag = Value::Tag::Lt;
    } else if (ctx->op->getText() == ">") {
      tag = Value::Tag::Gt;
    } else if (ctx->op->getText() == "<=") {
      tag = Value::Tag::Le;
    } else {
      tag = Value::Tag::Ge;
    }
    Value *result = curBasicBlock->create<BinaryInst>(tag, left, right);
    // 返回值
    valueMap[ctx] = result;
    return {};
  }

  virtual std::any
  visitConstExpr(sysy2022Parser::ConstExprContext *ctx) override {
    valueMap[ctx] = createRValue(ctx->expr());
    return {};
  }
};
