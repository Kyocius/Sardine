
// Generated from sysy2022.g4 by ANTLR 4.13.2


#include "sysy2022Visitor.h"

#include "sysy2022Parser.h"


using namespace antlrcpp;

using namespace antlr4;

namespace {

struct Sysy2022ParserStaticData final {
  Sysy2022ParserStaticData(std::vector<std::string> ruleNames,
                        std::vector<std::string> literalNames,
                        std::vector<std::string> symbolicNames)
      : ruleNames(std::move(ruleNames)), literalNames(std::move(literalNames)),
        symbolicNames(std::move(symbolicNames)),
        vocabulary(this->literalNames, this->symbolicNames) {}

  Sysy2022ParserStaticData(const Sysy2022ParserStaticData&) = delete;
  Sysy2022ParserStaticData(Sysy2022ParserStaticData&&) = delete;
  Sysy2022ParserStaticData& operator=(const Sysy2022ParserStaticData&) = delete;
  Sysy2022ParserStaticData& operator=(Sysy2022ParserStaticData&&) = delete;

  std::vector<antlr4::dfa::DFA> decisionToDFA;
  antlr4::atn::PredictionContextCache sharedContextCache;
  const std::vector<std::string> ruleNames;
  const std::vector<std::string> literalNames;
  const std::vector<std::string> symbolicNames;
  const antlr4::dfa::Vocabulary vocabulary;
  antlr4::atn::SerializedATNView serializedATN;
  std::unique_ptr<antlr4::atn::ATN> atn;
};

::antlr4::internal::OnceFlag sysy2022ParserOnceFlag;
#if ANTLR4_USE_THREAD_LOCAL_CACHE
static thread_local
#endif
std::unique_ptr<Sysy2022ParserStaticData> sysy2022ParserStaticData = nullptr;

void sysy2022ParserInitialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  if (sysy2022ParserStaticData != nullptr) {
    return;
  }
#else
  assert(sysy2022ParserStaticData == nullptr);
#endif
  auto staticData = std::make_unique<Sysy2022ParserStaticData>(
    std::vector<std::string>{
      "compUnit", "varDecl", "varDef", "initVal", "funcDef", "funcFParam", 
      "block", "stmt", "expr", "unaryExpr", "lVal", "cond", "constExpr"
    },
    std::vector<std::string>{
      "", "'const'", "'int'", "'float'", "','", "';'", "'['", "']'", "'='", 
      "'{'", "'}'", "'void'", "'('", "')'", "'if'", "'else'", "'while'", 
      "'break'", "'continue'", "'return'", "'*'", "'/'", "'%'", "'+'", "'-'", 
      "'!'", "'<'", "'>'", "'<='", "'>='", "'=='", "'!='", "'&&'", "'||'"
    },
    std::vector<std::string>{
      "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", 
      "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", 
      "ID", "INT", "FLOAT", "BlockComment", "LineComment", "Blank"
    }
  );
  static const int32_t serializedATNSegment[] = {
  	4,1,39,228,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,6,2,
  	7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,2,11,7,11,2,12,7,12,1,0,1,0,5,0,29,8,
  	0,10,0,12,0,32,9,0,1,0,1,0,1,1,3,1,37,8,1,1,1,1,1,1,1,1,1,5,1,43,8,1,
  	10,1,12,1,46,9,1,1,1,1,1,1,2,1,2,1,2,1,2,1,2,5,2,55,8,2,10,2,12,2,58,
  	9,2,1,2,1,2,3,2,62,8,2,1,3,1,3,1,3,1,3,1,3,5,3,69,8,3,10,3,12,3,72,9,
  	3,3,3,74,8,3,1,3,3,3,77,8,3,1,4,1,4,1,4,1,4,1,4,1,4,5,4,85,8,4,10,4,12,
  	4,88,9,4,3,4,90,8,4,1,4,1,4,1,4,1,5,1,5,1,5,3,5,98,8,5,1,5,3,5,101,8,
  	5,1,5,1,5,1,5,1,5,5,5,107,8,5,10,5,12,5,110,9,5,1,6,1,6,1,6,5,6,115,8,
  	6,10,6,12,6,118,9,6,1,6,1,6,1,7,1,7,1,7,1,7,1,7,1,7,3,7,128,8,7,1,7,1,
  	7,1,7,1,7,1,7,1,7,1,7,1,7,1,7,3,7,139,8,7,1,7,1,7,1,7,1,7,1,7,1,7,1,7,
  	1,7,1,7,1,7,1,7,1,7,3,7,153,8,7,1,7,3,7,156,8,7,1,8,1,8,1,8,1,8,1,8,1,
  	8,1,8,1,8,1,8,5,8,167,8,8,10,8,12,8,170,9,8,1,9,1,9,1,9,1,9,1,9,1,9,1,
  	9,1,9,1,9,1,9,1,9,1,9,5,9,184,8,9,10,9,12,9,187,9,9,3,9,189,8,9,1,9,1,
  	9,1,9,3,9,194,8,9,1,10,1,10,1,10,1,10,1,10,5,10,201,8,10,10,10,12,10,
  	204,9,10,1,11,1,11,1,11,1,11,1,11,1,11,1,11,1,11,1,11,1,11,1,11,1,11,
  	1,11,1,11,1,11,5,11,221,8,11,10,11,12,11,224,9,11,1,12,1,12,1,12,0,2,
  	16,22,13,0,2,4,6,8,10,12,14,16,18,20,22,24,0,7,1,0,2,3,2,0,2,3,11,11,
  	1,0,20,22,1,0,23,24,1,0,23,25,1,0,26,29,1,0,30,31,254,0,30,1,0,0,0,2,
  	36,1,0,0,0,4,49,1,0,0,0,6,76,1,0,0,0,8,78,1,0,0,0,10,94,1,0,0,0,12,111,
  	1,0,0,0,14,155,1,0,0,0,16,157,1,0,0,0,18,193,1,0,0,0,20,195,1,0,0,0,22,
  	205,1,0,0,0,24,225,1,0,0,0,26,29,3,2,1,0,27,29,3,8,4,0,28,26,1,0,0,0,
  	28,27,1,0,0,0,29,32,1,0,0,0,30,28,1,0,0,0,30,31,1,0,0,0,31,33,1,0,0,0,
  	32,30,1,0,0,0,33,34,5,0,0,1,34,1,1,0,0,0,35,37,5,1,0,0,36,35,1,0,0,0,
  	36,37,1,0,0,0,37,38,1,0,0,0,38,39,7,0,0,0,39,44,3,4,2,0,40,41,5,4,0,0,
  	41,43,3,4,2,0,42,40,1,0,0,0,43,46,1,0,0,0,44,42,1,0,0,0,44,45,1,0,0,0,
  	45,47,1,0,0,0,46,44,1,0,0,0,47,48,5,5,0,0,48,3,1,0,0,0,49,56,5,34,0,0,
  	50,51,5,6,0,0,51,52,3,24,12,0,52,53,5,7,0,0,53,55,1,0,0,0,54,50,1,0,0,
  	0,55,58,1,0,0,0,56,54,1,0,0,0,56,57,1,0,0,0,57,61,1,0,0,0,58,56,1,0,0,
  	0,59,60,5,8,0,0,60,62,3,6,3,0,61,59,1,0,0,0,61,62,1,0,0,0,62,5,1,0,0,
  	0,63,77,3,16,8,0,64,73,5,9,0,0,65,70,3,6,3,0,66,67,5,4,0,0,67,69,3,6,
  	3,0,68,66,1,0,0,0,69,72,1,0,0,0,70,68,1,0,0,0,70,71,1,0,0,0,71,74,1,0,
  	0,0,72,70,1,0,0,0,73,65,1,0,0,0,73,74,1,0,0,0,74,75,1,0,0,0,75,77,5,10,
  	0,0,76,63,1,0,0,0,76,64,1,0,0,0,77,7,1,0,0,0,78,79,7,1,0,0,79,80,5,34,
  	0,0,80,89,5,12,0,0,81,86,3,10,5,0,82,83,5,4,0,0,83,85,3,10,5,0,84,82,
  	1,0,0,0,85,88,1,0,0,0,86,84,1,0,0,0,86,87,1,0,0,0,87,90,1,0,0,0,88,86,
  	1,0,0,0,89,81,1,0,0,0,89,90,1,0,0,0,90,91,1,0,0,0,91,92,5,13,0,0,92,93,
  	3,12,6,0,93,9,1,0,0,0,94,95,7,0,0,0,95,97,5,34,0,0,96,98,5,6,0,0,97,96,
  	1,0,0,0,97,98,1,0,0,0,98,100,1,0,0,0,99,101,5,7,0,0,100,99,1,0,0,0,100,
  	101,1,0,0,0,101,108,1,0,0,0,102,103,5,6,0,0,103,104,3,16,8,0,104,105,
  	5,7,0,0,105,107,1,0,0,0,106,102,1,0,0,0,107,110,1,0,0,0,108,106,1,0,0,
  	0,108,109,1,0,0,0,109,11,1,0,0,0,110,108,1,0,0,0,111,116,5,9,0,0,112,
  	115,3,2,1,0,113,115,3,14,7,0,114,112,1,0,0,0,114,113,1,0,0,0,115,118,
  	1,0,0,0,116,114,1,0,0,0,116,117,1,0,0,0,117,119,1,0,0,0,118,116,1,0,0,
  	0,119,120,5,10,0,0,120,13,1,0,0,0,121,122,3,20,10,0,122,123,5,8,0,0,123,
  	124,3,16,8,0,124,125,5,5,0,0,125,156,1,0,0,0,126,128,3,16,8,0,127,126,
  	1,0,0,0,127,128,1,0,0,0,128,129,1,0,0,0,129,156,5,5,0,0,130,156,3,12,
  	6,0,131,132,5,14,0,0,132,133,5,12,0,0,133,134,3,22,11,0,134,135,5,13,
  	0,0,135,138,3,14,7,0,136,137,5,15,0,0,137,139,3,14,7,0,138,136,1,0,0,
  	0,138,139,1,0,0,0,139,156,1,0,0,0,140,141,5,16,0,0,141,142,5,12,0,0,142,
  	143,3,22,11,0,143,144,5,13,0,0,144,145,3,14,7,0,145,156,1,0,0,0,146,147,
  	5,17,0,0,147,156,5,5,0,0,148,149,5,18,0,0,149,156,5,5,0,0,150,152,5,19,
  	0,0,151,153,3,16,8,0,152,151,1,0,0,0,152,153,1,0,0,0,153,154,1,0,0,0,
  	154,156,5,5,0,0,155,121,1,0,0,0,155,127,1,0,0,0,155,130,1,0,0,0,155,131,
  	1,0,0,0,155,140,1,0,0,0,155,146,1,0,0,0,155,148,1,0,0,0,155,150,1,0,0,
  	0,156,15,1,0,0,0,157,158,6,8,-1,0,158,159,3,18,9,0,159,168,1,0,0,0,160,
  	161,10,2,0,0,161,162,7,2,0,0,162,167,3,16,8,3,163,164,10,1,0,0,164,165,
  	7,3,0,0,165,167,3,16,8,2,166,160,1,0,0,0,166,163,1,0,0,0,167,170,1,0,
  	0,0,168,166,1,0,0,0,168,169,1,0,0,0,169,17,1,0,0,0,170,168,1,0,0,0,171,
  	172,5,12,0,0,172,173,3,16,8,0,173,174,5,13,0,0,174,194,1,0,0,0,175,194,
  	3,20,10,0,176,194,5,35,0,0,177,194,5,36,0,0,178,179,5,34,0,0,179,188,
  	5,12,0,0,180,185,3,16,8,0,181,182,5,4,0,0,182,184,3,16,8,0,183,181,1,
  	0,0,0,184,187,1,0,0,0,185,183,1,0,0,0,185,186,1,0,0,0,186,189,1,0,0,0,
  	187,185,1,0,0,0,188,180,1,0,0,0,188,189,1,0,0,0,189,190,1,0,0,0,190,194,
  	5,13,0,0,191,192,7,4,0,0,192,194,3,18,9,0,193,171,1,0,0,0,193,175,1,0,
  	0,0,193,176,1,0,0,0,193,177,1,0,0,0,193,178,1,0,0,0,193,191,1,0,0,0,194,
  	19,1,0,0,0,195,202,5,34,0,0,196,197,5,6,0,0,197,198,3,16,8,0,198,199,
  	5,7,0,0,199,201,1,0,0,0,200,196,1,0,0,0,201,204,1,0,0,0,202,200,1,0,0,
  	0,202,203,1,0,0,0,203,21,1,0,0,0,204,202,1,0,0,0,205,206,6,11,-1,0,206,
  	207,3,16,8,0,207,222,1,0,0,0,208,209,10,4,0,0,209,210,7,5,0,0,210,221,
  	3,22,11,5,211,212,10,3,0,0,212,213,7,6,0,0,213,221,3,22,11,4,214,215,
  	10,2,0,0,215,216,5,32,0,0,216,221,3,22,11,3,217,218,10,1,0,0,218,219,
  	5,33,0,0,219,221,3,22,11,2,220,208,1,0,0,0,220,211,1,0,0,0,220,214,1,
  	0,0,0,220,217,1,0,0,0,221,224,1,0,0,0,222,220,1,0,0,0,222,223,1,0,0,0,
  	223,23,1,0,0,0,224,222,1,0,0,0,225,226,3,16,8,0,226,25,1,0,0,0,28,28,
  	30,36,44,56,61,70,73,76,86,89,97,100,108,114,116,127,138,152,155,166,
  	168,185,188,193,202,220,222
  };
  staticData->serializedATN = antlr4::atn::SerializedATNView(serializedATNSegment, sizeof(serializedATNSegment) / sizeof(serializedATNSegment[0]));

