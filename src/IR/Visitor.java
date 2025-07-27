package IR;

import Frontend.AST;
import IR.Type.*;
import IR.Value.*;
import IR.Value.Instructions.*;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

public class Visitor {
    private IRModule module;
    private Function CurFunction;
    private BasicBlock CurBasicBlock;
    private Value CurValue;
    //  符号表
    private final ArrayList<LinkedHashMap<String, Value>> symTbls = new ArrayList<>();
    private final ArrayList<LinkedHashMap<String, ArrayList<Integer>>> dimIndexTbls = new ArrayList<>();
    private final IRBuildFactory f = IRBuildFactory.getInstance();
    private final ArrayList<GlobalVar> globalVars = new ArrayList<>();

    //  库函数
    private final Function PrintFunc = new Function("@putint", VoidType.voidType);
    private final Function InputFunc = new Function("@getint", IntegerType.I32);
    private final Function PrintChFunc = new Function("@putch", VoidType.voidType);
    private final Function InputChFunc = new Function("@getch", IntegerType.I32);
    private final Function PrintArrFunc = new Function("@putarray", VoidType.voidType);
    private final Function InputArrFunc = new Function("@getarray", IntegerType.I32);
    private final Function PrintFloatFunc = new Function("@putfloat", VoidType.voidType);
    private final Function InputFloatFunc = new Function("@getfloat", FloatType.F32);
    private final Function PrintFArrFunc = new Function("@putfarray", VoidType.voidType);
    private final Function InputFArrFunc = new Function("@getfarray", IntegerType.I32);
    public static final Function MemsetFunc = new Function("@memset", VoidType.voidType);
    private final Function StartTimeFunc = new Function("@_sysy_starttime", VoidType.voidType);
    private final Function StopTimeFunc = new Function("@_sysy_stoptime", VoidType.voidType);
    private final Function ParallelStart = new Function("@parallelStart", IntegerType.I32);
    private final Function ParallelEnd = new Function("@parallelEnd", IntegerType.I32);


    //  从符号表中查找某个ident
    private Value find(String ident){
        int len = symTbls.size();
        for(int i = len - 1; i >= 0; i--){
            LinkedHashMap<String, Value> symTbl = symTbls.get(i);
            Value res = symTbl.get(ident);
            if(res != null){
                return res;
            }
        }

        return switch (ident) {
            case "getint" -> InputFunc;
            case "putint" -> PrintFunc;
            case "getch" -> InputChFunc;
            case "putch" -> PrintChFunc;
            case "getarray" -> InputArrFunc;
            case "putarray" -> PrintArrFunc;
            case "getfloat" -> InputFloatFunc;
            case "putfloat" -> PrintFloatFunc;
            case "getfarray" -> InputFArrFunc;
            case "putfarray" -> PrintFArrFunc;
            case "starttime" -> StartTimeFunc;
            case "stoptime" -> StopTimeFunc;
            default -> null;
        };

    }
    //  向符号表中放入元素
    private void pushSymbol(String ident, Value value){
        int len = symTbls.size();
        symTbls.get(len - 1).put(ident, value);
    }

    private void pushSymTbl(){
        symTbls.add(new LinkedHashMap<>());
    }

    private void popSymTbl(){
        int len = symTbls.size();
        symTbls.remove(len - 1);
    }

    private void pushDimIndex(String ident, ArrayList<Integer> dimIndex){
        int len = dimIndexTbls.size();
        dimIndexTbls.get(len - 1).put(ident, dimIndex);
    }

    private ArrayList<Integer> findDimIndex(String ident){
        int len = dimIndexTbls.size();
        for(int i = len - 1; i >= 0; i--){
            LinkedHashMap<String, ArrayList<Integer>> dimIndexTbl = dimIndexTbls.get(i);
            ArrayList<Integer> res = dimIndexTbl.get(ident);
            if(res != null){
                return res;
            }
        }
        return null;
    }

