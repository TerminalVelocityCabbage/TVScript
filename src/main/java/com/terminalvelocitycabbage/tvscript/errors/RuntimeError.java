package com.terminalvelocitycabbage.tvscript.errors;

import com.terminalvelocitycabbage.tvscript.parsing.Token;

public class RuntimeError extends RuntimeException {
    public final Token token;

    public RuntimeError(Token token, String message) {
        super(message);
        this.token = token;
    }
}
