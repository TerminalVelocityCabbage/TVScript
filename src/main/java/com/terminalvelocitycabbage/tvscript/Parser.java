package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import java.util.ArrayList;
import java.util.List;
import static com.terminalvelocitycabbage.tvscript.TokenType.*;

public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<Statement> parseStatements() {
        List<Statement> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
            // Consume optional newlines after statements
            while (match(NEWLINE));
        }
        return statements;
    }

    public Expression parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    private Statement declaration() {
        try {
            if (match(VAR, CONST, TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN)) {
                return varDeclaration(previous());
            }

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Statement varDeclaration(Token typeToken) {
        boolean isConst = typeToken.getType() == CONST;
        Token finalType = typeToken;

        if (isConst && match(TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN)) {
            finalType = previous();
        }

        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expression initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        } else if (isConst) {
            TVScript.error(name, "Constant variable must be initialized.");
            throw new ParseError();
        }

        return new Statement.Var(finalType, name, initializer, isConst);
    }

    private Statement statement() {
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(PASS)) return passStatement();
        if (match(INDENT)) return new Statement.Block(block());

        return expressionStatement();
    }

    private Statement ifStatement() {
        Token keyword = previous();
        Expression condition = expression();
        consume(COLON, "Expect ':' after if condition.");

        Statement thenBranch;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in if statement.");
            thenBranch = new Statement.Block(block());
        } else {
            thenBranch = statement();
        }

        Statement elseBranch = null;
        if (match(ELSE)) {
            consume(COLON, "Expect ':' after else.");
            if (match(NEWLINE)) {
                consume(INDENT, "Expect indentation after newline in else statement.");
                elseBranch = new Statement.Block(block());
            } else {
                elseBranch = statement();
            }
        }

        return new Statement.If(keyword, condition, thenBranch, elseBranch);
    }

    private Statement printStatement() {
        Token keyword = previous();
        Expression value = expression();
        return new Statement.Print(keyword, value);
    }

    private Statement passStatement() {
        return new Statement.Pass();
    }

    private List<Statement> block() {
        List<Statement> statements = new ArrayList<>();

        while (!check(DEDENT) && !isAtEnd()) {
            statements.add(declaration());
            while (match(NEWLINE));
        }

        consume(DEDENT, "Expect indentation decrease after block.");
        return statements;
    }

    private Statement expressionStatement() {
        Expression expr = expression();
        return new Statement.ExpressionStmt(expr);
    }

    private Expression expression() {
        return assignment();
    }

    private Expression assignment() {
        Expression expr = ternary();

        if (match(EQUAL)) {
            Token equals = previous();
            Expression value = assignment();

            if (expr instanceof Expression.Variable) {
                Token name = ((Expression.Variable)expr).name;
                return new Expression.Assign(name, value);
            }

            TVScript.error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expression ternary() {
        Expression expr = or();

        if (match(TokenType.QUESTION)) {
            Token operator = previous();
            Expression trueBranch = expression();
            consume(TokenType.COLON, "Expect ':' after ternary condition.");
            Expression falseBranch = ternary();
            expr = new Expression.Ternary(expr, operator, trueBranch, falseBranch);
        }

        return expr;
    }

    private Expression or() {
        Expression expr = and();

        while (match(TokenType.OR)) {
            Token operator = previous();
            Expression right = and();
            expr = new Expression.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expression and() {
        Expression expr = equality();

        while (match(TokenType.AND)) {
            Token operator = previous();
            Expression right = equality();
            expr = new Expression.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expression equality() {
        Expression expr = comparison();

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token operator = previous();
            Expression right = comparison();
            expr = new Expression.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expression comparison() {
        Expression expr = term();

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token operator = previous();
            Expression right = term();
            expr = new Expression.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expression term() {
        Expression expr = factor();

        while (match(TokenType.MINUS, TokenType.PLUS)) {
            Token operator = previous();
            Expression right = factor();
            expr = new Expression.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expression factor() {
        Expression expr = unary();

        while (match(TokenType.SLASH, TokenType.STAR, TokenType.PERCENT)) {
            Token operator = previous();
            Expression right = unary();
            expr = new Expression.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expression unary() {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            Token operator = previous();
            Expression right = unary();
            return new Expression.Unary(operator, right);
        }

        return primary();
    }

    private Expression primary() {
        if (match(TokenType.FALSE)) return new Expression.Literal(false);
        if (match(TokenType.TRUE)) return new Expression.Literal(true);
        if (match(TokenType.NONE)) return new Expression.Literal(null);

        if (match(TokenType.STRING)) {
            return new Expression.Literal(previous().getValue());
        }

        if (match(TokenType.STRING_PART)) {
            java.util.List<Expression> expressions = new java.util.ArrayList<>();
            expressions.add(new Expression.Literal(previous().getValue()));
            while (true) {
                consume(TokenType.LEFT_BRACE, "Expect '{' to start interpolation.");
                expressions.add(expression());
                consume(TokenType.RIGHT_BRACE, "Expect '}' after interpolation.");

                if (match(TokenType.STRING_PART)) {
                    expressions.add(new Expression.Literal(previous().getValue()));
                } else if (match(TokenType.STRING)) {
                    expressions.add(new Expression.Literal(previous().getValue()));
                    break;
                } else {
                    break;
                }
            }
            return new Expression.Interpolation(expressions);
        }

        if (match(TokenType.INTEGER, TokenType.DECIMAL)) {
            return new Expression.Literal(previous().getValue());
        }

        if (match(TokenType.IDENTIFIER)) {
            return new Expression.Variable(previous());
        }

        if (match(TokenType.LEFT_PAREN)) {
            Expression expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
            return new Expression.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().getType() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        TVScript.error(token, message);
        return new ParseError();
    }

    // Synchronization logic placeholder
    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().getType() == TokenType.NEWLINE) return;
            switch (peek().getType()) {
                case CLASS:
                case FUNCTION:
                case VAR:
                case CONST:
                case IF:
                case FOR:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            advance();
        }
    }
}
