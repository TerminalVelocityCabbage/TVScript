package com.terminalvelocitycabbage.tvscript;

public class Token {
    final TokenType type;
    final String lexeme;
    final Object value;
    final int line;

    public Token(TokenType type, String lexeme, Object value, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.value = value;
        this.line = line;
    }

    @Override
    public String toString() {
        if (value == null) {
            return "[" + type + " " + lexeme.replace("\n", "\\n").replace("\r", "\\r") + "]";
        }
        return "[" + type + " " + lexeme.replace("\n", "\\n").replace("\r", "\\r") + " = " + value + "]";
    }

    public TokenType getType() {
        return type;
    }

    public String getLexeme() {
        return lexeme;
    }

    public Object getValue() {
        return value;
    }

    public int getLine() {
        return line;
    }
}
