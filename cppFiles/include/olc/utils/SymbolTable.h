#pragma once

#include <iostream>
#include <string>
#include <unordered_map>
#include <vector>

#include <olc/Support.h>

namespace olc {

class Value;

class SymbolTable {
public:
  SymbolTable() { enterScope(); }

  Value *lookup(const std::string &name) const {
    for (auto it = scopes.rbegin(); it != scopes.rend(); ++it) {
      auto jt = it->find(name);
      if (jt != it->end()) {
        return jt->second.val;
      }
    }
    std::cerr << "Symbol not found: " << name << std::endl;
    olc_unreachable("Symbol not found");
  }

  std::vector<int> &lookupShape(const std::string &name) {
    for (auto it = scopes.rbegin(); it != scopes.rend(); ++it) {
      auto jt = it->find(name);
      if (jt != it->end()) {
        return jt->second.shape;
      }
    }
    std::cerr << "Symbol not found: " << name << std::endl;
    olc_unreachable("Symbol not found");
  }

  void
  insert(const std::string &name, Value *data, std::vector<int> shape = {}) {
    scopes.back().emplace(name, ScopeContext{data, shape});
  }

  void enterScope() { scopes.emplace_back(); }

  void exitScope() { scopes.pop_back(); }

private:
  struct ScopeContext {
    Value *val;
    std::vector<int> shape;
  };
  std::vector<std::unordered_map<std::string, ScopeContext>> scopes;
};

} // namespace olc