  antlr4::atn::ATNDeserializer deserializer;
  staticData->atn = deserializer.deserialize(staticData->serializedATN);

  const size_t count = staticData->atn->getNumberOfDecisions();
  staticData->decisionToDFA.reserve(count);
  for (size_t i = 0; i < count; i++) { 
    staticData->decisionToDFA.emplace_back(staticData->atn->getDecisionState(i), i);
  }
  sysy2022ParserStaticData = std::move(staticData);
}

}

sysy2022Parser::sysy2022Parser(TokenStream *input) : sysy2022Parser(input, antlr4::atn::ParserATNSimulatorOptions()) {}

sysy2022Parser::sysy2022Parser(TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options) : Parser(input) {
  sysy2022Parser::initialize();
  _interpreter = new atn::ParserATNSimulator(this, *sysy2022ParserStaticData->atn, sysy2022ParserStaticData->decisionToDFA, sysy2022ParserStaticData->sharedContextCache, options);
}

sysy2022Parser::~sysy2022Parser() {
  delete _interpreter;
}

const atn::ATN& sysy2022Parser::getATN() const {
  return *sysy2022ParserStaticData->atn;
}

std::string sysy2022Parser::getGrammarFileName() const {
  return "sysy2022.g4";
}

const std::vector<std::string>& sysy2022Parser::getRuleNames() const {
  return sysy2022ParserStaticData->ruleNames;
}

