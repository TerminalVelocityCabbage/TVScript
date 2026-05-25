package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.ast.Statement.VarStatement;
import com.terminalvelocitycabbage.tvscript.ast.Statement.FunctionStatement.Parameter;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import com.terminalvelocitycabbage.tvscript.parsing.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TVScriptClass {
    static final Object MISSING_MEMBER = new Object();

    final String name;
    final TVScriptClass superclass;
    final List<TVScriptTrait> traits;
    final List<VarStatement> fields;
    final Map<String, TVScriptFunction> methods;
    final Map<String, TVScriptFunction> staticMethods;
    final List<TVScriptFunction> constructors;
    final Map<String, List<TVScriptFunction>> operators;
    final boolean isType;
    final NativeClass nativeClass;
    final Map<String, Object> classConstants;
    final TokenType visibility;
    final String scriptPath;

    private final Map<Object, TVScriptInstance> nativeWrapperCache = new IdentityHashMap<>();

    public TVScriptClass(String name,
                         TVScriptClass superclass,
                         List<TVScriptTrait> traits,
                         List<VarStatement> fields,
                         Map<String, TVScriptFunction> methods,
                         Map<String, TVScriptFunction> staticMethods,
                         List<TVScriptFunction> constructors,
                         Map<String, List<TVScriptFunction>> operators,
                         boolean isType,
                         TokenType visibility,
                         String scriptPath) {
        this(name, superclass, traits, fields, methods, staticMethods, constructors, operators, isType, null, Map.of(), visibility, scriptPath);
    }

    public TVScriptClass(String name,
                         TVScriptClass superclass,
                         List<TVScriptTrait> traits,
                         List<VarStatement> fields,
                         Map<String, TVScriptFunction> methods,
                         Map<String, TVScriptFunction> staticMethods,
                         List<TVScriptFunction> constructors,
                         Map<String, List<TVScriptFunction>> operators,
                         boolean isType,
                         NativeClass nativeClass,
                         Map<String, Object> classConstants,
                         TokenType visibility,
                         String scriptPath) {
        this.name = name;
        this.superclass = superclass;
        this.traits = traits;
        this.fields = fields;
        this.methods = methods;
        this.staticMethods = staticMethods;
        this.constructors = constructors;
        this.operators = operators;
        this.isType = isType;
        this.nativeClass = nativeClass;
        this.classConstants = classConstants == null ? new HashMap<>() : new HashMap<>(classConstants);
        this.visibility = visibility;
        this.scriptPath = scriptPath;
    }

    public TVScriptInstance instantiate(Interpreter interpreter, Map<String, Object> arguments, Token callToken) {
        if (resolveNativeClass() != null) {
            return instantiateNative(interpreter, arguments, callToken);
        }

        TVScriptInstance instance = new TVScriptInstance(this, interpreter, null);

        initializeFields(instance, interpreter);

        if (constructors.isEmpty()) {
            if (!isType) {
                throw new RuntimeError(callToken, "No matching constructor found for " + name + " with provided arguments.");
            }
            applyTypeArguments(instance, arguments, callToken);
            return instance;
        }

        TVScriptFunction constructor = findBestConstructor(arguments, callToken);
        constructor.bind(instance).call(interpreter, arguments, callToken);

        return instance;
    }

    private TVScriptInstance instantiateNative(Interpreter interpreter, Map<String, Object> arguments, Token callToken) {
        NativeClass boundNativeClass = resolveNativeClass();
        if (boundNativeClass == null) {
            throw new RuntimeError(callToken, "Native class binding for '" + name + "' was not found.");
        }
        NativeClass.ConstructorBinding<?> constructor = findBestNativeConstructor(boundNativeClass, arguments, callToken);
        Map<String, Object> preparedArguments = prepareNativeArguments(boundNativeClass, constructor.parameters(), arguments, callToken, interpreter);
        Object nativeObject = constructor.create(preparedArguments);
        return wrapNativeInstance(nativeObject, interpreter);
    }

    public TVScriptInstance wrapNativeInstance(Object nativeObject, Interpreter interpreter) {
        if (nativeObject == null) {
            throw new IllegalArgumentException("Native object cannot be null.");
        }

        NativeClass boundNativeClass = resolveNativeClass();
        if (boundNativeClass == null) {
            throw new IllegalStateException("Class '" + name + "' is not bound to a native class.");
        }
        if (!boundNativeClass.javaClass().isInstance(nativeObject)) {
            throw new IllegalStateException("Object of type '" + nativeObject.getClass().getName()
                    + "' cannot be wrapped as native class '" + boundNativeClass.scriptName() + "'.");
        }

        TVScriptInstance cached = nativeWrapperCache.get(nativeObject);
        if (cached != null) {
            return cached;
        }

        TVScriptInstance instance = new TVScriptInstance(this, interpreter, nativeObject);
        initializeFields(instance, interpreter);
        nativeWrapperCache.put(nativeObject, instance);
        return instance;
    }

    private void initializeFields(TVScriptInstance instance, Interpreter interpreter) {
        if (superclass != null) {
            superclass.initializeFields(instance, interpreter);
        }

        for (VarStatement field : fields) {
            if (field.isConst() && !isType) {
                continue;
            }
            Object value = null;
            if (field.initializer() != null) {
                value = interpreter.evaluate(field.initializer());
            }
            instance.defineField(field.name(), value);
        }
    }

    private void applyTypeArguments(TVScriptInstance instance, Map<String, Object> arguments, Token callToken) {
        Map<String, VarStatement> fieldMap = new HashMap<>();
        for (VarStatement field : fields) {
            if (field.isConst() && !isType) {
                continue;
            }
            fieldMap.put(field.name().lexeme(), field);
        }

        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            VarStatement field = fieldMap.get(entry.getKey());
            if (field == null) {
                throw new RuntimeError(callToken, "Unknown field '" + entry.getKey() + "' for type '" + name + "'.");
            }
            instance.defineField(field.name(), entry.getValue());
        }
    }

    private TVScriptFunction findBestConstructor(Map<String, Object> arguments, Token callToken) {
        TVScriptFunction bestMatch = null;
        int minUnusedParameters = Integer.MAX_VALUE;

        for (TVScriptFunction constructor : constructors) {
            if (isCandidate(constructor, arguments)) {
                int unusedParams = constructor.arity() - arguments.size();
                if (unusedParams < minUnusedParameters) {
                    minUnusedParameters = unusedParams;
                    bestMatch = constructor;
                }
            }
        }

        if (bestMatch == null) {
            throw new RuntimeError(callToken, "No matching constructor found for " + name + " with provided arguments.");
        }

        return bestMatch;
    }

    private NativeClass.ConstructorBinding<?> findBestNativeConstructor(NativeClass boundNativeClass,
                                                                        Map<String, Object> arguments,
                                                                        Token callToken) {
        for (NativeClass.ConstructorBinding<?> constructor : boundNativeClass.constructors()) {
            if (isNativeCandidate(constructor.parameters(), arguments)) {
                return constructor;
            }
        }
        throw new RuntimeError(callToken, "No matching constructor found for " + name + " with provided arguments.");
    }

    private boolean isCandidate(TVScriptFunction constructor, Map<String, Object> arguments) {
        for (String argName : arguments.keySet()) {
            boolean found = false;
            for (Parameter param : constructor.parameters()) {
                if (param.name().lexeme().equals(argName)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        for (Parameter param : constructor.parameters()) {
            if (param.defaultValue() == null && !arguments.containsKey(param.name().lexeme())) {
                return false;
            }
        }

        return true;
    }

    private boolean isNativeCandidate(List<NativeClass.Parameter> parameters, Map<String, Object> arguments) {
        Set<String> parameterNames = new HashSet<>();
        for (NativeClass.Parameter parameter : parameters) {
            parameterNames.add(parameter.name());
        }

        for (String key : arguments.keySet()) {
            if (key.startsWith("$")) {
                int index;
                try {
                    index = Integer.parseInt(key.substring(1));
                } catch (NumberFormatException ex) {
                    return false;
                }
                if (index < 0 || index >= parameters.size()) {
                    return false;
                }
            } else if (!parameterNames.contains(key)) {
                return false;
            }
        }

        for (int i = 0; i < parameters.size(); i++) {
            NativeClass.Parameter parameter = parameters.get(i);
            if (!arguments.containsKey(parameter.name()) && !arguments.containsKey("$" + i)) {
                return false;
            }
        }

        return true;
    }

    private Map<String, Object> prepareNativeArguments(NativeClass owner,
                                                       List<NativeClass.Parameter> parameters,
                                                       Map<String, Object> arguments,
                                                       Token callToken,
                                                       Interpreter interpreter) {
        Map<String, Object> prepared = new HashMap<>();

        for (int i = 0; i < parameters.size(); i++) {
            NativeClass.Parameter parameter = parameters.get(i);
            Object provided;
            if (arguments.containsKey(parameter.name())) {
                provided = arguments.get(parameter.name());
            } else if (arguments.containsKey("$" + i)) {
                provided = arguments.get("$" + i);
            } else {
                throw new RuntimeError(callToken, "Missing required argument '" + parameter.name() + "'.");
            }

            Object nativeValue = interpreter.toNativeValue(provided);
            TVType.ResolvedType resolvedType = owner.resolveType(parameter.type());
            if (resolvedType.nativeClass() != null && nativeValue != null
                    && !resolvedType.nativeClass().javaClass().isInstance(nativeValue)) {
                throw new RuntimeError(callToken,
                        "Argument '" + parameter.name() + "' expected native type '" + resolvedType.namedType() + "'.");
            }
            prepared.put(parameter.name(), nativeValue);
        }

        for (String key : arguments.keySet()) {
            if (key.startsWith("$")) {
                int index;
                try {
                    index = Integer.parseInt(key.substring(1));
                } catch (NumberFormatException ex) {
                    throw new RuntimeError(callToken, "Invalid positional argument key '" + key + "'.");
                }
                if (index < 0 || index >= parameters.size()) {
                    throw new RuntimeError(callToken, "Unexpected positional argument index '" + index + "'.");
                }
            } else {
                boolean exists = false;
                for (NativeClass.Parameter parameter : parameters) {
                    if (parameter.name().equals(key)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    throw new RuntimeError(callToken, "Unexpected named argument '" + key + "'.");
                }
            }
        }

        return prepared;
    }

    public Object getClassMember(String memberName, Interpreter interpreter) {
        TVScriptFunction staticMethod = findStaticMethod(memberName);
        if (staticMethod != null) {
            checkVisibility(staticMethod.getVisibility(), staticMethod.getScriptPath(), interpreter, "static method " + memberName);
            return staticMethod;
        }
        if (classConstants.containsKey(memberName)) {
            return interpreter.toScriptValue(classConstants.get(memberName));
        }
        return MISSING_MEMBER;
    }

    public void checkVisibility(TokenType visibility, String targetScriptPath, Interpreter interpreter, String memberInfo) {
        if (visibility == TokenType.PUBLIC) return;
        
        String currentScriptPath = interpreter.getCurrentScriptPath();
        String currentModule = interpreter.getCurrentModule();
        
        if (visibility == TokenType.PRIVATE) {
            if (!currentScriptPath.equals(targetScriptPath)) {
                throw new RuntimeError(null, "Cannot access private " + memberInfo + " from script '" + currentScriptPath + "'.");
            }
            return;
        }
        
        if (visibility == TokenType.PROTECTED) {
            String currentFolder = getFolder(currentScriptPath);
            String targetFolder = getFolder(targetScriptPath);
            if (!currentFolder.equals(targetFolder)) {
                throw new RuntimeError(null, "Cannot access protected " + memberInfo + " from script '" + currentScriptPath + "'.");
            }
            return;
        }
        
        if (visibility == TokenType.MODULE) {
             // In tests/embedding, the module might be explicitly set on the interpreter.
             // If the target script also belongs to a module, we should compare them.
             // We can use a heuristic if they are not explicitly set, or just use what's in the interpreter.
             String targetModule = getModuleName(targetScriptPath);
             if (!currentModule.equals(targetModule)) {
                 throw new RuntimeError(null, "Cannot access module-private " + memberInfo + " from module '" + currentModule + "'.");
             }
        }
    }

    private String getModuleName(String path) {
        // Simple heuristic: if it starts with modules/X/, X is the module name.
        if (path.startsWith("modules/") || path.startsWith("modules\\")) {
            String sub = path.substring(8);
            int nextSlash = Math.max(sub.indexOf('/'), sub.indexOf('\\'));
            if (nextSlash != -1) {
                return sub.substring(0, nextSlash);
            }
            return sub;
        }
        return "default";
    }

    private String getFolder(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash == -1) return "";
        return path.substring(0, lastSlash);
    }

    public Object getNativeInstanceMember(TVScriptInstance instance, Token nameToken, Interpreter interpreter) {
        NativeClass boundNativeClass = resolveNativeClass();
        if (boundNativeClass == null) {
            return MISSING_MEMBER;
        }

        Object nativeObject = instance.getNativeObject();
        if (nativeObject == null) {
            return MISSING_MEMBER;
        }

        NativeClass.PropertyBinding<?> property = boundNativeClass.properties().get(nameToken.lexeme());
        if (property != null) {
            return interpreter.toScriptValue(property.get(nativeObject));
        }

        NativeClass.MethodBinding<?> nativeMethod = boundNativeClass.methods().get(nameToken.lexeme());
        if (nativeMethod != null) {
            return new TVScriptCallable() {
                @Override
                public int arity() {
                    return nativeMethod.parameters().size();
                }

                @Override
                public Object call(Interpreter callInterpreter, Map<String, Object> arguments, Token callToken) {
                    Map<String, Object> prepared = prepareNativeArguments(
                            boundNativeClass,
                            nativeMethod.parameters(),
                            arguments,
                            callToken,
                            callInterpreter
                    );
                    Object result = nativeMethod.invoke(nativeObject, prepared);
                    return callInterpreter.toScriptValue(result);
                }

                @Override
                public String toString() {
                    return "<native method " + nameToken.lexeme() + ">";
                }
            };
        }

        return MISSING_MEMBER;
    }

    public boolean setNativeInstanceProperty(TVScriptInstance instance, Token nameToken, Object value, Interpreter interpreter) {
        NativeClass boundNativeClass = resolveNativeClass();
        if (boundNativeClass == null) {
            return false;
        }

        NativeClass.PropertyBinding<?> property = boundNativeClass.properties().get(nameToken.lexeme());
        if (property == null) {
            return false;
        }

        Object nativeObject = instance.getNativeObject();
        if (nativeObject == null) {
            throw new RuntimeError(nameToken, "Native instance for class '" + name + "' was not initialized.");
        }

        TVType.ResolvedType resolvedType = boundNativeClass.resolveType(property.type());
        Object nativeValue = interpreter.toNativeValue(value);
        if (resolvedType.nativeClass() != null && nativeValue != null
                && !resolvedType.nativeClass().javaClass().isInstance(nativeValue)) {
            throw new RuntimeError(nameToken,
                    "Property '" + nameToken.lexeme() + "' expected native type '" + resolvedType.namedType() + "'.");
        }
        property.set(nativeObject, nativeValue);
        return true;
    }

    private NativeClass resolveNativeClass() {
        if (nativeClass != null) {
            return nativeClass;
        }
        if (superclass != null) {
            return superclass.resolveNativeClass();
        }
        return null;
    }

    public boolean isSameOrSubclass(String expectedTypeName) {
        TVScriptClass current = this;
        while (current != null) {
            if (current.name.equals(expectedTypeName)) {
                return true;
            }
            current = current.superclass;
        }
        return false;
    }

    TVScriptFunction findMethod(String name) {
        if (name.equals("constructor")) {
            return constructors.isEmpty() ? null : constructors.get(0);
        }

        if (methods.containsKey(name)) {
            return methods.get(name);
        }
        if (superclass != null) {
            return superclass.findMethod(name);
        }
        for (TVScriptTrait trait : traits) {
            TVScriptFunction method = trait.findMethod(name);
            if (method != null) return method;
        }
        return null;
    }

    TVScriptFunction findStaticMethod(String name) {
        return staticMethods.get(name);
    }

    TVScriptFunction findOperator(String operatorName, Object left, Object right) {
        List<TVScriptFunction> candidates = operators.get(operatorName);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        List<TVScriptFunction> matching = new ArrayList<>();
        for (TVScriptFunction function : candidates) {
            if (matches(function, left, right)) {
                matching.add(function);
            }
        }
        return matching.isEmpty() ? null : matching.get(0);
    }

    private boolean matches(TVScriptFunction function, Object left, Object right) {
        List<Parameter> parameters = function.parameters();
        if (parameters.size() == 1) {
            return isCompatibleArgument(parameters.get(0), right);
        }
        if (parameters.size() == 2) {
            return isCompatibleArgument(parameters.get(0), left) && isCompatibleArgument(parameters.get(1), right);
        }
        return false;
    }

    private boolean isCompatibleArgument(Parameter parameter, Object value) {
        Token type = parameter.type();
        return switch (type.type()) {
            case TYPE_INTEGER -> value instanceof Integer;
            case TYPE_DECIMAL -> value instanceof Double;
            case TYPE_STRING -> value instanceof String;
            case TYPE_BOOLEAN -> value instanceof Boolean;
            case NONE -> value == null;
            case IDENTIFIER -> {
                if (!(value instanceof TVScriptInstance instance)) {
                    yield false;
                }
                yield instance.getType().isSameOrSubclass(type.lexeme());
            }
            default -> true;
        };
    }

    public TokenType getVisibility() {
        return visibility;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    @Override
    public String toString() {
        return name;
    }
}
