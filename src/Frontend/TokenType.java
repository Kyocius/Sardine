package Frontend;

public enum TokenType {
    IDENFR, // ident
    HEXCON, //  hex-const
    OCTCON, //  oct-const
    DECCON, //  dec-const
    HEXFCON,
    DECFCON,   //  float-const
    STRCON, // string-const
    CONSTTK,    //  "const"
    INTTK,  //  "int"
    FLOATTK,    //  "float"
    BREAKTK,//  "break"
    CONTINUETK, //  "continue"
    IFTK,   //  "if"
    ELSETK, //  "else"
    NOT,    //  "!"
    AND,    //  "&&"
    OR,     //  "||"
    WHILETK,    //  "while"
    RETURNTK,   //  "return"
    PLUS,   //  "+"
    MINU,   //  "-"
    VOIDTK, //  "void"
    MULT,   //  "*"
    DIV,    //  "/"
    MOD,    //  "%"
    LSS,    //  "<"
    LEQ,    //  "<="
    GRE,    //  ">"
    GEQ,    //  ">="
    EQL,    //  "=="
    NEQ,    //  "!="
    ASSIGN, //  "="
    SEMICN, //  ";"
    COMMA,  //  ","
    LPARENT,    //  "("
    RPARENT,    //  ")"
    LBRACK,     //  "["
    RBRACK,     //  "]"
    LBRACE,     //  "{"
    RBRACE,     //  "}"
}
