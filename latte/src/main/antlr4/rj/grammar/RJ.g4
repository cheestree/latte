grammar RJ;

prog: exp EOF;

exp: (NOT | MINUS) exp
   | exp (STAR | SLASH | PERCENT) exp
   | exp (PLUS | MINUS) exp
   | exp (LT | GT | LE | GE) exp
   | exp (EQ | NEQ) exp
   | exp AND exp
   | exp OR exp
   | primary
   ;

primary
	: literal
	| RESULT
	| functionCall
	| fieldAccess
	| ID
	| LPAREN exp RPAREN
	;

functionCall
    : ID LPAREN args? RPAREN
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

args
    : exp (',' exp)*
    ;

RESULT: 'result';

AND: '&&';
OR: '||';
EQ: '==';
LE: '<=';
LT: '<';
GE: '>=';
GT: '>';
STAR    : '*';
SLASH   : '/';
PERCENT : '%';
PLUS: '+';
MINUS: '-';
NEQ: '!=';
NOT: '!';

LPAREN: '(';
RPAREN: ')';
DOT: '.';

BOOL: 'true' | 'false';
ID: [a-zA-Z_][a-zA-Z0-9_#]*;
STRING: '"' (~["])* '"';
INT: [0-9]+ ('_' [0-9]+)*;
REAL: ([0-9]+ '.' [0-9]+) | ('.' [0-9]+);

WS: [ \t\n\r]+ -> channel(HIDDEN);
