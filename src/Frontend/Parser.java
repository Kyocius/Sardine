package Frontend;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final TokenList tokenList;

    public Parser(TokenList tokenList){
        this.tokenList = tokenList;
    }

    public AST parseAST(){
        ArrayList<AST.CompUnit> units = new ArrayList<>();
        while (tokenList.hasNext()) {
            if (tokenList.ahead(2).getType().equals(TokenType.LPARENT)) {
                units.add(parseFuncDef());
            }
            else {
                units.add(parseDecl());
            }
        }
        return new AST(units);
    }

    private AST.FuncDef parseFuncDef(){
        Token type = tokenList.consume(TokenType.VOIDTK, TokenType.INTTK, TokenType.FLOATTK);
        Token ident = tokenList.consume(TokenType.IDENFR);
        ArrayList<AST.FuncFParam> fParams = new ArrayList<>();
        tokenList.consume(TokenType.LPARENT);
        if (!tokenList.get().getType().equals(TokenType.RPARENT)) {
            fParams = parseFuncFParams();
        }
        tokenList.consume(TokenType.RPARENT);
        AST.Block body = parseBlock();
        return new AST.FuncDef(type.getVal(), ident.getVal(), fParams, body);
    }

    private AST.Decl parseDecl(){
        ArrayList<AST.Def> defs = new ArrayList<>();
        boolean constant;
        if (tokenList.get().getType().equals(TokenType.CONSTTK)) {
            tokenList.consume();
            constant = true;
        }
        else {
            constant = false;
        }
        Token bType = tokenList.consume(TokenType.INTTK, TokenType.FLOATTK);
        defs.add(parseDef(constant));
        while (tokenList.get().getType().equals(TokenType.COMMA)) {
            tokenList.consume();
            defs.add(parseDef(constant));
        }
        tokenList.consume(TokenType.SEMICN);
        return new AST.Decl(constant, bType.getVal(), defs);
    }

    private AST.Def parseDef(boolean constant) {
        Token ident = tokenList.consume(TokenType.IDENFR);
        ArrayList<AST.Exp> indexes = new ArrayList<>();
        AST.Init init = null;
        while (tokenList.get().getType().equals(TokenType.LBRACK)) {
            tokenList.consume();
            indexes.add(parseAddExp());
            tokenList.consume(TokenType.RBRACK);
        }
        if (constant) {
            tokenList.consume(TokenType.ASSIGN);
            init = parseInitVal();
        }
        else {
            if (tokenList.hasNext() && tokenList.get().getType().equals(TokenType.ASSIGN)) {
                tokenList.consume();
                init = parseInitVal();
            }
        }
        return new AST.Def(ident.getVal(), indexes, init);
    }

    private AST.Init parseInitVal() {
        if (tokenList.get().getType().equals(TokenType.LBRACE)) {
            return parseInitArray();
        }
        else {
            return parseAddExp();
        }
    }

    private AST.InitArray parseInitArray(){
        ArrayList<AST.Init> init = new ArrayList<>();
        tokenList.consume(TokenType.LBRACE);
        if (!tokenList.get().getType().equals(TokenType.RBRACE)) {
            init.add(parseInitVal());
            while (tokenList.get().getType().equals(TokenType.COMMA)) {
                tokenList.consume();
                init.add(parseInitVal());
            }
        }
        tokenList.consume(TokenType.RBRACE);
        return new AST.InitArray(init);
    }

    private ArrayList<AST.FuncFParam> parseFuncFParams() {
        ArrayList<AST.FuncFParam> fParams = new ArrayList<>();
        fParams.add(parseFuncFParam());
        while (tokenList.hasNext() && tokenList.get().getType().equals(TokenType.COMMA)) {
            tokenList.consume(TokenType.COMMA);
            fParams.add(parseFuncFParam());
        }
        return fParams;
    }

    private AST.FuncFParam parseFuncFParam() {
        Token bType = tokenList.consume(TokenType.INTTK, TokenType.FLOATTK);
        Token ident = tokenList.consume(TokenType.IDENFR);
        boolean array = false;
        ArrayList<AST.Exp> sizes = new ArrayList<>();
        if (tokenList.hasNext() && tokenList.get().getType().equals(TokenType.LBRACK)) {
            array = true;
            tokenList.consume(TokenType.LBRACK);
            tokenList.consume(TokenType.RBRACK);
            while (tokenList.hasNext() && tokenList.get().getType().equals(TokenType.LBRACK)) {
                tokenList.consume(TokenType.LBRACK);
                sizes.add(parseAddExp());
                tokenList.consume(TokenType.RBRACK);
            }
        }
        return new AST.FuncFParam(bType.getVal(), ident.getVal(), array, sizes);
    }

    private AST.Block parseBlock() {
        ArrayList<AST.BlockItem> items = new ArrayList<>();
        tokenList.consume(TokenType.LBRACE);
        while (!tokenList.get().getType().equals(TokenType.RBRACE)) {
            items.add(parseBlockItem());
        }
        tokenList.consume(TokenType.RBRACE);
        return new AST.Block(items);
    }

    private AST.BlockItem parseBlockItem() {
        TokenType tokenType = tokenList.get().getType();
        if (tokenType.equals(TokenType.FLOATTK) || tokenType.equals(TokenType.INTTK) || tokenType.equals(TokenType.CONSTTK)) {
            return parseDecl();
        }
        else {
            return parseStmt();
        }
    }

    private AST.Stmt parseStmt() {
        TokenType stmtType = tokenList.get().getType();
        AST.Exp cond;
        switch (stmtType) {
            case LBRACE:
                return parseBlock();
            case IFTK:
                tokenList.consume();
                tokenList.consume(TokenType.LPARENT);
                cond = parseCond();
                tokenList.consume(TokenType.RPARENT);
                AST.Stmt thenTarget = parseStmt();
                AST.Stmt elseTarget = null;
                if (tokenList.hasNext() && tokenList.get().getType().equals(TokenType.ELSETK)) {
                    tokenList.consume(TokenType.ELSETK);
                    elseTarget = parseStmt();
                }
                return new AST.IfStmt(cond, thenTarget, elseTarget);
            case WHILETK:
                tokenList.consume();
                tokenList.consume(TokenType.LPARENT);
                cond = parseCond();
                tokenList.consume(TokenType.RPARENT);
                AST.Stmt body = parseStmt();
                return new AST.WhileStmt(cond, body);
            case BREAKTK:
                tokenList.consume(TokenType.BREAKTK);
                tokenList.consume(TokenType.SEMICN);
                return new AST.Break();
            case CONTINUETK:
                tokenList.consume(TokenType.CONTINUETK);
                tokenList.consume(TokenType.SEMICN);
                return new AST.Continue();
            case RETURNTK:
                tokenList.consume(TokenType.RETURNTK);
                AST.Exp value = null;
                if (!tokenList.get().getType().equals(TokenType.SEMICN)) {
                    value = parseAddExp();
                }
                tokenList.consume(TokenType.SEMICN);
                return new AST.Return(value);
            case IDENFR:
                // 先读出一整个 Exp 再判断是否只有一个 LVal (因为 LVal 可能是数组)
                AST.Exp temp2 = parseAddExp();
                AST.LVal left = extractLValFromExp(temp2);
                if (left == null) {
                    tokenList.consume(TokenType.SEMICN);
                    return new AST.ExpStmt(temp2);
                }
                else {
                    // 只有一个 LVal，可能是 Exp; 也可能是 Assign
                    if (tokenList.get().getType().equals(TokenType.ASSIGN)) {
                        tokenList.consume(TokenType.ASSIGN);
                        AST.Exp right = parseAddExp();
                        tokenList.consume(TokenType.SEMICN);
                        return new AST.Assign(left, right);
                    }
                    else {
                        tokenList.consume(TokenType.SEMICN);
                        return new AST.ExpStmt(temp2);
                    }
                }
            case SEMICN:
                tokenList.consume();
                return new AST.ExpStmt(null);
            default:
                AST.Exp exp = parseAddExp();
                return new AST.ExpStmt(exp);
        }
    }

    private AST.LVal parseLVal() {
        Token ident = tokenList.consume(TokenType.IDENFR);
        ArrayList<AST.Exp> indexes = new ArrayList<>();
        while (tokenList.hasNext() && tokenList.get().getType().equals(TokenType.LBRACK)) {
            tokenList.consume(TokenType.LBRACK);
            indexes.add(parseAddExp());
            tokenList.consume(TokenType.RBRACK);
        }
        return new AST.LVal(ident.getVal(), indexes);
    }

    private AST.PrimaryExp parsePrimary() {
        Token priExp = tokenList.get();
        if (priExp.getType().equals(TokenType.LPARENT)) {
            tokenList.consume();
            AST.Exp exp = parseAddExp();
            tokenList.consume(TokenType.RPARENT);
            return exp;
        }
        else if (priExp.getType().equals(TokenType.HEXCON)) {
            Token number = tokenList.consume();
            return new AST.Number(number.getVal(), "hex");
        }
        else if (priExp.getType().equals(TokenType.OCTCON)) {
            Token number = tokenList.consume();
            return new AST.Number(number.getVal(), "oct");
        }
        else if (priExp.getType().equals(TokenType.DECCON)) {
            Token number = tokenList.consume();
            return new AST.Number(number.getVal(), "dec");
        }
        else if (priExp.getType().equals(TokenType.DECFCON)) {
            Token number = tokenList.consume();
            return new AST.Number(number.getVal(), "decfloat");
        }
        else if (priExp.getType().equals(TokenType.HEXFCON)){
            Token number = tokenList.consume();
            return new AST.Number(number.getVal(), "hexfloat");
        }
        else if (priExp.getType().equals(TokenType.IDENFR) && tokenList.ahead(1).getType().equals(TokenType.LPARENT)) {
            return parseCall();
        }
        else {
            return parseLVal();
        }
    }

    private AST.Call parseCall() {
        Token ident = tokenList.consume(TokenType.IDENFR);
        ArrayList<AST.Exp> params = new ArrayList<>();
        tokenList.consume(TokenType.LPARENT);
        if (!tokenList.get().getType().equals(TokenType.RPARENT)) {
            params.add(parseAddExp());
            while (tokenList.get().getType().equals(TokenType.COMMA)) {
                tokenList.consume();
                params.add(parseAddExp());
            }
        }
        tokenList.consume(TokenType.RPARENT);
        return new AST.Call(ident.getVal(), params);
    }

    // 二元表达式的种类
    private enum BinaryExpType {
        LOR(TokenType.OR),
        LAND(TokenType.AND),
        EQ(TokenType.EQL, TokenType.NEQ),
        REL(TokenType.GRE, TokenType.LSS, TokenType.GEQ, TokenType.LEQ),
        ADD(TokenType.PLUS, TokenType.MINU),
        MUL(TokenType.MULT, TokenType.DIV, TokenType.MOD),
        ;

        private final List<TokenType> types;

        BinaryExpType(TokenType... types) {
            this.types = List.of(types);
        }

        public boolean contains(TokenType type) {
            return types.contains(type);
        }
    }

    // 解析二元表达式下一层表达式
    private AST.Exp parseSubBinaryExp(BinaryExpType expType) {
        return switch (expType) {
            case LOR -> parseBinaryExp(BinaryExpType.LAND);
            case LAND -> parseBinaryExp(BinaryExpType.EQ);
            case EQ -> parseBinaryExp(BinaryExpType.REL);
            case REL -> parseBinaryExp(BinaryExpType.ADD);
            case ADD -> parseBinaryExp(BinaryExpType.MUL);
            case MUL -> parseUnaryExp();
        };
    }

    // 解析二元表达式
    private AST.BinaryExp parseBinaryExp(BinaryExpType expType) {
        AST.Exp first = parseSubBinaryExp(expType);
        ArrayList<String> operators = new ArrayList<>();
        ArrayList<AST.Exp> follows = new ArrayList<>();
        while (tokenList.hasNext() && expType.contains(tokenList.get().getType())) {
            Token op = tokenList.consume(); // 取得当前层次的运算符
            operators.add(op.getVal());
            follows.add(parseSubBinaryExp(expType));
        }
        return new AST.BinaryExp(first, operators, follows);
    }

    private AST.UnaryExp parseUnaryExp() {
        ArrayList<String> unaryOps = new ArrayList<>();
        TokenType tokenType = tokenList.get().getType();
        while (tokenType.equals(TokenType.PLUS) || tokenType.equals(TokenType.MINU) || tokenType.equals(TokenType.NOT)) {
            unaryOps.add(tokenList.consume().getVal());
            tokenType = tokenList.get().getType();
        }
        AST.PrimaryExp primary = parsePrimary();
        return new AST.UnaryExp(unaryOps, primary);
    }

    private AST.BinaryExp parseAddExp() {
        return parseBinaryExp(BinaryExpType.ADD);
    }

    private AST.BinaryExp parseCond() {
        return parseBinaryExp(BinaryExpType.LOR);
    }

    // 从 Exp 中提取一个 LVal (如果不是仅有一个 LVal) 则返回 null
    private AST.LVal extractLValFromExp(AST.Exp exp) {
        AST.Exp cur = exp;
        while (cur instanceof AST.BinaryExp) {
            // 如果是二元表达式，只能有 first 否则一定不是一个 LVal
            if (!(((AST.BinaryExp) cur).follows().isEmpty())) {
                return null;
            }
            cur = ((AST.BinaryExp) cur).first();
        }
        assert cur instanceof AST.UnaryExp;
        if (!(((AST.UnaryExp) cur).unaryOps().isEmpty())) {
            return null; // 不能有一元运算符
        }
        AST.PrimaryExp primary = ((AST.UnaryExp) cur).primary();
        if (primary instanceof AST.LVal) {
            return (AST.LVal) primary;
        } else {
            return null; // 不是 LVal 
        }
    }
}
