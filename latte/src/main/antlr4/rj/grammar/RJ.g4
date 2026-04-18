grammar RJ;

prog: expression EOF;

expression: logicalOrExp;

logicalOrExp
	: logicalAndExp (OR logicalAndExp)*
	;

logicalAndExp
	: equalityExp (AND equalityExp)*
	;

equalityExp
	: relationalExp ((EQ | NEQ) relationalExp)?
	;

relationalExp
	: additiveExp ((LT | GT | LE | GE) additiveExp)?
	;

additiveExp
	: unaryExp ((PLUS | MINUS) unaryExp)*
	;

unaryExp
	: (NOT | MINUS) unaryExp
	| primary
	;

primary
	: literal
	| RESULT
	| oldExp
	| fieldAccess
	| ID
	| LPAREN expression RPAREN
	;

oldExp
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