const dfa::Vocabulary& sysy2022Parser::getVocabulary() const {
  return sysy2022ParserStaticData->vocabulary;
}

antlr4::atn::SerializedATNView sysy2022Parser::getSerializedATN() const {
  return sysy2022ParserStaticData->serializedATN;
}


//----------------- CompUnitContext ------------------------------------------------------------------

sysy2022Parser::CompUnitContext::CompUnitContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* sysy2022Parser::CompUnitContext::EOF() {
  return getToken(sysy2022Parser::EOF, 0);
}

std::vector<sysy2022Parser::VarDeclContext *> sysy2022Parser::CompUnitContext::varDecl() {
  return getRuleContexts<sysy2022Parser::VarDeclContext>();
}

sysy2022Parser::VarDeclContext* sysy2022Parser::CompUnitContext::varDecl(size_t i) {
  return getRuleContext<sysy2022Parser::VarDeclContext>(i);
}

std::vector<sysy2022Parser::FuncDefContext *> sysy2022Parser::CompUnitContext::funcDef() {
  return getRuleContexts<sysy2022Parser::FuncDefContext>();
}

sysy2022Parser::FuncDefContext* sysy2022Parser::CompUnitContext::funcDef(size_t i) {
  return getRuleContext<sysy2022Parser::FuncDefContext>(i);
}


size_t sysy2022Parser::CompUnitContext::getRuleIndex() const {
  return sysy2022Parser::RuleCompUnit;
}


std::any sysy2022Parser::CompUnitContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitCompUnit(this);
  else
    return visitor->visitChildren(this);
}

sysy2022Parser::CompUnitContext* sysy2022Parser::compUnit() {
  CompUnitContext *_localctx = _tracker.createInstance<CompUnitContext>(_ctx, getState());
  enterRule(_localctx, 0, sysy2022Parser::RuleCompUnit);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(30);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 2062) != 0)) {
      setState(28);
      _errHandler->sync(this);
      switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 0, _ctx)) {
      case 1: {
        setState(26);
        varDecl();
        break;
      }

      case 2: {
        setState(27);
        funcDef();
        break;
      }

      default:
        break;
      }
      setState(32);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(33);
    match(sysy2022Parser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- VarDeclContext ------------------------------------------------------------------

sysy2022Parser::VarDeclContext::VarDeclContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

std::vector<sysy2022Parser::VarDefContext *> sysy2022Parser::VarDeclContext::varDef() {
  return getRuleContexts<sysy2022Parser::VarDefContext>();
}

sysy2022Parser::VarDefContext* sysy2022Parser::VarDeclContext::varDef(size_t i) {
  return getRuleContext<sysy2022Parser::VarDefContext>(i);
}


size_t sysy2022Parser::VarDeclContext::getRuleIndex() const {
  return sysy2022Parser::RuleVarDecl;
}


std::any sysy2022Parser::VarDeclContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitVarDecl(this);
  else
    return visitor->visitChildren(this);
}

sysy2022Parser::VarDeclContext* sysy2022Parser::varDecl() {
  VarDeclContext *_localctx = _tracker.createInstance<VarDeclContext>(_ctx, getState());
  enterRule(_localctx, 2, sysy2022Parser::RuleVarDecl);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(36);
    _errHandler->sync(this);

    _la = _input->LA(1);
    if (_la == sysy2022Parser::T__0) {
      setState(35);
      antlrcpp::downCast<VarDeclContext *>(_localctx)->isConst = match(sysy2022Parser::T__0);
    }
    setState(38);
    antlrcpp::downCast<VarDeclContext *>(_localctx)->basicType = _input->LT(1);
    _la = _input->LA(1);
    if (!(_la == sysy2022Parser::T__1

    || _la == sysy2022Parser::T__2)) {
      antlrcpp::downCast<VarDeclContext *>(_localctx)->basicType = _errHandler->recoverInline(this);
    }
    else {
      _errHandler->reportMatch(this);
      consume();
    }
    setState(39);
    varDef();
    setState(44);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while (_la == sysy2022Parser::T__3) {
      setState(40);
      match(sysy2022Parser::T__3);
      setState(41);
      varDef();
      setState(46);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(47);
    match(sysy2022Parser::T__4);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- VarDefContext ------------------------------------------------------------------

sysy2022Parser::VarDefContext::VarDefContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* sysy2022Parser::VarDefContext::ID() {
  return getToken(sysy2022Parser::ID, 0);
}

std::vector<sysy2022Parser::ConstExprContext *> sysy2022Parser::VarDefContext::constExpr() {
  return getRuleContexts<sysy2022Parser::ConstExprContext>();
}

sysy2022Parser::ConstExprContext* sysy2022Parser::VarDefContext::constExpr(size_t i) {
  return getRuleContext<sysy2022Parser::ConstExprContext>(i);
}

sysy2022Parser::InitValContext* sysy2022Parser::VarDefContext::initVal() {
  return getRuleContext<sysy2022Parser::InitValContext>(0);
}


size_t sysy2022Parser::VarDefContext::getRuleIndex() const {
  return sysy2022Parser::RuleVarDef;
}


std::any sysy2022Parser::VarDefContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitVarDef(this);
  else
    return visitor->visitChildren(this);
}

sysy2022Parser::VarDefContext* sysy2022Parser::varDef() {
  VarDefContext *_localctx = _tracker.createInstance<VarDefContext>(_ctx, getState());
  enterRule(_localctx, 4, sysy2022Parser::RuleVarDef);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(49);
    match(sysy2022Parser::ID);
    setState(56);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while (_la == sysy2022Parser::T__5) {
      setState(50);
      match(sysy2022Parser::T__5);
      setState(51);
      constExpr();
      setState(52);
      match(sysy2022Parser::T__6);
      setState(58);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(61);
    _errHandler->sync(this);

    _la = _input->LA(1);
    if (_la == sysy2022Parser::T__7) {
      setState(59);
      match(sysy2022Parser::T__7);
      setState(60);
      initVal();
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- InitValContext ------------------------------------------------------------------

sysy2022Parser::InitValContext::InitValContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

sysy2022Parser::ExprContext* sysy2022Parser::InitValContext::expr() {
  return getRuleContext<sysy2022Parser::ExprContext>(0);
}

std::vector<sysy2022Parser::InitValContext *> sysy2022Parser::InitValContext::initVal() {
  return getRuleContexts<sysy2022Parser::InitValContext>();
}

sysy2022Parser::InitValContext* sysy2022Parser::InitValContext::initVal(size_t i) {
  return getRuleContext<sysy2022Parser::InitValContext>(i);
}


size_t sysy2022Parser::InitValContext::getRuleIndex() const {
  return sysy2022Parser::RuleInitVal;
}


std::any sysy2022Parser::InitValContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitInitVal(this);
  else
    return visitor->visitChildren(this);
}

sysy2022Parser::InitValContext* sysy2022Parser::initVal() {
  InitValContext *_localctx = _tracker.createInstance<InitValContext>(_ctx, getState());
  enterRule(_localctx, 6, sysy2022Parser::RuleInitVal);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(76);
    _errHandler->sync(this);
    switch (_input->LA(1)) {
      case sysy2022Parser::T__11:
      case sysy2022Parser::T__22:
      case sysy2022Parser::T__23:
      case sysy2022Parser::T__24:
      case sysy2022Parser::ID:
      case sysy2022Parser::INT:
      case sysy2022Parser::FLOAT: {
        enterOuterAlt(_localctx, 1);
        setState(63);
        expr(0);
        break;
      }

      case sysy2022Parser::T__8: {
        enterOuterAlt(_localctx, 2);
        setState(64);
        match(sysy2022Parser::T__8);
        setState(73);
        _errHandler->sync(this);

        _la = _input->LA(1);
        if ((((_la & ~ 0x3fULL) == 0) &&
          ((1ULL << _la) & 120317809152) != 0)) {
          setState(65);
          initVal();
          setState(70);
          _errHandler->sync(this);
          _la = _input->LA(1);
          while (_la == sysy2022Parser::T__3) {
            setState(66);
            match(sysy2022Parser::T__3);
            setState(67);
            initVal();
            setState(72);
            _errHandler->sync(this);
            _la = _input->LA(1);
          }
        }
        setState(75);
        match(sysy2022Parser::T__9);
        break;
      }

    default:
      throw NoViableAltException(this);
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- FuncDefContext ------------------------------------------------------------------

sysy2022Parser::FuncDefContext::FuncDefContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* sysy2022Parser::FuncDefContext::ID() {
  return getToken(sysy2022Parser::ID, 0);
}

sysy2022Parser::BlockContext* sysy2022Parser::FuncDefContext::block() {
  return getRuleContext<sysy2022Parser::BlockContext>(0);
}

std::vector<sysy2022Parser::FuncFParamContext *> sysy2022Parser::FuncDefContext::funcFParam() {
  return getRuleContexts<sysy2022Parser::FuncFParamContext>();
}

sysy2022Parser::FuncFParamContext* sysy2022Parser::FuncDefContext::funcFParam(size_t i) {
  return getRuleContext<sysy2022Parser::FuncFParamContext>(i);
}


size_t sysy2022Parser::FuncDefContext::getRuleIndex() const {
  return sysy2022Parser::RuleFuncDef;
}


std::any sysy2022Parser::FuncDefContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitFuncDef(this);
  else
    return visitor->visitChildren(this);
}

sysy2022Parser::FuncDefContext* sysy2022Parser::funcDef() {
  FuncDefContext *_localctx = _tracker.createInstance<FuncDefContext>(_ctx, getState());
  enterRule(_localctx, 8, sysy2022Parser::RuleFuncDef);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(78);
    antlrcpp::downCast<FuncDefContext *>(_localctx)->funcType = _input->LT(1);
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 2060) != 0))) {
      antlrcpp::downCast<FuncDefContext *>(_localctx)->funcType = _errHandler->recoverInline(this);
    }
    else {
      _errHandler->reportMatch(this);
      consume();
    }
    setState(79);
    match(sysy2022Parser::ID);
    setState(80);
    match(sysy2022Parser::T__11);
    setState(89);
    _errHandler->sync(this);

    _la = _input->LA(1);
    if (_la == sysy2022Parser::T__1

    || _la == sysy2022Parser::T__2) {
      setState(81);
      funcFParam();
      setState(86);
      _errHandler->sync(this);
      _la = _input->LA(1);
      while (_la == sysy2022Parser::T__3) {
        setState(82);
        match(sysy2022Parser::T__3);
        setState(83);
        funcFParam();
        setState(88);
        _errHandler->sync(this);
        _la = _input->LA(1);
      }
    }
    setState(91);
    match(sysy2022Parser::T__12);
    setState(92);
    block();
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- FuncFParamContext ------------------------------------------------------------------

sysy2022Parser::FuncFParamContext::FuncFParamContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* sysy2022Parser::FuncFParamContext::ID() {
  return getToken(sysy2022Parser::ID, 0);
}

std::vector<sysy2022Parser::ExprContext *> sysy2022Parser::FuncFParamContext::expr() {
  return getRuleContexts<sysy2022Parser::ExprContext>();
}

sysy2022Parser::ExprContext* sysy2022Parser::FuncFParamContext::expr(size_t i) {
  return getRuleContext<sysy2022Parser::ExprContext>(i);
}


size_t sysy2022Parser::FuncFParamContext::getRuleIndex() const {
  return sysy2022Parser::RuleFuncFParam;
}


std::any sysy2022Parser::FuncFParamContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitFuncFParam(this);
  else
    return visitor->visitChildren(this);
}

