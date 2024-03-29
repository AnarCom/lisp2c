package ru.nsu.lisp2c

import LispLexer
import LispParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectory

data class GeneratedExpression(val body: String, val varName: String)
sealed class SimplifiedExpression
class SimplifiedStringExpression(val value: String): SimplifiedExpression()
class SimplifiedListExpression(val values: List<SimplifiedExpression>, val bracketType: Int): SimplifiedExpression()

sealed interface Expression {
    fun generate(ctx: GeneratorContext): GeneratedExpression
}

fun GeneratorContext.createVar(cName: String): String {
    this.localVars.peek().add(cName)
    return "$lispObjectType $cName = 0;"
}
fun GeneratorContext.passArgument(cName: String) = "gc__inc_ref_counter($cName)"
fun GeneratorContext.returnVar(cName: String): String {
    return """
        gc__inc_ref_counter($cName);
        ${localVars.peek().joinToString ("\n"){ "gc__dec_ref_counter($it);" }}
        return $cName;
    """.trimIndent()
}

interface TopLevelOnlyExpressions : Expression

class CallExpression(val target: Expression, val args: List<Expression>) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val resultVarName = ctx.newVarName()
        val (targetBody, targetName) = target.generate(ctx)
        val generatedArgs = args.map { it.generate(ctx) }
        val argsString =
            (arrayOf("$targetName->value.callable.clojure") + generatedArgs.map { ctx.passArgument(it.varName) }).joinToString(", ")
        val body = """
            $targetBody
            ${generatedArgs.map { it.body }.joinToString("")}
            assert($targetName->type == CALLABLE);
            assert($targetName->value.callable.n_args == ${args.size});
            ${ctx.createVar(resultVarName)}
            $resultVarName = ((${functionType(args.size)})$targetName->value.callable.f)($argsString);
        """.trimIndent()
        return GeneratedExpression(body, resultVarName)
    }
}

private fun globalFunctionPrototype(bodyName: String, cName: String, arguments: String): String{
    return """
            $lispObjectType ${bodyName}($arguments);
            $lispObjectType $cName = NULL;
        """.trimIndent()
}

private fun globalFunctionMainInit(bodyName: String, cName: String, argCount: Int): String {
    return """
            $cName = lisp__callable_constructor($bodyName, $argCount, NULL);
        """.trimIndent()
}

class DefunExpression(val name: String, val arguments: List<IdentifierExpression>, val body: Expression) : Expression,
    TopLevelOnlyExpressions {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val cName = nameCName(name)
        val clojureType = cNameClojureType(cName)
        val bodyName = cNameBody(cName)
        val startLabel = cNameStartLabel(cName)
        ctx.scope[name] = Symbol(name, cName)

        val argumentString =
            (arrayOf("void *clj") + arguments.map { "$lispObjectType ${nameCName(it.name)}" }).joinToString(", ")

        ctx.prototypes += globalFunctionPrototype(bodyName, cName, argumentString)
        ctx.mainLines += globalFunctionMainInit(bodyName, cName, arguments.size)

        ctx.scope.pushScope()
        arguments.forEach { ctx.scope[it.name] = Symbol(it.name, nameCName(it.name)) }
        ctx.pushRecurContext(RecurContext(startLabel, arguments.map { nameCName(it.name) }))
        ctx.localVars.push(mutableListOf())
        arguments.forEach { ctx.localVars.peek().add(nameCName(it.name)) }
        val (generatedBody, returnVarName) = body.generate(ctx)
        ctx.popRecurContext()
        ctx.scope.popScope()


        val functionBody = """
            $lispObjectType ${bodyName}($argumentString){
                $startLabel:;
                $generatedBody;
                ${ctx.returnVar(returnVarName)}
            }
        """.trimIndent()

        ctx.localVars.pop()
        ctx.functionBodies += functionBody

        return GeneratedExpression("ERROR", "ERROR")
    }
}

// TODO: remove duplicate code
class DefunCExpression(val name: String, val arguments: List<IdentifierExpression>, val body: String) : Expression,
    TopLevelOnlyExpressions {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val cName = nameCName(name)
        val bodyName = cNameBody(cName)
        ctx.scope[name] = Symbol(name, cName)

        val argumentString =
            (arrayOf("void *__clj__") + arguments.map { "$lispObjectType ${it.name}" }).joinToString(", ")

        ctx.prototypes += globalFunctionPrototype(bodyName, cName, argumentString)
        ctx.mainLines += globalFunctionMainInit(bodyName, cName, arguments.size)

        val body = """
            $lispObjectType ${bodyName}($argumentString){
                $body
            }
        """.trimIndent()

        ctx.functionBodies += body

        return GeneratedExpression("ERROR", "ERROR")
    }
}

