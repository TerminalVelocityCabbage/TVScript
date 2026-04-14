package com.terminalvelocitycabbage.tvscript.ast;

import com.terminalvelocitycabbage.tvscript.parsing.Token;
import java.util.List;

/**
 * Base interface for all expressions in TVScript.
 */
public interface Expression {

    /**
     * Visitor interface for expressions.
     * @param <R> The return type of the visitor methods.
     */
    interface Visitor<R> {
        R visitBinaryExpression(BinaryExpression expr);
        R visitGroupingExpression(GroupingExpression expr);
        R visitLiteralExpression(LiteralExpression expr);
        R visitLogicalExpression(LogicalExpression expr);
        R visitUnaryExpression(UnaryExpression expr);
        R visitTernaryExpression(TernaryExpression expr);
        R visitInterpolationExpression(InterpolationExpression expr);
        R visitVariableExpression(VariableExpression expr);
        R visitAssignExpression(AssignExpression expr);
        R visitRangeExpression(RangeExpression expr);
        R visitMatchExpression(MatchExpression expr);
        R visitCallExpression(CallExpression expr);
        R visitFunctionExpression(FunctionExpression expr);
    }

    /**
     * Accepts a visitor.
     * @param visitor The visitor to accept.
     * @param <R> The return type of the visitor.
     * @return The result of the visitor.
     */
    <R> R accept(Visitor<R> visitor);

    record BinaryExpression(Expression left, Token operator, Expression right) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBinaryExpression(this);
        }
    }

    record GroupingExpression(Expression expression) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitGroupingExpression(this);
        }
    }

    record LiteralExpression(Object value) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLiteralExpression(this);
        }
    }

    record LogicalExpression(Expression left, Token operator, Expression right) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLogicalExpression(this);
        }
    }

    record UnaryExpression(Token operator, Expression right) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitUnaryExpression(this);
        }
    }

    record TernaryExpression(Expression condition, Token operator, Expression thenBranch, Expression elseBranch) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitTernaryExpression(this);
        }
    }

    record InterpolationExpression(List<Expression> expressions) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitInterpolationExpression(this);
        }
    }

    record VariableExpression(Token name) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVariableExpression(this);
        }
    }

    record AssignExpression(Token name, Expression value) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitAssignExpression(this);
        }
    }

    record RangeExpression(Token operator, Expression start, Expression end) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitRangeExpression(this);
        }
    }

    record MatchExpression(Token keyword, Expression condition, List<Case> cases, Expression defaultBranch) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitMatchExpression(this);
        }

        public record Case(List<Expression> patterns, Expression branch) {}
    }

    record CallExpression(Expression callee, Token paren, List<Argument> arguments) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitCallExpression(this);
        }

        public record Argument(Token name, Expression value) {}
    }

    record FunctionExpression(List<Statement.FunctionStatement.Parameter> parameters, Token returnType, Statement body) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionExpression(this);
        }
    }
}