    private final ArrayList<BasicBlock> whileEntryBLocks = new ArrayList<>();
    private final ArrayList<BasicBlock> whileOutBlocks = new ArrayList<>();

    //  argLinkedHashMap用于保存FuncFParams
    //  因为当你访问FuncFParams时，你还没有进入Block，而只有进入Block你才能push新的符号表
    //  所以为了把FuncFParams的声明也放进符号表，我们用tmpLinkedHashMap来保存
    private final LinkedHashMap<String, Value> argLinkedHashMap = new LinkedHashMap<>();

    //  isFetch表示当目标变量为指针的情况是否要取值
    //  true返回值，false返回指针
    private void visitLValAST(AST.LVal lValAST, boolean isFetch){
        String ident = lValAST.ident();
        CurValue = find(ident);

        assert CurValue != null;
        /*
         * 1. 普通/全局变量的指针i32*: a
         * 2. 普通数组的值的指针: a[2][2]
         * 3. 传入参数的数组指针: a, a[0]
         *
         * */

        if (!CurValue.getType().isPointerType()) return;
        if (!lValAST.indexes().isEmpty()) {
            boolean isArrVal = true;
            Value basePtr = CurValue;
            ArrayList<Value> indexs = new ArrayList<>();
            for (AST.Exp exp : lValAST.indexes()) {
                visitExpAST(exp, false);
                indexs.add(CurValue);
            }
            ArrayList<Integer> dimIndex = findDimIndex(ident);
            assert dimIndex != null;
            if(indexs.size() != dimIndex.size()){
                isArrVal = false;
            }

            ArrayList<Integer> factor = new ArrayList<>();
            for(int i = 0; i < dimIndex.size(); i++) factor.add(0);
            factor.set(factor.size() - 1, 1);
            for (int i = factor.size() - 2; i >= 0; i--) {
                factor.set(i, factor.get(i + 1) * dimIndex.get(i + 1));
            }

            CurValue = f.buildBinaryInst(indexs.getFirst(), f.buildNumber(factor.getFirst()), OP.Mul, CurBasicBlock);
            for(int i = 1; i < indexs.size(); i++){
                Value tmpValue = f.buildBinaryInst(indexs.get(i), f.buildNumber(factor.get(i)), OP.Mul, CurBasicBlock);
                CurValue = f.buildBinaryInst(CurValue, tmpValue, OP.Add, CurBasicBlock);
            }
            CurValue = f.buildPtrInst(basePtr, CurValue, CurBasicBlock);
            if(!isArrVal) return ;
        }

        else{
            if(CurValue instanceof AllocInst allocInst){
                if(allocInst.isArray()) return ;
            }
            if(CurValue instanceof GlobalVar globalVar){
                if(globalVar.isArray()) return ;
            }
            if(CurValue instanceof Argument argument){
                return ;
            }
        }

        if (isFetch) {
            CurValue = f.buildLoadInst(CurValue, CurBasicBlock);
        }
    }

    private void visitPrimaryExpAST(AST.PrimaryExp primaryExpAST, boolean isConst){
        if(primaryExpAST instanceof AST.Number number){
            if(number.isIntConst){
                CurValue = f.buildNumber(number.getIntConstVal());
            }
            else if(number.isFloatConst){
                CurValue = f.buildNumber(number.getFloatConstVal());
            }
        }
        else if(primaryExpAST instanceof AST.Exp expAST){
            visitExpAST(expAST, isConst);
        }
        else if(primaryExpAST instanceof AST.LVal lValAST){
            visitLValAST(lValAST, true);
        }
        else if(primaryExpAST instanceof AST.Call callAST){
            Function function = (Function) find(callAST.getIdent());
            ArrayList<AST.Exp> exps = callAST.getParams();

            ArrayList<Value> values = new ArrayList<>();

            for(AST.Exp exp : exps){
                visitExpAST(exp, isConst);
                values.add(CurValue);
            }

            ArrayList<Argument> arguments = Objects.requireNonNull(function).getArgs();
            for(int i = 0; i < values.size(); i++){
                Value value = values.get(i);
                Type argType = arguments.get(i).getType();
                Type CurType = value.getType();
                if(CurType == IntegerType.I32 && argType == FloatType.F32){
                    values.set(i, f.buildConversionInst(value, OP.Itof, CurBasicBlock));
                }
                else if(CurType == FloatType.F32 && argType == IntegerType.I32){
                    values.set(i, f.buildConversionInst(value, OP.Ftoi, CurBasicBlock));
                }
            }

            if(function.equals(StartTimeFunc) || function.equals(StopTimeFunc)){
                values.add(f.buildNumber(0));
            }
            CurValue = f.buildCallInst(function, values, CurBasicBlock);
        }
    }

