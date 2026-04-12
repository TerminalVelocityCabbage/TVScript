package com.terminalvelocitycabbage.tvscript.ast;

import com.terminalvelocitycabbage.tvscript.Token;

import java.util.List;

public abstract class Statement {
    public interface Visitor<R> {
        R visitBlockStmt(Block stmt);
        R visitExpressionStmt(ExpressionStmt stmt);
        R visitIfStmt(If stmt);
        R visitPrintStmt(Print stmt);
        R visitVarStmt(Var stmt);
        R visitPassStmt(Pass stmt);
    }

    public abstract <R> R accept(Visitor<R> visitor);

    public static class Block extends Statement {
        public final List<Statement> statements;

        public Block(List<Statement> statements) {
            this.statements = statements;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStmt(this);
        }
    }

    public static class ExpressionStmt extends Statement {
        public final Expression expression;

        public ExpressionStmt(Expression expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }
    }

    public static class If extends Statement {
        public final Token keyword;
        public final Expression condition;
        public final Statement thenBranch;
        public final Statement elseBranch;

        public If(Token keyword, Expression condition, Statement thenBranch, Statement elseBranch) {
            this.keyword = keyword;
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIfStmt(this);
        }
    }

    public static class Print extends Statement {
        public final Token keyword;
        public final Expression expression;

        public Print(Token keyword, Expression expression) {
            this.keyword = keyword;
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitPrintStmt(this);
        }
    }

    public static class Var extends Statement {
        public final Token type; // the type token or VAR/CONST
        public final Token name;
        public final Expression initializer;
        public final boolean isConst;

        public Var(Token type, Token name, Expression initializer, boolean isConst) {
            this.type = type;
            this.name = name;
            this.initializer = initializer;
            this.isConst = isConst;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVarStmt(this);
        }
    }

    public static class Pass extends Statement {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitPassStmt(this);
        }
    }
}
