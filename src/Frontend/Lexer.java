package Frontend;

import Driver.Config;

import java.io.*;

public class Lexer {

    private static final Lexer INSTANCE;
    private final PushbackReader inputReader = new PushbackReader(new FileReader(Config.inputFile));
    private char currentChar;
    private int currentCharCode;

    static {
        try {
            INSTANCE = new Lexer();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Lexer getInstance(){
        return INSTANCE;
    }

    private Lexer() throws FileNotFoundException {
    }

    private boolean isAlpha(char x){
        return (x <= 'z' && x >= 'a') || (x <= 'Z' && x >= 'A');
    }

    private boolean isNumber(char x) {
        return Character.isDigit(x);
    }

    private boolean isAlNum(char x){
        return (x <= 'z' && x >= 'a') || (x <= 'Z' && x >= 'A') || (x <= '9' && x >= '0');
    }

    private char readChar() throws IOException {
        currentCharCode = inputReader.read();
        currentChar = (char) currentCharCode;
        return currentChar;
    }

    private Token Ident() throws IOException {
        StringBuilder identBuilder = new StringBuilder();
        while (isAlNum(currentChar) || currentChar == '_'){
            identBuilder.append(currentChar);
            currentChar = readChar();
        }
        inputReader.unread(currentChar);
        String ident = identBuilder.toString();
        return switch (ident) {
            case "const" -> new Token(TokenType.CONSTTK, ident);
            case "int" -> new Token(TokenType.INTTK, ident);
            case "float" -> new Token(TokenType.FLOATTK, ident);
            case "break" -> new Token(TokenType.BREAKTK, ident);
            case "continue" -> new Token(TokenType.CONTINUETK, ident);
            case "if" -> new Token(TokenType.IFTK, ident);
            case "else" -> new Token(TokenType.ELSETK, ident);
            case "while" -> new Token(TokenType.WHILETK, ident);
            case "return" -> new Token(TokenType.RETURNTK, ident);
            case "void" -> new Token(TokenType.VOIDTK, ident);
            default -> new Token(TokenType.IDENFR, ident);
        };


    }
    private Token FString() throws IOException {
        StringBuilder sb = new StringBuilder().append('"');
        while (readChar() != '"') sb.append(currentChar);
        sb.append('"');
        return new Token(TokenType.STRCON, sb.toString());
    }

    /*
    * 产生整数/浮点数Token
    * Dec浮点数定义：
    * 1. [digit-seq].digit-seq [e+/-digit-seq]
    * 2. digit-seq.[e+/-digit-seq]
    * 3. digit-seq
    *
    * 可以是“数字序列.数字序列”，后面可选“e+/-数字序列”（如12.34e+56）
    可以是“数字序列.”，后面可选“e+/-数字序列”（如12.e-3）
    也可以只是“数字序列”（如123）
    *
    * */
    private Token Number() throws IOException {
        StringBuilder numBuilder = new StringBuilder();
        boolean isFloat = false;
        boolean isHex = false;
        boolean isOct = false;

        // 处理以小数点开头的浮点数（如 .0, .5, .AP-3）
        if (currentChar == '.') {
            isFloat = true;
            numBuilder.append(currentChar);
            readChar();
        }
        // 判断八进制
        else if (currentChar == '0') {
            numBuilder.append(currentChar);
            readChar();
            if (currentChar == 'x' || currentChar == 'X') {
                isHex = true;
                numBuilder.append(currentChar);
                readChar();
            } else if (isNumber(currentChar)) {
                isOct = true;
                // 不要在这里readChar()，让while循环处理
            } else if (currentChar == '.') {
                isFloat = true;
                numBuilder.append(currentChar);
                readChar();
            } else {
                inputReader.unread(currentChar);
                String num = numBuilder.toString();
                return new Token(TokenType.DECCON, num);
            }
        }

        while (true) {
            if (isHex) {
                if (isNumber(currentChar) ||
                    (currentChar >= 'a' && currentChar <= 'f') ||
                    (currentChar >= 'A' && currentChar <= 'F')) {
                    numBuilder.append(currentChar);
                } else if (currentChar == 'p' || currentChar == 'P') {
                    isFloat = true;
                    numBuilder.append(currentChar);
                } else if ((currentChar == '+' || currentChar == '-') && numBuilder.length() > 0) {
                    char last = numBuilder.charAt(numBuilder.length() - 1);
                    if (last == 'p' || last == 'P') {
                        numBuilder.append(currentChar);
                    } else {
                        break;
                    }
                } else if (currentChar == '.') {
                    isFloat = true;
                    numBuilder.append(currentChar);
                } else {
                    break;
                }
            } else if (isOct) {
                if (currentChar >= '0' && currentChar <= '7') {
                    numBuilder.append(currentChar);
                } else if (currentChar == '.') {
                    // 八进制数字后面有小数点，转换为浮点数
                    isFloat = true;
                    numBuilder.append(currentChar);
                } else if (currentChar == 'e' || currentChar == 'E') {
                    // 八进制数字后面有科学计数法，转换为浮点数
                    isFloat = true;
                    numBuilder.append(currentChar);
                } else if (currentChar == '8' || currentChar == '9') {
                    // 遇到8或9，说明这不是八进制数，转换为十进制处理
                    isOct = false;
                    numBuilder.append(currentChar);
                } else if ((currentChar == '+' || currentChar == '-') && numBuilder.length() > 0) {
                    char last = numBuilder.charAt(numBuilder.length() - 1);
                    if (last == 'e' || last == 'E') {
                        numBuilder.append(currentChar);
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            } else {
                if (isNumber(currentChar)) {
                    numBuilder.append(currentChar);
                } else if (currentChar == '.') {
                    if (isFloat) break;
                    isFloat = true;
                    numBuilder.append(currentChar);
                } else if (currentChar == 'e' || currentChar == 'E') {
                    isFloat = true;
                    numBuilder.append(currentChar);
                } else if ((currentChar == '+' || currentChar == '-') && numBuilder.length() > 0) {
                    char last = numBuilder.charAt(numBuilder.length() - 1);
                    if (last == 'e' || last == 'E') {
                        numBuilder.append(currentChar);
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            readChar();
        }

        inputReader.unread(currentChar);
        String num = numBuilder.toString();

        if (isHex && isFloat) return new Token(TokenType.HEXFCON, num);
        if (isHex) return new Token(TokenType.HEXCON, num);
        if (isFloat) return new Token(TokenType.DECFCON, num);  // 浮点数优先
        if (isOct) return new Token(TokenType.OCTCON, num);
        return new Token(TokenType.DECCON, num);
    }

    private void Comment() throws IOException {
        if (currentChar == '/') {
            // 单行注释，跳过直到行尾或文件结束
            while (readChar() != '\n' && currentCharCode != -1) {}
        } else if (currentChar == '*') {
            // 多行注释，跳过直到遇到 */
            while (true) {
                if (readChar() == -1) return;
                if (currentChar == '*') {
                    if (readChar() == '/') return;
                    else inputReader.unread(currentChar);
                }
            }
        }
    }

    private Token getToken() throws IOException {
        while ((currentCharCode = inputReader.read()) != -1){

            currentChar = (char) currentCharCode;

            while (currentChar == ' ' || currentChar == '\t' || currentChar == '\n'|| currentChar == '\r'){
                currentCharCode = inputReader.read();
                currentChar = (char) currentCharCode;
            }

            if(isAlpha(currentChar) || currentChar == '_'){
                return Ident();
            }
            if(isNumber(currentChar) || currentChar == '.'){
                return Number();
            }
            if(currentChar == '"'){
                return FString();
            }

            switch (currentChar){
                case '/':
                    readChar();
                    if(currentChar != '*' && currentChar != '/'){
                        inputReader.unread(currentChar);
                        return new Token(TokenType.DIV, "/");
                    }
                    else Comment();
                    break;
                case '!':
                    if(readChar() == '=') {
                        return new Token(TokenType.NEQ, "!=");
                    }
                    else {
                        inputReader.unread(currentCharCode);
                        return new Token(TokenType.NOT, "!");
                    }
                case '&':
                    if(readChar() == '&'){
                        return new Token(TokenType.AND, "&&");
                    }
                case '|':
                    if(readChar() == '|'){
                        return new Token(TokenType.OR, "||");
                    }
                case '+':
                    return new Token(TokenType.PLUS, "+");
                case '-':
                    return new Token(TokenType.MINU, "-");
                case '*':
                    return new Token(TokenType.MULT, "*");
                case '%':
                    return new Token(TokenType.MOD, "%");
                case '<':
                    if(readChar() == '='){
                        return new Token(TokenType.LEQ, "<=");
                    }
                    else{
                        inputReader.unread(currentCharCode);
                        return new Token(TokenType.LSS, "<");
                    }
                case '>':
                    if(readChar() == '='){
                        return new Token(TokenType.GEQ, ">=");
                    }
                    else{
                        inputReader.unread(currentCharCode);
                        return new Token(TokenType.GRE, ">");
                    }
                case '=':
                    if(readChar() == '='){
                        return new Token(TokenType.EQL, "==");
                    }
                    else {
                        inputReader.unread(currentCharCode);
                        return new Token(TokenType.ASSIGN, "=");
                    }
                case ';':
                    return new Token(TokenType.SEMICN, ";");
                case ',':
                    return new Token(TokenType.COMMA, ",");
                case '(':
                    return new Token(TokenType.LPARENT, "(");
                case ')':
                    return new Token(TokenType.RPARENT, ")");
                case '[':
                    return new Token(TokenType.LBRACK, "[");
                case ']':
                    return new Token(TokenType.RBRACK, "]");
                case '{':
                    return new Token(TokenType.LBRACE, "{");
                case '}':
                    return new Token(TokenType.RBRACE, "}");
                default:
                    return null;
            }
        }
        return null;
    }

    public TokenList scanTokens() throws IOException {
        TokenList tokenList = new TokenList();
        while (true){
            Token token = this.getToken();
            if(token == null) break;
            tokenList.addToken(token);
        }
        return tokenList;
    }
}
