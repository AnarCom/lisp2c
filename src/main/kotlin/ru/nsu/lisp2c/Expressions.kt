package ru.nsu.lisp2c

data class GeneratedExpression(val body: String, val varName: String)

sealed interface Expression {
    fun generate(ctx: GeneratorContext): GeneratedExpression
}

interface TopLevelOnlyExpressions : Expression

class CallExpression(val target: Expression, val args: List<Expression>) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val resultVarName = ctx.newVarName()
        val (targetBody, targetName) = target.generate(ctx)
        val generatedArgs = args.map { it.generate(ctx) }
        val argsString =
            (arrayOf("$targetName->value.callable.clojure") + generatedArgs.map { it.varName }).joinToString(", ")
        val body = """
            $targetBody
            ${generatedArgs.map { it.body }.joinToString("")}
            assert($targetName->type == CALLABLE);
            assert($targetName->value.callable.n_args == ${args.size});
            $lispObjectType $resultVarName = ((${functionType(args.size)})$targetName->value.callable.f)($argsString);
        """.trimIndent()
        return GeneratedExpression(body, resultVarName)
    }
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

        val prototype = """
            $lispObjectType ${bodyName}($argumentString);
            $lispObjectType $cName = NULL;
        """.trimIndent()

        val mainInit = """
            $cName = lisp__callable_constructor($bodyName, ${arguments.size}, NULL);
        """.trimIndent()

        ctx.mainLines += mainInit
        ctx.prototypes += prototype

        ctx.scope.pushScope()
        arguments.forEach { ctx.scope[it.name] = Symbol(it.name, nameCName(it.name)) }
        ctx.pushRecurContext(RecurContext(startLabel, arguments.map { nameCName(it.name) }))
        val (generatedBody, returnVarName) = body.generate(ctx)
        ctx.popRecurContext()
        ctx.scope.popScope()


        val body = """
            $lispObjectType ${bodyName}($argumentString){
                $startLabel:;
                $generatedBody;
                return $returnVarName;
            }
        """.trimIndent()

        ctx.functionBodies += body

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

        val prototype = """
            $lispObjectType ${bodyName}($argumentString);
            $lispObjectType $cName = NULL;
        """.trimIndent()

        val mainInit = """
            $cName = lisp__callable_constructor($bodyName, ${arguments.size}, NULL);
        """.trimIndent()

        ctx.mainLines += mainInit
        ctx.prototypes += prototype

        val body = """
            $lispObjectType ${bodyName}($argumentString){
                $body
            }
        """.trimIndent()

        ctx.functionBodies += body

        return GeneratedExpression("ERROR", "ERROR")
    }
}

class FnExpression(val arguments: List<IdentifierExpression>, val body: Expression) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val cName = ctx.newVarName()
        val clojureType = cNameClojureType(cName)
        val bodyName = cNameBody(cName)
        val startLabel = cNameStartLabel(cName)

        val argumentString =
            (arrayOf("$clojureType *clj") + arguments.map { "$lispObjectType ${nameCName(it.name)}" }).joinToString(", ")
        val symbolsToCapture = ctx.scope.allButTop(2)

        val prototype = """
            typedef struct {
                ${symbolsToCapture.values.joinToString("") { "$lispObjectType ${it.cName};\n" }}
            } $clojureType;            
            $lispObjectType ${bodyName}($argumentString);
        """.trimIndent()

        ctx.prototypes += prototype

        ctx.scope.pushScope()
        arguments.forEach { ctx.scope[it.name] = Symbol(it.name, nameCName(it.name)) }
        symbolsToCapture.values.forEach { ctx.scope[it.name] = Symbol(it.name, "clj->${it.cName}") }
        ctx.pushRecurContext(RecurContext(startLabel, arguments.map { nameCName(it.name) }))
        val (generatedBody, returnVarName) = body.generate(ctx)
        ctx.scope.popScope()
        ctx.popRecurContext()


        val funcionBody = """
            $lispObjectType ${bodyName}($argumentString){
                $startLabel:;
                $generatedBody;
                return $returnVarName;
            }
        """.trimIndent()
        ctx.functionBodies += funcionBody

        val clojureVar = ctx.newVarName()
        val body = """
            $clojureType *$clojureVar = malloc(sizeof($clojureType));
            ${symbolsToCapture.values.joinToString("") { "$clojureVar->${ctx.scope[it.name]!!.cName} = ${ctx.scope[it.name]!!.cName};\n" }}
            $lispObjectType $cName = lisp__callable_constructor($bodyName, ${arguments.size}, $clojureVar);
        """.trimIndent()

        return GeneratedExpression(body, cName)
    }
}

class IfExpression(val condition: Expression, val ifTrue: Expression, val ifFalse: Expression) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val resultVarName = ctx.newVarName()
        val (conditionBody, conditionVar) = condition.generate(ctx)
        val (trueBody, trueVar) = ifTrue.generate(ctx)
        val (falseBody, falseVar) = ifFalse.generate(ctx)

        val body = """
            $conditionBody
            $lispObjectType $resultVarName;
            if(lisp__is_true($conditionVar)){
                $trueBody
                $resultVarName = $trueVar;
            }else{
                $falseBody
                $resultVarName = $falseVar;
            }
        """.trimIndent()

        return GeneratedExpression(body, resultVarName)
    }
}

class IntExpression(val v: Int) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val varName = ctx.newVarName()
        val body = "$lispObjectType $varName = lisp__int_constructor($v);\n"
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
            ${ctx.recurContext.cNames.mapIndexed{ i, cName -> "$cName = ${generatedArgs[i].varName};\n" }.joinToString("")}
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