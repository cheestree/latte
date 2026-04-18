grammar RJ;

prog: expression EOF;

expression: logicalOrExpression;

logicalOrExpression
	: logicalAndExpression (OR logicalAndExpression)*
	;

logicalAndExpression
	: equalityExpression (AND equalityExpression)*
	;

equalityExpression
	: relationalExpression ((EQ | NEQ) relationalExpression)?
	;

relationalExpression
	: additiveExpression ((LT | GT | LE | GE) additiveExpression)?
	;

additiveExpression
	: unaryExpression ((PLUS | MINUS) unaryExpression)*
	;

unaryExpression
	: (NOT | MINUS) unaryExpression
	| primary
	;

primary
	: literal
	| RESULT
	| oldExpression
	| fieldAccess
	| ID
	| LPAREN expression RPAREN
	;

oldExpression
	: OLD LPAREN fieldAccess RPAREN
	;

fieldAccess
	: ID DOT ID
	;

literal
	: BOOL
	| STRING
	| INT
	| REAL
	;

OLD: 'old';
RESULT: 'result';

AND: '&&';
OR: '||';
EQ: '==';
LE: '<=';
LT: '<';
GE: '>=';
GT: '>';
PLUS: '+';
MINUS: '-';
NEQ: '!=';
NOT: '!';

LPAREN: '(';
RPAREN: ')';
DOT: '.';

BOOL: 'true' | 'false';
ID: '#'* [a-zA-Z_][a-zA-Z0-9_#]*;
STRING: '"' (~["])* '"';
INT: [0-9]+ ('_' [0-9]+)*;
REAL: ([0-9]+ '.' [0-9]+) | ('.' [0-9]+);

WS: [ \t\n\r]+ -> channel(HIDDEN);
