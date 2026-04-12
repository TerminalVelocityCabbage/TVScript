package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import java.util.List;

public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Expression parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    private Expression expression() {
        return or();
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
