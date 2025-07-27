package IR;

import IR.Value.User;
import IR.Value.Value;

public class Use {
    private Value value;
    private final User user;

    public Use(Value value, User user){
        this.value = value;
        this.user = user;
    }



    // Getters and Setters
    public void setValue(Value value) {
        this.value = value;
    }

    public Value getValue() {
        return value;
    }

    public User getUser() {
        return user;
    }
}
