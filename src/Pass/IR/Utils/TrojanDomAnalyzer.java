package Pass.IR.Utils;

import IR.IRModule;
import IR.Value.Function;
import IR.Value.*;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;


public class TrojanDomAnalyzer {

    private static ArrayList<Integer>[] graph1;
    private static ArrayList<Integer>[] graph2;
    private static int[] dfn;

    private static void initFunction(Function function) {
        LinkedHashMap<BasicBlock, LinkedHashSet<BasicBlock>> dom = new LinkedHashMap<>();
        LinkedHashMap<BasicBlock, ArrayList<BasicBlock>> idom = new LinkedHashMap<>();
        function.doming = new LinkedHashMap<>();
        for (IList.INode<BasicBlock, Function> bbNode : function.getBbs()) {
            dom.put(bbNode.getValue(), new LinkedHashSet<>());
            function.doming.put(bbNode.getValue(), new LinkedHashSet<>());
            idom.put(bbNode.getValue(), new ArrayList<>());
        }
        function.setDomer(dom);
        function.setIdoms(idom);
    }

    public static void calDomForModule(IRModule module) {
        for (Function function : module.functions()) {
            if (function.getBbs().getSize() > 1) calDomForFunction(function);
            else if (function.getBbs().getSize() == 1) {
                initFunction(function);
                var block = function.getBbs().getHead().getValue();
                function.getDomer().get(block).add(block);
                function.doming.get(block).add(block);
            }
        }
    }


    //    public static void calPredSuccBB(IRModule module){
//        BlockAnalyzer.updateBlockPredAndSuccs(module);
//    }
    public static long time0 = 0;

    public static double gettime() {
        long timenow = System.currentTimeMillis();
        double returntime = (timenow - time0) / 1000.0;
        time0 = timenow;
        return returntime;
    }

    public static void calDomForFunction(Function function) {
        initFunction(function);
//        gettime();
//        System.out.printf("||domRelation:%f s||", gettime());
        calDomRelation(function);
//        calDomFrontier(function);
//        System.out.printf("domFrontier: %f %n", gettime());
    }

//    public static void calDepthForModule((IRModule) module){
//        for(var function:module.functions.values()){
//            if(function.blocks.size()!=0) {
//                calDomDepthForFunction(function);
//            }
//        }
//    }

//    public static void calDomDepthForFunction(Function function){
//        dfs(0,function.blocks.get(0));
//    }


    //    public static void dfs(int domDepth,BasicBlock basicBlock){
//        basicBlock.setDomDepth(domDepth);
//        for(var BB:basicBlock.getIdominate()){
//            dfs(domDepth+1,BB);
//        }
//    }
    public static void dfs2(BasicBlock BB, Function function) {
        var idoms = function.getIdoms();
        var domby = function.getDomer();
        var doming = function.doming;
        if(idoms.get(BB).size() == 0){
//        if (BB.getIdominate().size() == 0) {
//            BB.getDominatedBy().add(BB);
            domby.get(BB).add(BB);
//            BB.getDominate().add(BB);
            doming.get(BB).add(BB);
        } else {
            for (var idomBB : idoms.get(BB)) {
                dfs2(idomBB, function);
                doming.get(BB).addAll(doming.get(idomBB));
//                BB.getDominate().addAll(idomBB.getDominate());
            }
            doming.get(BB).add(BB);
//            BB.getDominate().add(BB);
            for (var domBB : doming.get(BB)) {
                domby.get(domBB).add(BB);
            }
        }
    }
    /*
        Dom(0) = {0}
        for i = 1 to n
            Dom(i) = {0,1,2,3,....n}
        changed = true
        while(changed){
            for i = 1 to n
                tmp = {i} union { the intersection of all Dom(j) only if j in preds(i) }
                if tmp != Dom(i) then
                    Dom(i) = tmp
                    Changed = true
        }
    */

    public static void cleanBlockDomInfo(BasicBlock block, Function function){
        function.getIdoms().get(block).clear();
        function.getDomer().get(block).clear();
        function.doming.get(block).clear();
    }

    private static void updateIdomenator(Function function){
        var idom = function.getIdoms();
        for(var bb : idom.keySet()){
            for(var bedombb : idom.get(bb)){
                bedombb.setIdominator(bb);
            }
        }
    }

