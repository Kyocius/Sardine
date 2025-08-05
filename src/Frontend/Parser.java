package Frontend;

import java.util.ArrayList;
import java.util.List;

public class Parser {

    private final TokenList tokenList;

    public Parser(TokenList tokenList){
        this.tokenList = tokenList;
    }

    public AST parseAST() {
        ArrayList<AST.CompUnit> units = new ArrayList<>();
        while (tokenList.hasNext()) {
            // 判断是否为函数定义：当前 token 后第 2 个 token 是左括号
            boolean isFuncDef = tokenList.ahead(2).type().equals(TokenType.LPARENT);
            AST.CompUnit unit = isFuncDef ? parseFuncDef() : parseDecl();
            units.add(unit);
        }
        return new AST(units);
    }

    /**
     * 解析函数定义（FuncDef）。
     * 支持 void/int/float 返回类型，参数列表和函数体。
     * 语法示例：int foo(int a, float b) { ... }
     * @return AST.FuncDef 函数定义节点
     */
    private AST.FuncDef parseFuncDef() {
        // 解析返回类型（void/int/float）
        Token type = tokenList.consume(TokenType.VOIDTK, TokenType.INTTK, TokenType.FLOATTK);
        // 解析函数名标识符
        Token ident = tokenList.consume(TokenType.IDENFR);
        // 消耗左括号，进入参数列表
        tokenList.consume(TokenType.LPARENT);
        ArrayList<AST.FuncFParam> fParams;
        // 判断是否有参数
        if (!tokenList.get().type().equals(TokenType.RPARENT)) {
            fParams = parseFuncFParams();
        } else {
            fParams = new ArrayList<>();
        }
        // 消耗右括号，结束参数列表
        tokenList.consume(TokenType.RPARENT);
        // 解析函数体代码块
        AST.Block body = parseBlock();
        // 构造并返回函数定义节点
        return new AST.FuncDef(type.value(), ident.value(), fParams, body);
    }

    /**
     * 解析变量声明（Decl），包括常量和变量声明。
     * 支持多变量声明（逗号分隔），并处理类型和初始值。
     * @return AST.Decl 抽象语法树中的声明节点
     */
    private AST.Decl parseDecl() {
        ArrayList<AST.Def> defs = new ArrayList<>();
        boolean constant;
        // 判断是否为常量声明
        if (tokenList.get().type().equals(TokenType.CONSTTK)) {
            tokenList.consume();
            constant = true;
        } else {
            constant = false;
        }
        // 解析类型（int 或 float）
        Token bType = tokenList.consume(TokenType.INTTK, TokenType.FLOATTK);
        // 解析第一个变量定义
        defs.add(parseDef(constant));
        // 处理逗号分隔的多个变量定义
        while (tokenList.get().type().equals(TokenType.COMMA)) {
            tokenList.consume();
            defs.add(parseDef(constant));
        }
        // 末尾分号
        tokenList.consume(TokenType.SEMICN);
        // 构造声明节点
        return new AST.Decl(constant, bType.value(), defs);
    }

    /**
     * 解析单个变量定义（Def），包括变量名、数组下标和初始值。
     * @param constant 是否为常量定义
     * @return AST.Def 变量定义节点
     */
    private AST.Def parseDef(boolean constant) {
        // 变量名标识符
        Token ident = tokenList.consume(TokenType.IDENFR);
        ArrayList<AST.Exp> indexes = new ArrayList<>();
        AST.Init init = null;
        // 解析数组下标
        while (tokenList.get().type().equals(TokenType.LBRACK)) {
            tokenList.consume();
            indexes.add(parseAddExp());
            tokenList.consume(TokenType.RBRACK);
        }
        // 常量必须有初始值
        if (constant) {
            tokenList.consume(TokenType.ASSIGN);
            init = parseInitVal();
        }
        // 变量有初始值则解析
        else {
            if (tokenList.hasNext() && tokenList.get().type().equals(TokenType.ASSIGN)) {
                tokenList.consume();
                init = parseInitVal();
            }
        }
        // 构造变量定义节点
        return new AST.Def(ident.value(), indexes, init);
    }

    /**
     * 解析初始化值（Init），支持数组初始化和表达式初始化。
     * 如果当前 token 是左花括号，则解析为数组初始化，否则解析为表达式。
     * @return AST.Init 初始化值节点
     */
    private AST.Init parseInitVal() {
        // 判断是否为数组初始化
        if (tokenList.get().type().equals(TokenType.LBRACE)) {
            return parseInitArray();
        }
        // 否则为表达式初始化
        else {
            return parseAddExp();
        }
    }

