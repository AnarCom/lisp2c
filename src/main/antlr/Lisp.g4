grammar Lisp;

@lexer::members {
  private boolean heredocEndAhead(String partialHeredoc) {
    if (this.getCharPositionInLine() != 0) {
      // If the lexer is not at the start of a line, no end-delimiter can be possible
      return false;
    }

    // Get the delimiter
      String[] lines = partialHeredoc.split("\r?\n|\r");
    String firstLine = lines[0];
    String delimiter = firstLine.replaceAll("^<<-?\\s*", "");
    if(lines.length > 1 && lines[lines.length - 1].equals(delimiter)){
        return true;
    }
    return false;
  }
}

program : (expressions+=expression)* EOF ;
//argument: identifier | string | integer | boolean | expression;
//expression: OP identifier (identifier | string | integer | boolean | expression)* CP;
expression:
    defun_expression |
    call_expression |
    if_expression |
    identifier_expression |
    integer_expression |
    defunc_expression |
    fn_expression;

defun_expression: OP 'defun' name=identifier_expression '[' (args+=identifier_expression (',' args+=identifier_expression)*)? ']' body=expression CP;
fn_expression: OP 'fn' '[' (args+=identifier_expression (',' args+=identifier_expression)*)? ']' body=expression CP;
if_expression: OP 'if' condition=expression ifTrue=expression ifFalse=expression CP;
defunc_expression: OP 'defunc' name=identifier_expression '[' (args+=identifier_expression (',' args+=identifier_expression)*)? ']' body=HEREDOC CP;
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

HEREDOC
 : '<<'[a-zA-Z_][a-zA-Z_0-9]* NL ( {!heredocEndAhead(getText())}? . )* [a-zA-Z_][a-zA-Z_0-9]*
 ;

ANY
 : .
 ;

fragment NL
 : '\r'? '\n'
 | '\r'
 ;