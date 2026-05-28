package com.terminalvelocitycabbage.tvscript.parsing;

import com.terminalvelocitycabbage.tvscript.CompilationContext;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.ast.VisibleElement;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import java.util.ArrayList;
import java.util.List;

import static com.terminalvelocitycabbage.tvscript.parsing.TokenType.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

public class StatementParser extends BaseParser {

    private final Parser masterParser;
    private final ExpressionParser expressionParser;

    public StatementParser(List<Token> tokens, CompilationContext context, Parser masterParser, ExpressionParser expressionParser) {
        super(tokens, context);
        this.masterParser = masterParser;
        this.expressionParser = expressionParser;
    }

    public void setCurrent(int current) {
        this.current = current;
    }

    public int getCurrent() {
        return this.current;
    }

    public Statement statement() {
        if (match(IF)) return ifStatement();
        if (match(WHILE)) return whileStatement();
        if (match(FOR)) return forStatement();
        if (match(MATCH)) return matchStatement();
        if (match(BREAK)) return breakStatement();
        if (match(CONTINUE)) return continueStatement();
        if (match(RETURN)) return returnStatement();
        if (match(PRINT)) return printStatement();
        if (match(PASS)) return passStatement();
        if (match(DISPATCH)) return dispatchStatement();
        if (match(INDENT)) return new BlockStatement(masterParser.block());

        return expressionStatement();
    }

    public Statement expressionStatement() {
        Expression expr = expressionParser.expression();
        consume(NEWLINE, "Expect newline after expression.");
        return new ExpressionStatement(expr);
    }

    public Statement ifStatement() {
        Token keyword = previous();
        Expression condition = expressionParser.expression();
        consume(COLON, "Expect ':' after if condition.");

        Statement thenBranch;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in if statement.");
            thenBranch = new BlockStatement(masterParser.block());
        } else {
            thenBranch = statement();
        }

        Statement elseBranch = null;
        if (match(ELSE)) {
            consume(COLON, "Expect ':' after else.");
            if (match(NEWLINE)) {
                consume(INDENT, "Expect indentation after newline in else statement.");
                elseBranch = new BlockStatement(masterParser.block());
            } else {
                elseBranch = statement();
            }
        }

        return new IfStatement(keyword, condition, thenBranch, elseBranch);
    }

    public Statement whileStatement() {
        Token keyword = previous();
        Expression condition = expressionParser.expression();
        consume(COLON, "Expect ':' after while condition.");

        Statement body;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in while statement.");
            body = new BlockStatement(masterParser.block());
        } else {
            body = statement();
        }

        return new WhileStatement(keyword, condition, body);
    }

    public Statement forStatement() {
        Token keyword = previous();
        Token type = null;
        Token name = null;
        Token valueType = null;
        Token valueName = null;

        if (match(LEFT_BRACKET)) {
            if (match(TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, IDENTIFIER)) {
                type = previous();
                name = consume(IDENTIFIER, "Expect loop variable name.");

                if (match(PIPE)) {
                    if (match(TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, IDENTIFIER)) {
                        valueType = previous();
                        valueName = consume(IDENTIFIER, "Expect loop value variable name.");
                    } else {
                        throw error(peek(), "Expect type in loop value declaration.");
                    }
                }

                consume(RIGHT_BRACKET, "Expect ']' after loop variable.");
                consume(IN, "Expect 'in' after loop variable.");
            } else {
                throw error(peek(), "Expect type in loop variable declaration.");
            }
        }

        Expression range = expressionParser.expression();
        consume(COLON, "Expect ':' after for loop.");

        Statement body;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in for statement.");
            body = new BlockStatement(masterParser.block());
        } else {
            body = statement();
        }

        return new ForStatement(keyword, type, name, valueType, valueName, range, body);
    }

    public Statement returnStatement() {
        Token keyword = previous();
        Expression value = null;
        if (!check(NEWLINE) && !check(EOF) && !check(DEDENT)) {
            value = expressionParser.expression();
        }
        return new ReturnStatement(keyword, value);
    }

    public Statement matchStatement() {
        Token keyword = previous();
        Expression condition = expressionParser.expression();
        consume(COLON, "Expect ':' after match condition.");
        consume(NEWLINE, "Expect newline after match colon.");
        consume(INDENT, "Expect indentation in match body.");

        List<MatchStatement.Case> cases = new ArrayList<>();
        Statement defaultBranch = null;

        while (!check(DEDENT) && !isAtEnd()) {
            if (match(DEFAULT)) {
                consume(COLON, "Expect ':' after default.");
                if (match(NEWLINE)) {
                    consume(INDENT, "Expect indentation after newline in default branch.");
                    defaultBranch = new BlockStatement(masterParser.block());
                } else {
                    defaultBranch = statement();
                }
            } else {
                List<Expression> patterns = new ArrayList<>();
                do {
                    patterns.add(expressionParser.expression());
                } while (match(COMMA));

                consume(COLON, "Expect ':' after match patterns.");
                Statement branch;
                if (match(NEWLINE)) {
                    consume(INDENT, "Expect indentation after newline in match branch.");
                    branch = new BlockStatement(masterParser.block());
                } else {
                    branch = statement();
                }
                cases.add(new MatchStatement.Case(patterns, branch));
            }
            if (isAtEnd()) break;
        }

        consume(DEDENT, "Expect dedent after match body.");
        return new MatchStatement(keyword, condition, cases, defaultBranch);
    }

    public Statement printStatement() {
        Token keyword = previous();
        Expression expr = expressionParser.expression();
        consume(NEWLINE, "Expect newline after print statement.");
        return new PrintStatement(keyword, expr);
    }

    public Statement passStatement() {
        consume(NEWLINE, "Expect newline after pass.");
        return new PassStatement();
    }

    public Statement dispatchStatement() {
        Token eventName = consume(IDENTIFIER, "Expect event name after dispatch.");
        List<Expression.Argument> arguments = new ArrayList<>();
        if (match(LEFT_PAREN)) {
            if (!check(RIGHT_PAREN)) {
                do {
                    Token name = null;
                    if (check(IDENTIFIER) && checkNext(COLON)) {
                        name = consume(IDENTIFIER, "Expect argument name.");
                        consume(COLON, "Expect ':' after argument name.");
                    }
                    arguments.add(new Expression.Argument(name, expressionParser.expression()));
                } while (match(COMMA));
            }
            consume(RIGHT_PAREN, "Expect ')' after arguments.");
        }
        consume(NEWLINE, "Expect newline after dispatch.");
        return new DispatchStatement(eventName, arguments);
    }

    public Statement breakStatement() {
        Token keyword = previous();
        consume(NEWLINE, "Expect newline after break.");
        return new BreakStatement(keyword);
    }

    public Statement continueStatement() {
        Token keyword = previous();
        consume(NEWLINE, "Expect newline after continue.");
        return new ContinueStatement(keyword);
    }

}
