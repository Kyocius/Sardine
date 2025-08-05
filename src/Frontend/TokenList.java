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

    public Token consume() {
        return tokens.get(index++);
    }

    /** 用于消费指定类型的Token，支持多个类型 */
    public Token consume(TokenType... types) {
        if (index >= tokens.size()) return null;
        Token token = tokens.get(index);
        if (Arrays.asList(types).contains(token.type())) {
            index++;
            return token;
        }
        return null;
    }

    /** 用于消费指定类型的Token，只支持单个类型 */
    public Token consume(TokenType type) {
        return consume(new TokenType[]{type});
    }
}
