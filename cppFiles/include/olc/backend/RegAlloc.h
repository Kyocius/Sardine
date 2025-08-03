#pragma once

#include <olc/backend/Liveness.h>
#include <olc/backend/MachineIR.h>

namespace olc {

struct LivePair {
  AsmReg *var;
  // close interval
  InstID start, end;

  LivePair(AsmReg *var, LiveIntervalT intv)
      : var(var), start(intv.first), end(intv.second) {}

public:
  struct StartAscend {
    bool operator()(const LivePair &a, const LivePair &b) const {
      return a.start < b.start || (a.start == b.start && a.var < b.var);
    }
  };

  struct EndAscend {
    bool operator()(const LivePair &a, const LivePair &b) const {
      return a.end < b.end || (a.end == b.end && a.var < b.var);
    }
  };
};

inline bool hasOverlap(LivePair const &a, LivePair const &b) {
  return a.start <= b.end && b.start <= a.end;
}

struct LinearScan {

  AsmFunc *func;
  std::set<LivePair, LivePair::StartAscend> scanningIntervals;
  std::set<LivePair, LivePair::EndAscend> activeIntervals;
  std::map<AsmReg *, std::vector<LivePair>> fixedIntervals;
  // no entry means spilled out
  std::map<AsmReg *, PReg *> regMap;
  std::set<AsmReg *> spills;
  std::set<PReg *> freeIntRegs, freeFloatRegs;

  void initRegs() {
    for (int i = 0; i < 13; i++)
      freeIntRegs.emplace(AsmReg::makePReg(AsmType::I32, i));
    for (int i = 0; i < 32; i++)
      freeFloatRegs.emplace(AsmReg::makePReg(AsmType::F32, i));
  }

  void constructIntervals() {
    LivenessAnalysis analysis;
    analysis.runOnFunction(func);
    auto &liveness = analysis.liveness;

    std::map<AsmReg *, std::pair<InstID, InstID>> intvs;

    auto updateInterval = [&](auto &intvs, AsmReg *reg, InstID i) {
      if (intvs.count(reg) == 0) {
        intvs[reg] = {i, i};
      } else {
        auto &interval = intvs[reg];
        if (i < interval.first)
          interval.first = i;
        if (i > interval.second)
          interval.second = i;
      }
    };

    // debug print
    // for (InstID i = 0; i < liveness.size(); ++i) {
    //   for (auto &&reg : liveness[i]) {
    //     std::cerr << "inst " << i << " reg " << (isa<PReg>(reg) ? "P" : "V")
    //               << reg->id << " type " << (int)reg->type << '\n';
    //   }
    // }
    // std::cerr << "\n";

    for (InstID i = 0; i < liveness.size(); ++i) {
      for (auto &&reg : liveness[i]) {
        if (isa<PReg>(reg)) {
          fixedIntervals[reg].emplace_back(reg, LiveIntervalT{i, i});
        } else {
          updateInterval(intvs, reg, i);
        }
      }
    }

    // Resolve caller saved regs in fixed
    for (auto &label : func->labels) {
      for (auto inst = label->Head; inst; inst = inst->Next) {
        if (isa<AsmCallInst>(inst)) {
          InstID id = analysis.instOrdering.instIDMap.at(inst);
          // insert caller saves
          for (int i = 0; i < 4; i++) {
            auto *preg = AsmReg::makePReg(AsmType::I32, i);
            fixedIntervals[preg].emplace_back(preg, LiveIntervalT{id, id});
          }
          for (int i = 0; i < 16; i++) {
            auto *preg = AsmReg::makePReg(AsmType::F32, i);
            fixedIntervals[preg].emplace_back(preg, LiveIntervalT{id, id});
          }

          // r12 (ip)
          auto *preg = AsmReg::makePReg(AsmType::I32, 12);
          fixedIntervals[preg].emplace_back(preg, LiveIntervalT{id, id});
        }
      }
    }

    for (auto &&[reg, intv] : intvs) {
      scanningIntervals.emplace(reg, intv);
    }

    // debug print
    // for (auto &&[reg, intv] : intvs) {
    //   std::cerr << "reg " << (isa<PReg>(reg) ? "P" : "V") << reg->id
    //             << " start " << intv.first << " end " << intv.second << '\n';
    // }
    // std::cerr << "\n";
    // for (auto &&[reg, intvs] : fixedIntervals) {
    //   for (auto &intv : intvs)
    //     std::cerr << "fixed " << (isa<PReg>(reg) ? "P" : "V") << reg->id
    //               << " start " << intv.start << " end " << intv.end << '\n';
    // }
  }

  void runOnFunction(AsmFunc *func) {
    this->func = func;

    initRegs();
    constructIntervals();

    for (auto &intv : scanningIntervals) {
      expireOldIntervals(intv.start);
      if (!tryAllocateReg(intv)) {
        spillAtInterval(intv);
      }
    }
  }

  void expireOldIntervals(InstID curInst) {
    for (auto it = activeIntervals.begin(); it != activeIntervals.end();) {
      if (it->end < curInst) {
        auto *preg = regMap.at(it->var);
        if (preg->type == AsmType::F32) {
          freeFloatRegs.insert(preg);
        } else {
          freeIntRegs.insert(preg);
        }
        it = activeIntervals.erase(it);
      } else {
        break;
      }
    }
  }

  void spillAtInterval(LivePair const &intv) {
    if (!activeIntervals.empty()) {
      auto spill = *activeIntervals.rbegin();
      if (spill.end > intv.end) {
        auto preg = regMap.at(spill.var);
        regMap.erase(spill.var);
        regMap[intv.var] = preg;
        activeIntervals.erase(spill);
        activeIntervals.insert(intv);
        // std::cerr << "Spilled "
        //           << (spill.var->type == AsmType::F32 ? "VF" : "VI")
        //           << spill.var->id << " to stack\n";
        spills.insert(spill.var);
        return;
      }
    }
    // std::cerr << "Spilled " << (intv.var->type == AsmType::F32 ? "VF" : "VI")
    //           << intv.var->id << " to stack\n";
    spills.insert(intv.var);
  }

  bool tryAllocateReg(LivePair const &intv) {
    assert(isa<VReg>(intv.var) && "Cannot allocate preg");

    auto isConflictWithFixeds = [&](PReg *reg) -> bool {
      if (auto it = fixedIntervals.find(reg); it != fixedIntervals.end()) {
        for (auto &pair : it->second) {
          if (hasOverlap(pair, intv))
            return true;
        }
      }
      return false;
    };

    auto allocateFromPool = [&](std::set<PReg *> &pool) -> PReg * {
      for (auto it = pool.begin(); it != pool.end(); it++) {
        auto *reg = *it;
        if (!isConflictWithFixeds(reg)) {
          pool.erase(it);
          return reg;
        }
      }
      return nullptr;
    };

    auto &pool = intv.var->type == AsmType::F32 ? freeFloatRegs : freeIntRegs;

    if (auto *preg = allocateFromPool(pool)) {
      // std::cerr << "Allocated "
      //           << (intv.var->type == AsmType::F32 ? "PF" : "PI") << preg->id
      //           << " to " << (intv.var->type == AsmType::F32 ? "VF" : "VI")
      //           << intv.var->id << '\n';
      regMap[intv.var] = preg;
    } else
      return false;
    activeIntervals.insert(intv);
    return true;
  }
};

} // namespace olc