sysy2022Parser::FuncFParamContext* sysy2022Parser::funcFParam() {
  FuncFParamContext *_localctx = _tracker.createInstance<FuncFParamContext>(_ctx, getState());
  enterRule(_localctx, 10, sysy2022Parser::RuleFuncFParam);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(94);
    antlrcpp::downCast<FuncFParamContext *>(_localctx)->basicType = _input->LT(1);
    _la = _input->LA(1);
    if (!(_la == sysy2022Parser::T__1

    || _la == sysy2022Parser::T__2)) {
      antlrcpp::downCast<FuncFParamContext *>(_localctx)->basicType = _errHandler->recoverInline(this);
    }
    else {
      _errHandler->reportMatch(this);
      consume();
    }
    setState(95);
    match(sysy2022Parser::ID);
    setState(97);
    _errHandler->sync(this);

    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 11, _ctx)) {
    case 1: {
      setState(96);
      antlrcpp::downCast<FuncFParamContext *>(_localctx)->isArrayRef = match(sysy2022Parser::T__5);
      break;
    }

    default:
      break;
    }
    setState(100);
    _errHandler->sync(this);

    _la = _input->LA(1);
    if (_la == sysy2022Parser::T__6) {
      setState(99);
      match(sysy2022Parser::T__6);
    }
    setState(108);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while (_la == sysy2022Parser::T__5) {
      setState(102);
      match(sysy2022Parser::T__5);
      setState(103);
      expr(0);
      setState(104);
      match(sysy2022Parser::T__6);
      setState(110);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BlockContext ------------------------------------------------------------------

sysy2022Parser::BlockContext::BlockContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

std::vector<sysy2022Parser::VarDeclContext *> sysy2022Parser::BlockContext::varDecl() {
  return getRuleContexts<sysy2022Parser::VarDeclContext>();
}

sysy2022Parser::VarDeclContext* sysy2022Parser::BlockContext::varDecl(size_t i) {
  return getRuleContext<sysy2022Parser::VarDeclContext>(i);
}

std::vector<sysy2022Parser::StmtContext *> sysy2022Parser::BlockContext::stmt() {
  return getRuleContexts<sysy2022Parser::StmtContext>();
}

sysy2022Parser::StmtContext* sysy2022Parser::BlockContext::stmt(size_t i) {
  return getRuleContext<sysy2022Parser::StmtContext>(i);
}


size_t sysy2022Parser::BlockContext::getRuleIndex() const {
  return sysy2022Parser::RuleBlock;
}


std::any sysy2022Parser::BlockContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitBlock(this);
  else
    return visitor->visitChildren(this);
}

sysy2022Parser::BlockContext* sysy2022Parser::block() {
  BlockContext *_localctx = _tracker.createInstance<BlockContext>(_ctx, getState());
  enterRule(_localctx, 12, sysy2022Parser::RuleBlock);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(111);
    match(sysy2022Parser::T__8);
    setState(116);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 120318808622) != 0)) {
      setState(114);
      _errHandler->sync(this);
      switch (_input->LA(1)) {
        case sysy2022Parser::T__0:
        case sysy2022Parser::T__1:
        case sysy2022Parser::T__2: {
          setState(112);
          varDecl();
          break;
        }

        case sysy2022Parser::T__4:
        case sysy2022Parser::T__8:
        case sysy2022Parser::T__11:
        case sysy2022Parser::T__13:
        case sysy2022Parser::T__15:
        case sysy2022Parser::T__16:
        case sysy2022Parser::T__17:
        case sysy2022Parser::T__18:
        case sysy2022Parser::T__22:
        case sysy2022Parser::T__23:
        case sysy2022Parser::T__24:
        case sysy2022Parser::ID:
        case sysy2022Parser::INT:
        case sysy2022Parser::FLOAT: {
          setState(113);
          stmt();
          break;
        }

      default:
        throw NoViableAltException(this);
      }
      setState(118);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(119);
    match(sysy2022Parser::T__9);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- StmtContext ------------------------------------------------------------------

sysy2022Parser::StmtContext::StmtContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}


