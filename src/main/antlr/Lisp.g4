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
    boolean_expression |
    identifier_expression |
    integer_expression |
    defunc_expression |
    fn_expression |
    recur_expression |
    do_expression |
    macro_expand_expression |

    string_expression;

defun_expression: OP 'defun' name=identifier_expression '[' (args+=identifier_expression)* ']' body=expression CP;
fn_expression: OP 'fn' '[' (args+=identifier_expression)* ']' body=expression CP;
if_expression: OP 'if' condition=expression ifTrue=expression ifFalse=expression CP;
defunc_expression: OP 'defunc' name=identifier_expression '[' (args+=identifier_expression)* ']' body=HEREDOC CP;
recur_expression: OP 'recur' (args+=expression)* CP;
do_expression: OP 'do' (args+=expression)* CP;
macro_expand_expression: OP name=identifier_expression '!' (args+=simplified_expression)* CP;
call_expression: OP fn=expression (args+=expression)* CP;

string_expression: value=STRING;
integer_expression: value=NUMBER;
boolean_expression: value=(TRUE|FALSE);
identifier_expression: name=IDENTIFIER;



simplified_expression: simplified_square_expression | simplified_round_expression | simplified_expression_arg;
simplified_square_expression: '[' (args+=simplified_expression)* ']';
simplified_round_expression: '(' (args+=simplified_expression)* ')';

simplified_expression_arg: IDENTIFIER | STRING | NUMBER | TRUE | FALSE | '!' | 'fn' | 'if' | 'recur' | 'do';
//identifier: IDENTIFIER;
//string: STRING;
//integer: NUMBER;
//boolean: TRUE | FALSE;

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/
TRUE: 'true';
FALSE: 'false';
IDENTIFIER : ((LETTER (LETTER | DIGIT)*) | PLUS | MINUS | MULT | DIV | EQ) ;

PLUS : '+';
MINUS : '-';
MULT : '*';
DIV : '/';
EQ : '=';
OP : '(';
CP : ')';

STRING : '"'~["\\\n]*(('\\'.)~["\\\n]*)*'"' ;

NUMBER : (DIGIT)+ ;

WHITESPACE : [ \r\n\t] + -> channel (HIDDEN);

DIGIT : '0'..'9';

LETTER : LOWER | UPPER | '_' ;

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