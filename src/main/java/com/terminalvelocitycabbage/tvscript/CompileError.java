package com.terminalvelocitycabbage.tvscript;

public class CompileError extends RuntimeException {
    public final Token token;

    public CompileError(Token token, String message) {
        super(message);
        this.token = token;
    }
}
