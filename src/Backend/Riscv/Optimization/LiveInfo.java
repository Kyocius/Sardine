package Backend.Riscv.Optimization;

import Backend.Riscv.Component.RiscvBlock;
import Backend.Riscv.Component.RiscvFunction;
import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvOperand;
import Backend.Riscv.Operand.RiscvReg;
import Utils.DataStruct.IList;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class LiveInfo {
    private LinkedHashSet<RiscvReg> liveUse;
    private LinkedHashSet<RiscvReg> liveDef;
    private LinkedHashSet<RiscvReg> liveIn;
    private LinkedHashSet<RiscvReg> liveOut;

    public LiveInfo() {
        this.liveUse = new LinkedHashSet<>();
        this.liveDef = new LinkedHashSet<>();
        this.liveIn = new LinkedHashSet<>();
        this.liveOut = new LinkedHashSet<>();
    }

    public LinkedHashSet<RiscvReg> getLiveUse() {
        return liveUse;
    }

    public LinkedHashSet<RiscvReg> getLiveDef() {
        return liveDef;
    }

    public LinkedHashSet<RiscvReg> getLiveIn() {
        return liveIn;
    }

    public LinkedHashSet<RiscvReg> getLiveOut() {
        return liveOut;
    }

    public void setLiveOut(LinkedHashSet<RiscvReg> liveOut) {
        this.liveOut = liveOut;
    }

    public static LinkedHashMap<RiscvBlock, LiveInfo> liveInfoAnalysis(RiscvFunction function) {
        //分析liveUse liveDef
        LinkedHashMap<RiscvBlock, LiveInfo> liveInfoHashMap = new LinkedHashMap<>();
        for (IList.INode<RiscvBlock, RiscvFunction>
             blockNode = function.getBlocks().getHead();
             blockNode != null; blockNode = blockNode.getNext()) {
            RiscvBlock block = blockNode.getValue();
            if(block.getRiscvInstructions().isEmpty()) {
                continue;
            }
            LiveInfo liveInfo = new LiveInfo();
            liveInfoHashMap.put(block, liveInfo);
            for (IList.INode<RiscvInstruction, RiscvBlock>
                 insNode = block.getRiscvInstructions().getHead();
                 insNode != null; insNode = insNode.getNext()) {
                RiscvInstruction ins = insNode.getValue();
                for(RiscvOperand op:ins.getOperands()) {
                    if(op instanceof RiscvReg) {
                        if(!liveInfo.getLiveDef().contains((RiscvReg) op)) {
                            liveInfo.getLiveUse().add((RiscvReg) op);
                        }
                    }
                }
                if(ins.getDefReg() != null) {
                    liveInfo.getLiveDef().add(ins.getDefReg());
                }
            }
            liveInfo.liveIn.addAll(liveInfo.liveUse);
        }
        //计算LiveIn 计算LiveOut
        Boolean changed = true;
        while(changed) {
            changed = false;
            for (IList.INode<RiscvBlock, RiscvFunction>
                 blockNode = function.getBlocks().getTail();
                 blockNode != null; blockNode = blockNode.getPrev()) {
                RiscvBlock block = blockNode.getValue();
                LinkedHashSet<RiscvReg> newLiveOut = new LinkedHashSet<>();

                LiveInfo oldInfo = liveInfoHashMap.get(block);

                for(var succs: block.getSuccs()) {
                    assert liveInfoHashMap.containsKey(succs);
//                    System.out.println(function.dump());
//                    System.out.println(block.dump());
//                    System.out.println(succs.dump());
                    newLiveOut.addAll(liveInfoHashMap.get(succs).getLiveIn());
                }
                LinkedHashSet<RiscvReg> livein = oldInfo.getLiveIn();
                livein.clear();
                livein.addAll(newLiveOut);
                livein.removeAll(oldInfo.getLiveDef());
                livein.addAll(oldInfo.getLiveUse());
                if(!oldInfo.getLiveOut().equals(newLiveOut)) {
                    changed = true;
                    oldInfo.setLiveOut(newLiveOut);
                }
            }
        }
//        for (Map.Entry<RiscvBlock, LiveInfo> entry : liveInfoHashMap.entrySet()) {
//            System.out.println("Block: " + entry.getKey().getName() + ", LiveIn: ");
//            for (RiscvBlock block: entry.getKey().getSuccs()) {
//                System.out.println(block.getName());
//            }
//            for (RiscvReg reg: entry.getValue().getLiveOut()) {
//                System.out.println(reg);
//            }
//        }
        return liveInfoHashMap;
    }
}
