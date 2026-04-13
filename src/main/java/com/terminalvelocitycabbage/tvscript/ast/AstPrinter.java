package com.terminalvelocitycabbage.tvscript.ast;

public class AstPrinter implements Expression.Visitor<String> {
    public String print(Expression expr) {
        if (expr == null) return "null";
        return expr.accept(this);
    }

    @Override
    public String visitBinaryExpr(Expression.Binary expr) {
        return parenthesize(expr.operator.getLexeme(), expr.left, expr.right);
    }

    @Override
    public String visitGroupingExpr(Expression.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expression.Literal expr) {
        if (expr.value == null) return "none";
        return expr.value.toString();
    }

    @Override
    public String visitLogicalExpr(Expression.Logical expr) {
        return parenthesize(expr.operator.getLexeme(), expr.left, expr.right);
    }

    @Override
    public String visitUnaryExpr(Expression.Unary expr) {
        return parenthesize(expr.operator.getLexeme(), expr.right);
    }

    @Override
    public String visitTernaryExpr(Expression.Ternary expr) {
        return parenthesize("?", expr.condition, expr.trueBranch, expr.falseBranch);
    }

    @Override
    public String visitInterpolationExpr(Expression.Interpolation expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(interpolation");
        for (Expression expression : expr.expressions) {
            builder.append(" ");
            builder.append(expression.accept(this));
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitVariableExpr(Expression.Variable expr) {
        return expr.name.getLexeme();
    }

    @Override
    public String visitAssignExpr(Expression.Assign expr) {
        return parenthesize("= " + expr.name.getLexeme(), expr.value);
    }

    @Override
    public String visitRangeExpr(Expression.Range expr) {
        return parenthesize("..", expr.start, expr.end);
    }

    @Override
    public String visitMatchExpr(Expression.Match expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(match ").append(expr.condition.accept(this));
        for (Expression.Case matchCase : expr.cases) {
            builder.append(" (case ");
            for (Expression pattern : matchCase.patterns) {
                builder.append(pattern.accept(this)).append(" ");
            }
            builder.append(matchCase.branch.accept(this)).append(")");
        }
        if (expr.defaultBranch != null) {
            builder.append(" (default ").append(expr.defaultBranch.accept(this)).append(")");
        }
        builder.append(")");
        return builder.toString();
    }

    private String parenthesize(String name, Expression... exprs) {
        StringBuilder builder = new StringBuilder();

        builder.append("(").append(name);
        for (Expression expr : exprs) {
            builder.append(" ");
            builder.append(expr.accept(this));
        }
        builder.append(")");

        return builder.toString();
    }
}