    /**
     * 解析数组初始化（InitArray）。
     * 处理形如 `{val1, val2, ...}` 的初始化语法。
     * 支持嵌套数组初始化。
     * @return AST.InitArray 初始化数组节点
     */
    private AST.InitArray parseInitArray(){
        // 存储所有初始化值
        ArrayList<AST.Init> init = new ArrayList<>();
        // 消耗左花括号
        tokenList.consume(TokenType.LBRACE);
        // 如果不是右花括号，说明有初始化内容
        if (!tokenList.get().type().equals(TokenType.RBRACE)) {
            // 解析第一个初始化值
            init.add(parseInitVal());
            // 处理逗号分隔的多个初始化值
            while (tokenList.get().type().equals(TokenType.COMMA)) {
                tokenList.consume();
                init.add(parseInitVal());
            }
        }
        // 消耗右花括号
        tokenList.consume(TokenType.RBRACE);
        // 构造并返回 InitArray 节点
        return new AST.InitArray(init);
    }

    /**
     * 解析函数参数列表（FuncFParams）。
     * 形如：int a, float b, int c[10]
     * @return 参数列表的 AST 节点
     */
    private ArrayList<AST.FuncFParam> parseFuncFParams() {
        ArrayList<AST.FuncFParam> fParams = new ArrayList<>();
        // 解析第一个参数
        fParams.add(parseFuncFParam());
        // 处理逗号分隔的多个参数
        while (tokenList.hasNext() && tokenList.get().type().equals(TokenType.COMMA)) {
            tokenList.consume(TokenType.COMMA);
            fParams.add(parseFuncFParam());
        }
        return fParams;
    }

    /**
     * 解析单个函数参数（FuncFParam）。
     * 支持普通参数和数组参数（如 int a, float b[], int c[][10]）。
     * @return AST.FuncFParam 参数节点
     */
    private AST.FuncFParam parseFuncFParam() {
        // 解析参数类型（int 或 float）
        Token bType = tokenList.consume(TokenType.INTTK, TokenType.FLOATTK);
        // 解析参数名标识符
        Token ident = tokenList.consume(TokenType.IDENFR);
        boolean array = false; // 是否为数组参数
        ArrayList<AST.Exp> sizes = new ArrayList<>(); // 存储数组维度大小
        // 检查是否为数组参数
        if (tokenList.hasNext() && tokenList.get().type().equals(TokenType.LBRACK)) {
            array = true;
            tokenList.consume(TokenType.LBRACK); // 消耗 [
            tokenList.consume(TokenType.RBRACK); // 消耗 ]
            // 处理多维数组参数
            while (tokenList.hasNext() && tokenList.get().type().equals(TokenType.LBRACK)) {
                tokenList.consume(TokenType.LBRACK); // 消耗 [
                sizes.add(parseAddExp()); // 解析维度大小表达式
                tokenList.consume(TokenType.RBRACK); // 消耗 ]
            }
        }
        // 构造并返回参数节点
        return new AST.FuncFParam(bType.value(), ident.value(), array, sizes);
    }

    /**
     * 解析代码块（Block）。
     * Block 由一对大括号包裹，内部包含若干 BlockItem（声明或语句）。
     * @return AST.Block 代码块节点
     */
    private AST.Block parseBlock() {
        ArrayList<AST.BlockItem> items = new ArrayList<>();
        tokenList.consume(TokenType.LBRACE); // 消耗左大括号
        // 循环解析所有 BlockItem，直到遇到右大括号
        while (!tokenList.get().type().equals(TokenType.RBRACE)) {
            items.add(parseBlockItem());
        }
        tokenList.consume(TokenType.RBRACE); // 消耗右大括号
        return new AST.Block(items); // 构造并返回 Block 节点
    }

    /**
     * 解析代码块中的单个元素（BlockItem）。
     * BlockItem 可以是变量声明（Decl）或语句（Stmt）。
     * 如果当前 token 是类型或 const，则解析为声明，否则解析为语句。
     * @return AST.BlockItem 代码块元素节点
     */
    private AST.BlockItem parseBlockItem() {
        TokenType tokenType = tokenList.get().type();
        // 判断是否为声明（变量或常量）
        if (tokenType.equals(TokenType.FLOATTK) || tokenType.equals(TokenType.INTTK) || tokenType.equals(TokenType.CONSTTK)) {
            return parseDecl();
        }
        else {
            // 否则为语句
            return parseStmt();
        }
    }

