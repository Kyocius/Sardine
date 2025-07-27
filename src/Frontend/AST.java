package Frontend;

import java.util.ArrayList;
/**
 * 所有的语法树节点
 * 为简化编译器实现难度, 对文法进行了改写(不影响语义)
 */
public class AST {

    private final ArrayList<CompUnit> units;

    // CompUnit -> Decl | FuncDef
    public interface CompUnit {
    }

    // Decl -> ['const'] 'int' Def {',' Def} ';'
    public static class Decl implements CompUnit, BlockItem {
        public boolean constant;
        public String bType;
        public ArrayList<Def> defs;

        public Decl(boolean constant, String bType, ArrayList<Def> defs){
            this.constant = constant;
            this.bType = bType;
            this.defs = defs;
        }

        public boolean isConstant() {
            return this.constant;
        }

        public String getBType() {
            return bType;
        }

        public ArrayList<Def> getDefs() {
            return defs;
        }
    }

    // Def -> Ident {'[' Exp ']'} ['=' Init]
    public static class Def {
        
        public String ident;
        public ArrayList<Exp> indexes;
        public Init init;

        public Def(String ident, ArrayList<Exp> indexes, Init init) {
            this.ident = ident;
            this.indexes = indexes;
            this.init = init;
        }

        public String getIdent() {
            return this.ident;
        }

        public ArrayList<Exp> getIndexes() {
            return this.indexes;
        }

        public Init getInit() {
            return this.init;
        }
    }

    // Init -> Exp | InitArray
    public interface Init {
    }

    // InitArray -> '{' [ Init { ',' Init } ] '}'
    public static class InitArray implements Init {
        public ArrayList<Init> init;
        public int nowIdx = 0;

        public InitArray(ArrayList<Init> init) {
            assert init != null;
            this.init = init;
        }

        public Init getNowInit() {
            return this.init.get(nowIdx);
        }

        public boolean hasInit(int count) {
            return nowIdx < this.init.size();
        }
    }

    // FuncDef -> FuncType Ident '(' [FuncFParams] ')' Block
    // FuncFParams -> FuncFParam {',' FuncFParam}
    public static class FuncDef implements CompUnit {

        public String type; // FuncType
        public String ident; // name
        public ArrayList<FuncFParam> fParams;
        public Block body;

        public FuncDef(String type, String ident, ArrayList<FuncFParam> fParams, Block body) {
            this.type = type;
            this.ident = ident;
            this.fParams = fParams;
            this.body = body;
        }

        public String getType() {
            return this.type;
        }

        public String getIdent() {
            return this.ident;
        }

        public ArrayList<FuncFParam> getFParams() {
            return this.fParams;
        }

        public Block getBody() {
            return this.body;
        }
    }

    // FuncFParam -> BType Ident ['[' ']' { '[' Exp ']' }]
    public static class FuncFParam {

        public String bType;
        public String ident;
        public boolean array; // whether it is an array
        public ArrayList<Exp> sizes; // array sizes of each dim

        public FuncFParam(String bType, String ident, boolean array, ArrayList<Exp> sizes) {
            this.bType = bType;
            this.ident = ident;
            this.array = array;
            this.sizes = sizes;
        }

        public String getBType() {
            return this.bType;
        }

        public String getIdent() {
            return this.ident;
        }

        public boolean isArray() {
            return this.array;
        }

        public ArrayList<Exp> getSizes() {
            return this.sizes;
        }
    }

    // Block
    public static class Block implements Stmt {

        public ArrayList<BlockItem> items;

        public Block(ArrayList<BlockItem> items) {
            assert items != null;
            this.items = items;
        }

        public ArrayList<BlockItem> getItems() {
            return this.items;
        }
    }

    // BlockItem -> Decl | Stmt
    public interface BlockItem {
    }

    // Stmt -> Assign | ExpStmt | Block | IfStmt | WhileStmt | Break | Continue | Return
    public interface Stmt extends BlockItem {
    }

    // Assign
    public static class Assign implements Stmt {

        public LVal left;
        public Exp right;

        public Assign(LVal left, Exp right) {
            this.left = left;
            this.right = right;
        }

        public LVal getLVal() {
            return this.left;
        }

        public Exp getValue() {
            return this.right;
        }
    }

    // ExpStmt
    public static class ExpStmt implements Stmt {
        public Exp exp; // nullable, empty stmt if null

        public ExpStmt(Exp exp) {
            // assert exp != null;
            this.exp = exp;
        }

        public Exp getExp() {
            return this.exp;
        }
    }

    // IfStmt
    public static class IfStmt implements Stmt {

        public Exp cond;
        public Stmt thenTarget;
        public Stmt elseTarget;

        public IfStmt(Exp cond, Stmt thenTarget, Stmt elseTarget) {
            assert cond != null;
            assert thenTarget != null;
            // assert elseTarget != null;
            this.cond = cond;
            this.thenTarget = thenTarget;
            this.elseTarget = elseTarget;
        }