// TODO: gc
class FnExpression(val arguments: List<IdentifierExpression>, val body: Expression) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val cName = ctx.newVarName()
        val clojureType = cNameClojureType(cName)
        val bodyName = cNameBody(cName)
        val startLabel = cNameStartLabel(cName)

        val argumentString =
            (arrayOf("$clojureType *clj") + arguments.map { "$lispObjectType ${nameCName(it.name)}" }).joinToString(", ")
        val symbolsToCapture = ctx.scope.allButTop(2)
        val newSymbolNames = symbolsToCapture.map { it.key to ctx.newVarName() }.toMap()

        val prototype = """
            typedef struct {
                size_t sz;
                ${symbolsToCapture.values.joinToString("") { "$lispObjectType ${newSymbolNames[it.name]!!};\n" }}
            } __attribute__((packed)) $clojureType;            
            $lispObjectType ${bodyName}($argumentString);
        """.trimIndent()

        ctx.prototypes += prototype

        ctx.scope.pushScope()
        symbolsToCapture.values.forEach { ctx.scope[it.name] = Symbol(it.name, "clj->${newSymbolNames[it.name]!!}") }
        arguments.forEach { ctx.scope[it.name] = Symbol(it.name, nameCName(it.name)) }
        ctx.pushRecurContext(RecurContext(startLabel, arguments.map { nameCName(it.name) }))
        ctx.localVars.push(mutableListOf())
        arguments.forEach { ctx.localVars.peek().add(nameCName(it.name)) }
        val (generatedBody, returnVarName) = body.generate(ctx)
        ctx.scope.popScope()
        ctx.popRecurContext()


        val functionBody = """
            $lispObjectType ${bodyName}($argumentString){
                $startLabel:;
                $generatedBody;
                ${ctx.returnVar(returnVarName)}
            }
        """.trimIndent()
        ctx.localVars.pop()
        ctx.functionBodies += functionBody

        val clojureVar = ctx.newVarName()
        val body = """
            $clojureType *$clojureVar = malloc(sizeof($clojureType));
            $clojureVar->sz = ${symbolsToCapture.size};
            ${symbolsToCapture.values.joinToString("") { "$clojureVar->${newSymbolNames[it.name]!!} = gc__inc_ref_counter(${ctx.scope[it.name]!!.cName});\n" }}
            ${ctx.createVar(cName)}
            $cName = lisp__callable_constructor($bodyName, ${arguments.size}, $clojureVar);
        """.trimIndent()

        return GeneratedExpression(body, cName)
    }
}

class IfExpression(val condition: Expression, val ifTrue: Expression, val ifFalse: Expression) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val resultVarName = ctx.newVarName()
        val (conditionBody, conditionVar) = condition.generate(ctx)

        val oldLocalVars = ctx.localVars.peek().toMutableList()

        val (trueBody, trueVar) = ifTrue.generate(ctx)
        val trueLocalVars = (ctx.localVars.peek().toSet() - oldLocalVars.toSet()).toMutableList()
        ctx.localVars.peek().removeAll(trueLocalVars)


        val (falseBody, falseVar) = ifFalse.generate(ctx)
        val falseLocalVars = (ctx.localVars.peek().toSet() - oldLocalVars.toSet()).toMutableList()
        ctx.localVars.peek().removeAll(falseLocalVars)


        val body = """
            $conditionBody
            ${ctx.createVar(resultVarName)}
            if(lisp__is_true($conditionVar)){
                $trueBody
                gc__inc_ref_counter($trueVar);
                ${trueLocalVars.joinToString("\n"){ "gc__dec_ref_counter($it);" }}
                $resultVarName = $trueVar;
            }else{
                $falseBody
                gc__inc_ref_counter($falseVar);
                ${falseLocalVars.joinToString("\n") { "gc__dec_ref_counter($it);" }}
                $resultVarName = $falseVar;
            }
        """.trimIndent()
        return GeneratedExpression(body, resultVarName)
    }
}

class IntExpression(val v: Int) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val varName = ctx.newVarName()
        val body = "${ctx.createVar(varName)}\n$varName = lisp__int_constructor($v);\n"
        return GeneratedExpression(body, varName)
    }
}

class IdentifierExpression(val name: String) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        return GeneratedExpression("", ctx.scope[name]!!.cName)
    }
}

class RecurExpression(val args: List<Expression>): Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        if(args.size != ctx.recurContext.cNames.size){
            throw Exception("recur: wrong argument count")
        }

        val generatedArgs = args.map { it.generate(ctx) }
        val argCalculationBody = generatedArgs.joinToString(""){it.body}


        val body = """
            $argCalculationBody
            ${ctx.recurContext.cNames.mapIndexed{ i, cName -> 
                val dst = cName
                val src = generatedArgs[i].varName
                "if($dst != $src){gc__dec_ref_counter($dst);}$dst = $src;\n"
            }.joinToString("")}
            ${(ctx.localVars.peek().toSet() - ctx.recurContext.cNames.toSet() - generatedArgs.map { it.varName }.toSet()).joinToString("\n") { "gc__dec_ref_counter($it);" }}
            goto ${ctx.recurContext.label};
        """.trimIndent()
        return  GeneratedExpression(body, "NULL")
    }

}

