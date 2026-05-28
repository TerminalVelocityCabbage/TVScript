package com.terminalvelocitycabbage.tvscript.parsing;

import com.terminalvelocitycabbage.tvscript.TVScript;
import com.terminalvelocitycabbage.tvscript.errors.DefaultDiagnosticReporter;
import com.terminalvelocitycabbage.tvscript.CompilationContext;
import com.terminalvelocitycabbage.tvscript.errors.DiagnosticReporter;
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
public class Parser extends BaseParser {

    private static final Set<String> SUPPORTED_OPERATOR_NAMES = Set.of(
            "add", "subtract", "multiply", "divide", "modulo", "compare", "negative"
    );

    private final ExpressionParser expressionParser;
    private final StatementParser statementParser;
    private final DeclarationParser declarationParser;

    public Parser(List<Token> tokens, CompilationContext context) {
        super(tokens, context);
        this.expressionParser = new ExpressionParser(tokens, context, this);
        this.statementParser = new StatementParser(tokens, context, this, expressionParser);
        this.declarationParser = new DeclarationParser(tokens, context, this, expressionParser, statementParser);
    }

    public Parser(List<Token> tokens, DiagnosticReporter reporter) {
        this(tokens, new CompilationContext(reporter));
    }

    public Parser(List<Token> tokens) {
        this(tokens, new DefaultDiagnosticReporter());
    }

    private void syncSubParsers() {
        expressionParser.setCurrent(current);
        statementParser.setCurrent(current);
        declarationParser.setCurrent(current);
    }

    private void syncMasterParser(BaseParser subParser) {
        if (subParser == expressionParser) current = expressionParser.getCurrent();
        else if (subParser == statementParser) current = statementParser.getCurrent();
        else if (subParser == declarationParser) current = declarationParser.getCurrent();
        syncSubParsers();
    }

    /**
     * Parses the tokens into a list of statements.
     * @return A list of statements.
     */
    public List<Statement> parseStatements() {
        List<Statement> statements = new ArrayList<>();
        while (!isAtEnd()) {
            int before = current;
            Statement decl = declaration();
            if (decl != null) {
                statements.add(decl);
            }
            // Consume optional newlines after statements
            while (match(NEWLINE));

            // Safety check to prevent infinite loop if declaration() fails to progress
            if (current == before && !isAtEnd()) {
                advance();
            }
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

    private Expression expression() {
        expressionParser.setCurrent(current);
        Expression expr = expressionParser.expression();
        current = expressionParser.getCurrent();
        return expr;
    }

    private Statement declaration() {
        declarationParser.setCurrent(current);
        Statement stmt = declarationParser.declaration();
        current = declarationParser.getCurrent();
        return stmt;
    }

    Statement statement() {
        statementParser.setCurrent(current);
        Statement stmt = statementParser.statement();
        current = statementParser.getCurrent();
        return stmt;
    }

    FunctionStatement.Parameter parameter() {
        declarationParser.setCurrent(current);
        FunctionStatement.Parameter param = declarationParser.parameter();
        current = declarationParser.getCurrent();
        return param;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int current) {
        this.current = current;
    }

    public ExpressionParser getExpressionParser() {
        return expressionParser;
    }

    public StatementParser getStatementParser() {
        return statementParser;
    }

    public DeclarationParser getDeclarationParser() {
        return declarationParser;
    }

    boolean looksLikeTypedVariableDeclaration() {
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

    List<GenericParameter> parseGenericParameters() {
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

    boolean looksLikeGenericParameterDeclaration() {
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

    List<Statement> block() {
        List<Statement> statements = new ArrayList<>();

        while (!check(DEDENT) && !isAtEnd()) {
            statements.add(declaration());
            while (match(NEWLINE));
        }

        consume(DEDENT, "Expect indentation decrease after block.");
        return statements;
    }

    void synchronize() {
        if (isAtEnd()) return;
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
