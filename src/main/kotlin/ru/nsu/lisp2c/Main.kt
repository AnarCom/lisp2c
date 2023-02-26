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
//import sun.jvm.hotspot.debugger.cdbg.Sym
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

const val lispObjectType = "lisp__object *"
const val newObject = "gc__new_object"
const val varPrefix = "lisp_var"
fun nameCName(name: String) = "${varPrefix}_$name"
fun cNameClojureType(cname: String) = cname.replaceFirst(varPrefix, "lisp_clojure") + "_t"
fun cNameBody(cname: String) = cname.replaceFirst(varPrefix, "lisp_body")
fun cNameStartLabel(cname: String) = cname.replaceFirst(varPrefix, "lisp_start")
fun functionType(n: Int) = "lisp_fun_${n}_t"



data class Symbol(val name: String, val cName: String) {
    fun pair(): Pair<String, Symbol> = name to this
}

infix fun String.cname(cname: String) = this to Symbol(this, cname)
val builtinSymbols = mapOf(
    "*" cname "lisp__mul",
    "+" cname "lisp__add",
    "-" cname "lisp__sub",
    "/" cname "lisp__div",
    "=" cname "lisp__eq"

    )

data class GeneratorContext(
    val prototypes: MutableList<String> = mutableListOf(),
    val functionBodies: MutableList<String> = mutableListOf(),
    val mainLines: MutableList<String> = mutableListOf(),
    private var nextID: Int = 0,
    val scope:MapStack<String, Symbol> = MapStack(),
){
    fun newVarName() = "lisp_var_${nextID++}"
}


fun variableCName(name: String): String = "lisp_var_$name"


class Generator() {
    val ctx = GeneratorContext()

    init {
        ctx.scope.pushScope()
        builtinSymbols.forEach{ctx.scope[it.key] = it.value}

    }

    fun generate(prog: List<Expression>): String {
        val defuns = prog.filterIsInstance<DefunExpression>()
        defuns.forEach { it.generate(ctx) }
        val functionTypedefs = (0..10).map {n ->
            val args = (arrayOf("void*") + (0 until n).map { lispObjectType }).joinToString(", ")
            "typedef $lispObjectType(*${functionType(n)})($args);"
        }

        return """
            ${functionTypedefs.joinToString("\n")}
            ${ctx.prototypes.joinToString("\n")}
            ${ctx.functionBodies.joinToString("\n")}
            int main(){
                gc__init();
                ${ctx.mainLines.joinToString("\n")}
                return 0;
            }
        """.trimIndent()
    }
}

fun main(args: Array<String>) {
    val inputStream = FileInputStream("src/main/resources/factorial.lisp")
    val lexer = LispLexer(CharStreams.fromStream(inputStream))
    val parser = LispParser(CommonTokenStream(lexer))
    val prog = parser.program()
    val parsed = ProgramVisitor().visitProgram(prog)
    val generated = Generator().generate(parsed)
    val process = ProcessBuilder("clang-format")
        .redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    process.outputStream.write(generated.encodeToByteArray())
    process.outputStream.close()
    val formatted = process.inputStream.bufferedReader().readText()
    println(formatted)


}