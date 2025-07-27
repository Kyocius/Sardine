package Pass.IR.Utils;

import IR.Type.IntegerType;
import IR.Type.PointerType;
import IR.Value.*;
import IR.Value.Instructions.AllocInst;
import IR.Value.Instructions.Phi;
import IR.Value.Instructions.PtrInst;
import IR.Value.Instructions.LoadInst;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Stack;

public class AliasAnalysis {

    //  获取某个数组value的根定义，要么是alloc指令(局部变量，局部数组或参数数组)，要么是全局数组
    public static Value getRoot(Value pointer){
        Value iter = pointer;

        //  这里loadInst是因为有可能为局部变量或参数数组(本质上最初定义也是allocInst)
        //  我们一路循环直到iter是AllocInst或者为全局数组
        while (iter instanceof PtrInst || iter instanceof LoadInst){
            if (iter instanceof PtrInst) {
                iter = ((PtrInst) iter).getTarget();
            } else {
                iter = ((LoadInst) iter).getPointer();
            }
        }

        return iter;
    }

    public static boolean isGlobal(Value array){
        return array instanceof GlobalVar;
    }
    public static boolean isParam(Value array){
        if(array instanceof Argument argument){
            return argument.getType().isPointerType();
        }
        return false;
    }

    public static boolean isLocal(Value array){
        return !isGlobal(array) && !isParam(array);
    }

    public static enum Alias{
        NO,
        MAY,
        YES
    }

    //要求传入指针类型，否则总是返回False
    public static boolean isStaticSpace(Value root, LinkedHashSet<Value> adding){
        if(!(root.getType().isPointerType())){
            return false;
        }
        if(root instanceof Argument){
            return false;
        }
        for(var val : adding){
            if(!(val instanceof ConstInteger)){
                return false;
            }
        }
        return true;
    }

    public static boolean isStaticSpace(Value ptr){
        return isStaticSpace(getRoot(ptr), dumpAddingListFromPtr(ptr));
    }


    private static void simplifyAddingList(LinkedHashSet<Value> adding){
        int allconstsum = 0;
        ArrayList<ConstInteger> needRemove = new ArrayList<>();
        for(var val : adding){
            if(val instanceof ConstInteger constval){
                allconstsum += constval.getValue();
                needRemove.add(constval);
            }
        }
        for(var removeVal : needRemove){
            adding.remove(removeVal);
        }
        adding.add(new ConstInteger(allconstsum, IntegerType.I32));
    }

    private static ConstInteger getAnyConstFromHashList(LinkedHashSet<Value> adding){
        for(var  val : adding){
            if(val instanceof ConstInteger constval){
                return constval;
            }
        }
        return null;
    }
    private static boolean isSameAddingList(LinkedHashSet<Value> adding1,LinkedHashSet<Value> adding2 ){
        var const1 = getAnyConstFromHashList(adding1);
        var const2 = getAnyConstFromHashList(adding2);
        if(!(const1.getValue()== const2.getValue())){
            return false;
        }
        for(var val : adding1){
            if(val instanceof ConstInteger){
                continue;
            }
            if(!adding2.contains(val)){
                return false;
            }
        }
        return true;
    }

    public static  LinkedHashSet<Value> dumpAddingListFromPtr(Value value){
        Value root;
        LinkedHashSet<Value> adding = new LinkedHashSet<>();
        if(value instanceof PtrInst){
            root = getRoot(value);
            var tmp = value;
            while(tmp != root){
                assert tmp instanceof PtrInst;
                adding.add(((PtrInst) tmp).getOffset());
                tmp = ((PtrInst) tmp).getTarget();
            }
        }else{
            root = value;
            adding.add(new ConstInteger(0, IntegerType.I32));
        }
        simplifyAddingList(adding);
        return adding;
    }

    // 要求传入指针类型。如果不是指针类型，则总是返回MAY
    public static Alias checkAlias(Value value1, Value value2){
        if(!(value1.getType() instanceof PointerType) || !(value2.getType() instanceof PointerType)){
            return Alias.MAY;
        }
        Value root1 = getRoot(value1), root2 = getRoot(value2);
        if(root1 instanceof Phi || root2 instanceof Phi){
            return Alias.MAY;
        }
        var adding1 = dumpAddingListFromPtr(value1);
        var adding2 = dumpAddingListFromPtr(value2);

        // value = root(alloca/Arg/Global) + addinglist
        if(!root1.equals(root2)){
            return Alias.NO;
        }
        //same root
        //Argument, Alloca, Global may be root
        //Argument, Alloca, Global, PtrInst may be value
        if(isStaticSpace(root1, adding1) && isStaticSpace(root2, adding2)){
            if(adding1.size()>1 || adding2.size()>1){
                if (isSameAddingList(adding2, adding2)){
                    return Alias.YES;
                }
            }else{
                assert adding1.size() == 1 && adding2.size() == 1;
                if (getAnyConstFromHashList(adding1).getValue() == getAnyConstFromHashList(adding2).getValue()){
                    return Alias.YES;
                }
                return Alias.NO;
            }
        }
        return Alias.MAY;
    }

    public static boolean isPhiRelated(Value value){
        if(!(value instanceof User user)){
            return false;
        }
        Stack<Value> checklist = new Stack<>();
        checklist.add(user);
        while(!checklist.isEmpty()){
            var check = checklist.pop();
            if(check instanceof Phi){
                return true;
            }
            if(check instanceof User){
                checklist.addAll(((User) check).getOperands());
            }
        }
        return false;
    }
}