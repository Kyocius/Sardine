package Pass;

import Driver.Config;
import IR.IRModule;
import Pass.IR.*;
import Pass.Pass.*;
import Utils.IRDump;
import Utils.LLVMIRDump;

import java.io.IOException;
import java.util.ArrayList;

public class PassManager {
    private static final PassManager passManager = new PassManager();
    public static PassManager getInstance(){
        return passManager;
    }
    ArrayList<IRPass> irPasses = new ArrayList<>();
    ArrayList<IRPass> mirPasses = new ArrayList<>();


    /*
    * 一些irPass顺序的想法：
    * 1. ConstFold要在InstComb之前
    * 2. GlobalValueLocalize必须在Mem2reg之前
    * 3. MergeBb必须在Mem2reg前
    * 4. InstComb必须在DCE前
    * 5. LoopUnroll紧跟LCSSA
    * ...
    *
    * */
    private PassManager(){
        irPasses.add(new UniqueConst());
        irPasses.add(new Mem2Reg());
        irPasses.add(new DCE());
        irPasses.add(new GVN());
        irPasses.add(new TailRecursiveElimination());
        irPasses.add(new FuncInLine());
        irPasses.add(new GlobalValueLocalize());
        irPasses.add(new Mem2Reg());

        Pass();
        Pass();

        if(Config.isO1) {
            irPasses.add(new LCSSA());
            irPasses.add(new RemoveUselessLoop());
            if(Config.parallelOpen)
                irPasses.add(new Parallelize());
            irPasses.add(new LoopInterchange());
            irPasses.add(new LoopStrengthReduction());
            irPasses.add(new LoopUnroll());
            irPasses.add(new LoopRotate());
            irPasses.add(new LICM());
            irPasses.add(new LoopUnchangeElect());
            Pass();
            irPasses.add(new LoopPtrExtract());
            irPasses.add(new LoopPtrMotion());
            Pass();
            irPasses.add(new FuncInLine());
            Pass();
            irPasses.add(new Rem2DivMulSub());
            irPasses.add(new GVN());
            irPasses.add(new GCM());
        }
        mirPasses.add(new ConstFold());
        mirPasses.add(new DCE());
        mirPasses.add(new Rem2DivMulSub());

        mirPasses.add(new RemovePhi());
    }

    //  Pass里一套基本优化顺序，可以多次使用
    private void Pass(){
        irPasses.add(new RemoveUselessNE());
        irPasses.add(new RemoveUselessPhi());
        irPasses.add(new InstComb());
        irPasses.add(new DCE());
        irPasses.add(new ConstFold());
        irPasses.add(new ConstArrayFetch());
        irPasses.add(new InstComb());
        irPasses.add(new ConstFold());
        irPasses.add(new DCE());
//        irPasses.add(new RemoveUselessStore());
        irPasses.add(new PeepHole());
        irPasses.add(new GVN());
        irPasses.add(new GCM());
        irPasses.add(new MergeBB());
        irPasses.add(new PeepHole());
        irPasses.add(new RemoveUselessPhi());
        irPasses.add(new MergeBB());
        irPasses.add(new RemoveUselessPhi());
        irPasses.add(new ConstFold());
        irPasses.add(new MergeBB());
        irPasses.add(new DCE());
        irPasses.add(new RemoveUselessPhi());
        irPasses.add(new ArrayEliminate());
        irPasses.add(new PeepHole());
        irPasses.add(new BlockReorder());
    }


    public void runIRPasses(IRModule irModule) throws IOException {
        var starttimemills = System.currentTimeMillis();
        for(IRPass irPass : irPasses){
            if(irPass instanceof Parallelize && Config.parallelOpen){
                IRDump.DumpModule(irModule);
                LLVMIRDump.LLVMDumpModule(irModule);
            }
            irPass.run(irModule);
            if(irPass instanceof ArrayEliminate){
                while(((ArrayEliminate) irPass).needrepeat){
                    irPass.run(irModule);
                }
            }
            if(System.currentTimeMillis()-starttimemills > 1000*100){
                System.err.println("IRPassOver120s，break！");
                break;
            }
        }
    }


    public void runMirPasses(IRModule irModule){
        for(IRPass irPass : mirPasses){
            irPass.run(irModule);
        }
    }
}
