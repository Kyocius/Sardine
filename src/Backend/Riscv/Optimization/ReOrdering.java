package Backend.Riscv.Optimization;

import Backend.Riscv.Component.RiscvBlock;
import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Component.RiscvModule;
import Backend.Riscv.Instruction.*;
import Backend.Riscv.Operand.*;
import Utils.DataStruct.IList;

import javax.xml.crypto.dsig.spec.HMACParameterSpec;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class ReOrdering {

    private RiscvModule riscvModule;

    public ReOrdering(RiscvModule riscvModule) {
        this.riscvModule = riscvModule;
    }

    private ArrayList<RiscvPhyReg> getCallDefs() {
        ArrayList<Integer> list_int = new ArrayList<>(
                Arrays.asList(1, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 28, 29, 30, 31));
        ArrayList<Integer> list_float = new ArrayList<>(
                Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 28, 29, 30, 31));
        ArrayList<RiscvPhyReg> ret = new ArrayList<>();
        list_float.forEach(i -> ret.add(RiscvFPUReg.getRiscvFPUReg(i)));
        list_int.forEach(i -> ret.add(RiscvCPUReg.getRiscvCPUReg(i)));
        return ret;
    }


    //return conflict edge num and update liveout
    private int UpdateLiveOutForInst(RiscvInstruction rvInst, LinkedHashSet<RiscvReg> liveout) {
        if (rvInst instanceof RiscvMv) {
            assert rvInst.getOperands().get(0) instanceof RiscvReg;
            liveout.remove(rvInst.getOperands().get(0));
        }
        LinkedHashMap<RiscvReg, LinkedHashSet<RiscvReg>> conflict = new LinkedHashMap<>();
        int cnt = 0;
        ArrayList<RiscvReg> defRegs = new ArrayList<>();
        if (rvInst.getDefReg() != null) {
            defRegs.add(rvInst.getDefReg());
            if (rvInst instanceof RiscvJal) {
                defRegs.addAll(getCallDefs());
            } // 由于没有定义Call，所以这里用Jal代替
            liveout.addAll(defRegs);
            cnt += (defRegs.size()*liveout.size());
            if (!(rvInst instanceof RiscvMv)) {
                defRegs.forEach(liveout::remove);
            } else if (!(rvInst.getDefReg() instanceof RiscvPhyReg)) {
                defRegs.forEach(liveout::remove);
            }
        }
        for (RiscvOperand riscvOperand : rvInst.getOperands()) {
            if (riscvOperand instanceof RiscvReg) {
                liveout.add((RiscvReg) riscvOperand);
            }
        }
        if (rvInst instanceof RiscvJal) {
            liveout.addAll(((RiscvJal) rvInst).getUsedRegs());
        } // 由于没有定义Call，所以这里用Jal代替
//        var ret = 0;
//        for (var key : conflict.keySet()) {
//            ret += conflict.get(key).size();
//        }
        return cnt;
    }

    private boolean hasFixPhyReg(RiscvInstruction rvInst) {
        if (rvInst.getDefReg() instanceof RiscvPhyReg rvreg) {
            return !rvreg.canBeReorder();
        }
        for (var ope : rvInst.getOperands()) {
            if (ope instanceof RiscvPhyReg rvreg) {
                return !rvreg.canBeReorder();
            }
        }
        return false;
    }

    private boolean isLoadStore(RiscvInstruction rvInst) {
        return rvInst instanceof RiscvLw || rvInst instanceof RiscvSw ||
                rvInst instanceof RiscvLd || rvInst instanceof RiscvSd ||
                rvInst instanceof RiscvFlw || rvInst instanceof RiscvFsw ||
                rvInst instanceof RiscvFld || rvInst instanceof RiscvFsd;
    }

    private boolean isJump(RiscvInstruction rvInst) {
        return rvInst instanceof RiscvBranch || rvInst instanceof RiscvJ ||
                rvInst instanceof RiscvJal || rvInst instanceof RiscvJr;
    }

    private int needReorder(LinkedHashSet<RiscvReg> liveout,
                            IList.INode<RiscvInstruction, RiscvBlock> rvNode) {
        if (isJump(rvNode.getValue())) {
            return 0;
        }
        var liveoutCopy1 = new LinkedHashSet<>(liveout);
        var conflict1 = UpdateLiveOutForInst(rvNode.getValue(), liveoutCopy1);

        var neworder = new ArrayList<RiscvInstruction>();
        var curNode = rvNode;
        if (hasFixPhyReg(rvNode.getValue())) {
            return 0;
        }
        int maxValidReorderLen = 0;
        while (true) {
            curNode = curNode.getPrev();
            if (curNode == null) {
                return maxValidReorderLen;
            }
            if (rvNode.getValue().getOperands().contains(curNode.getValue().getDefReg()) ||
                    curNode.getValue().getOperands().contains(rvNode.getValue().getDefReg())) {
                return maxValidReorderLen;
            }
            if (isLoadStore(rvNode.getValue()) && isLoadStore(curNode.getValue())) {
                return maxValidReorderLen;
            }
            if (isJump(curNode.getValue())) {
                return maxValidReorderLen;
            }
            if (hasFixPhyReg(curNode.getValue())) {
                return maxValidReorderLen;
            }
            neworder.add(curNode.getValue());

            conflict1 += UpdateLiveOutForInst(curNode.getValue(), liveoutCopy1);

            var liveoutCopy2 = new LinkedHashSet<>(liveout);

            var conflict2 = 0;
            for (var newOrderInst : neworder) {
                conflict2 += UpdateLiveOutForInst(newOrderInst, liveoutCopy2);
            }
            conflict2 += UpdateLiveOutForInst(rvNode.getValue(), liveoutCopy2);
            if (conflict1 > conflict2) {
                maxValidReorderLen = neworder.size();
            } else if (conflict2 > conflict1) {
                return maxValidReorderLen;
            }
        }
    }


    public boolean reorderBlock(RiscvBlock bb, LiveInfo li, LinkedHashSet<RiscvInstruction> reordered) {
        var liveout = new LinkedHashSet<>(li.getLiveOut());
        var changed = false;
        for (IList.INode<RiscvInstruction, RiscvBlock>
             rvInstNode = bb.getRiscvInstructions().getTail();
             rvInstNode != null;
             rvInstNode = rvInstNode.getPrev()) {
//            if(reordered.contains(rvInstNode.getValue())){
//                continue;
//            }
            var needReorderLen = needReorder(liveout, rvInstNode);
            if (needReorderLen > 0) {
                var prev = rvInstNode;
                for (int i = 0; i < needReorderLen; i++) {
                    prev = prev.getPrev();
                }
                var newThis = rvInstNode.getPrev();
                reordered.add(rvInstNode.getValue());
                rvInstNode.removeFromList();
                rvInstNode.insertBefore(prev);
                rvInstNode = newThis;
                changed = true;
            }
            UpdateLiveOutForInst(rvInstNode.getValue(), liveout);
        }
        return changed;
    }

    public void run() {
        var startmill = System.currentTimeMillis();
        for (var funcname : riscvModule.getFunctions().keySet()) {
            var func = riscvModule.getFunctions().get(funcname);
            var liveinfo = LiveInfo.liveInfoAnalysis(func);
            for (var bbnode : func.getBlocks()) {
                if (bbnode.getValue().getRiscvInstructions().getSize() > 1000) {
                    continue;
                }
                //重排序该块，使用不动点算法
                var bb = bbnode.getValue();
                boolean change = true;
                LinkedHashSet<RiscvInstruction> reordered = new LinkedHashSet<>();
                while (change) {
                    change = reorderBlock(bb, liveinfo.get(bb), reordered);
                }
            }
        }
//        System.out.println(System.currentTimeMillis()-startmill);
    }

    public void dump() {
        try {
            var out = new BufferedWriter(new FileWriter("rv_backend_reorder.s"));
            out.write(riscvModule.toString());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
