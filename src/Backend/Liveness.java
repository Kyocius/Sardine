package Backend;

import java.util.*;

/**
 * Liveness分析相关类，翻译自C++版本
 * 用于分析寄存器的活跃性，为寄存器分配提供支持
 */

// 类型别名定义
class LivenessTypes {
    public static class InstID {
        public long id;

        public InstID(long id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            InstID instID = (InstID) obj;
            return id == instID.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    public static class VarSet extends HashSet<AsmReg> {
        // 集合运算辅助方法
        public void subtract(Set<AsmReg> other) {
            this.removeAll(other);
        }

        public void union(Set<AsmReg> other) {
            this.addAll(other);
        }

        public VarSet copy() {
            VarSet newSet = new VarSet();
            newSet.addAll(this);
            return newSet;
        }
    }

    public static class LiveInterval {
        public InstID start;
        public InstID end;

        public LiveInterval(InstID start, InstID end) {
            this.start = start;
            this.end = end;
        }
    }
}

/**
 * 基本块活跃性信息
 */
class LivenessBlockInfo {
    public AsmLabel label;
    public LivenessTypes.VarSet inRegs = new LivenessTypes.VarSet();
    public LivenessTypes.VarSet outRegs = new LivenessTypes.VarSet();
    public LivenessTypes.VarSet defRegs = new LivenessTypes.VarSet();
    public LivenessTypes.VarSet useRegs = new LivenessTypes.VarSet();

    public LivenessBlockInfo() {
    }

    public LivenessBlockInfo(AsmLabel label) {
        this.label = label;

        // 计算基本块的def和use集合
        for (AsmInst inst = label.head; inst != null; inst = inst.next) {
            // 处理use
            for (AsmValue use : inst.getUses()) {
                if (use instanceof AsmReg) {
                    AsmReg reg = (AsmReg) use;
                    if (!defRegs.contains(reg)) {
                        useRegs.add(reg);
                    }
                }
            }

            // 处理def
            for (AsmValue def : inst.getDefs()) {
                if (def instanceof AsmReg) {
                    AsmReg reg = (AsmReg) def;
                    defRegs.add(reg);
                }
            }
        }
    }

    /**
     * 更新live in集合
     * in = use ∪ (out \ def)
     * @return 是否发生了变化
     */
    public boolean updateLiveIn() {
        LivenessTypes.VarSet newInValues = outRegs.copy();
        newInValues.subtract(defRegs);
        newInValues.union(useRegs);

        boolean changed = newInValues.size() != inRegs.size() || !newInValues.equals(inRegs);
        inRegs = newInValues;
        return changed;
    }

    /**
     * 更新live out集合
     * out = ∪_{succ} in_{succ}
     */
    public void updateLiveOut(Map<AsmLabel, LivenessBlockInfo> infoMap) {
        outRegs.clear();
        for (AsmLabel succ : label.succs) {
            if (infoMap.containsKey(succ)) {
                outRegs.union(infoMap.get(succ).inRegs);
            }
        }
    }
}

/**
 * 指令排序管理器
 * 为每个指令分配唯一的ID，用于活跃性分析
 */
class InstOrderingManager {
    public List<AsmLabel> labelOrder = new ArrayList<>();
    public Map<AsmInst, Long> instIDMap = new HashMap<>();

    public void runOnFunction(AsmFunc func) {
        Stack<AsmLabel> worklist = new Stack<>();
        Map<AsmLabel, Boolean> visited = new HashMap<>();

        if (!func.labels.isEmpty()) {
            worklist.push(func.labels.get(0));
        }

        while (!worklist.isEmpty()) {
            AsmLabel block = worklist.pop();
            if (visited.getOrDefault(block, false)) {
                continue;
            }
            visited.put(block, true);
            runOnBlock(block);

            for (AsmLabel succ : block.succs) {
                worklist.push(succ);
            }
        }
    }

    public void runOnBlock(AsmLabel label) {
        labelOrder.add(label);

        for (AsmInst inst = label.head; inst != null; inst = inst.next) {
            long count = instIDMap.size();
            instIDMap.put(inst, 2 * count);
        }
    }
}

/**
 * 活跃性分析主类
 * 实现数据流分析算法来计算变量的活跃性
 */
public class Liveness {
    public InstOrderingManager instOrdering = new InstOrderingManager();
    public Map<AsmLabel, LivenessBlockInfo> blockInfoMap = new HashMap<>();
    // 每个程序点的活跃变量集合
    public List<LivenessTypes.VarSet> liveness = new ArrayList<>();

    private AsmFunc func;

    public void runOnFunction(AsmFunc func) {
        this.func = func;
        instOrdering.runOnFunction(func);
        buildBlockInfoMap();
        calcLiveness();
    }

