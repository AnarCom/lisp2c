package ru.nsu.lisp2c

import LispBaseVisitor

class ProgramVisitor: LispBaseVisitor<List<Expression>>(){
    override fun visitProgram(ctx: LispParser.ProgramContext): List<Expression> {
        return ctx.expressions.map { ExpressionVisitor().visitExpression(it) }
    }
}

class ExpressionVisitor: LispBaseVisitor<Expression>(){
    override fun visitExpression(ctx: LispParser.ExpressionContext?): Expression {
        return visitChildren(ctx)
    }

    override fun visitDefun_expression(ctx: LispParser.Defun_expressionContext): Expression {
        return DefunExpression(
            name = ctx.name.text,
            arguments = ctx.args.map { visitIdentifier_expression(it) as IdentifierExpression},
            body = visitExpression(ctx.body)
        )
    }

    override fun visitIf_expression(ctx: LispParser.If_expressionContext): Expression {
        return IfExpression(
            condition = visitExpression(ctx.condition),
            ifTrue = visitExpression(ctx.ifTrue),
            ifFalse = visitExpression(ctx.ifFalse),
        )
    }

    override fun visitCall_expression(ctx: LispParser.Call_expressionContext): Expression {
        return CallExpression(
            target = visitExpression(ctx.fn),
            args = ctx.args.map { visitExpression(it) }
        )
    }

    override fun visitIdentifier_expression(ctx: LispParser.Identifier_expressionContext): Expression {
        return IdentifierExpression(ctx.text)
    }

    override fun visitInteger_expression(ctx: LispParser.Integer_expressionContext): Expression {
        return IntExpression(ctx.text.toInt())
    }
}