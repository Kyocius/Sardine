package Backend.Arm.tools;


import Backend.Arm.Instruction.ArmInstruction;
import Backend.Arm.Operand.ArmOperand;
import Backend.Arm.Operand.ArmReg;
import Backend.Arm.Structure.ArmBlock;
import Backend.Arm.Structure.ArmFunction;
import Utils.DataStruct.IList;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class LiveInfo {
    private LinkedHashSet<ArmReg> liveUse;
    private LinkedHashSet<ArmReg> liveDef;
    private LinkedHashSet<ArmReg> liveIn;
    private LinkedHashSet<ArmReg> liveOut;

    public LiveInfo() {
        this.liveUse = new LinkedHashSet<>();
        this.liveDef = new LinkedHashSet<>();
        this.liveIn = new LinkedHashSet<>();
        this.liveOut = new LinkedHashSet<>();
    }

    public LinkedHashSet<ArmReg> getLiveUse() {
        return liveUse;
    }

    public LinkedHashSet<ArmReg> getLiveDef() {
        return liveDef;
    }

    public LinkedHashSet<ArmReg> getLiveIn() {
        return liveIn;
    }

    public LinkedHashSet<ArmReg> getLiveOut() {
        return liveOut;
    }

    public void setLiveOut(LinkedHashSet<ArmReg> liveOut) {
        this.liveOut = liveOut;
    }

    public static LinkedHashMap<ArmBlock, Backend.Arm.tools.LiveInfo> liveInfoAnalysis(ArmFunction function) {
        //分析liveUse liveDef
        LinkedHashMap<ArmBlock, Backend.Arm.tools.LiveInfo> liveInfoHashMap = new LinkedHashMap<>();
        for (IList.INode<ArmBlock, ArmFunction>
             blockNode = function.getBlocks().getHead();
             blockNode != null; blockNode = blockNode.getNext()) {
            ArmBlock block = blockNode.getValue();
            if(block.getArmInstructions().isEmpty()) {
                continue;
            }
            Backend.Arm.tools.LiveInfo liveInfo = new Backend.Arm.tools.LiveInfo();
            liveInfoHashMap.put(block, liveInfo);
            for (IList.INode<ArmInstruction, ArmBlock>
                 insNode = block.getArmInstructions().getHead();
                 insNode != null; insNode = insNode.getNext()) {
                ArmInstruction ins = insNode.getValue();
                for(ArmOperand op:ins.getOperands()) {
                    if(op instanceof ArmReg) {
                        if(!liveInfo.getLiveDef().contains((ArmReg) op)) {
                            liveInfo.getLiveUse().add((ArmReg) op);
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
            for (IList.INode<ArmBlock, ArmFunction>
                 blockNode = function.getBlocks().getTail();
                 blockNode != null; blockNode = blockNode.getPrev()) {
                ArmBlock block = blockNode.getValue();
                LinkedHashSet<ArmReg> newLiveOut = new LinkedHashSet<>();

                Backend.Arm.tools.LiveInfo oldInfo = liveInfoHashMap.get(block);

                for(var succs: block.getSuccs()) {
                    assert liveInfoHashMap.containsKey(succs);
//                    System.out.println(function.dump());
//                    System.out.println(block.dump());
//                    System.out.println(succs.dump());
                    newLiveOut.addAll(liveInfoHashMap.get(succs).getLiveIn());
                }
                LinkedHashSet<ArmReg> livein = oldInfo.getLiveIn();
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
//        for (Map.Entry<ArmBlock, LiveInfo> entry : liveInfoHashMap.entrySet()) {
//            System.out.println("Block: " + entry.getKey().getName() + ", LiveIn: ");
//            for (ArmBlock block: entry.getKey().getSuccs()) {
//                System.out.println(block.getName());
//            }
//            for (ArmReg reg: entry.getValue().getLiveOut()) {
//                System.out.println(reg);
//            }
//        }
        return liveInfoHashMap;
    }
}