    private void visitUnaryExpAST(AST.UnaryExp unaryExpAST, boolean isConst){
        visitPrimaryExpAST(unaryExpAST.primary(), isConst);
        ArrayList<String> unaryOPs = unaryExpAST.unaryOps();
        int count = 0;
        for (int i = unaryOPs.size() - 1; i >= 0; i--) {
            String unaryOP = unaryOPs.get(i);
            if (unaryOP.equals("-")) {
                count++;
            } else if (unaryOP.equals("!")) {
                count = 0;
                CurValue = f.buildBinaryInst(CurValue, f.buildNumber(0), OP.Eq, CurBasicBlock);
            }
        }
        if (count % 2 == 1) {
            if(CurValue instanceof ConstInteger constInt){
                CurValue = f.buildNumber(-constInt.getValue());
            }
            else if(CurValue instanceof ConstFloat constFloat){
                CurValue = f.buildNumber(-constFloat.getValue());
            }
            else {
                CurValue = f.buildBinaryInst(f.buildNumber(0), CurValue, OP.Sub, CurBasicBlock);
            }
        }
    }

    private void visitBinaryExpAST(AST.BinaryExp binaryExpAST, boolean isConst){
        //  将binaryExp的first赋值到CurValue上
        visitExpAST(binaryExpAST.first(), isConst);
        ArrayList<String> ops = binaryExpAST.operators();
        ArrayList<AST.Exp> exps = binaryExpAST.follows();
        for(int i = 0; i < ops.size(); i++){
            Value TmpValue = CurValue;
            visitExpAST(exps.get(i), isConst);

            if(TmpValue instanceof Const left && CurValue instanceof Const right){
                CurValue = f.buildCalculateNumber(left, right, ops.get(i));
            }
            else {
                CurValue = f.buildBinaryInst(TmpValue, CurValue, OP.str2op(ops.get(i)), CurBasicBlock);
            }
        }
    }

    private void visitExpAST(AST.Exp expAST, boolean isConst){
        if(expAST instanceof AST.UnaryExp unaryExpAST){
            visitUnaryExpAST(unaryExpAST, isConst);
        }
        else if(expAST instanceof AST.BinaryExp binaryExpAST){
            visitBinaryExpAST(binaryExpAST, isConst);
        }
    }

    private void visitLAndExpAST(AST.Exp lAndExpAST, BasicBlock TrueBlock, BasicBlock FalseBlock){
        BasicBlock NxtLAndBlock;
        AST.BinaryExp binaryExp = (AST.BinaryExp) lAndExpAST;
        AST.Exp nowExp;
        ArrayList<AST.Exp> follows = binaryExp.follows();

        for(int i = 0; i <= follows.size(); i++){
            if(i == 0) nowExp = binaryExp.first();
            else nowExp = follows.get(i - 1);

            if(i != follows.size()){
                NxtLAndBlock = f.buildBasicBlock(CurFunction);
            }
            else NxtLAndBlock = TrueBlock;

            visitExpAST(nowExp, false);

            CurValue = f.buildBinaryInst(CurValue, f.buildNumber(0), OP.Ne, CurBasicBlock);
            f.buildBrInst(CurValue, NxtLAndBlock, FalseBlock, CurBasicBlock);
            CurBasicBlock = NxtLAndBlock;
        }
    }

