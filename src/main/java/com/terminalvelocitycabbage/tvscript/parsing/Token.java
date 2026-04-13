package com.terminalvelocitycabbage.tvscript.parsing;

public record Token(TokenType type, String lexeme, Object value, int line) {

    @Override
    public String toString() {
        if (value == null) {
            return "[" + type + " " + lexeme.replace("\n", "\\n").replace("\r", "\\r") + "]";
        }
        return "[" + type + " " + lexeme.replace("\n", "\\n").replace("\r", "\\r") + " = " + value + "]";
    }
}