        public Exp getCond() {
            return this.cond;
        }

        public Stmt getThenTarget() {
            return this.thenTarget;
        }

        public Stmt getElseTarget() {
            return this.elseTarget;
        }
    }

    // WhileStmt
    public static class WhileStmt implements Stmt {

        public Exp cond;
        public Stmt body;

        public WhileStmt(Exp cond, Stmt body) {
            assert cond != null;
            assert body != null;
            this.cond = cond;
            this.body = body;
        }

        public Exp getCond() {
            return this.cond;
        }

        public Stmt getBody() {
            return this.body;
        }
    }

    // Break
    public static class Break implements Stmt {
        public Break() {
        }
    }

    // Continue
    public static class Continue implements Stmt {
        public Continue() {
        }
    }

    // Return
    public static class Return implements Stmt {
        public Exp value;

        public Return(Exp value) {
            // assert value != null;
            this.value = value;
        }

        public Exp getRetExp() {
            return this.value;
        }
    }

    // PrimaryExp -> Call | '(' Exp ')' | LVal | Number
    // Init -> Exp | InitArray
    // Exp -> BinaryExp | UnaryExp
    public interface Exp extends Init, PrimaryExp {
    }

    // BinaryExp: Arithmetic, Relation, Logical
    // BinaryExp -> Exp { Op Exp }, calc from left to right
    public static class BinaryExp implements Exp {

        public Exp first;
        public ArrayList<String> operators;
        public ArrayList<Exp> follows;

        public BinaryExp(Exp first, ArrayList<String> operators, ArrayList<Exp> follows) {
            assert first != null;
            assert operators != null;
            assert follows != null;
            this.first = first;
            this.operators = operators;
            this.follows = follows;
        }

        public Exp getFirst() {
            return this.first;
        }

        public ArrayList<String> getOperators() {
            return this.operators;
        }

        public ArrayList<Exp> getFollows() {
            return this.follows;
        }
    }

    // UnaryExp -> {UnaryOp} PrimaryExp
    public static class UnaryExp implements Exp {

        public ArrayList<String> unaryOps;
        public PrimaryExp primary;

        public UnaryExp(ArrayList<String> unaryOps, PrimaryExp primary) {
            assert unaryOps != null;
            assert primary != null;
            this.unaryOps = unaryOps;
            this.primary = primary;
        }

        public ArrayList<String> getUnaryOps() {
            return this.unaryOps;
        }

        public PrimaryExp getPrimary() {
            return this.primary;
        }
    }

    // PrimaryExp -> Call | '(' Exp ')' | LVal | Number
    public interface PrimaryExp {
    }

    // LVal -> Ident {'[' Exp ']'}
    public static class LVal implements PrimaryExp {

        public String ident;
        public ArrayList<Exp> indexes;

        public LVal(String ident, ArrayList<Exp> indexes) {
            assert ident != null;
            assert indexes != null;
            this.ident = ident;
            this.indexes = indexes;
        }

        public String getIdent() {
            return this.ident;
        }

        public ArrayList<Exp> getIndexes() {
            return this.indexes;
        }
    }

    // Number
    public static class Number implements PrimaryExp {

        public String number;
        public boolean isIntConst = false;
        public boolean isFloatConst = false;
        public int intConstVal = 0;
        public float floatConstVal = (float) 0.0;

        public Number(String number, String type) {
            assert number != null;
            this.number = number;

            if (type.equals("decfloat") || type.equals("hexfloat")) {
                isFloatConst = true;
                floatConstVal = Float.parseFloat(number);
                intConstVal = (int) floatConstVal;
            }
            else if (type.equals("hex") || type.equals("oct") || type.equals("dec")) {
                isIntConst = true;
                intConstVal = switch (type) {
                    case "hex" -> Integer.parseInt(number.substring(2), 16);
                    case "oct" -> Integer.parseInt(number.substring(1), 8);
                    case "dec" -> Integer.parseInt(number);
                    default -> throw new AssertionError("Bad Number!");
                };
                floatConstVal = (float) intConstVal;
            }
            else {
                assert false;
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
            return isIntConst ? "int " + intConstVal : isFloatConst ? "float" + floatConstVal : "???" + number;
        }
    }

    // Call -> Ident '(' [ Exp {',' Exp} ] ')'
    // FuncRParams -> Exp {',' Exp}, already inlined in Call
    public static class Call implements PrimaryExp {

        public String ident;
        public ArrayList<Exp> params;

        public int lineno = 0;

        public Call(String ident, ArrayList<Exp> params) {
            assert ident != null;
            assert params != null;
            this.ident = ident;
            this.params = params;
        }

        public String getIdent() {
            return this.ident;
        }

        public ArrayList<Exp> getParams() {
            return this.params;
        }
    }

    public AST(ArrayList<CompUnit> units) {
        assert units != null;
        this.units = units;
    }

    public ArrayList<CompUnit> getUnits() {
        return this.units;
    }
}
