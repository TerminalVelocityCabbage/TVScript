package com.terminalvelocitycabbage.tvscript.errors;

import com.terminalvelocitycabbage.tvscript.parsing.Token;

public class CompileError extends RuntimeException {
    public final Token token;

    public CompileError(Token token, String message) {
        super(message);
        this.token = token;
    }
}