    private void visitLOrExpAST(AST.Exp lOrExpAST, BasicBlock TrueBlock, BasicBlock FalseBlock){
        BasicBlock NxtLOrBlock;
        AST.BinaryExp binaryExpAST = (AST.BinaryExp) lOrExpAST;
        AST.Exp nowExp;
        ArrayList<AST.Exp> follows = binaryExpAST.follows();

        for(int i = 0; i <= follows.size(); i++){
            //  确定nowExp(需要计算是否为true的值)
            if(i == 0) nowExp = binaryExpAST.first();
            else nowExp = follows.get(i - 1);
            //  确定目标跳转的TrueBlock和NxtBlock
            if(i != follows.size()){
                NxtLOrBlock = f.buildBasicBlock(CurFunction);
            }
            else NxtLOrBlock = FalseBlock;

            visitLAndExpAST(nowExp, TrueBlock, NxtLOrBlock);

            CurBasicBlock = NxtLOrBlock;
        }
    }

    private void visitStmtAST(AST.Stmt stmtAST){
        if(stmtAST instanceof AST.Return retAST){
            if(retAST.getRetExp() != null) {
                visitExpAST(retAST.getRetExp(), false);

                Type CurType = CurValue.getType();
                Type CurFuncType = CurFunction.getType();
                if(CurType == IntegerType.I32 && CurFuncType == FloatType.F32){
                    CurValue = f.buildConversionInst(CurValue, OP.Itof, CurBasicBlock);
                }
                else if(CurFuncType == IntegerType.I32 && CurType == FloatType.F32){
                    CurValue = f.buildConversionInst(CurValue, OP.Ftoi, CurBasicBlock);
                }

                CurValue = f.buildRetInst(CurValue, CurBasicBlock);
            }
            else{
                f.buildRetInst(CurBasicBlock);
            }
        }
        else if(stmtAST instanceof AST.Assign assignAST){
            visitLValAST(assignAST.getLVal(), false);
            Value pointer = CurValue;
            visitExpAST(assignAST.getValue(), false);
            f.buildStoreInst(CurValue, pointer, CurBasicBlock);
        }
        else if(stmtAST instanceof AST.ExpStmt(AST.Exp exp)){
            visitExpAST(exp, false);
        }
        else if(stmtAST instanceof AST.Block blockAST){
            pushSymTbl();
            pushDimIndexTbl();
            visitBlockAST(blockAST);
            popDimIndexTbl();
            popSymTbl();
        }
        else if(stmtAST instanceof AST.IfStmt(AST.Exp cond, AST.Stmt thenTarget, AST.Stmt elseTarget)){
            BasicBlock TrueBlock = f.buildBasicBlock(CurFunction);
            BasicBlock NxtBlock = f.buildBasicBlock(CurFunction);
            BasicBlock FalseBlock = null;
            boolean hasElse = (elseTarget != null);
            if(hasElse){
                FalseBlock = f.buildBasicBlock(CurFunction);
                visitLOrExpAST(cond, TrueBlock, FalseBlock);
            }
            else{
                visitLOrExpAST(cond, TrueBlock, NxtBlock);
            }
            //  VisitCondAST之后，CurBlock的br已经构建完并指向正确的Block
            //  接下来我们为TrueBlock填写指令
            CurBasicBlock = TrueBlock;
            visitStmtAST(thenTarget);

            //  下面先考虑ifStmt中CurBlock不发生变化的情况
            //  即TrueBlock没有被构建br指令
            //  那么我们显然要给它构建br指令并设置CurBlock为NxtBlock
            //  然后我们发现就算CurBlock发生了变化，那么也是变成了TrueBlock里的NxtBlock
            //  而且是没有终结的状态，因此我们下面两行代码也可以适用于这种情况,令其跳转
            f.buildBrInst(NxtBlock, CurBasicBlock);

            if(elseTarget != null){
                //  开始构建FalseBlock
                CurBasicBlock = FalseBlock;
                visitStmtAST(elseTarget);

                //  原理同上，为CurBLock构建Br指令
                f.buildBrInst(NxtBlock, CurBasicBlock);
            }
            CurBasicBlock = NxtBlock;
        }
        else if(stmtAST instanceof AST.WhileStmt(AST.Exp cond, AST.Stmt body)){
            //  构建要跳转的CurCondBlock
            BasicBlock CondBlock = f.buildBasicBlock(CurFunction);
            f.buildBrInst(CondBlock, CurBasicBlock);
            CurBasicBlock = CondBlock;

            BasicBlock TrueBlock = f.buildBasicBlock(CurFunction);
            BasicBlock FalseBlock = f.buildBasicBlock(CurFunction);
            //  入栈，注意这里entry为CurCondBlock，因为continue要重新判断条件
            whileEntryBLocks.add(CondBlock);
            whileOutBlocks.add(FalseBlock);

            visitLOrExpAST(cond, TrueBlock, FalseBlock);

            CurBasicBlock = TrueBlock;
            visitStmtAST(body);
            f.buildBrInst(CondBlock, CurBasicBlock);
            CurBasicBlock = FalseBlock;

            //  while内的指令构建完了，出栈
            whileEntryBLocks.removeLast();
            whileOutBlocks.removeLast();
        }
        else if(stmtAST instanceof AST.Break){
            if(whileOutBlocks.isEmpty()) return;

            int len = whileOutBlocks.size();
            BasicBlock whileOutBlock = whileOutBlocks.get(len - 1);

            f.buildBrInst(whileOutBlock, CurBasicBlock);
            CurBasicBlock = f.buildBasicBlock(CurFunction);
        }
        else if(stmtAST instanceof AST.Continue ){
            if(whileEntryBLocks.isEmpty()) return;

            int len = whileEntryBLocks.size();
            BasicBlock whileEntryBlock = whileEntryBLocks.get(len - 1);

            f.buildBrInst(whileEntryBlock, CurBasicBlock);
            CurBasicBlock = f.buildBasicBlock(CurFunction);
        }
    }

