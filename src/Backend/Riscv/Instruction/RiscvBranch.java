package Backend.Riscv.Instruction;

import Backend.Riscv.Component.RiscvBlock;
import Backend.Riscv.Component.RiscvInstruction;
import Backend.Riscv.Operand.RiscvLabel;
import Backend.Riscv.Operand.RiscvOperand;
import Backend.Riscv.Operand.RiscvReg;
import IR.Value.Instructions.OP;

import java.util.ArrayList;
import java.util.Arrays;

public class RiscvBranch extends RiscvInstruction {
    private RiscvCmpType cmpType;

    public enum RiscvCmpType {
        beq,
        bne,
        blt,
        bge
    }

    public String cmpType2Str() {
        switch(cmpType) {
            case beq -> {
                return "beq";
            }
            case bge -> {
                return "bge";
            }
            case blt -> {
                return "blt";
            }
            case bne -> {
                return "bne";
            }
        }
        return null;
    }

    public RiscvCmpType getCmpType() {
        return cmpType;
    }

    public RiscvBranch(RiscvOperand left, RiscvOperand right, RiscvLabel falseTo,
                       RiscvCmpType cmpType) {
        super(new ArrayList<>(Arrays.asList(left, right, falseTo)), null);
        assert left instanceof RiscvReg && right instanceof RiscvReg;
        this.cmpType = cmpType;
    }

    public static RiscvBranch buildRiscvBranch(RiscvOperand left, RiscvOperand right, OP type,
                       RiscvLabel jLabel, boolean flag) {
        RiscvOperand trueLeft = null;
        RiscvOperand trueRight = null;
        RiscvCmpType trueCmpType = null;
        if(flag) {
            switch(type) {
                case Eq:
                    trueLeft = left;
                    trueRight = right;
                    trueCmpType = RiscvCmpType.bne;
                    break;
                case Ne:
                    trueLeft = left;
                    trueRight = right;
                    trueCmpType = RiscvCmpType.beq;
                    break;
                case Gt:
                    trueLeft = right;
                    trueRight = left;
                    trueCmpType = RiscvCmpType.bge;
                    break;
                case Ge:
                    trueLeft = left;
                    trueRight = right;
                    trueCmpType = RiscvCmpType.blt;
                    break;
                case Lt:
                    trueLeft = left;
                    trueRight = right;
                    trueCmpType = RiscvCmpType.bge;
                    break;
                case Le:
                    trueLeft = right;
                    trueRight = left;
                    trueCmpType = RiscvCmpType.blt;
            }
        } else {
            switch(type) {
                case Eq:
                    trueLeft = left;
                    trueRight = right;
                    trueCmpType = RiscvCmpType.beq;
                    break;
                case Ne:
                    trueLeft = left;
                    trueRight = right;
                    trueCmpType = RiscvCmpType.bne;
                    break;
                case Gt:
                    trueLeft = right;
                    trueRight = left;
                    trueCmpType = RiscvCmpType.blt;
                    break;
                case Ge:
                    trueLeft = left;
                    trueRight = right;
                    trueCmpType = RiscvCmpType.bge;
                    break;
                case Lt:
                    trueLeft = left;
                    trueRight = right;
                    trueCmpType = RiscvCmpType.blt;
                    break;
                case Le:
                    trueLeft = right;
                    trueRight = left;
                    trueCmpType = RiscvCmpType.bge;
            }
        }
        assert left instanceof RiscvReg && right instanceof RiscvReg;
        return new RiscvBranch(trueLeft, trueRight, jLabel, trueCmpType);
    }

    public void setPredSucc(RiscvBlock block) {
        assert getOperands().get(2) instanceof RiscvBlock;
        RiscvBlock block1 = (RiscvBlock) getOperands().get(2);
        block1.addPreds(block);
        block.addSuccs(block1);
    }

    @Override
    public String toString() {
        return cmpType2Str() + " " + getOperands().get(0) + "," +
                getOperands().get(1) + "," + getOperands().get(2);
    }
}
