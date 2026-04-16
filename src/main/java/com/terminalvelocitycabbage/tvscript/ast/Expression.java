package com.terminalvelocitycabbage.tvscript.ast;

import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.ast.Statement.FunctionStatement.Parameter;
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
        default R visitBinaryExpression(BinaryExpression expr) { return null; }
        default R visitGroupingExpression(GroupingExpression expr) { return null; }
        default R visitLiteralExpression(LiteralExpression expr) { return null; }
        default R visitLogicalExpression(LogicalExpression expr) { return null; }
        default R visitUnaryExpression(UnaryExpression expr) { return null; }
        default R visitTernaryExpression(TernaryExpression expr) { return null; }
        default R visitInterpolationExpression(InterpolationExpression expr) { return null; }
        default R visitVariableExpression(VariableExpression expr) { return null; }
        default R visitAssignExpression(AssignExpression expr) { return null; }
        default R visitRangeExpression(RangeExpression expr) { return null; }
        default R visitMatchExpression(MatchExpression expr) { return null; }
        default R visitCallExpression(CallExpression expr) { return null; }
        default R visitFunctionExpression(FunctionExpression expr) { return null; }
        default R visitGetExpression(GetExpression expr) { return null; }
        default R visitSetExpression(SetExpression expr) { return null; }
        default R visitThisExpression(ThisExpression expr) { return null; }
        default R visitNewExpression(NewExpression expr) { return null; }
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

    public record Argument(Token name, Expression value) {}

    record CallExpression(Expression callee, Token paren, List<Argument> arguments) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitCallExpression(this);
        }
    }

    record FunctionExpression(List<Parameter> parameters, Token returnType, Statement body) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionExpression(this);
        }
    }

    record GetExpression(Expression object, Token name) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitGetExpression(this);
        }
    }

    record SetExpression(Expression object, Token name, Expression value) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitSetExpression(this);
        }
    }

    record ThisExpression(Token keyword) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitThisExpression(this);
        }
    }

    record NewExpression(Token keyword, Expression callee, List<Argument> arguments) implements Expression {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitNewExpression(this);
        }
    }
}
