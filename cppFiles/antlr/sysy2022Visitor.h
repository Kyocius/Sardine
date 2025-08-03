
// Generated from sysy2022.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "sysy2022Parser.h"



/**
 * This class defines an abstract visitor for a parse tree
 * produced by sysy2022Parser.
 */
class  sysy2022Visitor : public antlr4::tree::AbstractParseTreeVisitor {
public:

  /**
   * Visit parse trees produced by sysy2022Parser.
   */
    virtual std::any visitCompUnit(sysy2022Parser::CompUnitContext *context) = 0;

    virtual std::any visitVarDecl(sysy2022Parser::VarDeclContext *context) = 0;

    virtual std::any visitVarDef(sysy2022Parser::VarDefContext *context) = 0;

    virtual std::any visitInitVal(sysy2022Parser::InitValContext *context) = 0;

    virtual std::any visitFuncDef(sysy2022Parser::FuncDefContext *context) = 0;

    virtual std::any visitFuncFParam(sysy2022Parser::FuncFParamContext *context) = 0;

    virtual std::any visitBlock(sysy2022Parser::BlockContext *context) = 0;

    virtual std::any visitAssignStmt(sysy2022Parser::AssignStmtContext *context) = 0;

    virtual std::any visitExprStmt(sysy2022Parser::ExprStmtContext *context) = 0;

    virtual std::any visitBlockStmt(sysy2022Parser::BlockStmtContext *context) = 0;

    virtual std::any visitIfStmt(sysy2022Parser::IfStmtContext *context) = 0;

    virtual std::any visitWhileStmt(sysy2022Parser::WhileStmtContext *context) = 0;

    virtual std::any visitBreakStmt(sysy2022Parser::BreakStmtContext *context) = 0;

    virtual std::any visitContinueStmt(sysy2022Parser::ContinueStmtContext *context) = 0;

    virtual std::any visitReturnStmt(sysy2022Parser::ReturnStmtContext *context) = 0;

    virtual std::any visitAddSubExpr(sysy2022Parser::AddSubExprContext *context) = 0;

    virtual std::any visitMulDivModExpr(sysy2022Parser::MulDivModExprContext *context) = 0;

    virtual std::any visitSubUnaryExpr(sysy2022Parser::SubUnaryExprContext *context) = 0;

    virtual std::any visitParenExpr(sysy2022Parser::ParenExprContext *context) = 0;

    virtual std::any visitLValExpr(sysy2022Parser::LValExprContext *context) = 0;

    virtual std::any visitIntLiteral(sysy2022Parser::IntLiteralContext *context) = 0;

    virtual std::any visitFloatLiteral(sysy2022Parser::FloatLiteralContext *context) = 0;

    virtual std::any visitFuncCall(sysy2022Parser::FuncCallContext *context) = 0;

    virtual std::any visitRecUnaryExpr(sysy2022Parser::RecUnaryExprContext *context) = 0;

    virtual std::any visitLVal(sysy2022Parser::LValContext *context) = 0;

    virtual std::any visitEqExpr(sysy2022Parser::EqExprContext *context) = 0;

    virtual std::any visitBinaryExpr(sysy2022Parser::BinaryExprContext *context) = 0;

    virtual std::any visitOrExpr(sysy2022Parser::OrExprContext *context) = 0;

    virtual std::any visitRelExpr(sysy2022Parser::RelExprContext *context) = 0;

    virtual std::any visitAndExpr(sysy2022Parser::AndExprContext *context) = 0;

    virtual std::any visitConstExpr(sysy2022Parser::ConstExprContext *context) = 0;


};

