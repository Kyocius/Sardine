package Backend.Arm.Structure;

import Backend.Arm.Instruction.ArmFMv;
import Backend.Arm.Operand.ArmLabel;
import Backend.Arm.Operand.ArmReg;
import Backend.Arm.Operand.ArmVirReg;
import Backend.Arm.Operand.ArmCPUReg;
import Backend.Arm.tools.RegisterIdAllocator;
import Backend.Arm.Instruction.ArmMv;
import Backend.Arm.Operand.*;
import IR.Value.Argument;
import IR.Value.Instructions.AllocInst;
import IR.Value.Value;
import Utils.DataStruct.IList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class ArmFunction extends ArmLabel {
    private final IList<ArmBlock, ArmFunction> blocks;
    private int argIntNum = 0; // 形参总数量
    private int argFloatNum = 0;
    private int stackPosition = 0;
    private final RegisterIdAllocator allocator = new RegisterIdAllocator();
    private int blockIndex = 0;
    private final LinkedHashSet<ArmOperand> allVirRegUsed = new LinkedHashSet<>();
    private final LinkedHashMap<Object, Integer> value2StackPos = new LinkedHashMap<>();
    private final ArrayList<ArmMv> mvs = new ArrayList<>();
    private final ArmVirReg retReg = new ArmVirReg(allocator.getId(), ArmVirReg.RegType.intType, this);
    private final ArrayList<ArmBlock> retBlocks = new ArrayList<>();
    private final ArrayList<ArmPhyReg> protectRegs = new ArrayList<>();

    public ArrayList<ArmBlock> getRetBlocks() {
        return retBlocks;
    }

    public ArmFunction(String name) {
        super(name);
        blocks = new IList<>(this);
    }

    public void addBlock(IList.INode<ArmBlock, ArmFunction> block) {
        blocks.add(block);
    }

    public IList<ArmBlock, ArmFunction> getBlocks() {
        return this.blocks;
    }

    public void parseArgs(ArrayList<Argument> args, LinkedHashMap<Value, ArmReg> value2Reg) {
        for(Argument arg: args) {
            if(arg.getType().isFloatTy()) {
                // AAPCS64: 8 floating-point argument registers (d0-d7)
                if(argFloatNum >= 8) {
                    // ARMv8-A: 8-byte alignment on stack
                    stackPosition = (stackPosition + 7) & ~7; // Align to 8 bytes
                    stackPosition += 8;
                    value2StackPos.put(arg, stackPosition);
                } else {
                    ArmVirReg virReg = getNewReg(ArmVirReg.RegType.floatType);
                    mvs.add(new ArmFMv(ArmFPUReg.getArmFArgReg(argFloatNum), virReg));
                    value2Reg.put(arg, virReg);
                }
                argFloatNum++;
            } else {
                // AAPCS64: 8 general-purpose argument registers (x0-x7)
                if(argIntNum >= 8) {
                    // ARMv8-A: 8-byte alignment on stack
                    stackPosition = (stackPosition + 7) & ~7; // Align to 8 bytes
                    stackPosition += 8;
                    value2StackPos.put(arg, stackPosition);
                } else {
                    ArmVirReg virReg = getNewReg(ArmVirReg.RegType.intType);
                    mvs.add(new ArmMv(ArmCPUReg.getArmArgReg(argIntNum), virReg));
                    value2Reg.put(arg, virReg);
                }
                argIntNum++;
            }
        }
    }

    public LinkedHashSet<ArmOperand> getAllVirRegUsed() {
        return allVirRegUsed;
    }


    public void alloc(ArmVirReg reg) {
        // ARMv8-A: Use 8-byte slots for general registers, maintain 16-byte alignment
        stackPosition += 8;
        // Ensure 16-byte alignment
        stackPosition = (stackPosition + 15) & ~15;
        value2StackPos.put(reg, stackPosition);
    }

    public void alloc(AllocInst allocInst) {
        // ARMv8-A: Ensure 16-byte stack alignment
        int allocSize = allocInst.getSize() * 4;
        // Align allocation size to 16 bytes
        allocSize = (allocSize + 15) & ~15;
        stackPosition = stackPosition + allocSize;
        value2StackPos.put(allocInst, stackPosition);
    }

    public void reMap(ArmVirReg desReg, ArmVirReg srcReg) {
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

    public ArrayList<ArmMv> getMvs() {
        return mvs;
    }

    public ArmVirReg getRetReg() {
        return retReg;
    }

    public int getStackPosition() {
        return stackPosition;
    }

    public String dump() {
        System.out.println("Dumping function: " + getName() + ", blocks count: " + blocks.getSize());
        if(blocks.getSize() == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        sb.append(".globl").append(" ").append(getName()).append('\n');
        sb.append(getName().replace("@", "")).append(":\n");
        for(IList.INode<ArmBlock, ArmFunction> block: blocks) {
            String blockDump = block.getValue().dump();
            System.out.println("Block dump length: " + blockDump.length());
            sb.append(blockDump);
        }
        System.out.println("Total function dump length: " + sb.length());
        return sb.toString();
    }

    public void addVirReg(ArmVirReg reg) {
        this.allVirRegUsed.add(reg);
    }

    public void replaceVirReg(ArmVirReg dest, ArmVirReg src) {
        allVirRegUsed.remove(src);
        allVirRegUsed.add(dest);
    }

    public ArmVirReg getNewReg(ArmVirReg.RegType regType) {
        return new ArmVirReg(allocator.getId(), regType, this);
    }

    public ArrayList<ArmPhyReg> getProtectReg() {
        return protectRegs;
    }
}