    /**
     * 解析单条语句（Stmt）。
     * 支持块语句、条件语句、循环、break、continue、return、赋值、表达式语句等。
     * @return AST.Stmt 语句节点
     */
    private AST.Stmt parseStmt() {
        TokenType stmtType = tokenList.get().type();
        AST.Exp cond;
        switch (stmtType) {
            case LBRACE:
                // 代码块语句
                return parseBlock();
            case IFTK:
                // if 语句
                tokenList.consume();
                tokenList.consume(TokenType.LPARENT);
                cond = parseCond();
                tokenList.consume(TokenType.RPARENT);
                AST.Stmt thenTarget = parseStmt();
                AST.Stmt elseTarget = null;
                // 处理 else 分支
                if (tokenList.hasNext() && tokenList.get().type().equals(TokenType.ELSETK)) {
                    tokenList.consume(TokenType.ELSETK);
                    elseTarget = parseStmt();
                }
                return new AST.IfStmt(cond, thenTarget, elseTarget);
            case WHILETK:
                // while 循环语句
                tokenList.consume();
                tokenList.consume(TokenType.LPARENT);
                cond = parseCond();
                tokenList.consume(TokenType.RPARENT);
                AST.Stmt body = parseStmt();
                return new AST.WhileStmt(cond, body);
            case BREAKTK:
                // break 语句
                tokenList.consume(TokenType.BREAKTK);
                tokenList.consume(TokenType.SEMICN);
                return new AST.Break();
            case CONTINUETK:
                // continue 语句
                tokenList.consume(TokenType.CONTINUETK);
                tokenList.consume(TokenType.SEMICN);
                return new AST.Continue();
            case RETURNTK:
                // return 语句
                tokenList.consume(TokenType.RETURNTK);
                AST.Exp value = null;
                if (!tokenList.get().type().equals(TokenType.SEMICN)) {
                    value = parseAddExp();
                }
                tokenList.consume(TokenType.SEMICN);
                return new AST.Return(value);
            case IDENFR:
                // 可能是赋值语句或表达式语句
                // 先读出一整个 Exp 再判断是否只有一个 LVal (因为 LVal 可能是数组)
                AST.Exp temp2 = parseAddExp();
                AST.LVal left = extractLValFromExp(temp2);
                if (left == null) {
                    // 普通表达式语句
                    tokenList.consume(TokenType.SEMICN);
                    return new AST.ExpStmt(temp2);
                }
                else {
                    // 只有一个 LVal，可能是 Exp; 也可能是 Assign
                    if (tokenList.get().type().equals(TokenType.ASSIGN)) {
                        // 赋值语句
                        tokenList.consume(TokenType.ASSIGN);
                        AST.Exp right = parseAddExp();
                        tokenList.consume(TokenType.SEMICN);
                        return new AST.Assign(left, right);
                    }
                    else {
                        // 只有 LVal 的表达式语句
                        tokenList.consume(TokenType.SEMICN);
                        return new AST.ExpStmt(temp2);
                    }
                }
            case SEMICN:
                // 空语句
                tokenList.consume();
                return new AST.ExpStmt(null);
            default:
                // 其它表达式语句
                AST.Exp exp = parseAddExp();
                return new AST.ExpStmt(exp);
        }
    }

    /**
     * 解析左值（LVal），即变量名及其可能的数组下标。
     * 例如：a、a[1]、a[1][2] 等。
     * @return AST.LVal 左值节点
     */
    private AST.LVal parseLVal() {
        // 消耗标识符（变量名）
        Token ident = tokenList.consume(TokenType.IDENFR);
        ArrayList<AST.Exp> indexes = new ArrayList<>();
        // 解析所有数组下标
        while (tokenList.hasNext() && tokenList.get().type().equals(TokenType.LBRACK)) {
            tokenList.consume(TokenType.LBRACK);
            indexes.add(parseAddExp());
            tokenList.consume(TokenType.RBRACK);
        }
        // 构造并返回 LVal 节点
        return new AST.LVal(ident.value(), indexes);
    }