size_t sysy2022Parser::StmtContext::getRuleIndex() const {
  return sysy2022Parser::RuleStmt;
}

void sysy2022Parser::StmtContext::copyFrom(StmtContext *ctx) {
  ParserRuleContext::copyFrom(ctx);
}

//----------------- ExprStmtContext ------------------------------------------------------------------

sysy2022Parser::ExprContext* sysy2022Parser::ExprStmtContext::expr() {
  return getRuleContext<sysy2022Parser::ExprContext>(0);
}

sysy2022Parser::ExprStmtContext::ExprStmtContext(StmtContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::ExprStmtContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitExprStmt(this);
  else
    return visitor->visitChildren(this);
}
//----------------- WhileStmtContext ------------------------------------------------------------------

sysy2022Parser::CondContext* sysy2022Parser::WhileStmtContext::cond() {
  return getRuleContext<sysy2022Parser::CondContext>(0);
}

sysy2022Parser::StmtContext* sysy2022Parser::WhileStmtContext::stmt() {
  return getRuleContext<sysy2022Parser::StmtContext>(0);
}

sysy2022Parser::WhileStmtContext::WhileStmtContext(StmtContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::WhileStmtContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitWhileStmt(this);
  else
    return visitor->visitChildren(this);
}
//----------------- IfStmtContext ------------------------------------------------------------------

sysy2022Parser::CondContext* sysy2022Parser::IfStmtContext::cond() {
  return getRuleContext<sysy2022Parser::CondContext>(0);
}

std::vector<sysy2022Parser::StmtContext *> sysy2022Parser::IfStmtContext::stmt() {
  return getRuleContexts<sysy2022Parser::StmtContext>();
}

sysy2022Parser::StmtContext* sysy2022Parser::IfStmtContext::stmt(size_t i) {
  return getRuleContext<sysy2022Parser::StmtContext>(i);
}

sysy2022Parser::IfStmtContext::IfStmtContext(StmtContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::IfStmtContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitIfStmt(this);
  else
    return visitor->visitChildren(this);
}
//----------------- BlockStmtContext ------------------------------------------------------------------

sysy2022Parser::BlockContext* sysy2022Parser::BlockStmtContext::block() {
  return getRuleContext<sysy2022Parser::BlockContext>(0);
}

sysy2022Parser::BlockStmtContext::BlockStmtContext(StmtContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::BlockStmtContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitBlockStmt(this);
  else
    return visitor->visitChildren(this);
}
//----------------- AssignStmtContext ------------------------------------------------------------------

sysy2022Parser::LValContext* sysy2022Parser::AssignStmtContext::lVal() {
  return getRuleContext<sysy2022Parser::LValContext>(0);
}

sysy2022Parser::ExprContext* sysy2022Parser::AssignStmtContext::expr() {
  return getRuleContext<sysy2022Parser::ExprContext>(0);
}

sysy2022Parser::AssignStmtContext::AssignStmtContext(StmtContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::AssignStmtContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitAssignStmt(this);
  else
    return visitor->visitChildren(this);
}
//----------------- BreakStmtContext ------------------------------------------------------------------

sysy2022Parser::BreakStmtContext::BreakStmtContext(StmtContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::BreakStmtContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitBreakStmt(this);
  else
    return visitor->visitChildren(this);
}
//----------------- ReturnStmtContext ------------------------------------------------------------------

sysy2022Parser::ExprContext* sysy2022Parser::ReturnStmtContext::expr() {
  return getRuleContext<sysy2022Parser::ExprContext>(0);
}

sysy2022Parser::ReturnStmtContext::ReturnStmtContext(StmtContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::ReturnStmtContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitReturnStmt(this);
  else
    return visitor->visitChildren(this);
}
//----------------- ContinueStmtContext ------------------------------------------------------------------

sysy2022Parser::ContinueStmtContext::ContinueStmtContext(StmtContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::ContinueStmtContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitContinueStmt(this);
  else
    return visitor->visitChildren(this);
}
sysy2022Parser::StmtContext* sysy2022Parser::stmt() {
  StmtContext *_localctx = _tracker.createInstance<StmtContext>(_ctx, getState());
  enterRule(_localctx, 14, sysy2022Parser::RuleStmt);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(155);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 19, _ctx)) {
    case 1: {
      _localctx = _tracker.createInstance<sysy2022Parser::AssignStmtContext>(_localctx);
      enterOuterAlt(_localctx, 1);
      setState(121);
      lVal();
      setState(122);
      match(sysy2022Parser::T__7);
      setState(123);
      expr(0);
      setState(124);
      match(sysy2022Parser::T__4);
      break;
    }

    case 2: {
      _localctx = _tracker.createInstance<sysy2022Parser::ExprStmtContext>(_localctx);
      enterOuterAlt(_localctx, 2);
      setState(127);
      _errHandler->sync(this);

      _la = _input->LA(1);
      if ((((_la & ~ 0x3fULL) == 0) &&
        ((1ULL << _la) & 120317808640) != 0)) {
        setState(126);
        expr(0);
      }
      setState(129);
      match(sysy2022Parser::T__4);
      break;
    }

    case 3: {
      _localctx = _tracker.createInstance<sysy2022Parser::BlockStmtContext>(_localctx);
      enterOuterAlt(_localctx, 3);
      setState(130);
      block();
      break;
    }

    case 4: {
      _localctx = _tracker.createInstance<sysy2022Parser::IfStmtContext>(_localctx);
      enterOuterAlt(_localctx, 4);
      setState(131);
      match(sysy2022Parser::T__13);
      setState(132);
      match(sysy2022Parser::T__11);
      setState(133);
      cond(0);
      setState(134);
      match(sysy2022Parser::T__12);
      setState(135);
      stmt();
      setState(138);
      _errHandler->sync(this);

      switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 17, _ctx)) {
      case 1: {
        setState(136);
        match(sysy2022Parser::T__14);
        setState(137);
        stmt();
        break;
      }

      default:
        break;
      }
      break;
    }

    case 5: {
      _localctx = _tracker.createInstance<sysy2022Parser::WhileStmtContext>(_localctx);
      enterOuterAlt(_localctx, 5);
      setState(140);
      match(sysy2022Parser::T__15);
      setState(141);
      match(sysy2022Parser::T__11);
      setState(142);
      cond(0);
      setState(143);
      match(sysy2022Parser::T__12);
      setState(144);
      stmt();
      break;
    }

    case 6: {
      _localctx = _tracker.createInstance<sysy2022Parser::BreakStmtContext>(_localctx);
      enterOuterAlt(_localctx, 6);
      setState(146);
      match(sysy2022Parser::T__16);
      setState(147);
      match(sysy2022Parser::T__4);
      break;
    }

    case 7: {
      _localctx = _tracker.createInstance<sysy2022Parser::ContinueStmtContext>(_localctx);
      enterOuterAlt(_localctx, 7);
      setState(148);
      match(sysy2022Parser::T__17);
      setState(149);
      match(sysy2022Parser::T__4);
      break;
    }

    case 8: {
      _localctx = _tracker.createInstance<sysy2022Parser::ReturnStmtContext>(_localctx);
      enterOuterAlt(_localctx, 8);
      setState(150);
      match(sysy2022Parser::T__18);
      setState(152);
      _errHandler->sync(this);

      _la = _input->LA(1);
      if ((((_la & ~ 0x3fULL) == 0) &&
        ((1ULL << _la) & 120317808640) != 0)) {
        setState(151);
        expr(0);
      }
      setState(154);
      match(sysy2022Parser::T__4);
      break;
    }

    default:
      break;
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ExprContext ------------------------------------------------------------------

