grammar Lisp;
/*------------------------------------------------------------------
 * A very basic implementation of a Lisp grammar.
 *------------------------------------------------------------------*/

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/
program : (expressions+=expression)* EOF ;
argument: identifier | string | integer | boolean | expression;
//expression: OP identifier (identifier | string | integer | boolean | expression)* CP;
expression: defun_expression | call_expression | if_expression;

defun_expression: OP 'defun' name=identifier '[' (args+=identifier (',' args+=identifier)*)? ']' body=expression CP;
if_expression: OP 'if' condition=argument ifTrue=argument ifFalse=argument CP;
call_expression: OP fn=identifier (args+=argument)* CP;

identifier: IDENTIFIER;
string: STRING;
integer: NUMBER;
boolean: TRUE | FALSE;

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/
IDENTIFIER : ((LETTER (LETTER | DIGIT)*) | PLUS | MINUS | MULT | DIV | EQ) ;

PLUS : '+';
MINUS : '-';
MULT : '*';
DIV : '/';
EQ : '=';
OP : '(';
CP : ')';
TRUE: 'true';
FALSE: 'false';

STRING : '"'~["\\\n]*(('\\'.)~["\\\n]*)*'"' ;

NUMBER : (DIGIT)+ ;

WHITESPACE : [ \r\n\t] + -> channel (HIDDEN);

DIGIT : '0'..'9';

LETTER : LOWER | UPPER ;

LOWER : ('a'..'z') ;
UPPER : ('A'..'Z') ;
