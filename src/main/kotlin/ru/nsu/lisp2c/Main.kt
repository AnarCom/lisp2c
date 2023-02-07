package ru.nsu.lisp2c

import LispBaseListener
import LispParser
import LispLexer
import LispListener
import LispBaseVisitor
import LispVisitor
import org.antlr.runtime.ANTLRInputStream
import org.antlr.runtime.ANTLRStringStream
import org.antlr.runtime.CharStream
import org.antlr.runtime.tree.ParseTree
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.FileInputStream
import java.io.FileReader
import java.lang.Exception
import java.util.InputMismatchException

//data class Expression()
// Function call: Simple function call
// Defun        : Allowed only on top level
// Fn           : Fuckery with closures
// List         : Create list in C
// If           : Ternary operator
// Let          : I don't know
// Recurse      : Simple


sealed interface Argument
class IntArgument(val v: Int) : Argument
class ExpressionArgument(val v: Expression) : Argument
class IdentifierArgument(val name: String) : Argument

sealed interface Expression
class CallExpression(val name: String, val args: List<Argument>) : Expression
class DefunExpression(val name: String, val arguments: List<IdentifierArgument>, val body: Expression): Expression
class IfExpression(val condition: Argument, val ifTrue: Argument, val ifFalse: Argument): Expression


class ProgramVisitor: LispBaseVisitor<List<Expression>>(){
    override fun visitProgram(ctx: LispParser.ProgramContext): List<Expression> {
        return ctx.expressions.map { ExpressionVisitor().visitExpression(it) }
    }
}

class ArgumentVisitor: LispBaseVisitor<Argument>(){
    override fun visitExpression(ctx: LispParser.ExpressionContext): Argument {
        return ExpressionArgument(ExpressionVisitor().visitExpression(ctx))
    }

    override fun visitIdentifier(ctx: LispParser.IdentifierContext): Argument {
        return IdentifierArgument(ctx.text)
    }

    override fun visitInteger(ctx: LispParser.IntegerContext): Argument {
        return IntArgument(v = ctx.text.toInt())
    }
}

class ExpressionVisitor: LispBaseVisitor<Expression>(){
    override fun visitExpression(ctx: LispParser.ExpressionContext?): Expression {
        return visitChildren(ctx)
    }

    override fun visitDefun_expression(ctx: LispParser.Defun_expressionContext): Expression {
        return DefunExpression(
            name = ctx.name.text,
            arguments = ctx.args.map { ArgumentVisitor().visitIdentifier(it) as IdentifierArgument},
            body = visitExpression(ctx.body)
        )
    }

    override fun visitIf_expression(ctx: LispParser.If_expressionContext): Expression {
        return IfExpression(
            condition = ArgumentVisitor().visitArgument(ctx.condition),
            ifTrue = ArgumentVisitor().visitArgument(ctx.ifTrue),
            ifFalse = ArgumentVisitor().visitArgument(ctx.ifFalse),
        )
    }

    override fun visitCall_expression(ctx: LispParser.Call_expressionContext): Expression {
        return CallExpression(
            name = ctx.fn.text,
            args = ctx.args.map { ArgumentVisitor().visitArgument(it) }
        )
    }
}

const val lispObjectType = "lisp__object"

class Function(val name: String, val args: List<String>, val closure: List<String>, val body: Expression){
    val cName = "lisp__defun__${name}__${args.size}"
    val clojureType = "${cName}__clojure_t"
    val prototype: String get()  {
//        var res = "$lispObjectType $cName($clojureType clojure"
        var res = "$lispObjectType $cName("
        args.forEach { res += "$lispObjectType ${argName(it)}, " }
        res = res.removeSuffix(", ")
        res += ")"
        return res
    }
    fun argName(arg: String) = "lisp__$arg"

    fun generatePrototype(): String{
//        var res = "typedef struct {\n"
//        closure.forEach {
//            res += "\t$lispObjectType $it;\n"
//        }
//        res += "} $clojureType;\n"
        val res = "$prototype;"

        return res
    }

    fun generateImplementation(): String{
        var res = "$prototype{\n"
        res += "return ${generateExpression(body)};"
        res += "}\n"
        return res
    }

    fun convertFunctionName(name: String, argsSize: Int): String{
        return when(name){
            "=" -> "lisp__eq"
            "*" -> "lisp__mul"
            "-" -> "lisp__sub"
            else -> "lisp__defun__${name}__${argsSize}"
        }
    }

    fun generateExpression(e: Expression): String{
        return when(e){
            is IfExpression -> "(lisp__is_true(${generateArgument(e.condition)}) ? ${generateArgument(e.ifTrue)} : ${generateArgument(e.ifFalse)})"
            is CallExpression -> "${convertFunctionName(e.name, e.args.size)}(${e.args.map{generateArgument(it)}.joinToString(", ")})"
            is DefunExpression -> throw Exception("Defun can only be used on top level")
        }
    }

    fun generateArgument(a: Argument): String{
        return when(a){
            is IntArgument -> "lisp__int_constructor(${a.v})"
            is ExpressionArgument -> generateExpression(a.v)
            is IdentifierArgument -> argName(a.name)
        }
    }

//    fun generate


}


fun main(args: Array<String>) {
    val inputStream = FileInputStream("src/main/resources/factorial.lisp")
    val lexer = LispLexer(CharStreams.fromStream(inputStream))
    val parser = LispParser(CommonTokenStream(lexer))
    val prog = parser.program()
    val parsed = ProgramVisitor().visitProgram(prog)

    // TODO: handle other bullshit
    val defuns = parsed.filterIsInstance<DefunExpression>()

    defuns.forEach {
        println(Function(it.name, it.arguments.map { it.name }, emptyList(), it.body).generatePrototype())
    }

    defuns.forEach { println(Function(it.name, it.arguments.map { it.name }, emptyList(), it.body).generateImplementation()) }



//    println(parsed)
}

