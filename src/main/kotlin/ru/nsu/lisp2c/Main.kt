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
import java.util.InputMismatchException


class PrintVisitor(): LispBaseVisitor<Unit>(){
    var level = 0
    override fun visitProgram(ctx: LispParser.ProgramContext) {
        visitChildren(ctx)
    }

    override fun visitExpression(ctx: LispParser.ExpressionContext) {
        val padding = " ".repeat(level)

        ctx.children.forEach{
            when(it){
                is TerminalNode -> println(padding + it.text)
                is LispParser.ExpressionContext -> {
                    level++
                    visitExpression(it)
                    level--
                }
                else -> throw InputMismatchException("Hui")
            }
        }
    }

}


fun main(args: Array<String>) {
    val inputStream = FileInputStream("src/main/resources/factorial.lisp")
    val lexer = LispLexer(CharStreams.fromStream(inputStream))
    val parser = LispParser(CommonTokenStream(lexer))
    val prog = parser.program()

    PrintVisitor().visitProgram(prog)

}