    //  很重要的一个函数，visit杂乱无章的初始值并分析出哪里需要填充value
    private ArrayList<Value> visitInitArray(ArrayList<Integer> indexs, AST.InitArray initArray, Value fillValue, boolean isConst){
        int curNum = 0;
        int totSize = 1;
        ArrayList<Value> values = new ArrayList<>();
        for (Integer index : indexs) {
            totSize *= index;
        }

        for(AST.Init init : initArray.init){
            if(init instanceof AST.Exp expAST){
                curNum++;
                visitExpAST(expAST, isConst);
                values.add(CurValue);
                //  TODO ！！！常量数组的优化！！！
            }
            else if(init instanceof AST.InitArray newInitArray){
                ArrayList<Integer> newIndexs = getIntegers(indexs, curNum);
                ArrayList<Value> newValues = visitInitArray(newIndexs, newInitArray, fillValue, isConst);
                values.addAll(newValues);
                curNum += newValues.size();
            }
        }

        //  填充元素
        for(int i = curNum; i < totSize; i++){
            values.add(fillValue);
        }
        return values;
    }

    private static ArrayList<Integer> getIntegers(ArrayList<Integer> indexs, int curNum) {
        ArrayList<Integer> newIndexs = new ArrayList<>();
        int start = 0;
        if(curNum == 0){
            start = 1;
        }
        else{
            int tmpMul = 1;
            for(int i = indexs.size() - 1; i >= 0; i--){
                tmpMul *= indexs.get(i);
                if(curNum % tmpMul != 0){
                    start = i + 1;
                    break;
                }
            }
        }

        for(int i = start; i < indexs.size(); i++){
            newIndexs.add(indexs.get(i));
        }
        return newIndexs;
    }

