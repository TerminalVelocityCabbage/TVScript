package com.terminalvelocitycabbage.tvscript.parsing;

import com.terminalvelocitycabbage.tvscript.CompilationContext;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.terminalvelocitycabbage.tvscript.parsing.TokenType.*;
import static com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

/**
 * Parses expressions from a list of tokens.
 */
public class ExpressionParser extends BaseParser {

    private final Parser statementParser;

    public ExpressionParser(List<Token> tokens, CompilationContext context, Parser statementParser) {
        super(tokens, context);
        this.statementParser = statementParser;
    }

    // This allows Parser to sync the current index with ExpressionParser
    public void setCurrent(int current) {
        this.current = current;
    }

    public int getCurrent() {
        return current;
    }

    public Expression expression() {
        return assignment();
    }

    public Expression matchExpression() {
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

    public Expression assignment() {
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

            reporter.error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    public Expression ternary() {
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

    public Expression or() {
        Expression expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expression right = and();
            expr = new LogicalExpression(expr, operator, right);
        }

        return expr;
    }

    public Expression and() {
        Expression expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expression right = equality();
            expr = new LogicalExpression(expr, operator, right);
        }

        return expr;
    }

    public Expression equality() {
        Expression expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expression right = comparison();
            expr = new BinaryExpression(expr, operator, right);
        }

        return expr;
    }

    public Expression comparison() {
        Expression expr = typeCheck();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expression right = typeCheck();
            expr = new BinaryExpression(expr, operator, right);
        }

        return expr;
    }

    public Expression typeCheck() {
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

    public Expression range() {
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

    public Expression term() {
        Expression expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expression right = factor();
            expr = new BinaryExpression(expr, operator, right);
        }

        return expr;
    }

    public Expression factor() {
        Expression expr = unary();

        while (match(SLASH, STAR, PERCENT)) {
            Token operator = previous();
            Expression right = unary();
            expr = new BinaryExpression(expr, operator, right);
        }

        return expr;
    }

    public Expression unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expression right = unary();
            return new UnaryExpression(operator, right);
        }

        return call();
    }

    public Expression call() {
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

    public Expression primary() {
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
                        parameters.add(statementParser.parameter());
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
                            body = new BlockStatement(statementParser.block());
                        } else {
                            body = statementParser.statement();
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

        if (match(LIST)) {
            return parseCollectionLiteral(previous(), previous());
        }
        if (match(SET)) {
            return parseCollectionLiteral(previous(), previous());
        }
        if (match(MAP)) {
            return parseCollectionLiteral(previous(), previous());
        }

        throw error(peek(), "Expect expression.");
    }

    public Expression finishCall(Expression callee, boolean nativeCall, List<Token> typeArguments) {
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
                        reporter.error(name, "Duplicate argument '" + name.lexeme() + "'.");
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

    public List<Token> tryParseTypeArgumentsForCall() {
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

    public boolean looksLikeTypeArgumentListForCall() {
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

    public Expression finishIndex(Expression object, Token bracket) {
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

    public Expression parseNewExpression(Token keyword) {
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

    public Expression parseCollectionLiteral(Token keyword, Token collectionType) {
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

    public Expression anonymousFunctionExpression() {
        // 'function' was already matched
        Token name = null;
        if (check(IDENTIFIER)) {
            name = advance();
        }
        consume(LEFT_PAREN, "Expect '(' after function keyword.");
        List<FunctionStatement.Parameter> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                parameters.add(statementParser.parameter());
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
            body = new BlockStatement(statementParser.block());
        } else {
            body = statementParser.statement();
            if (!(body instanceof BlockStatement)) {
                List<Statement> stmts = new ArrayList<>();
                stmts.add(body);
                body = new BlockStatement(stmts);
            }
        }

        return new FunctionExpression(parameters, returnType, body);
    }
}
