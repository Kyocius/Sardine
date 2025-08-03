
// Generated from sysy2022.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "sysy2022Visitor.h"


/**
 * This class provides an empty implementation of sysy2022Visitor, which can be
 * extended to create a visitor which only needs to handle a subset of the available methods.
 */
class  sysy2022BaseVisitor : public sysy2022Visitor {
public:

  virtual std::any visitCompUnit(sysy2022Parser::CompUnitContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitVarDecl(sysy2022Parser::VarDeclContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitVarDef(sysy2022Parser::VarDefContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitInitVal(sysy2022Parser::InitValContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitFuncDef(sysy2022Parser::FuncDefContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitFuncFParam(sysy2022Parser::FuncFParamContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBlock(sysy2022Parser::BlockContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitAssignStmt(sysy2022Parser::AssignStmtContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitExprStmt(sysy2022Parser::ExprStmtContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBlockStmt(sysy2022Parser::BlockStmtContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitIfStmt(sysy2022Parser::IfStmtContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitWhileStmt(sysy2022Parser::WhileStmtContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBreakStmt(sysy2022Parser::BreakStmtContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitContinueStmt(sysy2022Parser::ContinueStmtContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitReturnStmt(sysy2022Parser::ReturnStmtContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitAddSubExpr(sysy2022Parser::AddSubExprContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitMulDivModExpr(sysy2022Parser::MulDivModExprContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitSubUnaryExpr(sysy2022Parser::SubUnaryExprContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitParenExpr(sysy2022Parser::ParenExprContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitLValExpr(sysy2022Parser::LValExprContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitIntLiteral(sysy2022Parser::IntLiteralContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitFloatLiteral(sysy2022Parser::FloatLiteralContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitFuncCall(sysy2022Parser::FuncCallContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRecUnaryExpr(sysy2022Parser::RecUnaryExprContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitLVal(sysy2022Parser::LValContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitEqExpr(sysy2022Parser::EqExprContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBinaryExpr(sysy2022Parser::BinaryExprContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitOrExpr(sysy2022Parser::OrExprContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRelExpr(sysy2022Parser::RelExprContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitAndExpr(sysy2022Parser::AndExprContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitConstExpr(sysy2022Parser::ConstExprContext *ctx) override {
    return visitChildren(ctx);
  }


};

