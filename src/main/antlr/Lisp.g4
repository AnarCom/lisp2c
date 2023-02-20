grammar Lisp;
/*------------------------------------------------------------------
 * A very basic implementation of a Lisp grammar.
 *------------------------------------------------------------------*/

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/
program : (expressions+=expression)* EOF ;
//argument: identifier | string | integer | boolean | expression;
//expression: OP identifier (identifier | string | integer | boolean | expression)* CP;
expression:
    defun_expression |
    call_expression |
    if_expression |
    identifier_expression |
    integer_expression;

defun_expression: OP 'defun' name=identifier_expression '[' (args+=identifier_expression (',' args+=identifier_expression)*)? ']' body=expression CP;
if_expression: OP 'if' condition=expression ifTrue=expression ifFalse=expression CP;
call_expression: OP fn=expression (args+=expression)* CP;

identifier_expression: name=IDENTIFIER;
string_expression: value=STRING;
integer_expression: value=NUMBER;
boolean_expression: value=(TRUE|FALSE);

//identifier: IDENTIFIER;
//string: STRING;
//integer: NUMBER;
//boolean: TRUE | FALSE;

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
