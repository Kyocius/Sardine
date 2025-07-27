package IR.Value;

import IR.Type.Type;
import IR.Use;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class Value {
    private String name;
    private Type type;
    private ArrayList<Use> useList;
    //  valNumber用于给value命名
    public static int valNumber = -1;
    public Value(){}
    public Value(String name, Type type){
        this.name = name;
        this.type = type;
        this.useList = new ArrayList<>();
    }

    public int store=-1;
    @Override
    public String toString(){
        return this.type + " " + this.name;
    }

    public String toLLVMString(){
        return this.type + " " + this.name;
    }

    //  Getters and Setters
    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public ArrayList<Use> getUseList() {
        return useList;
    }

    public void addUse(Use use){
        useList.add(use);
    }

    public ArrayList<User> getUserList(){
        ArrayList<User> users = new ArrayList<>();
        LinkedHashSet<User> userSet = new LinkedHashSet<>();
        for(Use use : useList){
            User user = use.getUser();
            if(!userSet.contains(user)){
                users.add(user);
                userSet.add(user);
            }
        }
        return users;
    }

    public void removeOneUseByUser(User user){
        int idx = -1;
        for(int i = 0; i < useList.size(); i++){
            if(useList.get(i).getUser().equals(user)){
                idx = i;
                break;
            }
        }
        if (idx == -1) return ;
        useList.remove(idx);
    }

    public void removeUseByUser(User user) {
        ArrayList<Use> tmpUseList = new ArrayList<>(useList);
        for (Use use : useList) {
            if (use.getUser().equals(user)) {
                tmpUseList.remove(use);
            }
        }
        this.useList = tmpUseList;
    }

    //  将该 this 的所有被用到的 user 中的操作数都替换成 value
    //  也就是说this将在程序中彻底消失(悲x
    //  所有用到this的地方都会被传入的value所替换
    public void replaceUsedWith(Value value) {
        ArrayList<Use> tmp = new ArrayList<>(useList);
        for (Use use : tmp) {
            User user = use.getUser();
            int idx = user.getOperands().indexOf(this);
            user.setOperand(idx, value);
        }
        this.useList.clear();
    }

    public void setName(String name) {
        this.name = name;
    }
    public void setType(Type type) {
        this.type = type;
    }
}
