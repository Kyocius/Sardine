package IR.Value;

import IR.IRModule;
import IR.Type.Type;
import Pass.IR.Utils.IRLoop;
import Utils.DataStruct.IList;

import java.util.*;

public class Function extends Value{
    private final IList<BasicBlock, Function> bbs;
    private final ArrayList<Argument> args;
    private LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> idoms;
    private LinkedHashMap<BasicBlock, LinkedHashSet<BasicBlock>> domer;
    public LinkedHashMap<BasicBlock, LinkedHashSet<BasicBlock>> doming;
    private LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> df;
    private boolean mayHasSideEffect;
    private boolean storeGV;
    private boolean storeArg;
    public boolean isLibFunc = false;
    private final LinkedHashSet<GlobalVar> loadGVs;
    private final LinkedHashSet<GlobalVar> storeGVs;

    //  callerList记录调用这个function的其他函数
    private final ArrayList<Function> callerList;
    //  calleeList记录这个function调用的其他函数
    private final ArrayList<Function> calleeList;
    private BasicBlock Exit;

    private LinkedHashMap<BasicBlock, IRLoop> loopInfo = new LinkedHashMap<>();
    private ArrayList<IRLoop> topLoops = new ArrayList<>();
    private ArrayList<IRLoop> allLoops = new ArrayList<>();


    //  Function的Type就是它返回值的type
    public Function(String name, Type type){
        super(name, type);
        this.bbs = new IList<>(this);
        this.args = new ArrayList<>();
        this.callerList = new ArrayList<>();
        this.calleeList = new ArrayList<>();
        this.loadGVs = new LinkedHashSet<>();
        this.storeGVs = new LinkedHashSet<>();
    }
    public Function(String name, Type type, IList<BasicBlock, Function> bbs, ArrayList<Argument> args){
        super(name, type);
        this.bbs = bbs;
        this.args = args;
        this.callerList = new ArrayList<>();
        this.calleeList = new ArrayList<>();
        this.loadGVs = new LinkedHashSet<>();
        this.storeGVs = new LinkedHashSet<>();
    }

    public void setIdoms(LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> idoms) {
        this.idoms = idoms;
        for(IList.INode<BasicBlock, Function> bbNode : bbs){
            BasicBlock bb = bbNode.getValue();
            bb.setIdoms(idoms.get(bb));
            for(BasicBlock idomBb : idoms.get(bb)){
                idomBb.setIdominator(bb);
            }
        }
    }


    public int getArgNo(Argument arg) {
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(arg)) {
                return i;
            }
        }
        throw new RuntimeException("Unknown argument");
    }

    public void setStoreGV(boolean storeGV) {
        this.storeGV = storeGV;
    }

    public void setStoreArg(boolean storeArg) {
        this.storeArg = storeArg;
    }

    public boolean isStoreGV() {
        return storeGV;
    }

    public boolean isStoreArg() {
        return storeArg;
    }

    public void setDF(LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> df){
        this.df = df;
    }

    public LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> getIdoms() {
        return idoms;
    }

    public LinkedHashMap<BasicBlock, LinkedHashSet<BasicBlock>> getDomer(){
        return domer;
    }

    public LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> getDF(){
        return df;
    }

    public void setDomer(LinkedHashMap<BasicBlock, LinkedHashSet<BasicBlock>> domer){
        this.domer = domer;
    }

    public void addCaller(Function function){
        if(!callerList.contains(function)) {
            this.callerList.add(function);
        }
    }
    public void addCallee(Function function){
        if(!calleeList.contains(function)) {
            this.calleeList.add(function);
        }
    }

    public ArrayList<Function> getCallerList(){
        return callerList;
    }

    public ArrayList<Function> getCalleeList(){
        return calleeList;
    }

    public void setMayHasSideEffect(boolean mayHasSideEffect){
        this.mayHasSideEffect = mayHasSideEffect;
    }

    public void addArg(Argument argument){ args.add(argument); }

    public IList<BasicBlock, Function> getBbs() {
        return bbs;
    }

    public ArrayList<Argument> getArgs() {
        return args;
    }

    public BasicBlock getBbEntry() {
        return bbs.getHeadValue();
    }

    public void setBbExit(BasicBlock b) {
        this.Exit=b;
    }

    public BasicBlock getBbExit() {
        return this.Exit;
    }

    public boolean isLibFunction(){
        return isLibFunc;
    }

    public void setAsLibFunction(IRModule module){
        this.isLibFunc = true;
        module.libFunctions().add(this);
    }

    public boolean isMayHasSideEffect(){
        return mayHasSideEffect;
    }

    public void addLoadGV(GlobalVar gv){
        loadGVs.add(gv);
    }

    public void addStoreGV(GlobalVar gv){
        storeGVs.add(gv);
    }

    public LinkedHashSet<GlobalVar> getLoadGVs(){
        return loadGVs;
    }

    public LinkedHashSet<GlobalVar> getStoreGVs(){
        return storeGVs;
    }

    public void setLoopInfo(LinkedHashMap<BasicBlock, IRLoop> loopMap){
        this.loopInfo.clear();
        this.loopInfo.putAll(loopMap);
        for(BasicBlock bb : loopMap.keySet()){
            bb.setLoop(loopMap.get(bb));
        }
    }

    public void setTopLoops(ArrayList<IRLoop> topLoops){
        this.topLoops.clear();
        this.topLoops.addAll(topLoops);
    }

    public void setAllLoops(ArrayList<IRLoop> allLoops){
        this.allLoops = allLoops;
    }

    public ArrayList<IRLoop> getAllLoops(){
        return allLoops;
    }

    public ArrayList<IRLoop> getTopLoops(){
        return topLoops;
    }

    public int getLoopDepth(BasicBlock bb){
        IRLoop loop = loopInfo.get(bb);
        return loop.getLoopDepth();
    }
    public boolean istailrecursive=false;
}
