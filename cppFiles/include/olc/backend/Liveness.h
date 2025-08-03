#pragma once

#include <olc/backend/MachineIR.h>

#include <olc/Support.h>

#include <map>
#include <set>
#include <limits>

namespace olc {

struct LivenessBlockInfo;

using InstID = size_t;
using VarSetT = std::set<AsmReg *>;
using BlockBuilderMapT = std::map<AsmLabel *, LivenessBlockInfo>;
using LiveIntervalT = std::pair<InstID, InstID>;

struct LivenessBlockInfo {

  LivenessBlockInfo() = default;

  LivenessBlockInfo(AsmLabel *label);

  // in = use U out \ def
  bool updateLiveIn();

  // out = U_{succ} in_{succ}
  void updateLiveOut(BlockBuilderMapT const &infoMap);

  /// The current block.
  AsmLabel *label{nullptr};

  VarSetT inRegs;
  VarSetT outRegs;
  VarSetT defRegs;
  VarSetT useRegs;
};

struct InstOrderingManager {

  InstOrderingManager() = default;

  void runOnFunction(AsmFunc *func);
  void runOnBlock(AsmLabel *bb);

  std::vector<AsmLabel *> labelOrder;
  std::map<AsmInst *, InstID> instIDMap;
};

struct LivenessAnalysis {
  LivenessAnalysis() = default;

  void runOnFunction(AsmFunc *func);

  InstOrderingManager instOrdering;
  BlockBuilderMapT blockInfoMap;
  // live variables per program point
  std::vector<VarSetT> liveness;

private:
  AsmFunc *func;
  void buildBlockInfoMap();
  void calcLiveness();
};

} // namespace olc