    private void visitConstDef(AST.Def def, Type type, boolean isGlobal){
        String ident = def.ident();
        AST.Init init = def.init();
        //  常量数组
        if (!def.indexes().isEmpty()) {
            visitArray(ident, init, def.indexes(), type, isGlobal, true);
        }
        //  普通常量
        else {
            visitExpAST((AST.Exp) init, true);
            //  根据定义纠正类型
            if(type == IntegerType.I32 && CurValue.getType().isFloatTy()){
                CurValue = f.buildNumber((int) ((ConstFloat) CurValue).getValue());
            }
            else if(type == FloatType.F32 && CurValue.getType().isIntegerTy()){
                CurValue = f.buildNumber((float) ((ConstInteger) CurValue).getValue());
            }
            pushSymbol(ident, CurValue);
        }
    }

    private void visitVarDef(AST.Def def, Type type ,boolean isGlobal){
        String ident = def.ident();
        AST.Init init = def.init();
        Value fillValue = (type == IntegerType.I32 ? f.buildNumber(0) : f.buildNumber((float) 0.0));

        //  数组
        if(!def.indexes().isEmpty()){
            visitArray(ident, init, def.indexes(), type, isGlobal, false);
        }
        //  普通变量
        else{
            if(isGlobal){
                if(init instanceof AST.Exp expAST){
                    visitExpAST(expAST, true);
                    CurValue = f.buildGlobalVar(ident, CurValue.getType(), CurValue);
                }
                else if(init == null){
                    CurValue = f.buildGlobalVar(ident, type, fillValue);
                }
                globalVars.add((GlobalVar) CurValue);
            }
            else{
                CurValue = f.buildAllocInst(type, CurBasicBlock);
                if (init instanceof AST.Exp expAST){
                    Value TmpValue = CurValue;
                    visitExpAST(expAST, false);
                    f.buildStoreInst(CurValue, TmpValue, CurBasicBlock);
                    CurValue = TmpValue;
                }
            }
            pushSymbol(ident, CurValue);
        }
    }

    private void visitArray(String ident, AST.Init init, ArrayList<AST.Exp> indexsAST, Type type, boolean isGlobal, boolean isConst){
        ArrayList<Integer> dimIndexs = new ArrayList<>();
        Value fillValue = (type == IntegerType.I32 ? f.buildNumber(0) : f.buildNumber((float) 0.0));

        if(!isGlobal) fillValue = new Value("flag", VoidType.voidType);

        int size = 1;
        for(AST.Exp exp : indexsAST){
            visitExpAST(exp, true);
            int x = ((ConstInteger) CurValue).getValue();
            dimIndexs.add(x);
            size *= x;
        }
        pushDimIndex(ident, dimIndexs);
        ArrayList<Value> values = new ArrayList<>();
        if(init instanceof AST.InitArray initArray){
            if(!initArray.init.isEmpty() || !isGlobal) {
                values = visitInitArray(dimIndexs, initArray, fillValue, isConst);
            }
            if(initArray.init.isEmpty() && isGlobal){
                for(int i = 0; i < size; i++){
                    values.add(fillValue);
                }
            }
        }

        if(isGlobal){
            CurValue = f.getGlobalArray(ident, type, values, isConst);
            globalVars.add((GlobalVar) CurValue);
            if (init == null) ((GlobalVar) CurValue).setZeroInit(size);
            pushSymbol(ident, CurValue);
        }
        else{
            Value basePtr = f.buildAllocInst(size, type, CurBasicBlock, isConst, values);
            if (init != null) {
                ArrayList<Value> memsetValues = new ArrayList<>();
                memsetValues.add(basePtr);
                memsetValues.add(f.buildNumber(0));
                memsetValues.add(f.buildNumber(size * 4));
                f.buildCallInst(MemsetFunc, memsetValues, CurBasicBlock);
            }
            for(int i = 0; i < values.size(); i++){
                Value nowValue = values.get(i);
                if(nowValue.getName().equals("flag")){
                    continue;
                }
                CurValue = f.buildPtrInst(basePtr, f.buildNumber(i), CurBasicBlock);
                f.buildStoreInst(nowValue, CurValue, CurBasicBlock);
            }
            pushSymbol(ident, basePtr);
        }
    }

