package com.terminalvelocitycabbage.tvscript.ast;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import java.util.List;

/**
 * Base interface for all statements in TVScript.
 */
public interface Statement {

    /**
     * Visitor interface for statements.
     * @param <R> The return type of the visitor methods.
     */
    interface Visitor<R> {
        R visitBlockStatement(BlockStatement stmt);
        R visitExpressionStatement(ExpressionStatement stmt);
        R visitIfStatement(IfStatement stmt);
        R visitPrintStatement(PrintStatement stmt);
        R visitVarStatement(VarStatement stmt);
        R visitPassStatement(PassStatement stmt);
        R visitWhileStatement(WhileStatement stmt);
        R visitForStatement(ForStatement stmt);
        R visitBreakStatement(BreakStatement stmt);
        R visitContinueStatement(ContinueStatement stmt);
        R visitMatchStatement(MatchStatement stmt);
        R visitFunctionStatement(FunctionStatement stmt);
        R visitReturnStatement(ReturnStatement stmt);
    }

    /**
     * Accepts a visitor.
     * @param visitor The visitor to accept.
     * @param <R> The return type of the visitor.
     * @return The result of the visitor.
     */
    <R> R accept(Visitor<R> visitor);

    record BlockStatement(List<Statement> statements) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStatement(this);
        }
    }

    record ExpressionStatement(Expression expression) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStatement(this);
        }
    }

    record IfStatement(Token keyword, Expression condition, Statement thenBranch, Statement elseBranch) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIfStatement(this);
        }
    }

    record PrintStatement(Token keyword, Expression expression) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitPrintStatement(this);
        }
    }

    record VarStatement(Token type, Token name, Expression initializer, boolean isConst) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVarStatement(this);
        }
    }

    record PassStatement() implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitPassStatement(this);
        }
    }

    record WhileStatement(Token keyword, Expression condition, Statement body) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitWhileStatement(this);
        }
    }

    record ForStatement(Token keyword, Token type, Token name, Expression range, Statement body) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitForStatement(this);
        }
    }

    record BreakStatement(Token keyword) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBreakStatement(this);
        }
    }

    record ContinueStatement(Token keyword) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitContinueStatement(this);
        }
    }

    record MatchStatement(Token keyword, Expression condition, List<Case> cases, Statement defaultBranch) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitMatchStatement(this);
        }

        public record Case(List<Expression> patterns, Statement branch) {}
    }

    record FunctionStatement(Token name, List<Parameter> parameters, Token returnType, Statement body) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionStatement(this);
        }

        public record Parameter(Token type, Token name, Expression defaultValue) {}
    }

    record ReturnStatement(Token keyword, Expression value) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitReturnStatement(this);
        }
    }
}