    public static void calDomRelation(Function function) {
        if (function.getBbs().getSize() == 1) {
            initFunction(function);
            var block = function.getBbs().getHead().getValue();
            function.getDomer().get(block).add(block);
            function.doming.get(block).add(block);
            return;
        }

        var bb2index = new LinkedHashMap<BasicBlock, Integer>();
        var index2bb = new ArrayList<BasicBlock>();

        //采用Lengauer-Tarjan算法进行优化
        index2bb.add(null);
        int index = 1;
        for (var bbnode : function.getBbs()) {
            var bb = bbnode.getValue();
            cleanBlockDomInfo(bb, function);
            bb2index.put(bb, index);
            index2bb.add(bb);
            index++;
        }
        int paramM = 0;
        for (var bbnode : function.getBbs()) {
            var bb = bbnode.getValue();
            paramM += bb.getNxtBlocks().size();
        }
        //初始化
        DomTarjan.init(index, paramM);
//            System.out.println("!!!!!");
        //加边
        for (var bbnode : function.getBbs()) {
            var bb = bbnode.getValue();
            for (var succBB : bb.getNxtBlocks()) {
                DomTarjan.add(bb2index.get(bb), bb2index.get(succBB), 0);
                DomTarjan.add(bb2index.get(succBB), bb2index.get(bb), 1);
            }
        }
        DomTarjan.run();
        int cnt = DomTarjan.getCnt();
        int[] ord = DomTarjan.getOrd();
        int[] idom = DomTarjan.getIdom();
        //支配树已建立,求出idom
        for (int i = cnt; i >= 2; --i) {
            function.getIdoms().get(index2bb.get(idom[ord[i]])).add(index2bb.get(ord[i]));
//            index2bb.get(ord[i]).setIdominatedBy(index2bb.get(idom[ord[i]]));
//            index2bb.get(idom[ord[i]]).getIdominate().add(index2bb.get(ord[i]));
        }
        //TODO:考虑对支配树进行一次DFS,利用idom关系得到dom关系,一个节点能支配它在支配树中所有子树节点
        dfs2(function.getBbs().getHead().getValue(), function);
        updateIdomenator(function);
    }
    /*
       for x : blocks:
           DF(x) = None
       for x : blocks:
           if x.preds.size() > 1:
                for p : preds:
                runner = p
                while runner != idom(x)
                    DF(runner) = DF(runner) union {x}
                    runner = idom(runner)
    */
//    public static void calDomFrontier(Function function){
//        for(var block:function.blocks){
//            block.getDomFrontier().clear();
//        }
//        for(var block:function.blocks){
//            for(var succBB:block.getSuccs()){
//                var tmp = block;    //其实就是遍历cfg边(block->succBB)，只要block不能决定succBB，block的支配边界中，并用支配树上block的父节点更新block
//                while(tmp != null && (tmp==succBB||!succBB.getDominatedBy().contains(tmp))){
//                    tmp.getDomFrontier().add(succBB);
//                    tmp = tmp.getIdominatedBy();
//                }
//            }
//        }
//    }

    public static class DomTarjan {
        static int n, m;
        static int[] ans;
        static int tot;
        static int[][] fst;
        static int[] nxt;
        static int[] to;
        static int[] dfn;
        static int[] ord;


        static int cnt;
        static int[] fth;
        static int[] idom;
        static int[] semi;
        static int[] uni;
        static int[] mn;


        public static int getCnt() {
            return cnt;
        }

        public static int[] getOrd() {
            return ord;
        }

        public static int[] getIdom() {
            return idom;
        }

        static void init(int paramN, int paramM) {
            //fst,nxt和to数组用于建图
            n = paramN;
            m = paramM;
//            System.out.println(n);
//            System.out.println(m);
            ans = new int[n + 1];
            fst = new int[n + 1][3];
            ;
            nxt = new int[m + m + n + 1];
            to = new int[m + m + n + 1];
            ;
            dfn = new int[n + 1];
            ord = new int[n + 1];
            fth = new int[n + 1];
            idom = new int[n + 1];
            semi = new int[n + 1];
            uni = new int[n + 1];
            mn = new int[n + 1];
            cnt = 0;
            tot = 0;
        }

        static void add(int u, int v, int id) {
            nxt[++tot] = fst[u][id];
            to[tot] = v;
            fst[u][id] = tot;
        }

        static void Tarjan(int u) {
            ord[dfn[u] = ++cnt] = u;
            for (int i = fst[u][0]; i != 0; i = nxt[i]) {
                int v = to[i];
                if (dfn[v] == 0) {
                    fth[v] = u;
                    Tarjan(v);
                }
            }
        }

        static int uni_query(int u) {
            if (u == uni[u])
                return u;
            int tmp = uni_query(uni[u]);
            if (dfn[semi[mn[u]]] > dfn[semi[mn[uni[u]]]])
                mn[u] = mn[uni[u]];
            return uni[u] = tmp;
        }

        static void Lengauer_Tarjan(int s) {
            Tarjan(s);
            for (int i = 1; i <= n; ++i)
                semi[i] = uni[i] = mn[i] = i;
            for (int id = cnt; id >= 2; --id) {
                int u = ord[id];
                for (int i = fst[u][1]; i != 0; i = nxt[i]) {
                    int v = to[i];
                    if (dfn[v] == 0)
                        continue;
                    uni_query(v);
                    if (dfn[semi[u]] > dfn[semi[mn[v]]])
                        semi[u] = semi[mn[v]];
                }
                uni[u] = fth[u];
                add(semi[u], u, 2);
                u = fth[u];
                for (int i = fst[u][2]; i != 0; i = nxt[i]) {
                    int v = to[i];
                    uni_query(v);
                    idom[v] = (u == semi[mn[v]]) ? u : mn[v];
                }
                fst[u][2] = 0;
            }
            for (int i = 2; i <= cnt; ++i) {
                int u = ord[i];
                if (idom[u] != semi[u])
                    idom[u] = idom[idom[u]];
            }
        }

        public static void run() {
            Lengauer_Tarjan(1);
        }
    }


}