    private void visitDeclAST(AST.Decl declAST, boolean isGlobal){
        boolean isConst = declAST.constant();
        String typeStr = declAST.getBType();
        ArrayList<AST.Def> defs = declAST.defs();

        for (AST.Def def : defs) {
            Type type = null;
            if(typeStr.equals("int")) type = IntegerType.I32;
            else if(typeStr.equals("float")) type = FloatType.F32;
            if(isConst) visitConstDef(def, type, isGlobal);
            else visitVarDef(def, type, isGlobal);
        }
    }

    private void visitBlockItemAST(AST.BlockItem blockItemAST){
        if(blockItemAST instanceof AST.Stmt stmtAST){
            visitStmtAST(stmtAST);
        }
        else if(blockItemAST instanceof AST.Decl declAST){
            visitDeclAST(declAST, false);
        }
    }

    private void visitBlockAST(AST.Block blockAST){
        ArrayList<AST.BlockItem> blockItemASTS = blockAST.items();
        for(AST.BlockItem blockItemAST : blockItemASTS){
            visitBlockItemAST(blockItemAST);
        }
    }

    private void visitFuncDefAST(AST.FuncDef funcDefAST) {
        String ident = funcDefAST.ident();
        String type = funcDefAST.type();
        CurFunction = f.buildFunction(ident, type, module);

        pushSymbol(ident, CurFunction);
        argLinkedHashMap.clear();

        pushSymTbl();
        pushDimIndexTbl();
        CurBasicBlock = f.buildBasicBlock(CurFunction);
        if(!funcDefAST.getFParams().isEmpty()){
            //  开始构建entry基本块
            ArrayList<AST.FuncFParam> funcFParams = funcDefAST.getFParams();
            for(AST.FuncFParam funcFParam : funcFParams){
                //  平平无奇的起名环节
                String argName = funcFParam.ident();
                String argType = funcFParam.getBType();
                Argument argument;
                if(funcFParam.array()){
                    ArrayList<Integer> dimIndexs = new ArrayList<>();
                    dimIndexs.add(1);
                    for (AST.Exp exp : funcFParam.sizes()) {
                        visitExpAST(exp, true);
                        dimIndexs.add(((ConstInteger) CurValue).getValue());
                    }
                    argument = f.buildArgument(argName, argType + "*", CurFunction);
                    pushDimIndex(argName, dimIndexs);
                    pushSymbol(argName, argument);
                }
                else {
                    argument = f.buildArgument(argName, argType, CurFunction);
                    AllocInst allocInst = f.buildAllocInst(argument.getType(), CurBasicBlock);
                    f.buildStoreInst(argument, allocInst, CurBasicBlock);
                    pushSymbol(argName, allocInst);
                }
            }
            BasicBlock TmpBasicBlock = f.buildBasicBlock(CurFunction);
            f.buildBrInst(TmpBasicBlock, CurBasicBlock);
            CurBasicBlock = TmpBasicBlock;
        }

        visitBlockAST(funcDefAST.body());

        popSymTbl();
        popDimIndexTbl();

        //  删除掉br/ret后面不可达的语句以及无语句的bb
        IList.INode<BasicBlock, Function> itBbNode = CurFunction.getBbs().getHead();
        while (itBbNode != null){
            BasicBlock bb = itBbNode.getValue();
            itBbNode = itBbNode.getNext();
            boolean isTerminal = isTerminal(bb);

            // 如果没有ret语句，构建一个ret void
            if(!isTerminal){
                if(CurFunction.getType().isVoidTy()){
                    f.buildRetInst(CurBasicBlock);
                }
                else if(CurFunction.getType().isIntegerTy()) f.buildRetInst(f.buildNumber(0), CurBasicBlock);
                else if(CurFunction.getType().isFloatTy()) f.buildRetInst(f.buildNumber((float) 0.0), CurBasicBlock);
            }
        }
    }

