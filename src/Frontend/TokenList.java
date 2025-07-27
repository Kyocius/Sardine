package Frontend;

import java.util.ArrayList;
import java.util.Arrays;

public class TokenList {

    public final ArrayList<Token> tokens = new ArrayList<>();
    private int index = 0;

    public void addToken(Token token) {
        tokens.add(token);
    }

    public boolean hasNext() {
        return index < tokens.size();
    }

    public Token get() {
        return ahead(0);
    }

    public Token ahead(int count) {
        return tokens.get(index + count);
    }


    public Token consume(){
        return tokens.get(index++);
    }

    // Usage: tokenList.consumeExpected(TokenType.INT, TokenType.VOID)
    public Token consume(TokenType... types){
        Token token = tokens.get(index);
        for (TokenType type : types) {
            if (token.getType().equals(type)) {
                index++;
                return token;
            }
        }
        return null;
    }

    public Token consume(TokenType type) {
        Token token = tokens.get(index);
        if (token.getType().equals(type)) {
            index++;
            return token;
        }
        return null;
    }
}