    /**
     * 解析主表达式（PrimaryExp）。
     * 主表达式包括括号表达式、数字常量、标识符（变量或函数调用）。
     * @return AST.PrimaryExp 主表达式节点
     */
    private AST.PrimaryExp parsePrimary() {
        Token priExp = tokenList.get();
        // 括号表达式，如 (a + b)
        if (priExp.type().equals(TokenType.LPARENT)) {
            tokenList.consume();
            AST.Exp exp = parseAddExp();
            tokenList.consume(TokenType.RPARENT);
            return exp;
        }
        // 十六进制整型常量
        else if (priExp.type().equals(TokenType.HEXCON)) {
            Token number = tokenList.consume();
            return new AST.Number(number.value(), "hex");
        }
        // 八进制整型常量
        else if (priExp.type().equals(TokenType.OCTCON)) {
            Token number = tokenList.consume();
            return new AST.Number(number.value(), "oct");
        }
        // 十进制整型常量
        else if (priExp.type().equals(TokenType.DECCON)) {
            Token number = tokenList.consume();
            return new AST.Number(number.value(), "dec");
        }
        // 十进制浮点常量
        else if (priExp.type().equals(TokenType.DECFCON)) {
            Token number = tokenList.consume();
            return new AST.Number(number.value(), "decfloat");
        }
        // 十六进制浮点常量
        else if (priExp.type().equals(TokenType.HEXFCON)){
            Token number = tokenList.consume();
            return new AST.Number(number.value(), "hexfloat");
        }
        // 函数调用表达式，如 foo(a, b)
        else if (priExp.type().equals(TokenType.IDENFR) && tokenList.ahead(1).type().equals(TokenType.LPARENT)) {
            return parseCall();
        }
        // 变量或数组访问表达式
        else {
            return parseLVal();
        }
    }

    /**
     * 解析函数调用表达式。
     * 例如：foo(a, b, c)
     * 解析函数名、参数列表，并构造 AST.Call 节点。
     * @return AST.Call 函数调用节点
     */
    private AST.Call parseCall() {
        // 消耗函数名标识符
        Token ident = tokenList.consume(TokenType.IDENFR);
        ArrayList<AST.Exp> params = new ArrayList<>();
        // 消耗左括号
        tokenList.consume(TokenType.LPARENT);
        // 如果不是右括号，说明有参数
        if (!tokenList.get().type().equals(TokenType.RPARENT)) {
            // 解析第一个参数
            params.add(parseAddExp());
            // 处理逗号分隔的多个参数
            while (tokenList.get().type().equals(TokenType.COMMA)) {
                tokenList.consume();
                params.add(parseAddExp());
            }
        }
        // 消耗右括号
        tokenList.consume(TokenType.RPARENT);
        // 构造并返回函数调用节点
        return new AST.Call(ident.value(), params);
    }

    /**
     * 二元表达式的种类枚举。
     * 每个枚举值对应一组 TokenType，表示该层级的运算符。
     * 用于递归下降解析表达式时区分不同优先级的二元运算。
     */
    private enum BinaryExpType {
        LOR(TokenType.OR), // 逻辑或
        LAND(TokenType.AND), // 逻辑与
        EQ(TokenType.EQL, TokenType.NEQ), // 相等/不等
        REL(TokenType.GRE, TokenType.LSS, TokenType.GEQ, TokenType.LEQ), // 关系运算符
        ADD(TokenType.PLUS, TokenType.MINU), // 加减
        MUL(TokenType.MULT, TokenType.DIV, TokenType.MOD), // 乘除模
        ;

        /** 当前枚举类型包含的所有 TokenType 运算符 */
        private final List<TokenType> types;

        /**
         * 构造方法，初始化运算符类型列表
         * @param types 该优先级下的所有 TokenType
         */
        BinaryExpType(TokenType... types) {
            this.types = List.of(types);
        }

        /**
         * 判断给定 TokenType 是否属于当前枚举类型
         * @param type 要判断的 TokenType
         * @return 属于则返回 true，否则返回 false
         */
        public boolean contains(TokenType type) {
            return types.contains(type);
        }
    }

    /**
     * 递归解析二元表达式的下一层级。
     * 根据当前运算符优先级，调用更低优先级的解析方法。
     * 例如：LOR -> LAND -> EQ -> REL -> ADD -> MUL -> Unary
     * @param expType 当前二元表达式的优先级类型
     * @return AST.Exp 解析得到的表达式节点
     */
    private AST.Exp parseSubBinaryExp(BinaryExpType expType) {
        // 使用 switch 表达式，根据优先级递归调用下一级解析方法
        return switch (expType) {
            case LOR -> parseBinaryExp(BinaryExpType.LAND);   // 逻辑或
            case LAND -> parseBinaryExp(BinaryExpType.EQ);    // 逻辑与
            case EQ -> parseBinaryExp(BinaryExpType.REL);     // 相等/不等
            case REL -> parseBinaryExp(BinaryExpType.ADD);    // 关系运算符
            case ADD -> parseBinaryExp(BinaryExpType.MUL);    // 加减
            case MUL -> parseUnaryExp();                      // 乘除模，最底层为一元表达式
        };
    }

