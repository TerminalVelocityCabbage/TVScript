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
    private static final Set<String> SUPPORTED_OPERATOR_NAMES = Set.of(
            "add", "subtract", "multiply", "divide", "modulo", "compare", "negative"
    );

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
            Token visibility = null;
            if (match(PUBLIC, PRIVATE, PROTECTED, MODULE)) {
                visibility = previous();
                if (match(PUBLIC, PRIVATE, PROTECTED, MODULE)) {
                    throw error(previous(), "Only one visibility modifier is allowed.");
                }
            }

            if (match(IMPORT)) return importDeclaration();
            if (check(NATIVE) && checkNext(CLASS)) {
                advance();
                advance();
                return classDeclaration(true, visibility);
            }
            if (match(CLASS)) return classDeclaration(false, visibility);
            if (match(TRAIT)) return traitDeclaration();
            if (match(TYPE)) return typeDeclaration();
            if (match(CONSTRAINT)) return constraintDeclaration();
            if (check(NATIVE) && checkNext(EVENT)) {
                advance();
                advance();
                return eventDeclaration(true);
            }
            if (match(EVENT)) return eventDeclaration(false);
            if (match(ON)) return onDeclaration();
            if (match(FUNCTION)) {
                return functionDeclaration("function", visibility);
            }

            // Check for type-prefixed function or variable
            if (check(VAR, CONST, TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, LIST, SET, MAP) ||
                (check(IDENTIFIER) && looksLikeTypedVariableDeclaration())) {

                Token typeToken = consumeType("Expect type.");
                if (check(IDENTIFIER) && checkNext(LEFT_PAREN)) {
                    Token name = advance();
                    return finishFunctionDeclaration(name, "function", false, false, visibility, typeToken);
                }
                return varDeclaration(typeToken, visibility);
            }

            if (visibility != null) {
                throw error(visibility, "Visibility modifiers are not allowed here.");
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
        while (check(DOT)) {
            advance();
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
                    while (match(NEWLINE)) { /* skip empty lines */ }
                    if (check(DEDENT)) break;
                    
                    items.add(importItem());
                    
                    if (match(COMMA)) {
                        while (match(NEWLINE)) { /* skip newline after comma */ }
                    } else if (match(NEWLINE)) {
                        // next item or dedent
                    } else if (!check(DEDENT)) {
                        throw error(peek(), "Expect ',' or newline after import item.");
                    }
                }
                consume(DEDENT, "Expect dedent after import block.");
            } else {
                throw error(peek(), "Expect '[' or newline after ':' in import statement.");
            }
        }

        Token moduleToken = new Token(IDENTIFIER, modulePath.toString(), null, importKeyword.line());
        Token alias = null;
        if (match(AS)) {
            alias = consume(IDENTIFIER, "Expect alias name after 'as'.");
        }
        return new ImportStatement(moduleToken, items, alias);
    }

    private ImportStatement.ImportItem importItem() {
        Token name = consume(IDENTIFIER, "Expect import item name.");
        Token alias = null;
        if (match(AS)) {
            alias = consume(IDENTIFIER, "Expect alias name after 'as'.");
        }
        return new ImportStatement.ImportItem(name, alias);
    }

    private Statement varDeclaration(Token typeToken, Token visibility) {
        boolean isConst = typeToken.type() == CONST;
        Token finalType = typeToken;

        if (isConst && match(TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, FUNCTION, LIST, SET, MAP)) {
            finalType = previous();
        }

        if ((finalType.type() == LIST || finalType.type() == SET || finalType.type() == MAP) && match(LEFT_BRACKET)) {
            finalType = parseParameterizedType(finalType);
        }

        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expression initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        } else if (isConst) {
            TVScript.error(name, "Constant variable must be initialized.");
            throw new ParseError();
        }

        return new VarStatement(finalType, name, initializer, isConst, visibility);
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
        if (match(DISPATCH)) return dispatchStatement();
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

        Expression range = expression();
        consume(COLON, "Expect ':' after for loop.");

        Statement body;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in for statement.");
            body = new BlockStatement(block());
        } else {
            body = statement();
        }

        return new ForStatement(keyword, type, name, valueType, valueName, range, body);
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

    private Statement eventDeclaration(boolean isNative) {
        Token name = consume(IDENTIFIER, "Expect event name.");
        consume(COLON, "Expect ':' after event name.");

        List<VarStatement> fields = new ArrayList<>();
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in event definition.");
            while (!check(DEDENT) && !isAtEnd()) {
                if (match(NEWLINE)) continue;
                Token type = consumeType("Expect field type.");
                fields.add((VarStatement) varDeclaration(type, null));
                if (!check(DEDENT) && !check(EOF)) {
                    consume(NEWLINE, "Expect newline after event field.");
                }
            }
            consume(DEDENT, "Expect dedent after event fields.");
        } else {
            Token type = consumeType("Expect field type.");
            fields.add((VarStatement) varDeclaration(type, null));
        }

        return new EventStatement(name, fields, isNative);
    }

    private Statement onDeclaration() {
        Token eventName = consume(IDENTIFIER, "Expect event name after 'on'.");

        List<OnStatement.ListenerParameter> parameters = new ArrayList<>();
        if (match(LEFT_PAREN)) {
            if (!check(RIGHT_PAREN)) {
                do {
                    Token type = consumeType("Expect parameter type.");
                    Token name = consume(IDENTIFIER, "Expect parameter name.");
                    Expression filter = null;
                    if (match(COLON)) {
                        filter = expression();
                    }
                    parameters.add(new OnStatement.ListenerParameter(type, name, filter));
                } while (match(COMMA));
            }
            consume(RIGHT_PAREN, "Expect ')' after parameters.");
        }

        consume(COLON, "Expect ':' before on body.");

        Statement body;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in 'on' listener.");
            body = new BlockStatement(block());
        } else {
            body = statement();
        }

        return new OnStatement(eventName, parameters, body);
    }

    private Statement dispatchStatement() {
        Token eventName = consume(IDENTIFIER, "Expect event name after 'dispatch'.");

        List<Argument> arguments = new ArrayList<>();
        if (match(LEFT_PAREN)) {
            if (!check(RIGHT_PAREN)) {
                do {
                    Token argName = null;
                    if (check(IDENTIFIER) && checkNext(COLON)) {
                        argName = advance();
                        advance(); // consume ':'
                    }
                    Expression value = expression();
                    arguments.add(new Argument(argName, value));
                } while (match(COMMA));
            }
            consume(RIGHT_PAREN, "Expect ')' after arguments.");
        }

        return new DispatchStatement(eventName, arguments);
    }

    private Statement constraintDeclaration() {
        Token name = consume(IDENTIFIER, "Expect constraint name.");
        consume(EQUAL, "Expect '=' after constraint name.");

        Token superclassConstraint = null;
        List<Token> traitConstraints = new ArrayList<>();

        if (match(LEFT_BRACKET)) {
            if (!check(RIGHT_BRACKET)) {
                do {
                    traitConstraints.add(consume(IDENTIFIER, "Expect trait name."));
                } while (match(COMMA));
            }
            consume(RIGHT_BRACKET, "Expect ']' after trait constraints.");
        } else {
            superclassConstraint = consume(IDENTIFIER, "Expect class or constraint name.");
            if (match(LEFT_BRACKET)) {
                if (!check(RIGHT_BRACKET)) {
                    do {
                        traitConstraints.add(consume(IDENTIFIER, "Expect trait name."));
                    } while (match(COMMA));
                }
                consume(RIGHT_BRACKET, "Expect ']' after trait constraints.");
            }
        }

        return new ConstraintStatement(name, superclassConstraint, traitConstraints);
    }

    private Statement classDeclaration(boolean isNative, Token visibility) {
        Token name = consume(IDENTIFIER, "Expect class name.");
        List<GenericParameter> genericParameters = parseGenericParameters();

        Token superclass = null;
        List<Token> traits = new ArrayList<>();

        if (match(LEFT_BRACKET)) {
            if (!check(RIGHT_BRACKET)) {
                do {
                    traits.add(consume(IDENTIFIER, "Expect trait name."));
                } while (match(COMMA));
            }
            consume(RIGHT_BRACKET, "Expect ']' after traits.");
        }

        if (match(LESS)) {
            superclass = consume(IDENTIFIER, "Expect superclass name after '<'.");
        }

        consume(COLON, "Expect ':' before class body.");
        consume(NEWLINE, "Expect newline before class body.");
        consume(INDENT, "Expect indentation before class body.");

        List<VarStatement> fields = new ArrayList<>();
        List<FunctionStatement> methods = new ArrayList<>();
        List<FunctionStatement> staticMethods = new ArrayList<>();
        List<FunctionStatement> constructors = new ArrayList<>();

        while (!check(DEDENT) && !isAtEnd()) {
            Token memberVisibility = null;
            if (match(PUBLIC, PRIVATE, PROTECTED, MODULE)) {
                memberVisibility = previous();
                if (match(PUBLIC, PRIVATE, PROTECTED, MODULE)) {
                    throw error(previous(), "Only one visibility modifier is allowed.");
                }
            }

            if (match(TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, LIST, SET, MAP, VAR, CONST)) {
                Token typeToken = previous();
                if (check(IDENTIFIER) && checkNext(LEFT_PAREN)) {
                    Token memberName = advance();
                    methods.add(finishFunctionDeclaration(memberName, "method", false, false, memberVisibility, typeToken));
                } else {
                    if (isNative && typeToken.type() != CONST) {
                        throw error(typeToken, "Native classes cannot declare instance fields.");
                    }
                    fields.add((VarStatement) varDeclaration(typeToken, memberVisibility));
                }
            } else if (check(IDENTIFIER) && looksLikeTypedVariableDeclaration()) {
                Token typeToken = consumeType("Expect field type.");
                if (check(IDENTIFIER) && checkNext(LEFT_PAREN)) {
                    Token memberName = advance();
                    methods.add(finishFunctionDeclaration(memberName, "method", false, false, memberVisibility, typeToken));
                } else {
                    if (isNative) {
                        throw error(peek(), "Native classes cannot declare instance fields.");
                    }
                    fields.add((VarStatement) varDeclaration(typeToken, memberVisibility));
                }
            } else if (match(CONSTRUCTOR)) {
                if (isNative) {
                    throw error(previous(), "Native classes cannot declare constructors.");
                }
                constructors.add(constructorDeclaration(memberVisibility));
            } else if (match(FUNCTION)) {
                staticMethods.add((FunctionStatement)functionDeclaration("static function", memberVisibility));
            } else if (match(DEFAULT, OVERRIDE) || check(IDENTIFIER)) {
                methods.add(methodDeclaration(memberVisibility));
            } else if (match(PASS)) {
                if (memberVisibility != null) throw error(memberVisibility, "Visibility modifiers are not allowed on 'pass'.");
            } else if (match(NEWLINE)) {
                if (memberVisibility != null) throw error(memberVisibility, "Visibility modifiers are not allowed on empty lines.");
            } else {
                throw error(peek(), "Expect field or method declaration in class body.");
            }

            while (match(NEWLINE));
        }

        consume(DEDENT, "Expect dedent after class body.");

        if (constructors.isEmpty() && !isNative) {
            TVScript.error(name, "Class must have a constructor.");
            throw new ParseError();
        }

        return new ClassStatement(name, genericParameters, superclass, traits, fields, methods, staticMethods, constructors, isNative, visibility);
    }

    private Statement traitDeclaration() {
        Token name = consume(IDENTIFIER, "Expect trait name.");
        List<GenericParameter> genericParameters = parseGenericParameters();

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
                fields.add((VarStatement)varDeclaration(previous(), null));
            } else if (match(DEFAULT, OVERRIDE) || check(IDENTIFIER)) {
                methods.add(methodDeclaration(null));
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

        return new TraitStatement(name, genericParameters, traits, fields, methods);
    }

    private Statement typeDeclaration() {
        Token name = consume(IDENTIFIER, "Expect type name.");
        List<GenericParameter> genericParameters = parseGenericParameters();

        List<Token> traits = new ArrayList<>();
        if (match(LESS)) {
            consume(LEFT_BRACKET, "Expect '[' after '<' in type trait list.");
            do {
                traits.add(consume(IDENTIFIER, "Expect trait name."));
            } while (match(COMMA));
            consume(RIGHT_BRACKET, "Expect ']' after type trait list.");
        }

        consume(COLON, "Expect ':' before type body.");
        consume(NEWLINE, "Expect newline before type body.");
        consume(INDENT, "Expect indentation before type body.");

        List<VarStatement> fields = new ArrayList<>();
        List<FunctionStatement> methods = new ArrayList<>();
        List<FunctionStatement> operators = new ArrayList<>();

        while (!check(DEDENT) && !isAtEnd()) {
            if (match(TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, LIST, SET, MAP, VAR, CONST)) {
                VarStatement field = (VarStatement) varDeclaration(previous(), null);
                if (!field.isConst()) {
                    field = new VarStatement(field.type(), field.name(), field.initializer(), true, null);
                }
                fields.add(field);
            } else if (check(IDENTIFIER) && looksLikeTypedVariableDeclaration()) {
                Token type = consumeType("Expect field type.");
                VarStatement field = (VarStatement) varDeclaration(type, null);
                if (!field.isConst()) {
                    field = new VarStatement(field.type(), field.name(), field.initializer(), true, null);
                }
                fields.add(field);
            } else if (match(DEFAULT, OVERRIDE) || (check(IDENTIFIER) && checkNext(LEFT_PAREN))) {
                methods.add(methodDeclaration(null));
            } else if (match(OPERATOR)) {
                operators.add(operatorDeclaration(name));
            } else if (match(PASS)) {
                // Allow pass in type body
            } else if (match(NEWLINE)) {
                // Ignore empty lines
            } else {
                throw error(peek(), "Expect field, method or operator declaration in type body.");
            }

            while (match(NEWLINE));
        }

        consume(DEDENT, "Expect dedent after type body.");
        return new TypeStatement(name, genericParameters, traits, fields, methods, operators);
    }

    private FunctionStatement operatorDeclaration(Token ownerType) {
        Token operatorName = consume(IDENTIFIER, "Expect operator name.");
        if (!SUPPORTED_OPERATOR_NAMES.contains(operatorName.lexeme())) {
            throw error(operatorName, "Unsupported operator overload '" + operatorName.lexeme() + "'.");
        }

        consume(LEFT_PAREN, "Expect '(' after operator name.");
        List<FunctionStatement.Parameter> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                parameters.add(operatorParameter(ownerType));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after operator parameters.");

        int expectedArity = operatorName.lexeme().equals("negative") ? 1 : 2;
        if (parameters.size() != expectedArity) {
            throw error(operatorName, "Operator '" + operatorName.lexeme() + "' expects " + expectedArity + " parameter(s).");
        }

        Token returnType = null;
        if (match(ARROW)) {
            returnType = consumeType("Expect return type.");
        }
        if (returnType == null) {
            if (operatorName.lexeme().equals("compare")) {
                returnType = new Token(TYPE_DECIMAL, "decimal", null, operatorName.line());
            } else {
                returnType = ownerType;
            }
        }

        consume(COLON, "Expect ':' before operator body.");
        Statement body;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation after newline in operator declaration.");
            body = new BlockStatement(block());
        } else {
            body = statement();
            if (!(body instanceof BlockStatement)) {
                List<Statement> stmts = new ArrayList<>();
                stmts.add(body);
                body = new BlockStatement(stmts);
            }
        }

        return new FunctionStatement(operatorName, parameters, returnType, body, List.of(), false, false, null);
    }

    private FunctionStatement.Parameter operatorParameter(Token ownerType) {
        if (check(IDENTIFIER) && (checkNext(COMMA) || checkNext(RIGHT_PAREN))) {
            Token inferredName = advance();
            return new FunctionStatement.Parameter(ownerType, inferredName, null);
        }

        Token type = consumeType("Expect operator parameter type.");
        Token name = consume(IDENTIFIER, "Expect operator parameter name.");
        return new FunctionStatement.Parameter(type, name, null);
    }

    private FunctionStatement methodDeclaration(Token visibility) {
        boolean isDefault = match(DEFAULT);
        boolean isOverride = false;
        if (!isDefault) {
            isOverride = match(OVERRIDE);
        }

        Token name = consume(IDENTIFIER, "Expect method name.");
        return finishFunctionDeclaration(name, "method", isOverride, isDefault, visibility, null);
    }

    private FunctionStatement constructorDeclaration(Token visibility) {
        Token keyword = previous();
        return finishFunctionDeclaration(keyword, "constructor", false, false, visibility, null);
    }

    private FunctionStatement finishFunctionDeclaration(Token name, String kind, boolean isOverride, boolean isDefault, Token visibility, Token returnType) {
        List<GenericParameter> genericParameters = parseGenericParameters();
        consume(LEFT_PAREN, "Expect '(' after " + kind + ".");
        List<FunctionStatement.Parameter> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                parameters.add(parameter());
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        if (returnType == null && match(ARROW)) {
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

        return new FunctionStatement(name, parameters, returnType, body, genericParameters, isOverride, isDefault, visibility);
    }

    private Statement functionDeclaration(String kind, Token visibility) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        return finishFunctionDeclaration(name, kind, false, false, visibility, null);
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

    private List<GenericParameter> parseGenericParameters() {
        if (!check(LESS) || !looksLikeGenericParameterDeclaration()) {
            return List.of();
        }
        consume(LESS, "Expect '<' before generic type parameters.");

        List<GenericParameter> genericParameters = new ArrayList<>();
        if (!check(GREATER)) {
            do {
                Token parameterName = consume(IDENTIFIER, "Expect generic type parameter name.");
                Token superclassConstraint = null;
                List<Token> traitConstraints = new ArrayList<>();

                if (match(TILDE)) {
                    if (check(IDENTIFIER)) {
                        superclassConstraint = advance();
                    }

                    if (match(LEFT_BRACKET)) {
                        if (!check(RIGHT_BRACKET)) {
                            do {
                                traitConstraints.add(consume(IDENTIFIER, "Expect trait constraint name."));
                            } while (match(COMMA));
                        }
                        consume(RIGHT_BRACKET, "Expect ']' after generic trait constraints.");
                    } else if (superclassConstraint == null) {
                        throw error(peek(), "Expect superclass constraint or trait constraint list after '~'.");
                    }
                } else if (match(AMPERSAND)) {
                    throw error(previous(), "Use '~' and bracketed trait constraints for generic constraints.");
                }

                genericParameters.add(new GenericParameter(parameterName, superclassConstraint, traitConstraints));
            } while (match(COMMA));
        }

        consume(GREATER, "Expect '>' after generic type parameters.");
        return genericParameters;
    }

    private boolean looksLikeGenericParameterDeclaration() {
        if (!check(LESS)) {
            return false;
        }

        int depth = 0;
        int index = current;
        while (index < tokens.size()) {
            TokenType type = tokens.get(index).type();
            if (type == LESS) {
                depth++;
                index++;
                continue;
            }
            if (type == GREATER) {
                depth--;
                index++;
                if (depth == 0) {
                    if (index >= tokens.size()) {
                        return false;
                    }
                    TokenType next = tokens.get(index).type();
                    return next == LEFT_PAREN || next == LEFT_BRACKET || next == LESS || next == COLON;
                }
                continue;
            }
            if (depth > 0 && (type == COLON || type == NEWLINE || type == EOF)) {
                return false;
            }
            index++;
        }
        return false;
    }

    private Token consumeType(String message) {
        if (match(VAR, CONST, TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, FUNCTION, IDENTIFIER, LIST, SET, MAP)) {
            Token type = previous();

            // Handle dotted identifiers: network.Client
            if (type.type() == IDENTIFIER) {
                StringBuilder fullName = new StringBuilder(type.lexeme());
                while (match(DOT)) {
                    Token segment = consume(IDENTIFIER, "Expect type name segment after '.'.");
                    fullName.append('.').append(segment.lexeme());
                }
                if (fullName.length() > type.lexeme().length()) {
                    type = new Token(IDENTIFIER, fullName.toString(), null, type.line());
                }
            }

            if ((type.type() == LIST || type.type() == SET || type.type() == MAP) && match(LEFT_BRACKET)) {
                return parseParameterizedType(type);
            }
            if (type.type() == IDENTIFIER && match(LESS)) {
                return parseParameterizedType(type);
            }
            return type;
        }
        throw error(peek(), message);
    }

    private Token parseParameterizedType(Token baseType) {
        boolean isCollectionType = baseType.type() == MAP || baseType.type() == LIST || baseType.type() == SET;
        TokenType closingToken = isCollectionType ? RIGHT_BRACKET : GREATER;
        StringBuilder name = new StringBuilder(baseType.lexeme()).append(isCollectionType ? "[" : "<");

        if (baseType.type() == MAP) {
            Token keyType = consumeType("Expect map key type.");
            consume(PIPE, "Expect '|' between map key and value types.");
            Token valueType = consumeType("Expect map value type.");
            name.append(keyType.lexeme()).append("|").append(valueType.lexeme());
        } else if (baseType.type() == LIST || baseType.type() == SET) {
            Token elementType = consumeType("Expect collection element type.");
            name.append(elementType.lexeme());
            if (match(COMMA)) {
                throw error(previous(), "Collection types accept exactly one type argument.");
            }
        } else {
            if (!check(closingToken)) {
                boolean first = true;
                do {
                    Token argumentType = consumeType("Expect type argument.");
                    if (!first) {
                        name.append(", ");
                    }
                    name.append(argumentType.lexeme());
                    first = false;
                } while (match(COMMA));
            }
        }

        if (isCollectionType) {
            consume(RIGHT_BRACKET, "Expect ']' after type arguments.");
            name.append("]");
        } else {
            consume(GREATER, "Expect '>' after type arguments.");
            name.append(">");
        }
        return new Token(baseType.type(), name.toString(), null, baseType.line());
    }

    private void parseCollectionTypeParameters(Token collectionType) {
        consume(LEFT_BRACKET, "Expect '[' after collection type.");
        if (collectionType.type() == LIST || collectionType.type() == SET) {
            consumeType("Expect collection element type.");
            consume(RIGHT_BRACKET, "Expect ']' after collection element type.");
            return;
        }

        consumeType("Expect map key type.");
        consume(PIPE, "Expect '|' between map key and value types.");
        consumeType("Expect map value type.");
        consume(RIGHT_BRACKET, "Expect ']' after map type declaration.");
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
            } else if (expr instanceof IndexExpression) {
                IndexExpression index = (IndexExpression) expr;
                return new IndexSetExpression(index.object(), index.bracket(), index.index(), value);
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
            Expression right = null;
            if (!check(RIGHT_BRACKET) && !check(RIGHT_PAREN) && !check(COMMA) && !check(COLON) && !check(NEWLINE) && !check(EOF)) {
                right = term();
            }
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
                expr = finishCall(expr, false, List.of());
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
            } else if (check(LESS)) {
                List<Token> typeArguments = tryParseTypeArgumentsForCall();
                if (typeArguments != null) {
                    consume(LEFT_PAREN, "Expect '(' after type arguments.");
                    expr = finishCall(expr, false, typeArguments);
                } else {
                    break;
                }
            } else if (match(LEFT_BRACKET)) {
                expr = finishIndex(expr, previous());
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
                return finishCall(new VariableExpression(name), true, List.of());
            }
            return new NativeExpression(nativeKeyword, name);
        }

        if (match(NEW)) {
            return parseNewExpression(previous());
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

    private Expression finishCall(Expression callee, boolean nativeCall, List<Token> typeArguments) {
        List<CallExpression.Argument> arguments = new ArrayList<>();
        Set<String> argumentNames = new HashSet<>();
        boolean hasNamedArguments = false;
        boolean hasPositionalArguments = false;
        if (!check(RIGHT_PAREN)) {
            do {
                if (check(IDENTIFIER) && checkNext(COLON)) {
                    if (hasPositionalArguments) {
                        throw error(peek(), "Cannot use named arguments after positional arguments.");
                    }

                    Token name = consume(IDENTIFIER, "Expect argument name.");
                    if (!argumentNames.add(name.lexeme())) {
                        TVScript.error(name, "Duplicate argument '" + name.lexeme() + "'.");
                        throw new ParseError();
                    }
                    consume(COLON, "Expect ':' after argument name.");
                    Expression value = expression();
                    arguments.add(new CallExpression.Argument(name, value));
                    hasNamedArguments = true;
                } else {
                    if (hasNamedArguments) {
                        throw error(peek(), "Cannot use positional arguments after named arguments.");
                    }

                    arguments.add(new CallExpression.Argument(null, expression()));
                    hasPositionalArguments = true;
                }
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");
        return new CallExpression(callee, paren, arguments, typeArguments, nativeCall);
    }

    private List<Token> tryParseTypeArgumentsForCall() {
        if (!looksLikeTypeArgumentListForCall()) {
            return null;
        }

        consume(LESS, "Expect '<' before type arguments.");
        List<Token> typeArguments = new ArrayList<>();
        if (!check(GREATER)) {
            do {
                typeArguments.add(consumeType("Expect type argument."));
            } while (match(COMMA));
        }
        consume(GREATER, "Expect '>' after type arguments.");
        return typeArguments;
    }

    private boolean looksLikeTypeArgumentListForCall() {
        if (!check(LESS)) {
            return false;
        }

        int depth = 0;
        int index = current;
        while (index < tokens.size()) {
            TokenType type = tokens.get(index).type();
            if (type == LESS) {
                depth++;
                index++;
                continue;
            }
            if (type == GREATER) {
                depth--;
                index++;
                if (depth == 0) {
                    break;
                }
                continue;
            }

            if (depth <= 0) {
                return false;
            }

            if (type == COMMA || type == PIPE) {
                index++;
                continue;
            }

            if (isTypeToken(type)) {
                index++;
                continue;
            }

            return false;
        }

        if (depth != 0 || index >= tokens.size()) {
            return false;
        }

        return tokens.get(index).type() == LEFT_PAREN;
    }

    private boolean isTypeToken(TokenType type) {
        return type == TYPE_INTEGER || type == TYPE_DECIMAL || type == TYPE_STRING || type == TYPE_BOOLEAN
                || type == TYPE_RANGE || type == NONE || type == FUNCTION || type == IDENTIFIER
                || type == LIST || type == SET || type == MAP;
    }

    private boolean looksLikeTypedVariableDeclaration() {
        int endIndex = scanTypeEnd(current);
        return endIndex != -1 && endIndex < tokens.size() && tokens.get(endIndex).type() == IDENTIFIER;
    }

    private int scanTypeEnd(int startIndex) {
        if (startIndex >= tokens.size() || !isTypeToken(tokens.get(startIndex).type())) {
            return -1;
        }

        int index = startIndex;
        // Handle dotted identifiers: network.Client
        while (index < tokens.size() && tokens.get(index).type() == IDENTIFIER) {
            index++;
            if (index < tokens.size() && tokens.get(index).type() == DOT) {
                index++;
                if (index >= tokens.size() || tokens.get(index).type() != IDENTIFIER) {
                    return -1;
                }
            } else {
                break;
            }
        }

        if (index == startIndex) index++;

        if (index < tokens.size() && (tokens.get(index).type() == LESS || tokens.get(index).type() == LEFT_BRACKET)) {
            int angleDepth = 0;
            int squareDepth = 0;
            index++;
            if (tokens.get(startIndex + 1).type() == LESS) {
                angleDepth = 1;
            } else {
                squareDepth = 1;
            }

            while (index < tokens.size() && (angleDepth > 0 || squareDepth > 0)) {
                TokenType type = tokens.get(index).type();
                if (type == LESS) {
                    angleDepth++;
                } else if (type == GREATER) {
                    angleDepth--;
                } else if (type == LEFT_BRACKET) {
                    squareDepth++;
                } else if (type == RIGHT_BRACKET) {
                    squareDepth--;
                }
                index++;
            }
            if (angleDepth != 0 || squareDepth != 0) {
                return -1;
            }
        }

        return index;
    }

    private Expression finishIndex(Expression object, Token bracket) {
        if (match(DOT_DOT)) {
            Expression end = check(RIGHT_BRACKET) ? null : expression();
            consume(RIGHT_BRACKET, "Expect ']' after list slice.");
            return new SliceExpression(object, bracket, null, end);
        }

        Expression indexOrStart = expression();
        if (indexOrStart instanceof RangeExpression range && range.operator().type() == DOT_DOT) {
            consume(RIGHT_BRACKET, "Expect ']' after list slice.");
            return new SliceExpression(object, bracket, range.start(), range.end());
        }

        if (match(DOT_DOT)) {
            Expression end = check(RIGHT_BRACKET) ? null : expression();
            consume(RIGHT_BRACKET, "Expect ']' after list slice.");
            return new SliceExpression(object, bracket, indexOrStart, end);
        }

        consume(RIGHT_BRACKET, "Expect ']' after index.");
        return new IndexExpression(object, bracket, indexOrStart);
    }

    private Expression parseNewExpression(Token keyword) {
        if (match(LIST, SET, MAP)) {
            Token collectionType = previous();
            return parseCollectionLiteral(keyword, collectionType);
        }

        Expression callee = call();
        if (callee instanceof CallExpression call) {
            return new NewExpression(keyword, call.callee(), call.arguments(), call.typeArguments());
        }

        throw error(keyword, "Expect constructor call after 'new'.");
    }

    private Expression parseCollectionLiteral(Token keyword, Token collectionType) {
        if (collectionType.type() == MAP) {
            consume(LEFT_BRACKET, "Expect '[' after 'map'.");
            consume(PIPE, "Expect '|' in map constructor type slot.");
            consume(RIGHT_BRACKET, "Expect ']' after map constructor type slot.");

            List<MapEntry> entries = new ArrayList<>();
            if (match(LEFT_PAREN)) {
                if (!check(RIGHT_PAREN)) {
                    do {
                        Expression key = expression();
                        consume(COLON, "Expect ':' between map key and value.");
                        Expression value = expression();
                        entries.add(new MapEntry(key, value));
                    } while (match(COMMA));
                }
                consume(RIGHT_PAREN, "Expect ')' after map entries.");
            }
            return new CollectionLiteralExpression(keyword, collectionType, null, List.of(), entries);
        }

        consume(LEFT_BRACKET, "Expect '[' after collection type.");

        Expression size = null;
        if (collectionType.type() == SET) {
            consume(RIGHT_BRACKET, "Sets must use empty constructor brackets: new set[].");
        } else {
            if (!check(RIGHT_BRACKET)) {
                size = expression();
            }
            consume(RIGHT_BRACKET, "Expect ']' after list constructor.");
        }

        List<Expression> elements = new ArrayList<>();
        if (match(LEFT_PAREN)) {
            if (size != null) {
                throw error(previous(), "Cannot provide both list size and initializer values.");
            }

            if (!check(RIGHT_PAREN)) {
                do {
                    elements.add(expression());
                } while (match(COMMA));
            }
            consume(RIGHT_PAREN, "Expect ')' after collection elements.");
        }

        return new CollectionLiteralExpression(keyword, collectionType, size, elements, List.of());
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
                case CONSTRAINT:
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
