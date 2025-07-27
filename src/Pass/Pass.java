package Pass;

import IR.IRModule;

public interface Pass {
    String getName();

    interface IRPass extends Pass{
        void run(IRModule module);
    }

}