    /**
     * 解析二元表达式（如加减、乘除、关系、逻辑等）。
     * 按照指定的运算符优先级类型，将表达式拆分为 first（第一个操作数）、operators（运算符列表）、follows（后续操作数列表）。
     * 例如：a + b - c 会被解析为 first=a，operators=["+", "-"]，follows=[b, c]。
     * @param expType 当前解析的二元表达式类型（优先级）
     * @return AST.BinaryExp 二元表达式节点
     */
    private AST.BinaryExp parseBinaryExp(BinaryExpType expType) {
        // 解析第一个操作数
        AST.Exp first = parseSubBinaryExp(expType);
        ArrayList<String> operators = new ArrayList<>(); // 运算符列表
        ArrayList<AST.Exp> follows = new ArrayList<>();  // 后续操作数列表
        // 循环处理同一优先级的所有运算符和操作数
        while (tokenList.hasNext() && expType.contains(tokenList.get().type())) {
            Token op = tokenList.consume(); // 取得当前层次的运算符
            operators.add(op.value());
            follows.add(parseSubBinaryExp(expType));
        }
        // 构造并返回二元表达式节点
        return new AST.BinaryExp(first, operators, follows);
    }

    /**
     * 解析一元表达式（UnaryExp）。
     * 支持连续的一元运算符（+、-、!），如 -!+a。
     * 解析所有一元运算符后，递归解析主表达式（PrimaryExp）。
     * @return AST.UnaryExp 一元表达式节点
     */
    private AST.UnaryExp parseUnaryExp() {
        ArrayList<String> unaryOps = new ArrayList<>(); // 存储一元运算符
        TokenType tokenType = tokenList.get().type();
        // 循环解析所有一元运算符
        while (tokenType.equals(TokenType.PLUS) || tokenType.equals(TokenType.MINU) || tokenType.equals(TokenType.NOT)) {
            unaryOps.add(tokenList.consume().value()); // 消耗并记录运算符
            tokenType = tokenList.get().type();
        }
        AST.PrimaryExp primary = parsePrimary(); // 解析主表达式
        return new AST.UnaryExp(unaryOps, primary); // 构造并返回一元表达式节点
    }

    /**
     * 解析加法表达式（AddExp）。
     * 该方法用于解析加减运算表达式，优先级为加减（+、-）。
     * 例如：a + b - c
     * @return AST.BinaryExp 加法表达式节点
     */
    private AST.BinaryExp parseAddExp() {
        return parseBinaryExp(BinaryExpType.ADD);
    }

    /**
     * 解析条件表达式（Cond）。
     * 条件表达式用于 if、while 等语句，优先级为逻辑或（||）。
     * @return AST.BinaryExp 条件表达式节点
     */
    private AST.BinaryExp parseCond() {
        return parseBinaryExp(BinaryExpType.LOR);
    }

    /**
     * 从表达式 AST.Exp 中提取左值（LVal）。
     * 仅当表达式严格为一个 LVal（没有二元/一元运算符包裹）时返回对应 AST.LVal，否则返回 null。
     * 例如：a、a[1]、a[1][2]，但 a+b 或 -a 都不会被识别为 LVal。
     *
     * @param exp 待提取的表达式节点
     * @return 若表达式为单一 LVal，则返回对应 AST.LVal，否则返回 null
     */
    private AST.LVal extractLValFromExp(AST.Exp exp) {
        AST.Exp cur = exp;
        // 递归剥离所有二元表达式层级，确保没有跟随操作数
        while (cur instanceof AST.BinaryExp) {
            // 如果有跟随操作数，说明不是单一 LVal
            if (!(((AST.BinaryExp) cur).follows().isEmpty())) {
                return null;
            }
            cur = ((AST.BinaryExp) cur).first();
        }
        // 剩下的必须是一元表达式
        assert cur instanceof AST.UnaryExp;
        // 一元运算符不为空则不是 LVal
        if (!(((AST.UnaryExp) cur).unaryOps().isEmpty())) {
            return null; // 不能有一元运算符
        }
        // 检查主表达式是否为 LVal
        AST.PrimaryExp primary = ((AST.UnaryExp) cur).primary();
        if (primary instanceof AST.LVal) {
            return (AST.LVal) primary;
        } else {
            return null; // 不是 LVal
        }
    }
}