sysy2022Parser::ExprContext::ExprContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}


size_t sysy2022Parser::ExprContext::getRuleIndex() const {
  return sysy2022Parser::RuleExpr;
}

void sysy2022Parser::ExprContext::copyFrom(ExprContext *ctx) {
  ParserRuleContext::copyFrom(ctx);
}

//----------------- AddSubExprContext ------------------------------------------------------------------

std::vector<sysy2022Parser::ExprContext *> sysy2022Parser::AddSubExprContext::expr() {
  return getRuleContexts<sysy2022Parser::ExprContext>();
}

sysy2022Parser::ExprContext* sysy2022Parser::AddSubExprContext::expr(size_t i) {
  return getRuleContext<sysy2022Parser::ExprContext>(i);
}

sysy2022Parser::AddSubExprContext::AddSubExprContext(ExprContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::AddSubExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitAddSubExpr(this);
  else
    return visitor->visitChildren(this);
}
//----------------- MulDivModExprContext ------------------------------------------------------------------

std::vector<sysy2022Parser::ExprContext *> sysy2022Parser::MulDivModExprContext::expr() {
  return getRuleContexts<sysy2022Parser::ExprContext>();
}

sysy2022Parser::ExprContext* sysy2022Parser::MulDivModExprContext::expr(size_t i) {
  return getRuleContext<sysy2022Parser::ExprContext>(i);
}

sysy2022Parser::MulDivModExprContext::MulDivModExprContext(ExprContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::MulDivModExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitMulDivModExpr(this);
  else
    return visitor->visitChildren(this);
}
//----------------- SubUnaryExprContext ------------------------------------------------------------------

sysy2022Parser::UnaryExprContext* sysy2022Parser::SubUnaryExprContext::unaryExpr() {
  return getRuleContext<sysy2022Parser::UnaryExprContext>(0);
}

sysy2022Parser::SubUnaryExprContext::SubUnaryExprContext(ExprContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::SubUnaryExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitSubUnaryExpr(this);
  else
    return visitor->visitChildren(this);
}

sysy2022Parser::ExprContext* sysy2022Parser::expr() {
   return expr(0);
}

sysy2022Parser::ExprContext* sysy2022Parser::expr(int precedence) {
  ParserRuleContext *parentContext = _ctx;
  size_t parentState = getState();
  sysy2022Parser::ExprContext *_localctx = _tracker.createInstance<ExprContext>(_ctx, parentState);
  sysy2022Parser::ExprContext *previousContext = _localctx;
  (void)previousContext; // Silence compiler, in case the context is not used by generated code.
  size_t startState = 16;
  enterRecursionRule(_localctx, 16, sysy2022Parser::RuleExpr, precedence);

    size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    unrollRecursionContexts(parentContext);
  });
  try {
    size_t alt;
    enterOuterAlt(_localctx, 1);
    _localctx = _tracker.createInstance<SubUnaryExprContext>(_localctx);
    _ctx = _localctx;
    previousContext = _localctx;

    setState(158);
    unaryExpr();
    _ctx->stop = _input->LT(-1);
    setState(168);
    _errHandler->sync(this);
    alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 21, _ctx);
    while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER) {
      if (alt == 1) {
        if (!_parseListeners.empty())
          triggerExitRuleEvent();
        previousContext = _localctx;
        setState(166);
        _errHandler->sync(this);
        switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 20, _ctx)) {
        case 1: {
          auto newContext = _tracker.createInstance<MulDivModExprContext>(_tracker.createInstance<ExprContext>(parentContext, parentState));
          _localctx = newContext;
          pushNewRecursionContext(newContext, startState, RuleExpr);
          setState(160);

          if (!(precpred(_ctx, 2))) throw FailedPredicateException(this, "precpred(_ctx, 2)");
          setState(161);
          antlrcpp::downCast<MulDivModExprContext *>(_localctx)->op = _input->LT(1);
          _la = _input->LA(1);
          if (!((((_la & ~ 0x3fULL) == 0) &&
            ((1ULL << _la) & 7340032) != 0))) {
            antlrcpp::downCast<MulDivModExprContext *>(_localctx)->op = _errHandler->recoverInline(this);
          }
          else {
            _errHandler->reportMatch(this);
            consume();
          }
          setState(162);
          expr(3);
          break;
        }

        case 2: {
          auto newContext = _tracker.createInstance<AddSubExprContext>(_tracker.createInstance<ExprContext>(parentContext, parentState));
          _localctx = newContext;
          pushNewRecursionContext(newContext, startState, RuleExpr);
          setState(163);

          if (!(precpred(_ctx, 1))) throw FailedPredicateException(this, "precpred(_ctx, 1)");
          setState(164);
          antlrcpp::downCast<AddSubExprContext *>(_localctx)->op = _input->LT(1);
          _la = _input->LA(1);
          if (!(_la == sysy2022Parser::T__22

          || _la == sysy2022Parser::T__23)) {
            antlrcpp::downCast<AddSubExprContext *>(_localctx)->op = _errHandler->recoverInline(this);
          }
          else {
            _errHandler->reportMatch(this);
            consume();
          }
          setState(165);
          expr(2);
          break;
        }

        default:
          break;
        } 
      }
      setState(170);
      _errHandler->sync(this);
      alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 21, _ctx);
    }
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }
  return _localctx;
}

//----------------- UnaryExprContext ------------------------------------------------------------------

sysy2022Parser::UnaryExprContext::UnaryExprContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}


size_t sysy2022Parser::UnaryExprContext::getRuleIndex() const {
  return sysy2022Parser::RuleUnaryExpr;
}

void sysy2022Parser::UnaryExprContext::copyFrom(UnaryExprContext *ctx) {
  ParserRuleContext::copyFrom(ctx);
}

//----------------- RecUnaryExprContext ------------------------------------------------------------------

sysy2022Parser::UnaryExprContext* sysy2022Parser::RecUnaryExprContext::unaryExpr() {
  return getRuleContext<sysy2022Parser::UnaryExprContext>(0);
}

sysy2022Parser::RecUnaryExprContext::RecUnaryExprContext(UnaryExprContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::RecUnaryExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitRecUnaryExpr(this);
  else
    return visitor->visitChildren(this);
}
//----------------- IntLiteralContext ------------------------------------------------------------------

tree::TerminalNode* sysy2022Parser::IntLiteralContext::INT() {
  return getToken(sysy2022Parser::INT, 0);
}

sysy2022Parser::IntLiteralContext::IntLiteralContext(UnaryExprContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::IntLiteralContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitIntLiteral(this);
  else
    return visitor->visitChildren(this);
}
//----------------- FloatLiteralContext ------------------------------------------------------------------

tree::TerminalNode* sysy2022Parser::FloatLiteralContext::FLOAT() {
  return getToken(sysy2022Parser::FLOAT, 0);
}

sysy2022Parser::FloatLiteralContext::FloatLiteralContext(UnaryExprContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::FloatLiteralContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitFloatLiteral(this);
  else
    return visitor->visitChildren(this);
}
//----------------- LValExprContext ------------------------------------------------------------------

sysy2022Parser::LValContext* sysy2022Parser::LValExprContext::lVal() {
  return getRuleContext<sysy2022Parser::LValContext>(0);
}

