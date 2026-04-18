package com.terminalvelocitycabbage.tvscript.parsing;

import com.terminalvelocitycabbage.tvscript.TVScript;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import static com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            if (match(IMPORT)) return importDeclaration();
            if (match(CLASS)) return classDeclaration();
            if (match(TRAIT)) return traitDeclaration();
            if (match(MAIN)) return mainDeclaration();
            if (match(FUNCTION)) {
                return functionDeclaration("function");
            }

            if (match(VAR, CONST, TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE)) {
                return varDeclaration(previous());
            }

            if (check(IDENTIFIER) && checkNext(IDENTIFIER)) {
                advance();
                return varDeclaration(previous());
            }

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Statement importDeclaration() {
        Token importKeyword = previous();
        Token first = consume(IDENTIFIER, "Expect module path after 'import'.");
        StringBuilder modulePath = new StringBuilder(first.lexeme());
        while (match(DOT)) {
            Token segment = consume(IDENTIFIER, "Expect module path segment after '.'.");
            modulePath.append('.').append(segment.lexeme());
        }

        List<ImportStatement.ImportItem> items = new ArrayList<>();
        if (match(COLON)) {
            if (match(LEFT_BRACKET)) {
                if (!check(RIGHT_BRACKET)) {
                    items.add(importItem());
                    while (match(COMMA)) {
                        items.add(importItem());
                    }
                }
                consume(RIGHT_BRACKET, "Expect ']' after import block items.");
            } else if (match(NEWLINE)) {
                consume(INDENT, "Expect indentation after newline in import block.");
                while (!check(DEDENT) && !isAtEnd()) {
                    if (match(NEWLINE)) {
                        continue;
                    }
                    items.add(importItem());
                    if (!check(DEDENT)) {
                        consume(NEWLINE, "Expect newline after import item.");
                    }
                }
                consume(DEDENT, "Expect dedent after import block.");
            } else {
                throw error(peek(), "Expect '[' or newline after ':' in import statement.");
            }
        }

        Token moduleToken = new Token(IDENTIFIER, modulePath.toString(), null, importKeyword.line());
        return new ImportStatement(moduleToken, items);
    }

    private ImportStatement.ImportItem importItem() {
        Token name = consume(IDENTIFIER, "Expect import item name.");
        Token alias = null;
        if (match(AS)) {
            alias = consume(IDENTIFIER, "Expect alias name after 'as'.");
        }
        return new ImportStatement.ImportItem(name, alias);
    }

    private Statement varDeclaration(Token typeToken) {
        boolean isConst = typeToken.type() == CONST;
        Token finalType = typeToken;

        if (isConst && match(TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, FUNCTION)) {
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
        if (match(RETURN)) return returnStatement();
        if (match(PRINT)) return printStatement();
        if (match(PASS)) return passStatement();
        if (match(INDENT)) return new BlockStatement(block());

        return expressionStatement();
    }

    private Statement returnStatement() {
        Token keyword = previous();
        Expression value = null;
        if (!check(NEWLINE) && !check(EOF) && !check(DEDENT)) {
            value = expression();
        }
        return new ReturnStatement(keyword, value);
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

    private Statement mainDeclaration() {
        Token keyword = previous();
        List<FunctionStatement.Parameter> parameters = new ArrayList<>();
        if (match(LEFT_PAREN)) {
            if (!check(RIGHT_PAREN)) {
                do {
                    parameters.add(parameter());
                } while (match(COMMA));
            }
            consume(RIGHT_PAREN, "Expect ')' after main parameters.");
        }
        consume(COLON, "Expect ':' after main.");

        Statement body;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in main.");
            body = new BlockStatement(block());
        } else {
            body = statement();
            if (!(body instanceof BlockStatement)) {
                List<Statement> stmts = new ArrayList<>();
                stmts.add(body);
                body = new BlockStatement(stmts);
            }
        }

        return new FunctionStatement(keyword, parameters, null, body, false, false);
    }

    private Statement classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name.");

        Token superclass = null;
        List<Token> traits = new ArrayList<>();

        if (match(LESS)) {
            if (match(IDENTIFIER)) {
                superclass = previous();
            }

            if (match(LEFT_BRACKET)) {
                do {
                    traits.add(consume(IDENTIFIER, "Expect trait name."));
                } while (match(COMMA));
                consume(RIGHT_BRACKET, "Expect ']' after traits.");
            } else if (superclass == null) {
                throw error(peek(), "Expect superclass or trait list after '<'.");
            }
        }

        consume(COLON, "Expect ':' before class body.");
        consume(NEWLINE, "Expect newline before class body.");
        consume(INDENT, "Expect indentation before class body.");

        List<VarStatement> fields = new ArrayList<>();
        List<FunctionStatement> methods = new ArrayList<>();
        List<FunctionStatement> staticMethods = new ArrayList<>();
        List<FunctionStatement> constructors = new ArrayList<>();

        while (!check(DEDENT) && !isAtEnd()) {
            if (match(TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, VAR, CONST)) {
                fields.add((VarStatement)varDeclaration(previous()));
            } else if (check(IDENTIFIER) && checkNext(IDENTIFIER)) {
                advance();
                fields.add((VarStatement)varDeclaration(previous()));
            } else if (match(CONSTRUCTOR)) {
                constructors.add(constructorDeclaration());
            } else if (match(FUNCTION)) {
                staticMethods.add((FunctionStatement)functionDeclaration("static function"));
            } else if (match(DEFAULT, OVERRIDE) || check(IDENTIFIER)) {
                methods.add(methodDeclaration());
            } else if (match(PASS)) {
                // Allow pass in class body
            } else if (match(NEWLINE)) {
                // Ignore empty lines
            } else {
                throw error(peek(), "Expect field or method declaration in class body.");
            }

            while (match(NEWLINE));
        }

        consume(DEDENT, "Expect dedent after class body.");

        if (constructors.isEmpty()) {
            TVScript.error(name, "Class must have a constructor.");
            throw new ParseError();
        }

        return new ClassStatement(name, superclass, traits, fields, methods, staticMethods, constructors);
    }

    private Statement traitDeclaration() {
        Token name = consume(IDENTIFIER, "Expect trait name.");

        List<Token> traits = new ArrayList<>();
        if (match(LESS)) {
            consume(LEFT_BRACKET, "Expect '[' after '<' in trait inheritance.");
            do {
                traits.add(consume(IDENTIFIER, "Expect trait name."));
            } while (match(COMMA));
            consume(RIGHT_BRACKET, "Expect ']' after traits.");
        }

        consume(COLON, "Expect ':' before trait body.");
        consume(NEWLINE, "Expect newline before trait body.");
        consume(INDENT, "Expect indentation before trait body.");

        List<VarStatement> fields = new ArrayList<>();
        List<FunctionStatement> methods = new ArrayList<>();

        while (!check(DEDENT) && !isAtEnd()) {
            if (match(CONST)) {
                fields.add((VarStatement)varDeclaration(previous()));
            } else if (match(DEFAULT, OVERRIDE) || check(IDENTIFIER)) {
                methods.add(methodDeclaration());
            } else if (match(PASS)) {
                // Allow pass in trait body
            } else if (match(NEWLINE)) {
                // Ignore empty lines
            } else {
                throw error(peek(), "Expect constant field or method declaration in trait body.");
            }
            while (match(NEWLINE));
        }

        consume(DEDENT, "Expect dedent after trait body.");

        return new TraitStatement(name, traits, fields, methods);
    }

    private FunctionStatement methodDeclaration() {
        boolean isDefault = match(DEFAULT);
        boolean isOverride = false;
        if (!isDefault) {
            isOverride = match(OVERRIDE);
        }

        Token name = consume(IDENTIFIER, "Expect method name.");
        return finishFunctionDeclaration(name, "method", isOverride, isDefault);
    }

    private FunctionStatement constructorDeclaration() {
        Token keyword = previous();
        return finishFunctionDeclaration(keyword, "constructor", false, false);
    }

    private FunctionStatement finishFunctionDeclaration(Token name, String kind, boolean isOverride, boolean isDefault) {
        consume(LEFT_PAREN, "Expect '(' after " + kind + ".");
        List<FunctionStatement.Parameter> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                parameters.add(parameter());
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        Token returnType = null;
        if (match(ARROW)) {
            returnType = consumeType("Expect return type.");
        }

        Statement body = null;
        if (match(COLON)) {
            if (match(NEWLINE)) {
                consume(INDENT, "Expect indentation after newline in " + kind + " declaration.");
                body = new BlockStatement(block());
            } else {
                body = statement();
                if (!(body instanceof BlockStatement)) {
                    List<Statement> stmts = new ArrayList<>();
                    stmts.add(body);
                    body = new BlockStatement(stmts);
                }
            }
        }

        return new FunctionStatement(name, parameters, returnType, body, isOverride, isDefault);
    }

    private Statement functionDeclaration(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        return finishFunctionDeclaration(name, kind, false, false);
    }

    private FunctionStatement.Parameter parameter() {
        Token type = consumeType("Expect parameter type.");
        Token name = consume(IDENTIFIER, "Expect parameter name.");
        Expression defaultValue = null;
        if (match(EQUAL)) {
            defaultValue = expression();
        }
        return new FunctionStatement.Parameter(type, name, defaultValue);
    }

    private Token consumeType(String message) {
        if (match(TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, FUNCTION, IDENTIFIER)) {
            return previous();
        }
        throw error(peek(), message);
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
            } else if (expr instanceof GetExpression) {
                GetExpression get = (GetExpression)expr;
                return new SetExpression(get.object(), get.name(), value);
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
        Expression expr = typeCheck();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expression right = typeCheck();
            expr = new BinaryExpression(expr, operator, right);
        }

        return expr;
    }

    private Expression typeCheck() {
        Expression expr = range();

        while (match(IS, HAS, AS)) {
            Token operator = previous();
            Token typeName = consumeType("Expect type name after '" + operator.lexeme() + "'.");
            Token alias = null;
            if (operator.type() == IS && match(ARROW)) {
                alias = consume(IDENTIFIER, "Expect alias name after '->'.");
            }
            expr = new TypeBinaryExpression(expr, operator, typeName, alias);
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

        return call();
    }

    private Expression call() {
        Expression expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr, false);
            } else if (match(DOT)) {
                if (match(SUPER)) {
                    Token superKeyword = previous();
                    if (!(expr instanceof VariableExpression)) {
                        throw error(superKeyword, "Only traits can be used with '.super'.");
                    }
                    Token traitName = ((VariableExpression)expr).name();
                    consume(DOT, "Expect '.' after 'super'.");
                    Token method = consume(IDENTIFIER, "Expect trait method name.");
                    expr = new SuperExpression(superKeyword, method, traitName);
                } else {
                    Token name = consume(IDENTIFIER, "Expect property name after '.'.");
                    expr = new GetExpression(expr, name);
                }
            } else {
                break;
            }
        }

        return expr;
    }

    private Expression primary() {
        if (match(NATIVE)) {
            Token nativeKeyword = previous();
            Token name = consume(IDENTIFIER, "Expect native function name after 'native'.");
            if (match(LEFT_PAREN)) {
                return finishCall(new VariableExpression(name), true);
            }
            return new NativeExpression(nativeKeyword, name);
        }

        if (match(NEW)) {
            Token keyword = previous();
            Expression callee = call();

            if (callee instanceof CallExpression) {
                CallExpression call = (CallExpression)callee;
                return new NewExpression(keyword, call.callee(), call.arguments());
            } else {
                throw error(keyword, "Expect constructor call after 'new'.");
            }
        }

        if (match(SUPER)) {
            Token keyword = previous();
            if (check(LEFT_PAREN)) {
                // This is a super constructor call
                return new SuperExpression(keyword, new Token(IDENTIFIER, "constructor", null, keyword.line()), null);
            }
            consume(DOT, "Expect '.' after 'super'.");
            Token method = consume(IDENTIFIER, "Expect superclass method name.");
            return new SuperExpression(keyword, method, null);
        }

        if (match(THIS)) return new ThisExpression(previous());

        if (match(FUNCTION)) {
            return anonymousFunctionExpression();
        }

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
            // Check if this is an anonymous function (params) -> ...
            int checkpoint = current;
            try {
                List<FunctionStatement.Parameter> parameters = new ArrayList<>();
                if (!check(RIGHT_PAREN)) {
                    do {
                        parameters.add(parameter());
                    } while (match(COMMA));
                }
                consume(RIGHT_PAREN, "Expect ')' after parameters.");
                if (match(ARROW)) {
                    Token returnType = null;
                    if (check(TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, FUNCTION, IDENTIFIER)) {
                        returnType = previous();
                    }

                    Statement body;
                    if (match(COLON)) {
                        if (match(NEWLINE)) {
                            consume(INDENT, "Expect indentation after newline in function expression.");
                            body = new BlockStatement(block());
                        } else {
                            body = statement();
                            if (!(body instanceof BlockStatement)) {
                                List<Statement> stmts = new ArrayList<>();
                                stmts.add(body);
                                body = new BlockStatement(stmts);
                            }
                        }
                    } else {
                        // Single expression body
                        Expression expr = expression();
                        List<Statement> stmts = new ArrayList<>();
                        stmts.add(new ReturnStatement(null, expr)); // null keyword is okay for implicit return
                        body = new BlockStatement(stmts);
                    }
                    return new FunctionExpression(parameters, returnType, body);
                }
            } catch (ParseError e) {
                // Not a function, backtrack
                current = checkpoint;
            }

            Expression expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new GroupingExpression(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    private Expression finishCall(Expression callee, boolean nativeCall) {
        List<CallExpression.Argument> arguments = new ArrayList<>();
        Set<String> argumentNames = new HashSet<>();
        if (!check(RIGHT_PAREN)) {
            do {
                Token name = consume(IDENTIFIER, "Expect argument name.");
                if (!argumentNames.add(name.lexeme())) {
                    TVScript.error(name, "Duplicate argument '" + name.lexeme() + "'.");
                    throw new ParseError();
                }
                consume(COLON, "Expect ':' after argument name.");
                Expression value = expression();
                arguments.add(new CallExpression.Argument(name, value));
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");
        return new CallExpression(callee, paren, arguments, nativeCall);
    }

    private Expression anonymousFunctionExpression() {
        // 'function' was already matched
        Token name = null;
        if (check(IDENTIFIER)) {
            name = advance();
        }
        consume(LEFT_PAREN, "Expect '(' after function keyword.");
        List<FunctionStatement.Parameter> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                parameters.add(parameter());
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        Token returnType = null;
        if (match(ARROW)) {
            returnType = consumeType("Expect return type.");
        }

        consume(COLON, "Expect ':' before function body.");
        Statement body;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in function expression.");
            body = new BlockStatement(block());
        } else {
            body = statement();
            if (!(body instanceof BlockStatement)) {
                List<Statement> stmts = new ArrayList<>();
                stmts.add(body);
                body = new BlockStatement(stmts);
            }
        }

        // If it had a name, it's still an expression but we might want to store it in a FunctionStatement wrapper or just treat it as FunctionExpression
        return new FunctionExpression(parameters, returnType, body);
    }
    
    private boolean check(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) return true;
        }
        return false;
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

    private boolean checkNext(TokenType type) {
        if (isAtEnd()) return false;
        if (tokens.get(current + 1).type() == EOF) return false;
        return tokens.get(current + 1).type() == type;
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
                case IMPORT:
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
