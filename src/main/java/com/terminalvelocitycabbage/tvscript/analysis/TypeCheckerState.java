package com.terminalvelocitycabbage.tvscript.analysis;

import com.terminalvelocitycabbage.tvscript.CompilationContext;
import com.terminalvelocitycabbage.tvscript.analysis.types.ClassType;
import com.terminalvelocitycabbage.tvscript.analysis.types.TraitType;
import com.terminalvelocitycabbage.tvscript.analysis.types.Type;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.DiagnosticReporter;
import com.terminalvelocitycabbage.tvscript.execution.NativeClass;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import java.util.*;

import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

public class TypeCheckerState {

    public static class VariableStaticInfo {
        public final Type type;
        public final boolean isConst;

        public VariableStaticInfo(Type type, boolean isConst) {
            this.type = type;
            this.isConst = isConst;
        }
    }

    public final List<Map<String, VariableStaticInfo>> scopes = new ArrayList<>();
    public final Map<String, ClassStatement> classes = new HashMap<>();
    public final Map<String, String> classScriptPaths = new HashMap<>();
    public final Map<String, String> classModules = new HashMap<>();
    public final Map<String, TraitStatement> traits = new HashMap<>();
    public final Map<String, TypeStatement> types = new HashMap<>();
    public final Map<String, ConstraintStatement> constraints = new HashMap<>();
    public final Map<String, EventStatement> events = new HashMap<>();
    public final Map<String, FunctionStatement> functions = new HashMap<>();
    public final Map<String, VarStatement> globalVars = new HashMap<>();
    public final Map<String, String> varScriptPaths = new HashMap<>();
    public final Map<String, String> varModules = new HashMap<>();
    public final Map<String, String> functionScriptPaths = new HashMap<>();
    public final Map<String, String> functionModules = new HashMap<>();
    public final Map<String, Map<String, String>> scriptImports = new HashMap<>();
    public final Map<String, Map<String, String>> scriptQualifiedImports = new HashMap<>();
    public final Set<String> nativeFunctionNames = new HashSet<>();
    public final Map<String, NativeClass> nativeClasses = new HashMap<>();
    
    public final CompilationContext context;
    public final DiagnosticReporter reporter;
    
    public final Map<String, ClassType> classTypeCache = new HashMap<>();
    public final Map<String, TraitType> traitTypeCache = new HashMap<>();

    public int loopDepth = 0;
    public ClassStatement currentClass = null;
    public TypeStatement currentType = null;
    public Type currentReturnType = null;
    public String currentScriptPath = "default";
    public String currentModule = "default";

    public TypeCheckerState(CompilationContext context) {
        this.context = context;
        this.reporter = context.getReporter();
    }

    public Type resolveType(Token typeToken) {
        if (typeToken == null) return com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.VOID;
        
        String lexeme = typeToken.lexeme();
        if (lexeme != null && lexeme.contains("[")) {
            // Parameterized type like list[integer] or map[string|integer]
            int openBracket = lexeme.indexOf('[');
            int closeBracket = lexeme.lastIndexOf(']');
            String baseTypeName = lexeme.substring(0, openBracket);
            String innerTypes = lexeme.substring(openBracket + 1, closeBracket);
            
            com.terminalvelocitycabbage.tvscript.parsing.TokenType baseType = switch (baseTypeName) {
                case "list" -> com.terminalvelocitycabbage.tvscript.parsing.TokenType.LIST;
                case "set" -> com.terminalvelocitycabbage.tvscript.parsing.TokenType.SET;
                case "map" -> com.terminalvelocitycabbage.tvscript.parsing.TokenType.MAP;
                default -> com.terminalvelocitycabbage.tvscript.parsing.TokenType.IDENTIFIER;
            };

            if (baseType == com.terminalvelocitycabbage.tvscript.parsing.TokenType.MAP) {
                String[] parts = innerTypes.split("\\|");
                Type keyType = resolveTypeFromName(parts[0].trim());
                Type valueType = resolveTypeFromName(parts[1].trim());
                return new com.terminalvelocitycabbage.tvscript.analysis.types.CollectionType(com.terminalvelocitycabbage.tvscript.parsing.TokenType.MAP, List.of(keyType, valueType));
            } else if (baseType == com.terminalvelocitycabbage.tvscript.parsing.TokenType.LIST || baseType == com.terminalvelocitycabbage.tvscript.parsing.TokenType.SET) {
                Type elementType = resolveTypeFromName(innerTypes.trim());
                return new com.terminalvelocitycabbage.tvscript.analysis.types.CollectionType(baseType, List.of(elementType));
            }
        }

        if (lexeme != null && lexeme.contains("<")) {
            // Generic class type like Box<integer>
            int openBracket = lexeme.indexOf('<');
            int closeBracket = lexeme.lastIndexOf('>');
            String className = lexeme.substring(0, openBracket);
            String innerTypes = lexeme.substring(openBracket + 1, closeBracket);

            List<Type> genericArgs = Arrays.stream(innerTypes.split(","))
                    .map(String::trim)
                    .map(this::resolveTypeFromName)
                    .toList();
            return new com.terminalvelocitycabbage.tvscript.analysis.types.ClassType(className, genericArgs, null, new ArrayList<>());
        }

        return resolveType(typeToken.type(), typeToken.lexeme());
    }