    /**
     * 构建基本块信息映射表
     * 使用工作表算法计算每个基本块的live in/out集合
     */
    private void buildBlockInfoMap() {
        List<AsmLabel> worklist = new ArrayList<>();
        Map<AsmLabel, Boolean> present = new HashMap<>();

        // 初始化所有基本块的信息
        for (AsmLabel block : instOrdering.labelOrder) {
            blockInfoMap.computeIfAbsent(block, LivenessBlockInfo::new);
            LivenessBlockInfo blockInfo = blockInfoMap.get(block);
            if (blockInfo.updateLiveIn()) {
                for (AsmLabel pred : block.preds) {
                    if (!present.getOrDefault(pred, false)) {
                        worklist.add(pred);
                        present.put(pred, true);
                    }
                }
            }
        }

        // 迭代计算直到收敛
        while (!worklist.isEmpty()) {
            AsmLabel block = worklist.remove(worklist.size() - 1);
            present.put(block, false);

            LivenessBlockInfo blockInfo = blockInfoMap.get(block);
            blockInfo.updateLiveOut(blockInfoMap);

            if (blockInfo.updateLiveIn()) {
                for (AsmLabel pred : block.preds) {
                    if (!present.getOrDefault(pred, false)) {
                        worklist.add(pred);
                        present.put(pred, true);
                    }
                }
            }
        }
    }

    /**
     * 计算详细的活跃性信息
     * 为每个指令的入口和出口点计算活跃变量集合
     */
    private void calcLiveness() {
        // Lambda函数的Java等价实现
        java.util.function.Function<AsmInst, Long> inPoint =
            inst -> instOrdering.instIDMap.get(inst);
        java.util.function.Function<AsmInst, Long> outPoint =
            inst -> instOrdering.instIDMap.get(inst) + 1;

        // TODO: 处理函数参数

        // 初始化活跃性数组
        int totalPoints = instOrdering.instIDMap.size() * 2;
        liveness.clear();
        for (int i = 0; i < totalPoints; i++) {
            liveness.add(new LivenessTypes.VarSet());
        }

        boolean changed;
        do {
            changed = false;

            // 反向遍历基本块
            for (int i = instOrdering.labelOrder.size() - 1; i >= 0; i--) {
                AsmLabel block = instOrdering.labelOrder.get(i);
                LivenessBlockInfo blockInfo = blockInfoMap.get(block);

                // 反向遍历基本块中的指令
                for (AsmInst inst = block.tail; inst != null; inst = inst.prev) {
                    int inIdx = inPoint.apply(inst).intValue();
                    int outIdx = outPoint.apply(inst).intValue();

                    LivenessTypes.VarSet inset = liveness.get(inIdx);
                    LivenessTypes.VarSet outset = liveness.get(outIdx);

                    // 计算out集合
                    if (inst != block.tail) {
                        int nextInIdx = inPoint.apply(inst.next).intValue();
                        LivenessTypes.VarSet nextIn = liveness.get(nextInIdx);
                        outset.clear();
                        outset.union(nextIn);
                    } else {
                        outset.clear();
                        outset.union(blockInfo.outRegs);
                    }

                    // 计算新的in集合
                    LivenessTypes.VarSet newInset = outset.copy();

                    // 处理def
                    for (AsmValue def : inst.getDefs()) {
                        if (def instanceof AsmReg) {
                            newInset.remove((AsmReg) def);
                        }
                    }

                    // 处理call指令的特殊def
                    if (inst instanceof AsmCallInst) {
                        AsmCallInst callInst = (AsmCallInst) inst;
                        for (PReg preg : callInst.callDefs) {
                            newInset.remove(preg);
                        }
                    }

                    // 处理use
                    for (AsmValue use : inst.getUses()) {
                        if (use instanceof AsmReg) {
                            newInset.add((AsmReg) use);
                        }
                    }

                    // 处理call指令的特殊use
                    if (inst instanceof AsmCallInst) {
                        AsmCallInst callInst = (AsmCallInst) inst;
                        for (PReg preg : callInst.callUses) {
                            newInset.add(preg);
                        }
                    }

                    // 检查是否发生变化
                    if (!newInset.equals(inset)) {
                        changed = true;
                        inset.clear();
                        inset.union(newInset);
                    }
                }
            }
        } while (changed);
    }

    /**
     * 获取指令入口点的活跃变量集合
     */
    public LivenessTypes.VarSet getLiveInAt(AsmInst inst) {
        Long id = instOrdering.instIDMap.get(inst);
        if (id != null && id.intValue() < liveness.size()) {
            return liveness.get(id.intValue());
        }
        return new LivenessTypes.VarSet();
    }

    /**
     * 获取指令出口点的活跃变量集合
     */
    public LivenessTypes.VarSet getLiveOutAt(AsmInst inst) {
        Long id = instOrdering.instIDMap.get(inst);
        if (id != null && (id.intValue() + 1) < liveness.size()) {
            return liveness.get(id.intValue() + 1);
        }
        return new LivenessTypes.VarSet();
    }
}
