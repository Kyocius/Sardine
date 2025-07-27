package Backend.Riscv.Component;

import Backend.Riscv.Instruction.RiscvFmv;
import Backend.Riscv.Instruction.RiscvMv;
import Backend.Riscv.Operand.*;
import IR.Type.IntegerType;
import IR.Type.PointerType;
import IR.Value.Argument;
import IR.Value.BasicBlock;
import IR.Value.Function;
import IR.Value.Instructions.AllocInst;
import IR.Value.Value;
import Utils.DataStruct.IList;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;

public class RiscvFunction extends RiscvLabel {
    private IList<RiscvBlock, RiscvFunction> blocks;
    private int argIntNum = 0; // 形参总数量
    private int argFloatNum = 0;
    private int stackPosition = 0;
    private int virRegIndex = 0;
    private int blockIndex = 0;
    private LinkedHashSet<RiscvOperand> allVirRegUsed = new LinkedHashSet<>();
    private LinkedHashMap<Object, Integer> value2StackPos = new LinkedHashMap<>();
    private ArrayList<RiscvMv> mvs = new ArrayList<>();
    private RiscvVirReg retReg = new RiscvVirReg(this.updateVirRegIndex(), RiscvVirReg.RegType.intType, this);
    private ArrayList<RiscvBlock> retBlocks = new ArrayList<>();

    public ArrayList<RiscvBlock> getRetBlocks() {
        return retBlocks;
    }

    public RiscvFunction(String name) {
        super(name);
        blocks = new IList<>(this);
    }

    public void addBlock(IList.INode<RiscvBlock, RiscvFunction> block) {
        blocks.add(block);
    }

    public IList<RiscvBlock, RiscvFunction> getBlocks() {
        return this.blocks;
    }

    public void parseArgs(ArrayList<Argument> args, LinkedHashMap<Value, RiscvReg> value2Reg) {
        for(Argument arg: args) {
            if(arg.getType().isFloatTy()) {
                if(argFloatNum >= 8) {
                    stackPosition += 8;
                    value2StackPos.put(arg, stackPosition);
                } else {
                    RiscvVirReg virReg = new RiscvVirReg(virRegIndex++, RiscvVirReg.RegType.floatType, this);
                    mvs.add(new RiscvFmv(RiscvFPUReg.getRiscvFArgReg(argFloatNum), virReg));
                    value2Reg.put(arg, virReg);
                }
                argFloatNum++;
            } else {
                if(argIntNum >= 8) {
                    stackPosition += 8;
                    value2StackPos.put(arg, stackPosition);
                } else {
                    RiscvVirReg virReg = new RiscvVirReg(virRegIndex++, RiscvVirReg.RegType.intType, this);
                    mvs.add(new RiscvMv(RiscvCPUReg.getRiscvArgReg(argIntNum), virReg));
                    value2Reg.put(arg, virReg);
                }
                argIntNum++;
            }
        }
    }

    public LinkedHashSet<RiscvOperand> getAllVirRegUsed() {
        return allVirRegUsed;
    }

    public int updateVirRegIndex() {
        this.virRegIndex ++;
        return this.virRegIndex - 1;
    }

    public void alloc(RiscvVirReg reg) {
        stackPosition += 8;
        value2StackPos.put(reg, stackPosition);
    }

    public void alloc(AllocInst allocInst) {
        stackPosition = stackPosition + allocInst.getSize() * 4;
        value2StackPos.put(allocInst, stackPosition);
    }

    public void reMap(RiscvVirReg desReg, RiscvVirReg srcReg) {
        value2StackPos.put(desReg, value2StackPos.get(srcReg));
        allVirRegUsed.remove(srcReg);
        allVirRegUsed.add(desReg);
    }

    public int getOffset(Object obj) {
        assert value2StackPos.containsKey(obj);
        return value2StackPos.get(obj);
    }

    public boolean containOffset(Object obj) {
        return value2StackPos.containsKey(obj);
    }

    public int allocBlockIndex() {
        return ++this.blockIndex;
    }

    public ArrayList<RiscvMv> getMvs() {
        return mvs;
    }

    public RiscvVirReg getRetReg() {
        return retReg;
    }

    public int getStackPosition() {
        return stackPosition;
    }

    public String dump() {
        if(blocks.getSize() == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        sb.append("""
            .align\t1
            .globl""").append(" ").append(getName()).append('\n');
        sb.append(getName().replace("@", "") + ":\n");
        for(IList.INode<RiscvBlock, RiscvFunction> block: blocks) {
            sb.append(block.getValue().dump());
        }
        return sb.toString();
    }

    public void addVirReg(RiscvVirReg reg) {
        this.allVirRegUsed.add(reg);
    }

    public void replaceVirReg(RiscvVirReg dest, RiscvVirReg src){
        allVirRegUsed.remove(src);
        allVirRegUsed.add(dest);
    }
}
