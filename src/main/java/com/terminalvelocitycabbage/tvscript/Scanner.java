package com.terminalvelocitycabbage.tvscript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;

    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("import", TokenType.IMPORT);
        keywords.put("main", TokenType.MAIN);
        keywords.put("public", TokenType.PUBLIC);
        keywords.put("private", TokenType.PRIVATE);
        keywords.put("protected", TokenType.PROTECTED);
        keywords.put("mod", TokenType.MOD);
        keywords.put("var", TokenType.VAR);
        keywords.put("const", TokenType.CONST);
        keywords.put("integer", TokenType.TYPE_INTEGER);
        keywords.put("decimal", TokenType.TYPE_DECIMAL);
        keywords.put("string", TokenType.TYPE_STRING);
        keywords.put("boolean", TokenType.TYPE_BOOLEAN);
        keywords.put("function", TokenType.FUNCTION);
        keywords.put("return", TokenType.RETURN);
        keywords.put("if", TokenType.IF);
        keywords.put("else", TokenType.ELSE);
        keywords.put("for", TokenType.FOR);
        keywords.put("while", TokenType.WHILE);
        keywords.put("match", TokenType.MATCH);
        keywords.put("default", TokenType.DEFAULT);
        keywords.put("break", TokenType.BREAK);
        keywords.put("continue", TokenType.CONTINUE);
        keywords.put("print", TokenType.PRINT);
        keywords.put("none", TokenType.NONE);
        keywords.put("class", TokenType.CLASS);
        keywords.put("new", TokenType.NEW);
        keywords.put("trait", TokenType.TRAIT);
        keywords.put("type", TokenType.TYPE);
        keywords.put("operator", TokenType.OPERATOR);
        keywords.put("this", TokenType.THIS);
        keywords.put("super", TokenType.SUPER);
        keywords.put("override", TokenType.OVERRIDE);
        keywords.put("is", TokenType.IS);
        keywords.put("has", TokenType.HAS);
        keywords.put("as", TokenType.AS);
        keywords.put("list", TokenType.LIST);
        keywords.put("set", TokenType.SET);
        keywords.put("map", TokenType.MAP);
        keywords.put("enum", TokenType.ENUM);
        keywords.put("event", TokenType.EVENT);
        keywords.put("on", TokenType.ON);
        keywords.put("dispatch", TokenType.DISPATCH);
        keywords.put("annotation", TokenType.ANNOTATION);
        keywords.put("throw", TokenType.THROW);
        keywords.put("throws", TokenType.THROWS);
        keywords.put("try", TokenType.TRY);
        keywords.put("catch", TokenType.CATCH);
        keywords.put("async", TokenType.ASYNC);
        keywords.put("await", TokenType.AWAIT);
        keywords.put("launch", TokenType.LAUNCH);
        keywords.put("all", TokenType.ALL);
        keywords.put("timeout", TokenType.TIMEOUT);
        keywords.put("pass", TokenType.PASS);
        keywords.put("and", TokenType.AND);
        keywords.put("or", TokenType.OR);
        keywords.put("true", TokenType.TRUE);
        keywords.put("false", TokenType.FALSE);
    }

    private final Stack<Integer> indentLevels = new Stack<>();
    private boolean isAtLineStart = true;
    private int interpolationDepth = 0;
    private boolean isScanningTripleQuotedString = false;
    private int detectedIndentSize = 0;
    private char detectedIndentChar = '\0';

    public Scanner(String source) {
        this.source = source;
        indentLevels.push(0);
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        // Add final dedents
        while (indentLevels.peek() > 0) {
            tokens.add(new Token(TokenType.DEDENT, "", null, line));
            indentLevels.pop();
        }

        tokens.add(new Token(TokenType.EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        if (isAtLineStart) {
            handleIndentation();
            start = current;
        }

        if (isAtEnd()) return;

        char c = advance();
        switch (c) {
            case '(': addToken(TokenType.LEFT_PAREN); break;
            case ')': addToken(TokenType.RIGHT_PAREN); break;
            case '{':
                addToken(TokenType.LEFT_BRACE);
                if (interpolationDepth > 0) interpolationDepth++;
                break;
            case '}':
                if (interpolationDepth > 0) {
                    interpolationDepth--;
                    if (interpolationDepth == 0) {
                        // We closed an interpolation block, continue scanning the string
                        addToken(TokenType.RIGHT_BRACE);
                        start = current;
                        scanString(false);
                        return;
                    }
                }
                addToken(TokenType.RIGHT_BRACE);
                break;
            case '[': addToken(TokenType.LEFT_BRACKET); break;
            case ']': addToken(TokenType.RIGHT_BRACKET); break;
            case ',': addToken(TokenType.COMMA); break;
            case '.':
                if (match('.')) {
                    addToken(TokenType.DOT_DOT);
                } else {
                    addToken(TokenType.DOT);
                }
                break;
            case '-':
                if (match('-')) {
                    addToken(TokenType.MINUS_MINUS);
                } else if (match('=')) {
                    addToken(TokenType.MINUS_EQUAL);
                } else if (match('>')) {
                    addToken(TokenType.ARROW);
                } else {
                    addToken(TokenType.MINUS);
                }
                break;
            case '+':
                if (match('+')) {
                    addToken(TokenType.PLUS_PLUS);
                } else if (match('=')) {
                    addToken(TokenType.PLUS_EQUAL);
                } else {
                    addToken(TokenType.PLUS);
                }
                break;
            case ':': addToken(TokenType.COLON); break;
            case ';': addToken(TokenType.SEMICOLON); break;
            case '*':
                addToken(match('=') ? TokenType.STAR_EQUAL : TokenType.STAR);
                break;
            case '%':
                addToken(match('=') ? TokenType.PERCENT_EQUAL : TokenType.PERCENT);
                break;
            case '|':
                addToken(TokenType.PIPE);
                break;
            case '?': addToken(TokenType.QUESTION); break;
            case '@': addToken(TokenType.AT); break;
            case '!':
                addToken(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
                break;
            case '=':
                addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
                break;
            case '<':
                addToken(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
                break;
            case '>':
                addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
                break;
            case '/':
                if (match('/')) {
                    if (match('/')) {
                        // Block comment ///
                        while (!isAtEnd()) {
                            if (peek() == '/' && peekNext() == '/' && peekNextNext() == '/') {
                                // Close block comment
                                advance(); advance(); advance();
                                break;
                            }
                            if (peek() == '\n') line++;
                            advance();
                        }
                    } else {
                        // Single line comment
                        while (peek() != '\n' && !isAtEnd()) advance();
                    }
                } else if (match('=')) {
                    addToken(TokenType.SLASH_EQUAL);
                } else {
                    addToken(TokenType.SLASH);
                }
                break;

            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace
                break;

            case '\n':
                addToken(TokenType.NEWLINE);
                line++;
                isAtLineStart = true;
                break;

            case '"':
                scanString(true);
                break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    TVScript.error(line, "Unexpected character: " + c);
                }
                break;
        }
    }

    private void handleIndentation() {
        int indentation = 0;
        int spaces = 0;
        int tabs = 0;

        while (!isAtEnd() && (peek() == ' ' || peek() == '\t')) {
            char c = advance();
            if (c == ' ') spaces++;
            else tabs++;
        }

        // Skip empty lines or comment-only lines
        if (peek() == '\n' || (peek() == '/' && peekNext() == '/')) {
            isAtLineStart = true; // Wait for next line
            return;
        }

        // Determine indentation style from the first indented line
        if (detectedIndentChar == '\0' && (spaces > 0 || tabs > 0)) {
            if (tabs > 0) {
                if (spaces > 0) {
                    TVScript.error(line, "Mixed tabs and spaces in indentation.");
                }
                detectedIndentChar = '\t';
                detectedIndentSize = 1;
            } else {
                detectedIndentChar = ' ';
                if (spaces == 2 || spaces == 4) {
                    detectedIndentSize = spaces;
                } else {
                    TVScript.error(line, "Indentation must be 2 spaces, 4 spaces, or 1 tab.");
                }
            }
        }

        int currentIndent;
        if (detectedIndentChar == '\t') {
            if (spaces > 0) TVScript.error(line, "Mixed tabs and spaces in indentation.");
            currentIndent = tabs;
        } else if (detectedIndentChar == ' ') {
            if (tabs > 0) TVScript.error(line, "Mixed tabs and spaces in indentation.");
            if (spaces % detectedIndentSize != 0) {
                TVScript.error(line, "Inconsistent indentation. Expected multiples of " + detectedIndentSize + " spaces.");
            }
            currentIndent = spaces / detectedIndentSize;
        } else {
            // No indentation detected yet, or no indentation on this line
            if (spaces > 0 || tabs > 0) {
                // This shouldn't happen if detectedIndentChar is null but we have spaces/tabs,
                // because the "Determine indentation style" block above would have set it.
                // But just in case:
                currentIndent = spaces + tabs;
            } else {
                currentIndent = 0;
            }
        }

        // Use a virtual stack of levels (0, 1, 2...) instead of raw spaces
        int level = indentLevels.size() - 1; // Current level is size - 1 because we push 0 initially

        if (currentIndent > level) {
            if (currentIndent > level + 1) {
                TVScript.error(line, "Indentation jumped too far.");
            }
            indentLevels.push(currentIndent);
            addToken(TokenType.INDENT);
        } else {
            while (currentIndent < indentLevels.size() - 1) {
                indentLevels.pop();
                addToken(TokenType.DEDENT);
            }
            if (currentIndent != indentLevels.size() - 1) {
                TVScript.error(line, "Indentation error at line " + line);
            }
        }
        isAtLineStart = false;
    }

    private void scanString(boolean isStartOfFullString) {
        if (isStartOfFullString) {
            if (peek() == '"' && peekNext() == '"') {
                advance(); advance();
                isScanningTripleQuotedString = true;
            } else {
                isScanningTripleQuotedString = false;
            }
        }

        while (!isAtEnd()) {
            if (isScanningTripleQuotedString) {
                if (peek() == '"' && peekNext() == '"' && peekNextNext() == '"') {
                    advance(); advance(); advance();
                    addToken(TokenType.STRING, source.substring(start + (isStartOfFullString ? 3 : 0), current - 3));
                    isScanningTripleQuotedString = false;
                    return;
                }
            } else {
                if (peek() == '"') {
                    advance();
                    addToken(TokenType.STRING, source.substring(start + (isStartOfFullString ? 1 : 0), current - 1));
                    return;
                }
            }

            if (peek() == '{') {
                // String interpolation
                addToken(TokenType.STRING_PART, source.substring(start + (isStartOfFullString ? (isScanningTripleQuotedString ? 3 : 1) : 0), current));
                start = current;
                advance(); // consume '{'
                addToken(TokenType.LEFT_BRACE);
                start = current;
                interpolationDepth = 1;
                return; // Return to main scan loop to handle expression
            }

            if (peek() == '\n') {
                if (!isScanningTripleQuotedString) {
                    TVScript.error(line, "Unterminated string at line " + line);
                    return;
                }
                line++;
            }
            advance();
        }

        TVScript.error(line, "Unterminated string at line " + line);
    }

    private void number() {
        while (isDigit(peek())) advance();

        // Look for a fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance();

            while (isDigit(peek())) advance();
            addToken(TokenType.DECIMAL, Double.parseDouble(source.substring(start, current)));
        } else {
            addToken(TokenType.INTEGER, Integer.parseInt(source.substring(start, current)));
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) type = TokenType.IDENTIFIER;
        addToken(type);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private char advance() {
        return source.charAt(current++);
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object value) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, value, line));
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private char peekNextNext() {
        if (current + 2 >= source.length()) return '\0';
        return source.charAt(current + 2);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
