package Frontend;

public class Token {
    private TokenType type;
    private String val;

    public Token(TokenType type, String val){
        this.type = type;
        this.val = val;
    }

    public TokenType getType() {
        return type;
    }

    public String getVal() {
        return val;
    }
}