sysy2022Parser::LValExprContext::LValExprContext(UnaryExprContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::LValExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitLValExpr(this);
  else
    return visitor->visitChildren(this);
}
//----------------- FuncCallContext ------------------------------------------------------------------

tree::TerminalNode* sysy2022Parser::FuncCallContext::ID() {
  return getToken(sysy2022Parser::ID, 0);
}

std::vector<sysy2022Parser::ExprContext *> sysy2022Parser::FuncCallContext::expr() {
  return getRuleContexts<sysy2022Parser::ExprContext>();
}

sysy2022Parser::ExprContext* sysy2022Parser::FuncCallContext::expr(size_t i) {
  return getRuleContext<sysy2022Parser::ExprContext>(i);
}

sysy2022Parser::FuncCallContext::FuncCallContext(UnaryExprContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::FuncCallContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitFuncCall(this);
  else
    return visitor->visitChildren(this);
}
//----------------- ParenExprContext ------------------------------------------------------------------

sysy2022Parser::ExprContext* sysy2022Parser::ParenExprContext::expr() {
  return getRuleContext<sysy2022Parser::ExprContext>(0);
}

sysy2022Parser::ParenExprContext::ParenExprContext(UnaryExprContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::ParenExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitParenExpr(this);
  else
    return visitor->visitChildren(this);
}
sysy2022Parser::UnaryExprContext* sysy2022Parser::unaryExpr() {
  UnaryExprContext *_localctx = _tracker.createInstance<UnaryExprContext>(_ctx, getState());
  enterRule(_localctx, 18, sysy2022Parser::RuleUnaryExpr);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(193);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 24, _ctx)) {
    case 1: {
      _localctx = _tracker.createInstance<sysy2022Parser::ParenExprContext>(_localctx);
      enterOuterAlt(_localctx, 1);
      setState(171);
      match(sysy2022Parser::T__11);
      setState(172);
      expr(0);
      setState(173);
      match(sysy2022Parser::T__12);
      break;
    }

    case 2: {
      _localctx = _tracker.createInstance<sysy2022Parser::LValExprContext>(_localctx);
      enterOuterAlt(_localctx, 2);
      setState(175);
      lVal();
      break;
    }

    case 3: {
      _localctx = _tracker.createInstance<sysy2022Parser::IntLiteralContext>(_localctx);
      enterOuterAlt(_localctx, 3);
      setState(176);
      match(sysy2022Parser::INT);
      break;
    }

    case 4: {
      _localctx = _tracker.createInstance<sysy2022Parser::FloatLiteralContext>(_localctx);
      enterOuterAlt(_localctx, 4);
      setState(177);
      match(sysy2022Parser::FLOAT);
      break;
    }

    case 5: {
      _localctx = _tracker.createInstance<sysy2022Parser::FuncCallContext>(_localctx);
      enterOuterAlt(_localctx, 5);
      setState(178);
      match(sysy2022Parser::ID);
      setState(179);
      match(sysy2022Parser::T__11);
      setState(188);
      _errHandler->sync(this);

      _la = _input->LA(1);
      if ((((_la & ~ 0x3fULL) == 0) &&
        ((1ULL << _la) & 120317808640) != 0)) {
        setState(180);
        expr(0);
        setState(185);
        _errHandler->sync(this);
        _la = _input->LA(1);
        while (_la == sysy2022Parser::T__3) {
          setState(181);
          match(sysy2022Parser::T__3);
          setState(182);
          expr(0);
          setState(187);
          _errHandler->sync(this);
          _la = _input->LA(1);
        }
      }
      setState(190);
      match(sysy2022Parser::T__12);
      break;
    }

    case 6: {
      _localctx = _tracker.createInstance<sysy2022Parser::RecUnaryExprContext>(_localctx);
      enterOuterAlt(_localctx, 6);
      setState(191);
      antlrcpp::downCast<RecUnaryExprContext *>(_localctx)->op = _input->LT(1);
      _la = _input->LA(1);
      if (!((((_la & ~ 0x3fULL) == 0) &&
        ((1ULL << _la) & 58720256) != 0))) {
        antlrcpp::downCast<RecUnaryExprContext *>(_localctx)->op = _errHandler->recoverInline(this);
      }
      else {
        _errHandler->reportMatch(this);
        consume();
      }
      setState(192);
      unaryExpr();
      break;
    }

    default:
      break;
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- LValContext ------------------------------------------------------------------

sysy2022Parser::LValContext::LValContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* sysy2022Parser::LValContext::ID() {
  return getToken(sysy2022Parser::ID, 0);
}

std::vector<sysy2022Parser::ExprContext *> sysy2022Parser::LValContext::expr() {
  return getRuleContexts<sysy2022Parser::ExprContext>();
}

sysy2022Parser::ExprContext* sysy2022Parser::LValContext::expr(size_t i) {
  return getRuleContext<sysy2022Parser::ExprContext>(i);
}


size_t sysy2022Parser::LValContext::getRuleIndex() const {
  return sysy2022Parser::RuleLVal;
}


std::any sysy2022Parser::LValContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitLVal(this);
  else
    return visitor->visitChildren(this);
}

sysy2022Parser::LValContext* sysy2022Parser::lVal() {
  LValContext *_localctx = _tracker.createInstance<LValContext>(_ctx, getState());
  enterRule(_localctx, 20, sysy2022Parser::RuleLVal);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    size_t alt;
    enterOuterAlt(_localctx, 1);
    setState(195);
    match(sysy2022Parser::ID);
    setState(202);
    _errHandler->sync(this);
    alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 25, _ctx);
    while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER) {
      if (alt == 1) {
        setState(196);
        match(sysy2022Parser::T__5);
        setState(197);
        expr(0);
        setState(198);
        match(sysy2022Parser::T__6); 
      }
      setState(204);
      _errHandler->sync(this);
      alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 25, _ctx);
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- CondContext ------------------------------------------------------------------

sysy2022Parser::CondContext::CondContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}


size_t sysy2022Parser::CondContext::getRuleIndex() const {
  return sysy2022Parser::RuleCond;
}

void sysy2022Parser::CondContext::copyFrom(CondContext *ctx) {
  ParserRuleContext::copyFrom(ctx);
}

//----------------- EqExprContext ------------------------------------------------------------------

std::vector<sysy2022Parser::CondContext *> sysy2022Parser::EqExprContext::cond() {
  return getRuleContexts<sysy2022Parser::CondContext>();
}

sysy2022Parser::CondContext* sysy2022Parser::EqExprContext::cond(size_t i) {
  return getRuleContext<sysy2022Parser::CondContext>(i);
}

sysy2022Parser::EqExprContext::EqExprContext(CondContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::EqExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitEqExpr(this);
  else
    return visitor->visitChildren(this);
}
//----------------- BinaryExprContext ------------------------------------------------------------------

sysy2022Parser::ExprContext* sysy2022Parser::BinaryExprContext::expr() {
  return getRuleContext<sysy2022Parser::ExprContext>(0);
}

sysy2022Parser::BinaryExprContext::BinaryExprContext(CondContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::BinaryExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitBinaryExpr(this);
  else
    return visitor->visitChildren(this);
}
//----------------- OrExprContext ------------------------------------------------------------------

std::vector<sysy2022Parser::CondContext *> sysy2022Parser::OrExprContext::cond() {
  return getRuleContexts<sysy2022Parser::CondContext>();
}

sysy2022Parser::CondContext* sysy2022Parser::OrExprContext::cond(size_t i) {
  return getRuleContext<sysy2022Parser::CondContext>(i);
}

sysy2022Parser::OrExprContext::OrExprContext(CondContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::OrExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitOrExpr(this);
  else
    return visitor->visitChildren(this);
}
//----------------- RelExprContext ------------------------------------------------------------------

