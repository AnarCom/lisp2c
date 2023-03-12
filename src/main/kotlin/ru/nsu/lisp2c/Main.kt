package ru.nsu.lisp2c

import LispLexer
import LispParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.FileInputStream
import java.io.FileOutputStream

//data class Expression()
// Function call: Simple function call
// Defun        : Allowed only on top level
// Fn           : Fuckery with closures
// List         : Create list in C
// If           : Ternary operator
// Let          : I don't know
// Recurse      : Simple

data class Symbol(val name: String, val cName: String) {
    fun pair(): Pair<String, Symbol> = name to this
}

infix fun String.cname(cname: String) = this to Symbol(this, cname)
val builtinSymbols = mapOf(
    "*" cname "lisp__mul",
    "+" cname "lisp__add",
    "-" cname "lisp__sub",
    "/" cname "lisp__div",
    "=" cname "lisp__eq",

    "or" cname "lisp__or",
    "and" cname "lisp__and",
    "not" cname "lisp__not",

    "head" cname "lisp__head",
    "tail" cname "lisp__tail",
    "append" cname "lisp__append",
    "size" cname "lisp__size",

)


fun main(args: Array<String>) {
    val inputStream = FileInputStream("src/main/resources/factorial.lisp")
    val lexer = LispLexer(CharStreams.fromStream(inputStream))
    val parser = LispParser(CommonTokenStream(lexer))
    val prog = parser.program()
    val parsed = ProgramVisitor().visitProgram(prog)
    val generated = Generator().generate(parsed)

    val formatted = reformatCode(generated)
//    println(formatted)
    FileOutputStream("../c-lisp2c-runtime/main.c").bufferedWriter().apply {
        write(formatted)
        close()
    }
}