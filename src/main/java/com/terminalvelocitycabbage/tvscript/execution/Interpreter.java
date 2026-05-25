package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.execution.values.*;
import com.terminalvelocitycabbage.tvscript.util.AstUtils;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;

import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes the AST by visiting each node.
 */
public class Interpreter {

    static final Object NO_OPERATOR_OVERLOAD = new Object();

    static class BreakException extends RuntimeException {
        BreakException() { super(null, null, false, false); }
    }

    static class ContinueException extends RuntimeException {
        ContinueException() { super(null, null, false, false); }
    }

    final Environment configuredGlobals;
    Environment environment;
    final EventSystem eventSystem;

    final Map<String, Map<String, String>> scriptImports = new HashMap<>();
    final Map<String, Map<String, String>> scriptQualifiedImports = new HashMap<>();

    String currentScriptPath = "default";
    String currentModule = "default";

    private final ExpressionInterpreter expressionInterpreter = new ExpressionInterpreter(this);
    private final StatementInterpreter statementInterpreter = new StatementInterpreter(this);

    public Interpreter() {
        this(new Environment());
    }

    public Interpreter(Environment configuredGlobals) {
        this(configuredGlobals, new TVEventsEventSystem());
    }

    public Interpreter(Environment configuredGlobals, EventSystem eventSystem) {
        this.configuredGlobals = configuredGlobals;
        this.environment = new Environment(configuredGlobals);
        this.eventSystem = eventSystem;
    }

    public void reset() {
        environment = new Environment(configuredGlobals);
    }

    /**
     * Interprets a list of statements.
     * @param statements The statements to interpret.
     */
    public void interpret(List<Statement> statements) {
        for (Statement statement : statements) {
            if (statement != null) execute(statement);
        }
        eventSystem.dispatch("InitializedEvent", Map.of());
    }

    public String getCurrentScriptPath() {
        return currentScriptPath;
    }

    public void setCurrentScriptPath(String currentScriptPath) {
        this.currentScriptPath = currentScriptPath;
    }

    public String getCurrentModule() {
        return currentModule;
    }

    public void setCurrentModule(String currentModule) {
        this.currentModule = currentModule;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public java.util.Collection<TVScriptNativeFunction> getNativeFunctions() {
        return environment.getNativeFunctions();
    }

    Object toNativeValue(Object value) {
        if (value instanceof TVScriptInstance instance && instance.getNativeObject() != null) {
            return instance.getNativeObject();
        }
        return value;
    }

    Object toScriptValue(Object value) {
        if (value == null || value instanceof TVScriptInstance) {
            return value;
        }

        TVScriptClass nativeClass = findTVScriptClassForNativeValue(value);
        if (nativeClass != null) {
            return nativeClass.wrapNativeInstance(value, this);
        }

        return value;
    }

    private TVScriptClass findTVScriptClassForNativeValue(Object value) {
        for (NativeClass nativeClass : environment.getNativeClasses()) {
            if (!nativeClass.javaClass().isInstance(value)) {
                continue;
            }

            try {
                Object classValue = environment.get(new Token(TokenType.CLASS, nativeClass.scriptName(), null, 0));
                if (classValue instanceof TVScriptClass tvScriptClass) {
                    return tvScriptClass;
                }
            } catch (RuntimeError ignored) {
                // class not yet bound in this environment
            }
        }
        return null;
    }

    public Object evaluate(Expression expression) {
        if (expression == null) return null;
        return expression.accept(expressionInterpreter);
    }

    public Object evaluate(Expression expression, Environment env) {
        Environment previous = this.environment;
        try {
            this.environment = env;
            return evaluate(expression);
        } finally {
            this.environment = previous;
        }
    }

    void execute(Statement stmt) {
        if (stmt == null) return;
        stmt.accept(statementInterpreter);
    }

    Object tryEvaluateOverloadedBinary(Token operator, Object left, Object right) {
        if (!isTypeInstance(left) && !isTypeInstance(right)) {
            return NO_OPERATOR_OVERLOAD;
        }

        String operatorName = switch (operator.type()) {
            case PLUS -> "add";
            case MINUS -> "subtract";
            case STAR -> "multiply";
            case SLASH -> "divide";
            case PERCENT -> "modulo";
            case GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, EQUAL_EQUAL, BANG_EQUAL -> "compare";
            default -> null;
        };

        if (operatorName == null) {
            return NO_OPERATOR_OVERLOAD;
        }

        TVScriptFunction function = findOperatorFunction(operator, left, right, operatorName);
        if (function == null) {
            if (operatorName.equals("compare")) {
                throw missingOperatorError(operator, "comparison", left, right);
            }
            throw missingOperatorError(operator, operatorName, left, right);
        }

        Object result = function.call(this, buildOperatorArguments(function, left, right), operator);
        if (operatorName.equals("compare")) {
            if (!(result instanceof Number compareValue)) {
                throw new RuntimeError(operator, "Operator compare must return decimal.");
            }
            return evaluateComparisonResult(operator, compareValue.doubleValue());
        }
        return result;
    }

    Object tryEvaluateOverloadedUnary(Token operator, Object right) {
        if (!isTypeInstance(right) || operator.type() != TokenType.MINUS) {
            return NO_OPERATOR_OVERLOAD;
        }

        TVScriptClass owner = ((TVScriptInstance) right).getType();
        TVScriptFunction function = owner.findOperator("negative", null, right);
        if (function == null) {
            throw missingOperatorError(operator, "negative", null, right);
        }
        return function.call(this, buildOperatorArguments(function, null, right), operator);
    }

    private TVScriptFunction findOperatorFunction(Token operator, Object left, Object right, String operatorName) {
        if (left instanceof TVScriptInstance leftInstance && leftInstance.getType().isType) {
            TVScriptFunction function = leftInstance.getType().findOperator(operatorName, left, right);
            if (function != null) {
                return function;
            }
        }
        if (right instanceof TVScriptInstance rightInstance && rightInstance.getType().isType) {
            return rightInstance.getType().findOperator(operatorName, left, right);
        }
        return null;
    }

    private Map<String, Object> buildOperatorArguments(TVScriptFunction function, Object left, Object right) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        List<Statement.FunctionStatement.Parameter> parameters = function.parameters();
        if (parameters.size() == 1) {
            arguments.put(parameters.get(0).name().lexeme(), right);
        } else {
            arguments.put(parameters.get(0).name().lexeme(), left);
            arguments.put(parameters.get(1).name().lexeme(), right);
        }
        return arguments;
    }