std::vector<sysy2022Parser::CondContext *> sysy2022Parser::RelExprContext::cond() {
  return getRuleContexts<sysy2022Parser::CondContext>();
}

sysy2022Parser::CondContext* sysy2022Parser::RelExprContext::cond(size_t i) {
  return getRuleContext<sysy2022Parser::CondContext>(i);
}

sysy2022Parser::RelExprContext::RelExprContext(CondContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::RelExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitRelExpr(this);
  else
    return visitor->visitChildren(this);
}
//----------------- AndExprContext ------------------------------------------------------------------

std::vector<sysy2022Parser::CondContext *> sysy2022Parser::AndExprContext::cond() {
  return getRuleContexts<sysy2022Parser::CondContext>();
}

sysy2022Parser::CondContext* sysy2022Parser::AndExprContext::cond(size_t i) {
  return getRuleContext<sysy2022Parser::CondContext>(i);
}

sysy2022Parser::AndExprContext::AndExprContext(CondContext *ctx) { copyFrom(ctx); }


std::any sysy2022Parser::AndExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitAndExpr(this);
  else
    return visitor->visitChildren(this);
}

sysy2022Parser::CondContext* sysy2022Parser::cond() {
   return cond(0);
}

sysy2022Parser::CondContext* sysy2022Parser::cond(int precedence) {
  ParserRuleContext *parentContext = _ctx;
  size_t parentState = getState();
  sysy2022Parser::CondContext *_localctx = _tracker.createInstance<CondContext>(_ctx, parentState);
  sysy2022Parser::CondContext *previousContext = _localctx;
  (void)previousContext; // Silence compiler, in case the context is not used by generated code.
  size_t startState = 22;
  enterRecursionRule(_localctx, 22, sysy2022Parser::RuleCond, precedence);

    size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    unrollRecursionContexts(parentContext);
  });
  try {
    size_t alt;
    enterOuterAlt(_localctx, 1);
    _localctx = _tracker.createInstance<BinaryExprContext>(_localctx);
    _ctx = _localctx;
    previousContext = _localctx;

    setState(206);
    expr(0);
    _ctx->stop = _input->LT(-1);
    setState(222);
    _errHandler->sync(this);
    alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 27, _ctx);
    while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER) {
      if (alt == 1) {
        if (!_parseListeners.empty())
          triggerExitRuleEvent();
        previousContext = _localctx;
        setState(220);
        _errHandler->sync(this);
        switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 26, _ctx)) {
        case 1: {
          auto newContext = _tracker.createInstance<RelExprContext>(_tracker.createInstance<CondContext>(parentContext, parentState));
          _localctx = newContext;
          pushNewRecursionContext(newContext, startState, RuleCond);
          setState(208);

          if (!(precpred(_ctx, 4))) throw FailedPredicateException(this, "precpred(_ctx, 4)");
          setState(209);
          antlrcpp::downCast<RelExprContext *>(_localctx)->op = _input->LT(1);
          _la = _input->LA(1);
          if (!((((_la & ~ 0x3fULL) == 0) &&
            ((1ULL << _la) & 1006632960) != 0))) {
            antlrcpp::downCast<RelExprContext *>(_localctx)->op = _errHandler->recoverInline(this);
          }
          else {
            _errHandler->reportMatch(this);
            consume();
          }
          setState(210);
          cond(5);
          break;
        }

        case 2: {
          auto newContext = _tracker.createInstance<EqExprContext>(_tracker.createInstance<CondContext>(parentContext, parentState));
          _localctx = newContext;
          pushNewRecursionContext(newContext, startState, RuleCond);
          setState(211);

          if (!(precpred(_ctx, 3))) throw FailedPredicateException(this, "precpred(_ctx, 3)");
          setState(212);
          antlrcpp::downCast<EqExprContext *>(_localctx)->op = _input->LT(1);
          _la = _input->LA(1);
          if (!(_la == sysy2022Parser::T__29

          || _la == sysy2022Parser::T__30)) {
            antlrcpp::downCast<EqExprContext *>(_localctx)->op = _errHandler->recoverInline(this);
          }
          else {
            _errHandler->reportMatch(this);
            consume();
          }
          setState(213);
          cond(4);
          break;
        }

        case 3: {
          auto newContext = _tracker.createInstance<AndExprContext>(_tracker.createInstance<CondContext>(parentContext, parentState));
          _localctx = newContext;
          pushNewRecursionContext(newContext, startState, RuleCond);
          setState(214);

          if (!(precpred(_ctx, 2))) throw FailedPredicateException(this, "precpred(_ctx, 2)");
          setState(215);
          antlrcpp::downCast<AndExprContext *>(_localctx)->op = match(sysy2022Parser::T__31);
          setState(216);
          cond(3);
          break;
        }

        case 4: {
          auto newContext = _tracker.createInstance<OrExprContext>(_tracker.createInstance<CondContext>(parentContext, parentState));
          _localctx = newContext;
          pushNewRecursionContext(newContext, startState, RuleCond);
          setState(217);

          if (!(precpred(_ctx, 1))) throw FailedPredicateException(this, "precpred(_ctx, 1)");
          setState(218);
          antlrcpp::downCast<OrExprContext *>(_localctx)->op = match(sysy2022Parser::T__32);
          setState(219);
          cond(2);
          break;
        }

        default:
          break;
        } 
      }
      setState(224);
      _errHandler->sync(this);
      alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 27, _ctx);
    }
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }
  return _localctx;
}

//----------------- ConstExprContext ------------------------------------------------------------------

sysy2022Parser::ConstExprContext::ConstExprContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

sysy2022Parser::ExprContext* sysy2022Parser::ConstExprContext::expr() {
  return getRuleContext<sysy2022Parser::ExprContext>(0);
}


size_t sysy2022Parser::ConstExprContext::getRuleIndex() const {
  return sysy2022Parser::RuleConstExpr;
}


std::any sysy2022Parser::ConstExprContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<sysy2022Visitor*>(visitor))
    return parserVisitor->visitConstExpr(this);
  else
    return visitor->visitChildren(this);
}

sysy2022Parser::ConstExprContext* sysy2022Parser::constExpr() {
  ConstExprContext *_localctx = _tracker.createInstance<ConstExprContext>(_ctx, getState());
  enterRule(_localctx, 24, sysy2022Parser::RuleConstExpr);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(225);
    expr(0);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

bool sysy2022Parser::sempred(RuleContext *context, size_t ruleIndex, size_t predicateIndex) {
  switch (ruleIndex) {
    case 8: return exprSempred(antlrcpp::downCast<ExprContext *>(context), predicateIndex);
    case 11: return condSempred(antlrcpp::downCast<CondContext *>(context), predicateIndex);

  default:
    break;
  }
  return true;
}

bool sysy2022Parser::exprSempred(ExprContext *_localctx, size_t predicateIndex) {
  switch (predicateIndex) {
    case 0: return precpred(_ctx, 2);
    case 1: return precpred(_ctx, 1);

  default:
    break;
  }
  return true;
}

bool sysy2022Parser::condSempred(CondContext *_localctx, size_t predicateIndex) {
  switch (predicateIndex) {
    case 2: return precpred(_ctx, 4);
    case 3: return precpred(_ctx, 3);
    case 4: return precpred(_ctx, 2);
    case 5: return precpred(_ctx, 1);

  default:
    break;
  }
  return true;
}

void sysy2022Parser::initialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  sysy2022ParserInitialize();
#else
  ::antlr4::internal::call_once(sysy2022ParserOnceFlag, sysy2022ParserInitialize);
#endif
}
