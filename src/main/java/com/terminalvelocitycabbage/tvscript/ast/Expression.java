package com.terminalvelocitycabbage.tvscript.ast;

import com.terminalvelocitycabbage.tvscript.Token;

public abstract class Expression {
    public interface Visitor<R> {
        R visitBinaryExpr(Binary expr);
        R visitGroupingExpr(Grouping expr);
        R visitLiteralExpr(Literal expr);
        R visitLogicalExpr(Logical expr);
        R visitUnaryExpr(Unary expr);
        R visitTernaryExpr(Ternary expr);
        R visitInterpolationExpr(Interpolation expr);
        R visitVariableExpr(Variable expr);
        R visitAssignExpr(Assign expr);
    }

    public abstract <R> R accept(Visitor<R> visitor);

    public static class Binary extends Expression {
        public final Expression left;
        public final Token operator;
        public final Expression right;

        public Binary(Expression left, Token operator, Expression right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBinaryExpr(this);
        }
    }

    public static class Grouping extends Expression {
        public final Expression expression;

        public Grouping(Expression expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitGroupingExpr(this);
        }
    }

    public static class Literal extends Expression {
        public final Object value;

        public Literal(Object value) {
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLiteralExpr(this);
        }
    }

    public static class Logical extends Expression {
        public final Expression left;
        public final Token operator;
        public final Expression right;

        public Logical(Expression left, Token operator, Expression right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLogicalExpr(this);
        }
    }

    public static class Unary extends Expression {
        public final Token operator;
        public final Expression right;

        public Unary(Token operator, Expression right) {
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitUnaryExpr(this);
        }
    }

    public static class Variable extends Expression {
        public final Token name;

        public Variable(Token name) {
            this.name = name;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVariableExpr(this);
        }
    }

    public static class Ternary extends Expression {
        public final Expression condition;
        public final Token operator;
        public final Expression trueBranch;
        public final Expression falseBranch;

        public Ternary(Expression condition, Token operator, Expression trueBranch, Expression falseBranch) {
            this.condition = condition;
            this.operator = operator;
            this.trueBranch = trueBranch;
            this.falseBranch = falseBranch;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitTernaryExpr(this);
        }
    }

    public static class Interpolation extends Expression {
        public final java.util.List<Expression> expressions;

        public Interpolation(java.util.List<Expression> expressions) {
            this.expressions = expressions;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitInterpolationExpr(this);
        }
    }

    public static class Assign extends Expression {
        public final Token name;
        public final Expression value;

        public Assign(Token name, Expression value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitAssignExpr(this);
        }
    }
}
