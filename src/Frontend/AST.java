package Frontend;

import java.util.ArrayList;
import java.util.Objects;

public record AST(ArrayList<CompUnit> units) {

    // CompUnit -> Decl | FuncDef
    public sealed

    interface CompUnit permits Decl, FuncDef {
    }

    // Decl -> ['const'] 'int' Def {',' Def} ';'
    public record Decl(boolean constant, String bType, ArrayList<Def> defs) implements CompUnit, BlockItem {
        public Decl(boolean constant, String bType, ArrayList<Def> defs) {
            this.constant = constant;
            this.bType = Objects.requireNonNull(bType);
            this.defs = Objects.requireNonNull(defs);
        }

        public String getBType() {
            return bType;
        }
    }

    // Def -> Ident {'[' Exp ']'} ['=' Init]
    public record Def(String ident, ArrayList<Exp> indexes, Init init) {

        public Def(String ident, ArrayList<Exp> indexes, Init init) {
            this.ident = Objects.requireNonNull(ident);
            this.indexes = Objects.requireNonNull(indexes);
            this.init = init; // 可以为null
        }
    }

    // Init -> Exp | InitArray
    public sealed

    interface Init permits Exp, InitArray {
    }

    // InitArray -> '{' [ Init { ',' Init } ] '}'
    public static final class InitArray implements Init {
        public final ArrayList<Init> init;
        public int nowIdx = 0;

        public InitArray(ArrayList<Init> init) {
            this.init = Objects.requireNonNull(init);
        }

        public Init getNowInit() {
            return this.init.get(nowIdx);
        }

        public boolean hasInit(int count) {
            return nowIdx < this.init.size();
        }
    }

    /**
     * @param type  FuncType
     * @param ident name
     */ // FuncDef -> FuncType Ident '(' [FuncFParams] ')' Block
        // FuncFParams -> FuncFParam {',' FuncFParam}
        public record FuncDef(String type, String ident, ArrayList<FuncFParam> fParams, Block body) implements CompUnit {

            public FuncDef(String type, String ident, ArrayList<FuncFParam> fParams, Block body) {
                this.type = Objects.requireNonNull(type);
                this.ident = Objects.requireNonNull(ident);
                this.fParams = Objects.requireNonNull(fParams);
                this.body = Objects.requireNonNull(body);
            }

            public ArrayList<FuncFParam> getFParams() {
                return this.fParams;
            }
        }

    /**
     * @param array whether it is an array
     * @param sizes array sizes of each dim
     */ // FuncFParam -> BType Ident ['[' ']' { '[' Exp ']' }]
        public record FuncFParam(String bType, String ident, boolean array, ArrayList<Exp> sizes) {

            public FuncFParam(String bType, String ident, boolean array, ArrayList<Exp> sizes) {
                this.bType = Objects.requireNonNull(bType);
                this.ident = Objects.requireNonNull(ident);
                this.array = array;
                this.sizes = Objects.requireNonNull(sizes);
            }

            public String getBType() {
                return this.bType;
            }
        }

    // Block
        public record Block(ArrayList<BlockItem> items) implements Stmt {

            public Block(ArrayList<BlockItem> items) {
                this.items = Objects.requireNonNull(items);
            }
        }

    // BlockItem -> Decl | Stmt
    public sealed

    interface BlockItem permits Decl, Stmt {
    }

    // Stmt -> Assign | ExpStmt | Block | IfStmt | WhileStmt | Break | Continue | Return
    public sealed

    interface Stmt extends BlockItem
            permits Assign, ExpStmt, Block, IfStmt, WhileStmt, Break, Continue, Return {
    }

    // Assign
        public record Assign(LVal left, Exp right) implements Stmt {

            public Assign(LVal left, Exp right) {
                this.left = Objects.requireNonNull(left);
                this.right = Objects.requireNonNull(right);
            }

            public LVal getLVal() {
                return this.left;
            }

            public Exp getValue() {
                return this.right;
            }
        }

    /**
     * @param exp nullable, empty stmt if null
     */ // ExpStmt
        public record ExpStmt(Exp exp) implements Stmt {
        // 可以为null
    }

    // IfStmt
        public record IfStmt(Exp cond, Stmt thenTarget, Stmt elseTarget) implements Stmt {

            public IfStmt(Exp cond, Stmt thenTarget, Stmt elseTarget) {
                this.cond = Objects.requireNonNull(cond);
                this.thenTarget = Objects.requireNonNull(thenTarget);
                this.elseTarget = elseTarget; // 可以为null
            }
        }

    // WhileStmt
        public record WhileStmt(Exp cond, Stmt body) implements Stmt {

