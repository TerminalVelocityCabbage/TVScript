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
        default R visitBlockStatement(BlockStatement stmt) { return null; }
        default R visitExpressionStatement(ExpressionStatement stmt) { return null; }
        default R visitIfStatement(IfStatement stmt) { return null; }
        default R visitPrintStatement(PrintStatement stmt) { return null; }
        default R visitVarStatement(VarStatement stmt) { return null; }
        default R visitPassStatement(PassStatement stmt) { return null; }
        default R visitWhileStatement(WhileStatement stmt) { return null; }
        default R visitForStatement(ForStatement stmt) { return null; }
        default R visitBreakStatement(BreakStatement stmt) { return null; }
        default R visitContinueStatement(ContinueStatement stmt) { return null; }
        default R visitMatchStatement(MatchStatement stmt) { return null; }
        default R visitImportStatement(ImportStatement stmt) { return null; }
        default R visitFunctionStatement(FunctionStatement stmt) { return null; }
        default R visitReturnStatement(ReturnStatement stmt) { return null; }
        default R visitClassStatement(ClassStatement stmt) { return null; }
        default R visitTraitStatement(TraitStatement stmt) { return null; }
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

    record ImportStatement(Token module, List<ImportItem> items) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitImportStatement(this);
        }

        public record ImportItem(Token name, Token alias) {}
    }

    record FunctionStatement(Token name, List<Parameter> parameters, Token returnType, Statement body, boolean isOverride, boolean isDefault) implements Statement {
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

    record ClassStatement(Token name, Token superclass, List<Token> traits, List<VarStatement> fields, List<FunctionStatement> methods, List<FunctionStatement> staticMethods, List<FunctionStatement> constructors) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitClassStatement(this);
        }
    }

    record TraitStatement(Token name, List<Token> traits, List<VarStatement> fields, List<FunctionStatement> methods) implements Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitTraitStatement(this);
        }
    }
}
