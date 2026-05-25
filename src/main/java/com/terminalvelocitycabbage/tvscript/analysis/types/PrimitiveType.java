package com.terminalvelocitycabbage.tvscript.analysis.types;

import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

public class PrimitiveType implements Type {

    public static final PrimitiveType INTEGER = new PrimitiveType(TokenType.TYPE_INTEGER, "integer");
    public static final PrimitiveType DECIMAL = new PrimitiveType(TokenType.TYPE_DECIMAL, "decimal");
    public static final PrimitiveType BOOLEAN = new PrimitiveType(TokenType.TYPE_BOOLEAN, "boolean");
    public static final PrimitiveType STRING = new PrimitiveType(TokenType.TYPE_STRING, "string");
    public static final PrimitiveType RANGE = new PrimitiveType(TokenType.TYPE_RANGE, "range");
    public static final PrimitiveType NONE = new PrimitiveType(TokenType.NONE, "none");
    public static final PrimitiveType VOID = new PrimitiveType(TokenType.NONE, "void"); // For function returns
    public static final PrimitiveType FUNCTION = new PrimitiveType(TokenType.FUNCTION, "function");

    private final TokenType tokenType;
    private final String name;

    private PrimitiveType(TokenType tokenType, String name) {
        this.tokenType = tokenType;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isAssignableTo(Type other) {
        if (this == other) return true;
        if (this == NONE) return true; 
        if (other == NONE) return true;
        
        // Primitive widening (e.g., integer to decimal)
        if (this == INTEGER && other == DECIMAL) return true;
        
        return false;
    }

    @Override
    public TokenType toTokenType() {
        return tokenType;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrimitiveType that = (PrimitiveType) o;
        return tokenType == that.tokenType;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(tokenType);
    }

    @Override
    public String toString() {
        return name;
    }
}
