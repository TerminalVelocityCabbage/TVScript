package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.Map;

public final class TVType {

    public static final TVType INTEGER = primitive(TokenType.TYPE_INTEGER, "integer");
    public static final TVType DECIMAL = primitive(TokenType.TYPE_DECIMAL, "decimal");
    public static final TVType STRING = primitive(TokenType.TYPE_STRING, "string");
    public static final TVType BOOLEAN = primitive(TokenType.TYPE_BOOLEAN, "boolean");
    public static final TVType NONE = primitive(TokenType.NONE, "none");

    private enum Kind {
        PRIMITIVE,
        SELF,
        REF
    }

    private final Kind kind;
    private final TokenType primitiveTokenType;
    private final String primitiveName;
    private final NativeClass reference;

    private TVType(Kind kind, TokenType primitiveTokenType, String primitiveName, NativeClass reference) {
        this.kind = kind;
        this.primitiveTokenType = primitiveTokenType;
        this.primitiveName = primitiveName;
        this.reference = reference;
    }

    private static TVType primitive(TokenType tokenType, String name) {
        return new TVType(Kind.PRIMITIVE, tokenType, name, null);
    }

    public static TVType self() {
        return new TVType(Kind.SELF, null, "self", null);
    }

    public static TVType ref(NativeClass nativeClass) {
        if (nativeClass == null) {
            throw new IllegalArgumentException("Native class reference cannot be null.");
        }
        return new TVType(Kind.REF, null, nativeClass.scriptName(), nativeClass);
    }

    public ResolvedType resolve(NativeClass owner, Map<String, NativeClass> registeredClasses) {
        return switch (kind) {
            case PRIMITIVE -> new ResolvedType(primitiveTokenType, null, null);
            case SELF -> {
                if (owner == null) {
                    throw new IllegalStateException("Cannot resolve self type without owner class.");
                }
                yield new ResolvedType(TokenType.CLASS, owner.scriptName(), owner);
            }
            case REF -> {
                if (reference == null) {
                    throw new IllegalStateException("Missing native class reference.");
                }
                NativeClass resolved = registeredClasses.get(reference.scriptName());
                if (resolved == null) {
                    throw new IllegalStateException("Unresolved native type reference '" + reference.scriptName() + "'.");
                }
                yield new ResolvedType(TokenType.CLASS, resolved.scriptName(), resolved);
            }
        };
    }

    @Override
    public String toString() {
        if (kind == Kind.PRIMITIVE) {
            return primitiveName;
        }
        if (kind == Kind.SELF) {
            return "self";
        }
        return reference.scriptName();
    }

    public record ResolvedType(TokenType tokenType, String namedType, NativeClass nativeClass) {
    }
}