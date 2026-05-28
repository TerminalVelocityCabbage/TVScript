package com.terminalvelocitycabbage.tvscript.parsing;

import com.terminalvelocitycabbage.tvscript.CompilationContext;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.ast.VisibleElement;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.terminalvelocitycabbage.tvscript.parsing.TokenType.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

public class DeclarationParser extends BaseParser {

    private final Parser masterParser;
    private final ExpressionParser expressionParser;
    private final StatementParser statementParser;

    private static final Set<String> SUPPORTED_OPERATOR_NAMES = Set.of(
            "add", "subtract", "multiply", "divide", "modulo", "compare", "negative"
    );

    public DeclarationParser(List<Token> tokens, CompilationContext context, Parser masterParser, ExpressionParser expressionParser, StatementParser statementParser) {
        super(tokens, context);
        this.masterParser = masterParser;
        this.expressionParser = expressionParser;
        this.statementParser = statementParser;
    }

    public void setCurrent(int current) {
        this.current = current;
    }

    public int getCurrent() {
        return this.current;
    }

    public Statement declaration() {
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
                (check(IDENTIFIER) && masterParser.looksLikeTypedVariableDeclaration())) {

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

            return statementParser.statement();
        } catch (ParseError error) {
            masterParser.synchronize();
            return null;
        }
    }

    public Statement importDeclaration() {
        StringBuilder modulePath = new StringBuilder();
        Token moduleToken = consume(IDENTIFIER, "Expect identifier in import path.");
        modulePath.append(moduleToken.lexeme());
        while (match(DOT)) {
            modulePath.append(".");
            modulePath.append(consume(IDENTIFIER, "Expect identifier after '.'. ").lexeme());
        }

        List<ImportStatement.ImportItem> items = new ArrayList<>();
        if (match(COLON)) {
            consume(LEFT_BRACKET, "Expect '[' after ':' in selective import.");
            if (!check(RIGHT_BRACKET)) {
                do {
                    items.add(importItem());
                } while (match(COMMA));
            }
            consume(RIGHT_BRACKET, "Expect ']' after import items.");
        }

        Token alias = null;
        if (match(AS)) {
            alias = consume(IDENTIFIER, "Expect alias name after 'as'.");
        }

        consume(NEWLINE, "Expect newline after import.");
        return new ImportStatement(new Token(IDENTIFIER, modulePath.toString(), null, moduleToken.line()), items, alias);
    }

    private ImportStatement.ImportItem importItem() {
        Token name = consume(IDENTIFIER, "Expect item name in selective import.");
        Token alias = null;
        if (match(AS)) {
            alias = consume(IDENTIFIER, "Expect alias name after 'as'.");
        }
        return new ImportStatement.ImportItem(name, alias);
    }

    public Statement varDeclaration(Token typeToken, Token visibility) {
        boolean isConst = typeToken.type() == CONST;
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expression initializer = null;
        if (match(EQUAL)) {
            initializer = expressionParser.expression();
        } else if (isConst) {
            throw error(name, "Constant variable must be initialized.");
        }

        consume(NEWLINE, "Expect newline after variable declaration.");
        return new VarStatement(typeToken, name, initializer, isConst, visibility);
    }

    public Statement eventDeclaration(boolean isNative) {
        Token name = consume(IDENTIFIER, "Expect event name.");
        consume(COLON, "Expect ':' after event name.");
        consume(NEWLINE, "Expect newline after event colon.");
        consume(INDENT, "Expect indentation after event declaration.");

        List<VarStatement> fields = new ArrayList<>();
        while (!check(DEDENT) && !isAtEnd()) {
            Token typeToken = consumeType("Expect field type.");
            fields.add((VarStatement) varDeclaration(typeToken, null));
        }

        consume(DEDENT, "Expect dedent after event fields.");
        return new EventStatement(name, fields, isNative);
    }

    public Statement onDeclaration() {
        Token eventName = consume(IDENTIFIER, "Expect event name after 'on'.");
        List<OnStatement.ListenerParameter> parameters = new ArrayList<>();
        if (match(LEFT_PAREN)) {
            if (!check(RIGHT_PAREN)) {
                do {
                    Token type = consumeType("Expect parameter type.");
                    Token paramName = consume(IDENTIFIER, "Expect parameter name.");
                    Expression filter = null;
                    if (match(IF)) {
                        filter = expressionParser.expression();
                    }
                    parameters.add(new OnStatement.ListenerParameter(type, paramName, filter));
                } while (match(COMMA));
            }
            consume(RIGHT_PAREN, "Expect ')' after parameters.");
        }

        consume(COLON, "Expect ':' after listener declaration.");
        consume(NEWLINE, "Expect newline after listener colon.");
        consume(INDENT, "Expect indentation after listener declaration.");
        Statement body = new BlockStatement(masterParser.block());

        return new OnStatement(eventName, parameters, body);
    }

    public Statement constraintDeclaration() {
        Token name = consume(IDENTIFIER, "Expect constraint name.");
        Token superclass = null;
        if (match(LESS)) {
            superclass = consume(IDENTIFIER, "Expect superclass constraint name.");
        }
        List<Token> traits = new ArrayList<>();
        if (match(HAS)) {
            do {
                traits.add(consume(IDENTIFIER, "Expect trait name."));
            } while (match(AND));
        }
        consume(NEWLINE, "Expect newline after constraint declaration.");
        return new ConstraintStatement(name, superclass, traits);
    }

    public Statement classDeclaration(boolean isNative, Token visibility) {
        Token name = consume(IDENTIFIER, "Expect class name.");
        List<GenericParameter> genericParameters = masterParser.parseGenericParameters();

        Token superclass = null;
        if (match(LESS)) {
            superclass = consume(IDENTIFIER, "Expect superclass name.");
        }

        List<Token> traits = new ArrayList<>();
        if (match(HAS)) {
            do {
                traits.add(consume(IDENTIFIER, "Expect trait name."));
            } while (match(AND));
        }

        consume(COLON, "Expect ':' before class body.");
        consume(NEWLINE, "Expect newline after class colon.");
        consume(INDENT, "Expect indentation before class body.");

        List<VarStatement> fields = new ArrayList<>();
        List<FunctionStatement> methods = new ArrayList<>();
        List<FunctionStatement> staticMethods = new ArrayList<>();
        List<FunctionStatement> constructors = new ArrayList<>();

        while (!check(DEDENT) && !isAtEnd()) {
            Token memberVisibility = null;
            if (match(PUBLIC, PRIVATE, PROTECTED, MODULE)) {
                memberVisibility = previous();
            }

            if (match(FUNCTION)) {
                methods.add(finishFunctionDeclaration(consume(IDENTIFIER, "Expect method name."), "method", false, false, memberVisibility, null));
            } else if (match(OVERRIDE)) {
                consume(FUNCTION, "Expect 'function' after 'override'.");
                methods.add(finishFunctionDeclaration(consume(IDENTIFIER, "Expect method name."), "method", true, false, memberVisibility, null));
            } else if (match(CONSTRUCTOR)) {
                constructors.add(finishFunctionDeclaration(previous(), "constructor", false, false, memberVisibility, null));
            } else if (check(VAR, CONST, TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, LIST, SET, MAP) ||
                       (check(IDENTIFIER) && masterParser.looksLikeTypedVariableDeclaration())) {
                Token type = consumeType("Expect type.");
                if (check(IDENTIFIER) && checkNext(LEFT_PAREN)) {
                    Token methodName = advance();
                    methods.add(finishFunctionDeclaration(methodName, "method", false, false, memberVisibility, type));
                } else {
                    fields.add((VarStatement) varDeclaration(type, memberVisibility));
                }
            } else {
                throw error(peek(), "Expect member declaration.");
            }
            while (match(NEWLINE));
        }

        consume(DEDENT, "Expect dedent after class body.");

        if (constructors.isEmpty() && !isNative) {
            throw error(name, "Class must have a constructor.");
        }

        return new ClassStatement(name, genericParameters, superclass, traits, fields, methods, staticMethods, constructors, isNative, visibility);
    }

    public Statement traitDeclaration() {
        Token name = consume(IDENTIFIER, "Expect trait name.");
        List<GenericParameter> genericParameters = masterParser.parseGenericParameters();

        List<Token> traits = new ArrayList<>();
        if (match(HAS)) {
            do {
                traits.add(consume(IDENTIFIER, "Expect trait name."));
            } while (match(AND));
        }

        consume(COLON, "Expect ':' before trait body.");
        consume(NEWLINE, "Expect newline after trait colon.");
        consume(INDENT, "Expect indentation before trait body.");

        List<VarStatement> fields = new ArrayList<>();
        List<FunctionStatement> methods = new ArrayList<>();

        while (!check(DEDENT) && !isAtEnd()) {
            if (match(FUNCTION)) {
                methods.add(finishFunctionDeclaration(consume(IDENTIFIER, "Expect method name."), "method", false, false, null, null));
            } else if (check(VAR, CONST, TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, LIST, SET, MAP) ||
                       (check(IDENTIFIER) && masterParser.looksLikeTypedVariableDeclaration())) {
                Token type = consumeType("Expect type.");
                if (check(IDENTIFIER) && checkNext(LEFT_PAREN)) {
                    methods.add(finishFunctionDeclaration(advance(), "method", false, false, null, type));
                } else {
                    fields.add((VarStatement) varDeclaration(type, null));
                }
            } else {
                throw error(peek(), "Expect member declaration.");
            }
            while (match(NEWLINE));
        }

        consume(DEDENT, "Expect dedent after trait body.");
        return new TraitStatement(name, genericParameters, traits, fields, methods);
    }

    public Statement typeDeclaration() {
        Token name = consume(IDENTIFIER, "Expect type name.");
        List<GenericParameter> genericParameters = masterParser.parseGenericParameters();

        List<Token> traits = new ArrayList<>();
        if (match(HAS)) {
            do {
                traits.add(consume(IDENTIFIER, "Expect trait name."));
            } while (match(AND));
        }

        consume(COLON, "Expect ':' before type body.");
        consume(NEWLINE, "Expect newline after type colon.");
        consume(INDENT, "Expect indentation before type body.");

        List<VarStatement> fields = new ArrayList<>();
        List<FunctionStatement> methods = new ArrayList<>();
        List<FunctionStatement> operators = new ArrayList<>();

        while (!check(DEDENT) && !isAtEnd()) {
            if (match(OPERATOR)) {
                operators.add(operatorDeclaration(name));
            } else if (match(FUNCTION)) {
                methods.add(finishFunctionDeclaration(consume(IDENTIFIER, "Expect method name."), "method", false, false, null, null));
            } else if (check(VAR, CONST, TYPE_INTEGER, TYPE_DECIMAL, TYPE_STRING, TYPE_BOOLEAN, TYPE_RANGE, NONE, LIST, SET, MAP) ||
                       (check(IDENTIFIER) && masterParser.looksLikeTypedVariableDeclaration())) {
                Token type = consumeType("Expect type.");
                if (check(IDENTIFIER) && checkNext(LEFT_PAREN)) {
                    methods.add(finishFunctionDeclaration(advance(), "method", false, false, null, type));
                } else {
                    fields.add((VarStatement) varDeclaration(type, null));
                }
            } else {
                throw error(peek(), "Expect member declaration.");
            }
            while (match(NEWLINE));
        }

        consume(DEDENT, "Expect dedent after type body.");
        return new TypeStatement(name, genericParameters, traits, fields, methods, operators);
    }

    public FunctionStatement operatorDeclaration(Token ownerType) {
        Token operatorName = consume(IDENTIFIER, "Expect operator name.");
        if (!SUPPORTED_OPERATOR_NAMES.contains(operatorName.lexeme())) {
            throw error(operatorName, "Unsupported operator '" + operatorName.lexeme() + "'.");
        }

        consume(LEFT_PAREN, "Expect '(' after operator name.");
        List<FunctionStatement.Parameter> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                parameters.add(operatorParameter(ownerType));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after operator parameters.");

        Token returnType = null;
        if (match(COLON)) {
            returnType = consumeType("Expect return type.");
        }

        consume(COLON, "Expect ':' before operator body.");
        Statement body;
        if (match(NEWLINE)) {
            consume(INDENT, "Expect indentation before operator body.");
            body = new BlockStatement(masterParser.block());
        } else {
            body = statementParser.statement();
        }

        return new FunctionStatement(operatorName, parameters, returnType, body, new ArrayList<>(), false, false, null);
    }

    private FunctionStatement.Parameter operatorParameter(Token ownerType) {
        Token type = consumeType("Expect parameter type.");
        Token name = consume(IDENTIFIER, "Expect parameter name.");
        return new FunctionStatement.Parameter(type, name, null);
    }

    public Statement functionDeclaration(String kind, Token visibility) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        return finishFunctionDeclaration(name, kind, false, false, visibility, null);
    }

    public FunctionStatement finishFunctionDeclaration(Token name, String kind, boolean isOverride, boolean isDefault, Token visibility, Token returnType) {
        List<GenericParameter> genericParameters = masterParser.parseGenericParameters();
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<FunctionStatement.Parameter> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                parameters.add(parameter());
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        if (match(COLON) && returnType == null) {
            returnType = consumeType("Expect return type.");
        }

        Statement body = null;
        if (match(COLON)) {
            if (match(NEWLINE)) {
                consume(INDENT, "Expect indentation after newline in " + kind + " declaration.");
                body = new BlockStatement(masterParser.block());
            } else {
                body = statementParser.statement();
            }
        } else if (!isDefault && !isNative(name)) {
            // No body allowed unless default or native (though native is handled separately)
            // Actually, traits can have abstract methods
        }

        return new FunctionStatement(name, parameters, returnType, body, genericParameters, isOverride, isDefault, visibility);
    }

    private boolean isNative(Token name) {
        // This is a bit of a hack, we might need a better way to detect native functions in the parser
        return false;
    }

    public FunctionStatement.Parameter parameter() {
        Token type = consumeType("Expect parameter type.");
        Token name = consume(IDENTIFIER, "Expect parameter name.");
        Expression defaultValue = null;
        if (match(EQUAL)) {
            defaultValue = expressionParser.expression();
        }
        return new FunctionStatement.Parameter(type, name, defaultValue);
    }

}