    private RuntimeError missingOperatorError(Token operator, String operatorName, Object left, Object right) {
        return new RuntimeError(operator,
                "no operator overload defined for \"" + operatorName + "\" between " +
                        runtimeTypeName(left) + " and " + runtimeTypeName(right));
    }

    public String runtimeTypeName(Object value) {
        if (value instanceof TVScriptInstance instance) {
            return instance.getType().name;
        }
        if (value instanceof Integer) return "integer";
        if (value instanceof Double) return "decimal";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof String) return "string";
        if (value == null) return "none";
        return value.getClass().getSimpleName().toLowerCase();
    }

    boolean isTypeInstance(Object value) {
        return value instanceof TVScriptInstance instance && instance.getType().isType;
    }

    boolean evaluateComparisonResult(Token operator, double compareValue) {
        return switch (operator.type()) {
            case EQUAL_EQUAL -> compareValue == 0;
            case BANG_EQUAL -> compareValue != 0;
            case LESS -> compareValue < 0;
            case LESS_EQUAL -> compareValue <= 0;
            case GREATER -> compareValue > 0;
            case GREATER_EQUAL -> compareValue >= 0;
            default -> throw new RuntimeError(operator, "Unsupported comparison operator.");
        };
    }

    boolean checkType(Object value, Token typeToken, Object resolvedType) {
        if (value == null) return typeToken.type() == TokenType.NONE;

        if (resolvedType instanceof TVScriptClass) {
            if (!(value instanceof TVScriptInstance)) return false;
            TVScriptClass klass = ((TVScriptInstance) value).getType();
            return isClassOrSubclass(klass, (TVScriptClass) resolvedType);
        }

        if (resolvedType instanceof TVScriptTrait) {
            return checkHasTrait(value, (TVScriptTrait) resolvedType);
        }

        // Basic types
        switch (typeToken.type()) {
            case TYPE_INTEGER: return value instanceof Integer;
            case TYPE_DECIMAL: return value instanceof Double;
            case TYPE_STRING: return value instanceof String;
            case TYPE_BOOLEAN: return value instanceof Boolean;
            case NONE: return value == null;
        }

        return false;
    }

    void applyDeclaredCollectionTypes(Token declaredType, Object value, Token errorToken) {
        if (value == null) {
            return;
        }

        ParsedRuntimeType parsedType = parseRuntimeType(declaredType.lexeme());

        if (declaredType.type() == TokenType.LIST) {
            if (!(value instanceof TVList list)) {
                throw new RuntimeError(errorToken, "Expected list value for type '" + declaredType.lexeme() + "'.");
            }
            if (!parsedType.arguments().isEmpty()) {
                String elementType = parsedType.arguments().get(0);
                validateExistingListElements(list, elementType, errorToken);
                list.setElementType(elementType);
            }
            return;
        }

        if (declaredType.type() == TokenType.SET) {
            if (!(value instanceof TVSet set)) {
                throw new RuntimeError(errorToken, "Expected set value for type '" + declaredType.lexeme() + "'.");
            }
            if (!parsedType.arguments().isEmpty()) {
                String elementType = parsedType.arguments().get(0);
                validateExistingSetElements(set, elementType, errorToken);
                set.setElementType(elementType);
            }
            return;
        }

        if (declaredType.type() == TokenType.MAP) {
            if (!(value instanceof TVMap map)) {
                throw new RuntimeError(errorToken, "Expected map value for type '" + declaredType.lexeme() + "'.");
            }
            if (parsedType.arguments().size() >= 2) {
                String keyType = parsedType.arguments().get(0);
                String valueType = parsedType.arguments().get(1);
                validateExistingMapEntries(map, keyType, valueType, errorToken);
                map.setTypes(keyType, valueType);
            }
        }
    }

    void preserveCollectionTypeConstraints(Object previousValue, Object newValue, Token errorToken) {
        if (previousValue instanceof TVList previousList && newValue instanceof TVList newList && previousList.elementType() != null) {
            validateExistingListElements(newList, previousList.elementType(), errorToken);
            newList.setElementType(previousList.elementType());
            return;
        }

        if (previousValue instanceof TVSet previousSet && newValue instanceof TVSet newSet && previousSet.elementType() != null) {
            validateExistingSetElements(newSet, previousSet.elementType(), errorToken);
            newSet.setElementType(previousSet.elementType());
            return;
        }

        if (previousValue instanceof TVMap previousMap && newValue instanceof TVMap newMap
                && (previousMap.keyType() != null || previousMap.valueType() != null)) {
            validateExistingMapEntries(newMap, previousMap.keyType(), previousMap.valueType(), errorToken);
            newMap.setTypes(previousMap.keyType(), previousMap.valueType());
        }
    }

    private void validateExistingListElements(TVList list, String elementType, Token token) {
        for (Object element : list.values()) {
            if (element != null && !matchesTypeName(element, elementType)) {
                throw new RuntimeError(token,
                        "List expects elements of type '" + elementType + "' but found '" + runtimeTypeName(element) + "'.");
            }
        }
    }

    private void validateExistingSetElements(TVSet set, String elementType, Token token) {
        for (Object element : set.values()) {
            if (element != null && !matchesTypeName(element, elementType)) {
                throw new RuntimeError(token,
                        "Set expects elements of type '" + elementType + "' but found '" + runtimeTypeName(element) + "'.");
            }
        }
    }

    private void validateExistingMapEntries(TVMap map, String keyType, String valueType, Token token) {
        for (Map.Entry<Object, Object> entry : map.values().entrySet()) {
            if (keyType != null && entry.getKey() != null && !matchesTypeName(entry.getKey(), keyType)) {
                throw new RuntimeError(token,
                        "Map expects keys of type '" + keyType + "' but found '" + runtimeTypeName(entry.getKey()) + "'.");
            }
            if (valueType != null && entry.getValue() != null && !matchesTypeName(entry.getValue(), valueType)) {
                throw new RuntimeError(token,
                        "Map expects values of type '" + valueType + "' but found '" + runtimeTypeName(entry.getValue()) + "'.");
            }
        }
    }

    public boolean matchesTypeName(Object value, String typeName) {
        if (value == null) {
            return true;
        }

        ParsedRuntimeType parsedType = parseRuntimeType(typeName);
        String baseType = parsedType.baseName();

        return switch (baseType) {
            case "integer" -> value instanceof Integer;
            case "decimal" -> value instanceof Double || value instanceof Integer;
            case "string" -> value instanceof String;
            case "boolean" -> value instanceof Boolean;
            case "none" -> value == null;
            case "list" -> {
                if (!(value instanceof TVList list)) {
                    yield false;
                }
                if (parsedType.arguments().isEmpty()) {
                    yield true;
                }
                String elementType = parsedType.arguments().get(0);
                if (list.elementType() != null) {
                    yield list.elementType().equals(elementType);
                }
                boolean valid = true;
                for (Object element : list.values()) {
                    if (element != null && !matchesTypeName(element, elementType)) {
                        valid = false;
                        break;
                    }
                }
                yield valid;
            }
            case "set" -> {
                if (!(value instanceof TVSet set)) {
                    yield false;
                }
                if (parsedType.arguments().isEmpty()) {
                    yield true;
                }
                String elementType = parsedType.arguments().get(0);
                if (set.elementType() != null) {
                    yield set.elementType().equals(elementType);
                }
                boolean valid = true;
                for (Object element : set.values()) {
                    if (element != null && !matchesTypeName(element, elementType)) {
                        valid = false;
                        break;
                    }
                }
                yield valid;
            }
            case "map" -> {
                if (!(value instanceof TVMap map)) {
                    yield false;
                }
                if (parsedType.arguments().size() < 2) {
                    yield true;
                }
                String keyType = parsedType.arguments().get(0);
                String valueType = parsedType.arguments().get(1);
                if (map.keyType() != null && map.valueType() != null) {
                    yield map.keyType().equals(keyType) && map.valueType().equals(valueType);
                }
                boolean valid = true;
                for (Map.Entry<Object, Object> entry : map.values().entrySet()) {
                    if (entry.getKey() != null && !matchesTypeName(entry.getKey(), keyType)) {
                        valid = false;
                        break;
                    }
                    if (entry.getValue() != null && !matchesTypeName(entry.getValue(), valueType)) {
                        valid = false;
                        break;
                    }
                }
                yield valid;
            }
            default -> {
                Object resolvedType = null;
                try {
                    resolvedType = environment.get(new Token(TokenType.IDENTIFIER, baseType, null, 0));
                } catch (RuntimeError ignored) {
                    // Fall back to runtime type name comparison below.
                }

                if (resolvedType instanceof TVScriptClass expectedClass) {
                    if (!(value instanceof TVScriptInstance instance)) {
                        yield false;
                    }
                    yield isClassOrSubclass(instance.getType(), expectedClass);
                }

                if (resolvedType instanceof TVScriptTrait expectedTrait) {
                    yield checkHasTrait(value, expectedTrait);
                }

                yield runtimeTypeName(value).equals(baseType);
            }
        };
    }

    private ParsedRuntimeType parseRuntimeType(String typeName) {
        int angleStart = typeName.indexOf('<');
        int squareStart = typeName.indexOf('[');

        int bracketStart;
        int endIndex;
        if (angleStart >= 0 && typeName.endsWith(">")) {
            bracketStart = angleStart;
            endIndex = typeName.length() - 1;
        } else if (squareStart >= 0 && typeName.endsWith("]")) {
            bracketStart = squareStart;
            endIndex = typeName.length() - 1;
        } else {
            return new ParsedRuntimeType(typeName.trim(), List.of());
        }

        String baseName = typeName.substring(0, bracketStart).trim();
        String argumentsText = typeName.substring(bracketStart + 1, endIndex).trim();
        if (argumentsText.isEmpty()) {
            return new ParsedRuntimeType(baseName, List.of());
        }

        if ("map".equals(baseName)) {
            return new ParsedRuntimeType(baseName, AstUtils.splitTopLevel(argumentsText, '|'));
        }

        return new ParsedRuntimeType(baseName, AstUtils.splitTopLevel(argumentsText, ','));
    }

    boolean isClassOrSubclass(TVScriptClass actual, TVScriptClass expected) {
        if (actual == expected) return true;
        if (actual.superclass != null) return isClassOrSubclass(actual.superclass, expected);
        return false;
    }

    boolean checkHasTrait(Object value, TVScriptTrait trait) {
        if (!(value instanceof TVScriptInstance)) return false;
        TVScriptClass klass = ((TVScriptInstance) value).getType();
        return hasTrait(klass, trait);
    }

    private boolean hasTrait(TVScriptClass klass, TVScriptTrait trait) {
        for (TVScriptTrait t : klass.traits) {
            if (isTraitOrSupertrait(t, trait)) return true;
        }
        if (klass.superclass != null) return hasTrait(klass.superclass, trait);
        return false;
    }

    boolean isTraitOrSupertrait(TVScriptTrait actual, TVScriptTrait expected) {
        if (actual == expected) return true;
        for (TVScriptTrait t : actual.supertraits) {
            if (isTraitOrSupertrait(t, expected)) return true;
        }
        return false;
    }

    boolean matchPattern(Object condition, Object pattern) {
        if (pattern instanceof RangeValue) {
            RangeValue range = (RangeValue) pattern;
            if (condition instanceof Integer) {
                int val = (int) condition;
                return val >= range.start() && val <= range.end();
            }
            if (condition instanceof Double) {
                double val = (double) condition;
                return val >= range.start() && val <= range.end();
            }
        }
        return isEqual(condition, pattern);
    }

    public void executeBlock(List<Statement> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;
            for (Statement statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    void executeRangeLoop(ForStatement stmt, Environment previous, RangeValue range) {
        for (int i = range.start(); i <= range.end(); i++) {
            this.environment = new Environment(previous);
            if (stmt.name() != null) {
                this.environment.define(stmt.name(), i, stmt.type().type(), false);
            }

            try {
                execute(stmt.body());
            } catch (ContinueException e) {
                // continue
            } finally {
                this.environment = previous;
            }
        }
    }

    void executeValueLoop(ForStatement stmt, Environment previous, Iterable<?> values) {
        for (Object value : values) {
            this.environment = new Environment(previous);
            if (stmt.name() != null) {
                this.environment.define(stmt.name(), value, stmt.type().type(), false);
            }

            try {
                execute(stmt.body());
            } catch (ContinueException e) {
                // continue
            } finally {
                this.environment = previous;
            }
        }
    }

    void executeMapLoop(ForStatement stmt, Environment previous, Map<Object, Object> values) {
        for (Map.Entry<Object, Object> entry : values.entrySet()) {
            this.environment = new Environment(previous);
            if (stmt.name() != null) {
                this.environment.define(stmt.name(), entry.getKey(), stmt.type().type(), false);
            }
            if (stmt.valueName() != null) {
                this.environment.define(stmt.valueName(), entry.getValue(), stmt.valueType().type(), false);
            }

            try {
                execute(stmt.body());
            } catch (ContinueException e) {
                // continue
            } finally {
                this.environment = previous;
            }
        }
    }

    TokenType inferType(Object value) {
        if (value instanceof Integer) return TokenType.TYPE_INTEGER;
        if (value instanceof Double) return TokenType.TYPE_DECIMAL;
        if (value instanceof String) return TokenType.TYPE_STRING;
        if (value instanceof Boolean) return TokenType.TYPE_BOOLEAN;
        if (value instanceof TVList) return TokenType.LIST;
        if (value instanceof TVSet) return TokenType.SET;
        if (value instanceof TVMap) return TokenType.MAP;
        if (value instanceof TVScriptCallable) return TokenType.FUNCTION;
        if (value instanceof TVScriptInstance) return TokenType.CLASS;
        return null;
    }

    public List<Object> extractPositionalArguments(Map<String, Object> arguments, Token callToken, String methodName) {
        for (String key : arguments.keySet()) {
            if (!key.startsWith("$")) {
                throw new RuntimeError(callToken, "Method '" + methodName + "' only supports positional arguments.");
            }
        }

        List<Object> positional = new ArrayList<>();
        int index = 0;
        while (arguments.containsKey("$" + index)) {
            positional.add(arguments.get("$" + index));
            index++;
        }

        if (positional.size() != arguments.size()) {
            throw new RuntimeError(callToken, "Invalid argument structure for method '" + methodName + "'.");
        }

        return positional;
    }

    Integer evaluateOptionalIndex(Token token, Expression expression, boolean disallowNegative) {
        if (expression == null) {
            return null;
        }

        Object value = evaluate(expression);
        if (!(value instanceof Integer integer)) {
            throw new RuntimeError(token, "List index must be an integer.");
        }

        if (disallowNegative && integer < 0) {
            throw new RuntimeError(token, "List range bounds must be non-negative.");
        }

        return integer;
    }

    void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Integer || operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    void checkNumberOperands(Token operator, Object left, Object right) {
        if ((left instanceof Integer || left instanceof Double) &&
            (right instanceof Integer || right instanceof Double)) return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    public boolean isTruthy(Token operator, Object object) {
        if (object instanceof Boolean) return (boolean) object;
        if (operator != null) {
            throw new RuntimeError(operator, "Expected boolean value.");
        }
        // Fallback for internal calls where operator might be null
        return false;
    }

    boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    public String stringify(Object object) {
        if (object == null) return "none";

        return object.toString();
    }
}
