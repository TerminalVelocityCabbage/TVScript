package com.terminalvelocitycabbage.tvscript.parsing;

import com.terminalvelocitycabbage.tvscript.CompilationContext;
import com.terminalvelocitycabbage.tvscript.errors.DiagnosticReporter;

import java.util.List;

import static com.terminalvelocitycabbage.tvscript.parsing.TokenType.*;

/**
 * Base class for Parser and its sub-components, providing token stream navigation.
 */
public abstract class BaseParser {

    protected static class ParseError extends RuntimeException {}

    protected final List<Token> tokens;
    protected final CompilationContext context;
    protected final DiagnosticReporter reporter;
    protected int current = 0;

    protected BaseParser(List<Token> tokens, CompilationContext context) {
        this.tokens = tokens;
        this.context = context;
        this.reporter = context.getReporter();
    }

    protected boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    protected boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type() == type;
    }

    protected boolean check(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) return true;
        }
        return false;
    }

    protected boolean checkNext(TokenType type) {
        if (isAtEnd()) return false;
        if (tokens.get(current + 1).type() == EOF) return false;
        return tokens.get(current + 1).type() == type;
    }

    protected Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    protected boolean isAtEnd() {
        return peek().type() == EOF;
    }

    protected Token peek() {
        return tokens.get(current);
    }

    protected Token previous() {
        return tokens.get(current - 1);
    }

    protected Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    protected Token consumeType(String message) {
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

    protected Token parseParameterizedType(Token baseType) {
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

    protected void parseCollectionTypeParameters(Token collectionType) {
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

    protected ParseError error(Token token, String message) {
        reporter.error(token, message);
        return new ParseError();
    }

    protected boolean isTypeToken(TokenType type) {
        return type == TYPE_INTEGER || type == TYPE_DECIMAL || type == TYPE_STRING || type == TYPE_BOOLEAN
                || type == TYPE_RANGE || type == NONE || type == FUNCTION || type == IDENTIFIER
                || type == LIST || type == SET || type == MAP;
    }
}
