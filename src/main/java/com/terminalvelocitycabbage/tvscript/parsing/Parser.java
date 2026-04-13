package com.terminalvelocitycabbage.tvscript.parsing;

import com.terminalvelocitycabbage.tvscript.TVScript;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import static com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

import java.util.ArrayList;
import java.util.List;

import static com.terminalvelocitycabbage.tvscript.parsing.TokenType.*;

/**
 * Parses a list of tokens into an Abstract Syntax Tree (AST).
 */
public class Parser {

    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Parses the tokens into a list of statements.
     * @return A list of statements.
     */
    public List<Statement> parseStatements() {
        List<Statement> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
            // Consume optional newlines after statements
            while (match(NEWLINE));
        }
        return statements;
    }

    /**
     * Parses a single expression.
     * @return The parsed expression, or null if a parse error occurred.
     */
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
        boolean isConst = typeToken.type() == CONST;
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

        return new VarStatement(finalType, name, initializer, isConst);
    }

    private Statement statement() {
        if (match(IF)) return ifStatement();
        if (match(WHILE)) return whileStatement();
        if (match(FOR)) return forStatement();
        if (match(MATCH)) return matchStatement();
        if (match(BREAK)) return breakStatement();
        if (match(CONTINUE)) return continueStatement();
        if (match(PRINT)) return printStatement();
        if (match(PASS)) return passStatement();
        if (match(INDENT)) return new BlockStatement(block());

        return expressionStatement();
    }

    private Statement whileStatement() {
        Token keyword = previous();
        Expression condition = expression();
        consume(COLON, "Expect ':' after while condition.");

        Statement body;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in while statement.");
            body = new BlockStatement(block());
        } else {
            body = statement();
        }

        return new WhileStatement(keyword, condition, body);
    }

    private Statement forStatement() {
        Token keyword = previous();
        Token type = null;
        Token name = null;

        if (match(LEFT_BRACKET)) {
            if (match(TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, IDENTIFIER)) {
                type = previous();
                name = consume(IDENTIFIER, "Expect loop variable name.");
                consume(RIGHT_BRACKET, "Expect ']' after loop variable.");
                consume(IN, "Expect 'in' after loop variable.");
            } else {
                throw error(peek(), "Expect type in loop variable declaration.");
            }
        }

        Expression range = expression();
        consume(COLON, "Expect ':' after for loop.");

        Statement body;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in for statement.");
            body = new BlockStatement(block());
        } else {
            body = statement();
        }

        return new ForStatement(keyword, type, name, range, body);
    }

    private Statement breakStatement() {
        Token keyword = previous();
        return new BreakStatement(keyword);
    }

    private Statement continueStatement() {
        Token keyword = previous();
        return new ContinueStatement(keyword);
    }

    private Statement ifStatement() {
        Token keyword = previous();
        Expression condition = expression();
        consume(COLON, "Expect ':' after if condition.");

        Statement thenBranch;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in if statement.");
            thenBranch = new BlockStatement(block());
        } else {
            thenBranch = statement();
        }

        Statement elseBranch = null;
        if (match(ELSE)) {
            consume(COLON, "Expect ':' after else.");
            if (match(NEWLINE)) {
                consume(INDENT, "Expect indentation after newline in else statement.");
                elseBranch = new BlockStatement(block());
            } else {
                elseBranch = statement();
            }
        }

        return new IfStatement(keyword, condition, thenBranch, elseBranch);
    }

    private Statement printStatement() {
        Token keyword = previous();
        Expression value = expression();
        return new PrintStatement(keyword, value);
    }

    private Statement passStatement() {
        return new PassStatement();
    }

    private Statement matchStatement() {
        Token keyword = previous();
        Expression condition = expression();
        consume(COLON, "Expect ':' after match condition.");
        consume(NEWLINE, "Expect newline after match condition.");
        consume(INDENT, "Expect indentation after match statement.");

        List<MatchStatement.Case> cases = new ArrayList<>();
        Statement defaultBranch = null;

        while (!check(DEDENT) && !isAtEnd()) {
            if (match(DEFAULT)) {
                consume(COLON, "Expect ':' after default.");
                if (match(NEWLINE)) {
                    consume(INDENT, "Expect indentation after default block.");
                    defaultBranch = new BlockStatement(block());
                } else {
                    defaultBranch = statement();
                    while (match(NEWLINE));
                }
            } else {
                List<Expression> patterns = new ArrayList<>();
                do {
                    patterns.add(expression());
                } while (match(COMMA));

                consume(COLON, "Expect ':' after match patterns.");
                Statement branch;
                if (match(NEWLINE)) {
                    consume(INDENT, "Expect indentation after match case block.");
                    branch = new BlockStatement(block());
                } else {
                    branch = statement();
                    while (match(NEWLINE));
                }
                cases.add(new MatchStatement.Case(patterns, branch));
            }
        }

        consume(DEDENT, "Expect dedent after match cases.");
        return new MatchStatement(keyword, condition, cases, defaultBranch);
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
        return new ExpressionStatement(expr);
    }

    private Expression expression() {
        return assignment();
    }

    private Expression matchExpression() {
        Token keyword = previous();
        Expression condition = expression();
        consume(COLON, "Expect ':' after match condition.");
        consume(NEWLINE, "Expect newline after match condition.");
        consume(INDENT, "Expect indentation after match expression.");

        List<MatchExpression.Case> cases = new ArrayList<>();
        Expression defaultBranch = null;

        while (!check(DEDENT) && !isAtEnd()) {
            if (match(DEFAULT)) {
                consume(COLON, "Expect ':' after default.");
                defaultBranch = expression();
                while (match(NEWLINE));
            } else {
                List<Expression> patterns = new ArrayList<>();
                do {
                    patterns.add(expression());
                } while (match(COMMA));

                consume(COLON, "Expect ':' after match patterns.");
                Expression branch = expression();
                cases.add(new MatchExpression.Case(patterns, branch));
                while (match(NEWLINE));
            }
        }

        consume(DEDENT, "Expect dedent after match cases.");
        return new MatchExpression(keyword, condition, cases, defaultBranch);
    }

    private Expression assignment() {
        Expression expr = ternary();

        if (match(EQUAL)) {
            Token equals = previous();
            Expression value = assignment();

            if (expr instanceof VariableExpression) {
                Token name = ((VariableExpression)expr).name();
                return new AssignExpression(name, value);
            }

            TVScript.error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expression ternary() {
        if (match(MATCH)) return matchExpression();
        Expression expr = or();

        if (match(QUESTION)) {
            Token operator = previous();
            Expression trueBranch = expression();
            consume(COLON, "Expect ':' after ternary condition.");
            Expression falseBranch = ternary();
            expr = new TernaryExpression(expr, operator, trueBranch, falseBranch);
        }

        return expr;
    }

    private Expression or() {
        Expression expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expression right = and();
            expr = new LogicalExpression(expr, operator, right);
        }

        return expr;
    }

    private Expression and() {
        Expression expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expression right = equality();
            expr = new LogicalExpression(expr, operator, right);
        }

        return expr;
    }

    private Expression equality() {
        Expression expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expression right = comparison();
            expr = new BinaryExpression(expr, operator, right);
        }

        return expr;
    }

    private Expression comparison() {
        Expression expr = range();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expression right = range();
            expr = new BinaryExpression(expr, operator, right);
        }

        return expr;
    }

    private Expression range() {
        Expression expr = term();

        if (match(DOT_DOT)) {
            Token operator = previous();
            Expression right = term();
            expr = new RangeExpression(operator, expr, right);
        }

        return expr;
    }

    private Expression term() {
        Expression expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expression right = factor();
            expr = new BinaryExpression(expr, operator, right);
        }

        return expr;
    }

    private Expression factor() {
        Expression expr = unary();

        while (match(SLASH, STAR, PERCENT)) {
            Token operator = previous();
            Expression right = unary();
            expr = new BinaryExpression(expr, operator, right);
        }

        return expr;
    }

    private Expression unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expression right = unary();
            return new UnaryExpression(operator, right);
        }

        return primary();
    }

    private Expression primary() {
        if (match(FALSE)) return new LiteralExpression(false);
        if (match(TRUE)) return new LiteralExpression(true);
        if (match(NONE)) return new LiteralExpression(null);

        if (match(STRING)) {
            return new LiteralExpression(previous().value());
        }

        if (match(STRING_PART)) {
            List<Expression> expressions = new ArrayList<>();
            expressions.add(new LiteralExpression(previous().value()));
            while (true) {
                consume(LEFT_BRACE, "Expect '{' to start interpolation.");
                expressions.add(expression());
                consume(RIGHT_BRACE, "Expect '}' after interpolation.");

                if (match(STRING_PART)) {
                    expressions.add(new LiteralExpression(previous().value()));
                } else if (match(STRING)) {
                    expressions.add(new LiteralExpression(previous().value()));
                    break;
                } else {
                    break;
                }
            }
            return new InterpolationExpression(expressions);
        }

        if (match(INTEGER, DECIMAL)) {
            return new LiteralExpression(previous().value());
        }

        if (match(IDENTIFIER)) {
            return new VariableExpression(previous());
        }

        if (match(LEFT_PAREN)) {
            Expression expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new GroupingExpression(expr);
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
        return peek().type() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type() == EOF;
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

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type() == NEWLINE) return;
            switch (peek().type()) {
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
