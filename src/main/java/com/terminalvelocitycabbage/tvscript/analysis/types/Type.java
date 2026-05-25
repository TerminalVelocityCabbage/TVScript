package com.terminalvelocitycabbage.tvscript.analysis.types;

import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

public interface Type {
    String getName();
    boolean isAssignableTo(Type other);
    
    // Helper to get back to TokenType for backward compatibility or simple checks
    TokenType toTokenType();

    default String getNamedType() {
        return null;
    }
}