class DoExpression(val args: List<Expression>): Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val generatedArgs = args.map { it.generate(ctx) }
        val body = generatedArgs.joinToString("") { it.body }
        return GeneratedExpression(body, generatedArgs.last().varName)
    }
}

class BooleanExpression(val value: Boolean): Expression{
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val varName = ctx.newVarName()
        val body = "$lispObjectType $varName = lisp__bool_constructor(${if(value) 1 else 0});\n"
        return GeneratedExpression(body, varName)
    }
}

class StringExpression(val value: String): Expression{
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val varName = ctx.newVarName()
        val body = """
            ${ctx.createVar(varName)}
            $varName = lisp__list_constructor();
            ${value.map { "$varName = lisp__list_append(0, $varName, lisp__char_constructor(${it.code}));" }.joinToString("\n")}
        """.trimIndent()
        return GeneratedExpression(body, varName)
    }

}

class MacroExpandExpression(val macroName: String, val forms: SimplifiedExpression): Expression{
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val serializedForms = serializeSimpleExpression(ctx, forms)
        val resultVar = ctx.newVarName();
        val code = """
            ${ctx.lastUsableSnapshot}
            ${serializedForms.body}
            $lispObjectType $resultVar =  ((${functionType(1)})${ctx.scope[macroName]!!.cName } ->value.callable.f)(0, ${serializedForms.varName});
            lisp__print_serialized_form($resultVar);
            
            return 0;
            }            
        """.trimIndent()

        val runtimePath = "../c-lisp2c-runtime"

        val exeFileName = ctx.macroExpansionDir.absolutePathString() + "/${ctx.nextMacroExpansionId++}"
        val codeFileName = "$exeFileName.c"
        File(codeFileName).writeText(reformatCode(code))
        val gccProcess = ProcessBuilder(
            "gcc",
            "-I$runtimePath",
            "-o", exeFileName,
            "$runtimePath/runtime.c",
            "$runtimePath/GarbageCollector.c",
            "$runtimePath/macro.c",
            codeFileName
            )
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .start()
        if(gccProcess.waitFor() != 0){
            throw Exception("Macro expansion failed")
        }
        val exeProcess = ProcessBuilder(exeFileName)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        if(exeProcess.waitFor() != 0){
            throw Exception("Macro expander crashed")
        }

        val expandedMacro = exeProcess.inputStream.bufferedReader().readText()
        println("Macro expanded to $expandedMacro")

        val lexer = LispLexer(CharStreams.fromString(expandedMacro))
        val parser = LispParser(CommonTokenStream(lexer))
        val parsed = parser.expression();
        val parsedExpression = ExpressionVisitor().visitExpression(parsed)
        return parsedExpression.generate(ctx)
    }

    private fun serializeSimpleExpression(ctx: GeneratorContext, expression: SimplifiedExpression): GeneratedExpression{
        return when(expression){
            is SimplifiedListExpression -> serializeSimpleListExpression(ctx, expression)
            is SimplifiedStringExpression -> serializeSimpleStringExpression(ctx, expression)
        }
    }

    private fun generateForm(ctx: GeneratorContext, isList: Boolean, bracketType: Int, contentVarName: String): GeneratedExpression{
        val varName = ctx.newVarName();
        val body ="""
            $lispObjectType $varName = lisp__list_constructor();
             $varName = lisp__list_append(0, $varName, lisp__bool_constructor($isList));
             $varName = lisp__list_append(0, $varName, lisp__bool_constructor($bracketType));
             $varName = lisp__list_append(0, $varName, $contentVarName);
        """.trimIndent()
        return GeneratedExpression(body, varName);
    }

    private fun serializeSimpleStringExpression(ctx: GeneratorContext, expression: SimplifiedStringExpression): GeneratedExpression{
        val tempName = ctx.newVarName()
        val resultList = generateForm(ctx, false, 0, tempName);
        val body = """
            $lispObjectType $tempName = lisp__list_constructor();
            ${expression.value.map { "$tempName = lisp__list_append(0, $tempName, lisp__char_constructor(${it.toInt()}));" }.joinToString("\n")}
            ${resultList.body}
        """.trimIndent()
        return GeneratedExpression(body, resultList.varName)
    }

    private fun serializeSimpleListExpression(ctx: GeneratorContext, expression: SimplifiedListExpression): GeneratedExpression{
        val generatedArgs = expression.values.map { serializeSimpleExpression(ctx, it) }

        val tempVar = ctx.newVarName()
        val resultList = generateForm(ctx, true, expression.bracketType, tempVar)
        val body = """
            ${generatedArgs.joinToString ("\n"){ it.body }}
            $lispObjectType $tempVar = lisp__list_constructor();
            ${generatedArgs.map { "$tempVar = lisp__list_append(0, $tempVar, ${it.varName});"}.joinToString("\n")};
            ${resultList.body}
        """.trimIndent()
        return GeneratedExpression(body, resultList.varName)
    }
}