#pragma once

#include <cstdint>
#include <string_view>
#include <variant>
#include <vector>

struct Decl {
    enum {FunctionDecl, ParamVarDecl, VarDecl} tag;
    std::string_view name;
};

struct FunctionDecl {
    static bool classof(const Decl *p) { return p->tag == Decl::FunctionDecl; }
    enum {Void, Int, Float} tag;
    int type;
    std::vector<Decl> params;
    Block body;
};

struct ParamVarDecl {
    static bool classof(const Decl *p) { return p->tag == Decl::ParamVarDecl; }
    bool is_const;
    bool has_init;

    std::vector<Expr *> dims;
    InitList init;
};

struct VarDecl {
    static bool classof(const Decl *p) { return p->tag == Decl::VarDecl; }
    bool is_const;
    bool is_glob;
    bool has_init;

    std::vector<Expr *> dims;
    InitList init;
};

struct InitList {
  Expr *val1;  // nullable
  std::vector<InitList> val2;
};

struct Stmt {
    enum { Assign, ExprStmt, DeclStmt, BlockStmt, IfStmt, WhileStmt, BreakStmt, ContinueStmt, ReturnStmt } tag;
};

struct Expr : Stmt {
    static bool classof(const Stmt *p) { return p->tag == Stmt::ExprStmt; }
    enum { Add, Sub, Rsb, Mul, Div, Mod, Lt, Le, Ge, Gt, Eq, Ne, And, Or,Call, Index, IntConst } tag;
    int result;
};

struct Binary : Expr {
    static bool classof(const Expr *p) { return p->tag == Expr::Add; }
    Expr *lhs;
    Expr *rhs;
};

struct Index : Expr {
    static bool classof(const Expr *p) { return p->tag == Expr::Index; }
    std::string_view name;
    // dims为空时即是直接访问普通变量
    std::vector<Expr *> dims;
    Decl *lhs_sym;  // typeck前是nullptr，若typeck成功则非空
};

struct IntConst : Expr {
    static bool classof(const Expr *p) { return p->tag == Expr::IntConst; }
    int val;
    static IntConst ZERO;  // 值为0的IntConst，很多地方会用到，所以做个单例
};

struct Call : Expr {
    static bool classof(const Expr *p) { return p->tag == Expr::Call; }
    //
};

struct DeclStmt : Stmt {
    static bool classof(const Stmt *p) { return p->tag == Stmt::DeclStmt; }
    std::vector<Decl> decls;
};

struct Block : Stmt {
    static bool classof(const Stmt *p) { return p->tag == Stmt::BlockStmt; }
    std::vector<Stmt *> stmts;
};

struct IfStmt : Stmt {
    static bool classof(const Stmt *p) { return p->tag == Stmt::IfStmt; }
    Expr *cond;
    Stmt *then_stmt;
    Stmt *else_stmt;
};

struct WhileStmt : Stmt {
    static bool classof(const Stmt *p) { return p->tag == Stmt::WhileStmt; }
    Expr *cond;
    Stmt *body;
};

struct BreakStmt : Stmt {
    static bool classof(const Stmt *p) { return p->tag == Stmt::BreakStmt; }
    // 因为Break和Continue不保存任何信息，用单例来节省一点内存
    static BreakStmt INSTANCE;
};

struct ContinueStmt : Stmt {
    static bool classof(const Stmt *p) { return p->tag == Stmt::ContinueStmt; }
    static ContinueStmt INSTANCE;
};

struct ReturnStmt : Stmt {
    static bool classof(const Stmt *p) { return p->tag == Stmt::ReturnStmt; }
    Expr *val;  // nullable
};

struct Type;