            public WhileStmt(Exp cond, Stmt body) {
                this.cond = Objects.requireNonNull(cond);
                this.body = Objects.requireNonNull(body);
            }
        }

    // Break
    public static final class Break implements Stmt {
        public Break() {
        }
    }

    // Continue
    public static final class Continue implements Stmt {
        public Continue() {
        }
    }

    // Return
        public record Return(Exp value) implements Stmt {
        // 可以为null

        public Exp getRetExp() {
                return this.value;
            }
        }

    // PrimaryExp -> Call | '(' Exp ')' | LVal | Number
    // Init -> Exp | InitArray
    // Exp -> BinaryExp | UnaryExp
    public sealed

    interface Exp extends Init, PrimaryExp
            permits BinaryExp, UnaryExp {
    }

    // BinaryExp: Arithmetic, Relation, Logical
        // BinaryExp -> Exp { Op Exp }, calc from left to right
        public record BinaryExp(Exp first, ArrayList<String> operators, ArrayList<Exp> follows) implements Exp, PrimaryExp {

            public BinaryExp(Exp first, ArrayList<String> operators, ArrayList<Exp> follows) {
                this.first = Objects.requireNonNull(first);
                this.operators = Objects.requireNonNull(operators);
                this.follows = Objects.requireNonNull(follows);
            }
        }

    // UnaryExp -> {UnaryOp} PrimaryExp
        public record UnaryExp(ArrayList<String> unaryOps, PrimaryExp primary) implements Exp, PrimaryExp {

            public UnaryExp(ArrayList<String> unaryOps, PrimaryExp primary) {
                this.unaryOps = Objects.requireNonNull(unaryOps);
                this.primary = Objects.requireNonNull(primary);
            }
        }

    // PrimaryExp -> Call | '(' Exp ')' | LVal | Number
    public sealed

    interface PrimaryExp permits BinaryExp, Call, Exp, LVal, Number, UnaryExp {
    }

    // LVal -> Ident {'[' Exp ']'}
    public record LVal(String ident, ArrayList<Exp> indexes) implements PrimaryExp {

        public LVal(String ident, ArrayList<Exp> indexes) {
            this.ident = Objects.requireNonNull(ident);
            this.indexes = Objects.requireNonNull(indexes);
        }
    }

    // Number
    public static final class Number implements PrimaryExp {

        public final String number;
        public final boolean isIntConst;
        public final boolean isFloatConst;
        public final int intConstVal;
        public final float floatConstVal;

        public Number(String number, String type) {
            this.number = Objects.requireNonNull(number);

            if ("decfloat".equals(type) || "hexfloat".equals(type)) {
                this.isFloatConst = true;
                this.isIntConst = false;
                this.floatConstVal = Float.parseFloat(number);
                this.intConstVal = (int) floatConstVal;
            } else if ("hex".equals(type) || "oct".equals(type) || "dec".equals(type)) {
                this.isIntConst = true;
                this.isFloatConst = false;
                this.intConstVal = switch (type) {
                    case "hex" -> Integer.parseInt(number.substring(2), 16);
                    case "oct" -> Integer.parseInt(number.substring(1), 8);
                    case "dec" -> Integer.parseInt(number);
                    default -> throw new IllegalArgumentException("不支持的数字类型: " + type);
                };
                this.floatConstVal = (float) intConstVal;
            } else {
                throw new IllegalArgumentException("未知的数字类型: " + type);
            }
        }

        public String getNumber() {
            return this.number;
        }

        public boolean isFloatConst() {
            return isFloatConst;
        }

        public boolean isIntConst() {
            return isIntConst;
        }

        public float getFloatConstVal() {
            return floatConstVal;
        }

        public int getIntConstVal() {
            return intConstVal;
        }

        @Override
        public String toString() {
            return isIntConst ?
                    "int " + intConstVal :
                    isFloatConst ?
                            "float" + floatConstVal :
                            "???" + number;
        }
    }

    // Call -> Ident '(' [ Exp {',' Exp} ] ')'
    // FuncRParams -> Exp {',' Exp}, already inlined in Call
    public static final class Call implements PrimaryExp {

        public final String ident;
        public final ArrayList<Exp> params;
        private int lineno = 0;

        public Call(String ident, ArrayList<Exp> params) {
            this.ident = Objects.requireNonNull(ident);
            this.params = Objects.requireNonNull(params);
        }

        public String getIdent() {
            return this.ident;
        }

        public ArrayList<Exp> getParams() {
            return this.params;
        }

        // 增加的getter/setter方法保持兼容性
        public int getLineno() {
            return lineno;
        }

        public void setLineno(int lineno) {
            this.lineno = lineno;
        }
    }

    public AST(ArrayList<CompUnit> units) {
        this.units = Objects.requireNonNull(units);
    }
}
