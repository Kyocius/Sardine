package Utils;

import IR.IRModule;
import IR.Value.*;
import IR.Value.Instructions.Instruction;
import Utils.DataStruct.IList;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashSet;

public class UseValueChecker {
    private BufferedWriter out = null;
    private LinkedHashSet<Value> globalValue = new LinkedHashSet<>();
    private LinkedHashSet<Value> localValue = new LinkedHashSet<>();
    private boolean hasFault = false;

    public void check(IRModule irModule, String check_name) throws IOException {
        globalValue = new LinkedHashSet<>();
        localValue = new LinkedHashSet<>();
        globalValue.addAll(irModule.globalVars());
        globalValue.addAll(irModule.functions());
        out = new BufferedWriter(new FileWriter(check_name + ".txt"));
        addALLInsts(irModule);
        for(GlobalVar var : irModule.globalVars()) {
            for(User user: var.getUserList()) {
                if(!localValue.contains(user) && !globalValue.contains(user)) {
                    printNotExist(var, user);
                } else {
                    checkBeUsed(var, user);
                }
            }
        }

        for(Function function: irModule.functions()) {
            checkFunction(function);
        }
        if(!hasFault) {
            out.write("useValue Check Pass!\n");
        }
        out.close();
    }

    private void addALLInsts(IRModule irModule) {
        for(Function function: irModule.functions()) {
            for (IList.INode<BasicBlock, Function> basicBlockFunctionINode : function.getBbs()) {
                BasicBlock block = basicBlockFunctionINode.getValue();
                for(IList.INode<Instruction, BasicBlock> instructionBasicBlockINode: block.getInsts()) {
                    localValue.add(instructionBasicBlockINode.getValue());
                }
            }
        }
    }

    private void checkFunction(Function function) throws IOException {
        LinkedHashSet<Value> localFuncValue = new LinkedHashSet<>(function.getArgs());
        for (IList.INode<BasicBlock, Function> basicBlockFunctionINode : function.getBbs()) {
            BasicBlock block = basicBlockFunctionINode.getValue();
            localFuncValue.add(block);
            for(IList.INode<Instruction, BasicBlock> instructionBasicBlockINode: block.getInsts()) {
                localFuncValue.add(instructionBasicBlockINode.getValue());
            }
        }
        //check函数本体的User
        for(User user: function.getUserList()) {
            if(!localValue.contains(user)) {
                printNotExist(function, user);
            } else {
                checkBeUsed(function, user);
            }
        }
        //check argument's user
        for(Argument arg: function.getArgs()) {
            for(User user: arg.getUserList()) {
                if(!localFuncValue.contains(user)) {
                    printNotExist(arg, user);
                } else {
                    checkBeUsed(arg, user);
                }
            }
        }
        for (IList.INode<BasicBlock, Function> basicBlockFunctionINode : function.getBbs()) {
            BasicBlock block = basicBlockFunctionINode.getValue();
            //check block's user
            for(User user: block.getUserList()) {
                out.write(block.toLLVMString() + " block user1 " + user.toLLVMString());
                if(!localFuncValue.contains(user)) {
                    printNotExist(block, user);
                } else {
                    checkBeUsed(block, user);
                }
            }
            //check instruction's user
            for(IList.INode<Instruction, BasicBlock> instructionBasicBlockINode: block.getInsts()) {
                Instruction ins = instructionBasicBlockINode.getValue();
                for(User user: ins.getUserList()) {
                    if(!localFuncValue.contains(user)) {
                        printNotExist(ins, user);
                    } else {
                        checkBeUsed(ins, user);
                    }
                }
            }
        }
    }

    private void checkBeUsed(Value beUsd, User user) throws IOException {
        boolean flag = false;
        for(Value value: user.getOperands()) {
            if(value == beUsd) {
                flag = true;
                break;
            }
        }
        if(!flag) {
            printWrongUse(beUsd, user);
        }
    }

    private void printNotExist(Value beUsed, Value user) throws IOException {
        hasFault = true;
        out.write(beUsed.toLLVMString() + "\nis used by NoneExist\n" + user.toLLVMString() + "\n\n");
    }

    private void printWrongUse(Value beUsed, Value user) throws IOException {
        hasFault = true;
        out.write(beUsed.toLLVMString() + "\nis not used by\n" + user.toLLVMString() + "\n\n");
    }
}