    private static boolean isTerminal(BasicBlock bb) {
        boolean isTerminal = false;
        IList.INode<Instruction, BasicBlock> itInstNode = bb.getInsts().getHead();

        while (itInstNode != null){
            Instruction inst = itInstNode.getValue();
            itInstNode = itInstNode.getNext();
            if (isTerminal){
                inst.removeSelf();
            }
            else{
                if(inst instanceof RetInst || inst instanceof BrInst){
                    isTerminal = true;
                }
            }
        }
        return isTerminal;
    }

    private void initLibFunc(){
        PrintFunc.addArg(new Argument("x", IntegerType.I32, PrintFunc));
        PrintChFunc.addArg(new Argument("x", IntegerType.I32, PrintChFunc));
        PrintFloatFunc.addArg(new Argument("x", FloatType.F32, PrintFloatFunc));
        PrintArrFunc.addArg(new Argument("x", IntegerType.I32, PrintArrFunc));
        PrintArrFunc.addArg(new Argument("x2", new PointerType(IntegerType.I32), PrintArrFunc));
        PrintFArrFunc.addArg(new Argument("x", IntegerType.I32, PrintFArrFunc));
        PrintFArrFunc.addArg(new Argument("x2", new PointerType(FloatType.F32), PrintFArrFunc));

        InputArrFunc.addArg(new Argument("x", new PointerType(IntegerType.I32), InputArrFunc));
        InputFArrFunc.addArg(new Argument("x", new PointerType(FloatType.F32), InputFArrFunc));

        MemsetFunc.addArg(new Argument("x", new PointerType(IntegerType.I32), MemsetFunc));
        MemsetFunc.addArg(new Argument("x2", IntegerType.I32, MemsetFunc));
        MemsetFunc.addArg(new Argument("x3", IntegerType.I32, MemsetFunc));

        StartTimeFunc.addArg(new Argument("x", IntegerType.I32, StartTimeFunc));
        StopTimeFunc.addArg(new Argument("x2", IntegerType.I32, StopTimeFunc));

        ParallelEnd.addArg(new Argument("x", IntegerType.I32, ParallelEnd));

        PrintFunc.setAsLibFunction(module);
        PrintChFunc.setAsLibFunction(module);
        PrintFloatFunc.setAsLibFunction(module);
        PrintArrFunc.setAsLibFunction(module);
        PrintFArrFunc.setAsLibFunction(module);

        InputFunc.setAsLibFunction(module);
        InputChFunc.setAsLibFunction(module);
        InputFloatFunc.setAsLibFunction(module);
        InputArrFunc.setAsLibFunction(module);
        InputFArrFunc.setAsLibFunction(module);

        MemsetFunc.setAsLibFunction(module);
        StartTimeFunc.setAsLibFunction(module);
        StopTimeFunc.setAsLibFunction(module);
        ParallelStart.setAsLibFunction(module);
        ParallelEnd.setAsLibFunction(module);
    }

    private void pushDimIndexTbl(){
        dimIndexTbls.add(new LinkedHashMap<>());
    }

    private void popDimIndexTbl(){
        int len = dimIndexTbls.size();
        dimIndexTbls.remove(len - 1);
    }

    public IRModule visitAST(AST compAST) {
        ArrayList<Function> functions = new ArrayList<>();
        ArrayList<Function> libFunctions = new ArrayList<>();
        module = new IRModule(functions, globalVars, libFunctions);

        initLibFunc();
        pushSymTbl();
        pushDimIndexTbl();

        for (AST.CompUnit compUnit : compAST.units()) {
            if (compUnit instanceof AST.FuncDef funcDefAST) {
                visitFuncDefAST(funcDefAST);
            }
            else if(compUnit instanceof AST.Decl declAST){
                visitDeclAST(declAST, true);
            }
        }
        return module;
    }
}
