
#include <olc/backend/MachineIR.h>

namespace olc {

VReg *AsmReg::makeVReg(AsmType type) {
  static uint32_t vregCounter[2] = {};
  return new VReg(type, vregCounter[static_cast<int>(type)]++);
}

PReg *AsmReg::makePReg(AsmType type, int id) {
  static std::array<PReg, 16> intRegs{
      PReg{AsmType::I32, 0},  PReg{AsmType::I32, 1},  PReg{AsmType::I32, 2},
      PReg{AsmType::I32, 3},  PReg{AsmType::I32, 4},  PReg{AsmType::I32, 5},
      PReg{AsmType::I32, 6},  PReg{AsmType::I32, 7},  PReg{AsmType::I32, 8},
      PReg{AsmType::I32, 9},  PReg{AsmType::I32, 10}, PReg{AsmType::I32, 11},
      PReg{AsmType::I32, 12}, PReg{AsmType::I32, 13}, PReg{AsmType::I32, 14},
      PReg{AsmType::I32, 15},
  };
  static std::array<PReg, 32> floatRegs{
      PReg{AsmType::F32, 0},  PReg{AsmType::F32, 1},  PReg{AsmType::F32, 2},
      PReg{AsmType::F32, 3},  PReg{AsmType::F32, 4},  PReg{AsmType::F32, 5},
      PReg{AsmType::F32, 6},  PReg{AsmType::F32, 7},  PReg{AsmType::F32, 8},
      PReg{AsmType::F32, 9},  PReg{AsmType::F32, 10}, PReg{AsmType::F32, 11},
      PReg{AsmType::F32, 12}, PReg{AsmType::F32, 13}, PReg{AsmType::F32, 14},
      PReg{AsmType::F32, 15}, PReg{AsmType::F32, 16}, PReg{AsmType::F32, 17},
      PReg{AsmType::F32, 18}, PReg{AsmType::F32, 19}, PReg{AsmType::F32, 20},
      PReg{AsmType::F32, 21}, PReg{AsmType::F32, 22}, PReg{AsmType::F32, 23},
      PReg{AsmType::F32, 24}, PReg{AsmType::F32, 25}, PReg{AsmType::F32, 26},
      PReg{AsmType::F32, 27}, PReg{AsmType::F32, 28}, PReg{AsmType::F32, 29},
      PReg{AsmType::F32, 30}, PReg{AsmType::F32, 31},
  };

  if (type == AsmType::I32) {
    return &intRegs[id];
  } else {
    return &floatRegs[id];
  }
}

} // namespace olc
