grammar RJ;

prog: exp EOF;

exp: 
   (NOT | MINUS) exp					# ExpUnary
   | exp (STAR | SLASH | PERCENT) exp	# ExpMult
   | exp (PLUS | MINUS) exp				# ExpAdd
   | exp (LT | GT | LE | GE) exp		# ExpRel
   | exp (EQ | NEQ) exp					# ExpEq
   | exp AND exp						# ExpAnd
   | exp OR exp							# ExpOr
   | primary							# ExpPrim
   ;

primary
	: literal							# PrimLit
	| RETURN							# PrimRet
	| functionCall						# PrimFunCall
	| fieldAccess						# PrimFieldAcc
	| ID								# PrimId
	| LPAREN exp RPAREN					# PrimParen
	;

functionCall							
    : ID LPAREN args? RPAREN			# ExpFunCall
    ;

fieldAccess
	: ID DOT ID							# ExpFieldAcc
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

RETURN: 'return';

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
