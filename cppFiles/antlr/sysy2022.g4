// $antlr-format reflowComments false
// $antlr-format off

// antlr语法示例: https://github.com/antlr/grammars-v4
// antlr语法示例 - c: https://github.com/antlr/grammars-v4/blob/master/c/C.g4
// antlr语法示例 - c++: https://github.com/antlr/grammars-v4/tree/master/cpp

grammar sysy2022;

//===----------------------------------------------------------------------===//
// 语法部分
//===----------------------------------------------------------------------===//
// 编译单元
// ! 可以以任何顺序出现
// 语义约束：
// 1. 一个sy程序有且仅有一个main函数 需要输出其返回值
// 2. 顶层标识符不允许重名 -> 可以共用一个符号表
compUnit
	: (varDecl | funcDef)* EOF
	;

//===----------------------------------------------------------------------===//
// 声明部分
//===----------------------------------------------------------------------===//
// 变量声明&常量声明
// 语义约束：
// 1. 只要有初始化列表都会隐式初始化 全局变量隐式初始化
// 2. 初始化列表元素类型与数组类型一致 变量或常量初值可以隐式转换
varDecl
	: isConst='const'? basicType=('int' | 'float') varDef (',' varDef)* ';'
	;

// 变量定义
varDef
	: ID ('[' constExpr ']')* ('=' initVal)?
	;

// 变量初值
initVal
	: expr
	| '{' (initVal (',' initVal)*)? '}'
	;

//===----------------------------------------------------------------------===//
// 函数部分
//===----------------------------------------------------------------------===//
// 函数定义
// 语义约束：
// 1. 保证返回值为void时 return语句不带返回值
funcDef
	: funcType=('void' | 'int' | 'float') ID '(' (funcFParam (',' funcFParam)*)? ')' block
	;

// 函数形参
// 语义约束：
// 1. 数组传地址
// 2. 多维数组可以部分传递
funcFParam
	: basicType=('int' | 'float') ID isArrayRef='['? ']'? ('[' expr ']')*
	;

// 语句块
block
	: '{' (varDecl | stmt)* '}'
	;

// 语句
stmt
	: lVal '=' expr ';' 					#assignStmt		// 赋值语句
	| expr? ';' 							#exprStmt		// 表达式语句 // ! 表达式语句并没有任何实际作用
	| block 								#blockStmt		// 语句块
	| 'if' '(' cond ')' stmt ('else' stmt)? #ifStmt			// if语句 // ! 就近匹配
	| 'while' '(' cond ')' stmt 			#whileStmt		// while语句
	| 'break' ';' 							#breakStmt		// break语句
	| 'continue' ';' 						#continueStmt	// conitinue语句
	| 'return' expr? ';' 					#returnStmt		// return语句
	;

//===----------------------------------------------------------------------===//
// 表达式部分
//===----------------------------------------------------------------------===//
// 所支持的所有运算符/表达式(按照优先级)：
// "基础表达式"：括号() 变量引用ID 数组变量引用ID[] 字面量Number
// 一元运算符：+ - ! 函数调用()
// 二元运算符：* / %    	+ -
// 关系运算符：> >= < <= 	== !=
// 逻辑运算符：&& 			||
// 赋值运算符(忽略)：=
// 注：
// 1. 关系表达式中可以出现算术表达式 非0为真 0为假 按照语法规则 算术表达式中并不会出现关系表达式 比如不会出现'!'
// 2. 变量引用ID和数组变量引用ID[]称为"左值" 只有左值可以被赋值 只有左值可以出现在赋值语句左侧 语义约束保证是变量
// 3. 和c语言不同 赋值表达式实际上是赋值语句 是一种语句而非表达式 故赋值语句没有"值"的属性

// 表达式
expr
	: unaryExpr 						#subUnaryExpr	// 一元表达式
	| expr op=('*' | '/' | '%') expr	#mulDivModExpr	// 乘除取余表达式
	| expr op=('+' | '-') expr			#addSubExpr		// 加减表达式
	;

// 一元表达式
unaryExpr
	: '(' expr ')' 						#parenExpr		// 括号表达式
	| lVal								#lValExpr		// 左值表达式
	| INT								#intLiteral		// int字面量
	| FLOAT								#floatLiteral	// float字面量
	| ID '(' (expr (',' expr)*)? ')'	#funcCall		// 函数调用
	| op=('+' | '-' | '!') unaryExpr	#recUnaryExpr	// 一元表达式
	;

// 左值表达式
// 语义约束：
// 数组保证定位到元素
lVal
	: ID ('[' expr ']')*
	;

//===----------------------------------------------------------------------===//
// 条件表达式
//===----------------------------------------------------------------------===//
// 条件表达式
// 语义约束：
// 逻辑运算符具有短路特性
cond
	: expr										#binaryExpr	// 二元表达式
	| cond op=('<' | '>' | '<=' | '>=') cond	#relExpr	// 关系表达式
	| cond op=('==' | '!=') cond				#eqExpr		// 相等表达式
	| cond op='&&' cond							#andExpr	// 逻辑与表达式
	| cond op='||' cond							#orExpr		// 逻辑或表达式
	;

//===----------------------------------------------------------------------===//
// 常量表达式
//===----------------------------------------------------------------------===//
// 为什么要单独有一个常量表达式ConstExpr？
// 可能是为了多一个结点方便进行常量检查 给语义检查留空间 可以不用管

// 常量表达式
// 语义约束：
// 1. 作为初始化列表时保证可以求值到int或float
// 2. 作为数组维数时保证可以求值到非负整数
constExpr
	: expr
	;

//===----------------------------------------------------------------------===//
// 词法部分
//===----------------------------------------------------------------------===//
// 标识符
// 语义约束：
// 最近作用域优先
// 可以和函数名重名 -> 使用不同的符号表
ID
	: [a-zA-Z_][a-zA-Z_0-9]*
	;

// 整数
// 注：整数和浮点数不作为"fragment" 认为是两种不同的Number 以便类型检查
INT
    : [1-9] [0-9]*
    | '0' [0-7]*
    | '0' [xX] [0-9a-fA-F]+
    ;

// 浮点数
FLOAT
    : DecimalFloatingConstant
    | HexadecimalFloatingConstant
    ;

fragment DecimalFloatingConstant
    : ([0-9]+)? '.' [0-9]+ ExponentPart?
	| [0-9]+ '.' ExponentPart?
    | [0-9]+ ExponentPart
    ;

fragment ExponentPart
    : [eE] [+-]? [0-9]+
    ;

fragment HexadecimalFloatingConstant
    : '0' [xX] ([0-9a-fA-F]+)? '.' [0-9a-fA-F]+ BinaryExponentPart
	| '0' [xX] [0-9a-fA-F]+ '.' BinaryExponentPart
    | '0' [xX] [0-9a-fA-F]+ BinaryExponentPart
	;

fragment BinaryExponentPart
    : [pP] [+-]? [0-9]+
    ;

//===----------------------------------------------------------------------===//
// 注释部分
//===----------------------------------------------------------------------===//
// 块注释
BlockComment
    : '/*' .*? '*/' -> channel(HIDDEN)
    ;

// 行注释
LineComment
    : '//' ~[\r\n]* -> channel(HIDDEN)
    ;

// 空白符
Blank
    : [ \t\r\n]+ -> channel(HIDDEN)
    ;