    public Type resolveTypeFromName(String name) {
        return switch (name) {
            case "integer" -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.INTEGER;
            case "decimal" -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.DECIMAL;
            case "string" -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.STRING;
            case "boolean" -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.BOOLEAN;
            case "range" -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.RANGE;
            case "none" -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.NONE;
            case "function" -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.FUNCTION;
            case "void" -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.VOID;
            default -> {
                if (name.contains("[") || name.contains("<")) {
                    yield resolveType(new Token(com.terminalvelocitycabbage.tvscript.parsing.TokenType.NONE, name, null, 0));
                }
                if (classes.containsKey(name)) yield resolveClassType(name);
                if (traits.containsKey(name)) yield resolveTraitType(name);
                yield new com.terminalvelocitycabbage.tvscript.analysis.types.ClassType(name); // Generic or unknown yet
            }
        };
    }

    public Type resolveType(com.terminalvelocitycabbage.tvscript.parsing.TokenType type, String namedType) {
        if (namedType != null) {
            if (namedType.contains("[")) {
                return resolveType(new Token(type, namedType, null, 0));
            }
            if (classes.containsKey(namedType)) return resolveClassType(namedType);
            if (traits.containsKey(namedType)) return resolveTraitType(namedType);
            
            if (type == com.terminalvelocitycabbage.tvscript.parsing.TokenType.IDENTIFIER || type == com.terminalvelocitycabbage.tvscript.parsing.TokenType.CLASS) {
                return resolveClassType(namedType);
            }
        }

        return switch (type) {
            case TYPE_INTEGER -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.INTEGER;
            case TYPE_DECIMAL -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.DECIMAL;
            case TYPE_STRING -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.STRING;
            case TYPE_BOOLEAN -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.BOOLEAN;
            case TYPE_RANGE -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.RANGE;
            case LIST, SET, MAP -> {
                // If we got here, it's a raw collection type without parameters in lexeme
                // Default to ANY or Object if possible, but TVScript might expect parameters
                yield new com.terminalvelocitycabbage.tvscript.analysis.types.CollectionType(type, List.of(com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.VOID));
            }
            case NONE -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.NONE;
            case CLASS -> new com.terminalvelocitycabbage.tvscript.analysis.types.ClassType(namedType != null ? namedType : "object");
            case TRAIT -> new com.terminalvelocitycabbage.tvscript.analysis.types.TraitType(namedType != null ? namedType : "trait");
            case IDENTIFIER -> new com.terminalvelocitycabbage.tvscript.analysis.types.ClassType(namedType);
            case FUNCTION -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.FUNCTION;
            default -> com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.VOID;
        };
    }

    public com.terminalvelocitycabbage.tvscript.analysis.types.ClassType resolveClassType(String name) {
        if (classTypeCache.containsKey(name)) return classTypeCache.get(name);

        ClassStatement stmt = classes.get(name);
        if (stmt == null) return new com.terminalvelocitycabbage.tvscript.analysis.types.ClassType(name);

        // Put a stub in cache first to handle recursion
        com.terminalvelocitycabbage.tvscript.analysis.types.ClassType stub = new com.terminalvelocitycabbage.tvscript.analysis.types.ClassType(name);
        classTypeCache.put(name, stub);

        com.terminalvelocitycabbage.tvscript.analysis.types.ClassType superclass = null;
        if (stmt.superclass() != null) {
            superclass = resolveClassType(stmt.superclass().lexeme());
        }

        List<com.terminalvelocitycabbage.tvscript.analysis.types.TraitType> traitTypes = new ArrayList<>();
        for (Token traitToken : stmt.traits()) {
            traitTypes.add(resolveTraitType(traitToken.lexeme()));
        }

        com.terminalvelocitycabbage.tvscript.analysis.types.ClassType fullType = new com.terminalvelocitycabbage.tvscript.analysis.types.ClassType(name, new ArrayList<>(), superclass, traitTypes);
        classTypeCache.put(name, fullType);
        return fullType;
    }

    public com.terminalvelocitycabbage.tvscript.analysis.types.TraitType resolveTraitType(String name) {
        if (traitTypeCache.containsKey(name)) return traitTypeCache.get(name);

        TraitStatement stmt = traits.get(name);
        if (stmt == null) return new com.terminalvelocitycabbage.tvscript.analysis.types.TraitType(name);

        com.terminalvelocitycabbage.tvscript.analysis.types.TraitType stub = new com.terminalvelocitycabbage.tvscript.analysis.types.TraitType(name);
        traitTypeCache.put(name, stub);

        List<com.terminalvelocitycabbage.tvscript.analysis.types.TraitType> supertraits = new ArrayList<>();
        for (Token traitToken : stmt.traits()) {
            supertraits.add(resolveTraitType(traitToken.lexeme()));
        }

        com.terminalvelocitycabbage.tvscript.analysis.types.TraitType fullType = new com.terminalvelocitycabbage.tvscript.analysis.types.TraitType(name, supertraits);
        traitTypeCache.put(name, fullType);
        return fullType;
    }

    public Type resolveTVType(com.terminalvelocitycabbage.tvscript.execution.TVType tvType, com.terminalvelocitycabbage.tvscript.execution.NativeClass owner) {
        if (tvType == null) return com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType.VOID;
        com.terminalvelocitycabbage.tvscript.execution.TVType.ResolvedType resolved = tvType.resolve(owner, nativeClasses);
        return resolveType(resolved.tokenType(), resolved.namedType());
    }
}
