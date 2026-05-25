package com.terminalvelocitycabbage.tvscript.analysis.types;

import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import java.util.List;
import java.util.ArrayList;

public class FunctionType implements Type {

    private final List<Type> parameterTypes;
    private final Type returnType;

    public FunctionType(List<Type> parameterTypes, Type returnType) {
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }

    @Override
    public String getName() {
        return "function";
    }

    @Override
    public boolean isAssignableTo(Type other) {
        if (this == other) return true;
        if (other instanceof FunctionType otherFunc) {
            if (parameterTypes.size() != otherFunc.parameterTypes.size()) return false;
            // Parameter types should be contravariant, but TVScript might be simpler
            for (int i = 0; i < parameterTypes.size(); i++) {
                if (!otherFunc.parameterTypes.get(i).isAssignableTo(parameterTypes.get(i))) return false;
            }
            // Return type should be covariant
            return returnType.isAssignableTo(otherFunc.returnType);
        }
        return false;
    }

    @Override
    public TokenType toTokenType() {
        return TokenType.FUNCTION;
    }

    public List<Type> getParameterTypes() {
        return parameterTypes;
    }

    public Type getReturnType() {
        return returnType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionType that = (FunctionType) o;
        return parameterTypes.equals(that.parameterTypes) && returnType.equals(that.returnType);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(parameterTypes, returnType);
    }

    @Override
    public String toString() {
        return "function(" + parameterTypes.toString() + ") -> " + returnType.toString();
    }
}
