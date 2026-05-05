package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.TVScript;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import static com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.terminalvelocitycabbage.tvscript.ast.Statement.MatchStatement.Case;
import com.terminalvelocitycabbage.tvscript.ast.Expression.Argument;

/**
 * Executes the AST by visiting each node.
 */
public class Interpreter implements Expression.Visitor<Object>, Statement.Visitor<Void> {

    private static final Object NO_OPERATOR_OVERLOAD = new Object();

    private static class BreakException extends RuntimeException {
        BreakException() { super(null, null, false, false); }
    }

    private static class ContinueException extends RuntimeException {
        ContinueException() { super(null, null, false, false); }
    }

    private static class RangeValue {
        final int start;
        final int end;
        RangeValue(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class TVList {
        private final List<Object> values;
        private String elementType;

        TVList(List<Object> values) {
            this(values, null);
        }

        TVList(List<Object> values, String elementType) {
            this.values = values;
            this.elementType = elementType;
        }

        List<Object> values() {
            return values;
        }

        String elementType() {
            return elementType;
        }

        void setElementType(String elementType) {
            this.elementType = elementType;
        }

        @Override
        public String toString() {
            return values.toString();
        }
    }

    private static class TVSet {
        private final Set<Object> values;
        private String elementType;

        TVSet(Set<Object> values) {
            this(values, null);
        }

        TVSet(Set<Object> values, String elementType) {
            this.values = values;
            this.elementType = elementType;
        }

        Set<Object> values() {
            return values;
        }

        String elementType() {
            return elementType;
        }

        void setElementType(String elementType) {
            this.elementType = elementType;
        }

        @Override
        public String toString() {
            return values.toString();
        }
    }

    private static class TVMap {
        private final Map<Object, Object> values;
        private String keyType;
        private String valueType;

        TVMap(Map<Object, Object> values) {
            this(values, null, null);
        }

        TVMap(Map<Object, Object> values, String keyType, String valueType) {
            this.values = values;
            this.keyType = keyType;
            this.valueType = valueType;
        }

        Map<Object, Object> values() {
            return values;
        }

        String keyType() {
            return keyType;
        }

        String valueType() {
            return valueType;
        }

        void setTypes(String keyType, String valueType) {
            this.keyType = keyType;
            this.valueType = valueType;
        }

        @Override
        public String toString() {
            return values.toString();
        }
    }

    private abstract static class CollectionMethod implements TVScriptCallable {
        private final String name;
        private final int arity;

        CollectionMethod(String name, int arity) {
            this.name = name;
            this.arity = arity;
        }

        @Override
        public int arity() {
            return arity;
        }

        @Override
        public Object call(Interpreter interpreter, Map<String, Object> arguments, Token callToken) {
            List<Object> positional = interpreter.extractPositionalArguments(arguments, callToken, name);
            if (positional.size() != arity) {
                throw new RuntimeError(callToken,
                        "Method '" + name + "' expects " + arity + " argument(s), but got " + positional.size() + ".");
            }
            return invoke(interpreter, positional, callToken);
        }

        protected abstract Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken);
    }

    private record ParsedRuntimeType(String baseName, List<String> arguments) {}

    private final Environment configuredGlobals;
    private Environment environment;

    public Interpreter() {
        this(new Environment());
    }

    public Interpreter(Environment configuredGlobals) {
        this.configuredGlobals = configuredGlobals;
        this.environment = new Environment(configuredGlobals);
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

    private void execute(Statement stmt) {
        stmt.accept(this);
    }

    /**
     * Evaluates an expression.
     * @param expression The expression to evaluate.
     * @return The result of the evaluation.
     */
    public Object evaluate(Expression expression) {
        if (expression == null) return null;
        return expression.accept(this);
    }

    @Override
    public Object visitBinaryExpression(BinaryExpression expr) {
        Object left = evaluate(expr.left());
        Object right = evaluate(expr.right());

        Object overloadedResult = tryEvaluateOverloadedBinary(expr.operator(), left, right);
        if (overloadedResult != NO_OPERATOR_OVERLOAD) {
            return overloadedResult;
        }

        switch (expr.operator().type()) {
            case GREATER:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left > (int) right;
                }
                return ((Number) left).doubleValue() > ((Number) right).doubleValue();
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left >= (int) right;
                }
                return ((Number) left).doubleValue() >= ((Number) right).doubleValue();
            case LESS:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left < (int) right;
                }
                return ((Number) left).doubleValue() < ((Number) right).doubleValue();
            case LESS_EQUAL:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left <= (int) right;
                }
                return ((Number) left).doubleValue() <= ((Number) right).doubleValue();
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
            case MINUS:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left - (int) right;
                }
                return ((Number) left).doubleValue() - ((Number) right).doubleValue();
            case PLUS:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left + (int) right;
                }
                return ((Number) left).doubleValue() + ((Number) right).doubleValue();
            case SLASH:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    if ((int) right == 0) throw new RuntimeError(expr.operator(), "Division by zero.");
                    return (int) left / (int) right;
                }
                return ((Number) left).doubleValue() / ((Number) right).doubleValue();
            case STAR:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left * (int) right;
                }
                return ((Number) left).doubleValue() * ((Number) right).doubleValue();
            case PERCENT:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    if ((int) right == 0) throw new RuntimeError(expr.operator(), "Modulo by zero.");
                    return (int) left % (int) right;
                }
                return ((Number) left).doubleValue() % ((Number) right).doubleValue();
            default:
                return null;
        }
    }

    @Override
    public Object visitGroupingExpression(GroupingExpression expr) {
        return evaluate(expr.expression());
    }

    @Override
    public Object visitLiteralExpression(LiteralExpression expr) {
        return expr.value();
    }

    @Override
    public Object visitLogicalExpression(LogicalExpression expr) {
        Object left = evaluate(expr.left());

        if (expr.operator().type() == TokenType.OR) {
            if (isTruthy(expr.operator(), left)) return left;
        } else {
            if (!isTruthy(expr.operator(), left)) return left;
        }

        Object right = evaluate(expr.right());
        isTruthy(expr.operator(), right); // Ensure right side is also a boolean
        return right;
    }

    @Override
    public Object visitUnaryExpression(UnaryExpression expr) {
        Object right = evaluate(expr.right());

        Object overloadedResult = tryEvaluateOverloadedUnary(expr.operator(), right);
        if (overloadedResult != NO_OPERATOR_OVERLOAD) {
            return overloadedResult;
        }

        switch (expr.operator().type()) {
            case BANG:
                return !isTruthy(expr.operator(), right);
            case MINUS:
                checkNumberOperand(expr.operator(), right);
                if (right instanceof Integer) return -(int) right;
                return -(double) right;
            default:
                return null;
        }
    }

    private Object tryEvaluateOverloadedBinary(Token operator, Object left, Object right) {
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

    private Object tryEvaluateOverloadedUnary(Token operator, Object right) {
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

    private String runtimeTypeName(Object value) {
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

    private boolean isTypeInstance(Object value) {
        return value instanceof TVScriptInstance instance && instance.getType().isType;
    }

    private boolean evaluateComparisonResult(Token operator, double compareValue) {
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

    @Override
    public Object visitTernaryExpression(TernaryExpression expr) {
        Object condition = evaluate(expr.condition());

        if (isTruthy(expr.operator(), condition)) {
            return evaluate(expr.thenBranch());
        } else {
            return evaluate(expr.elseBranch());
        }
    }

    @Override
    public Object visitInterpolationExpression(InterpolationExpression expr) {
        StringBuilder builder = new StringBuilder();
        for (Expression expression : expr.expressions()) {
            builder.append(stringify(evaluate(expression)));
        }
        return builder.toString();
    }

    @Override
    public Object visitVariableExpression(VariableExpression expr) {
        return environment.get(expr.name());
    }

    @Override
    public Object visitNativeExpression(NativeExpression expr) {
        Object value = environment.get(expr.name());
        if (!environment.isNativeFunctionName(expr.name().lexeme()) || !(value instanceof TVScriptNativeFunction)) {
            throw new RuntimeError(expr.keyword(), "'" + expr.name().lexeme() + "' is not a native function.");
        }
        return value;
    }

    @Override
    public Object visitAssignExpression(AssignExpression expr) {
        Object value = evaluate(expr.value());
        Object previousValue = environment.get(expr.name());
        preserveCollectionTypeConstraints(previousValue, value, expr.name());
        environment.assign(expr.name(), value);
        return value;
    }

    @Override
    public Object visitRangeExpression(RangeExpression expr) {
        Object start = evaluate(expr.start());
        Object end = expr.end() == null ? null : evaluate(expr.end());

        if (!(start instanceof Integer) || (end != null && !(end instanceof Integer))) {
            throw new RuntimeError(expr.operator(), "Range bounds must be integers.");
        }

        return new RangeValue((int) start, end == null ? Integer.MIN_VALUE : (int) end);
    }

    @Override
    public Object visitMatchExpression(MatchExpression expr) {
        Object condition = evaluate(expr.condition());

        for (MatchExpression.Case matchCase : expr.cases()) {
            for (Expression pattern : matchCase.patterns()) {
                Object patternValue = evaluate(pattern);
                if (matchPattern(condition, patternValue)) {
                    return evaluate(matchCase.branch());
                }
            }
        }

        if (expr.defaultBranch() != null) {
            return evaluate(expr.defaultBranch());
        }

        throw new RuntimeError(expr.keyword(), "Match expression not exhaustive.");
    }

    @Override
    public Object visitCallExpression(CallExpression expr) {
        Object callee = evaluate(expr.callee());

        if (expr.nativeCall()) {
            if (!(expr.callee() instanceof VariableExpression variableExpression)) {
                throw new RuntimeError(expr.paren(), "Native calls must target a global function name.");
            }

            if (!environment.isNativeFunctionName(variableExpression.name().lexeme()) || !(callee instanceof TVScriptNativeFunction)) {
                throw new RuntimeError(expr.paren(), "'" + variableExpression.name().lexeme() + "' is not a native function.");
            }
        } else if (expr.callee() instanceof VariableExpression variableExpression
                && environment.isNativeFunctionName(variableExpression.name().lexeme())
                && callee instanceof TVScriptNativeFunction) {
            throw new RuntimeError(expr.paren(), "Native functions must be called with 'native'.");
        }

        if (!(callee instanceof TVScriptCallable)) {
            throw new RuntimeError(expr.paren(), "Can only call functions and classes.");
        }

        TVScriptCallable function = (TVScriptCallable) callee;
        Map<String, Object> arguments = new HashMap<>();
        int positionalIndex = 0;
        for (Argument arg : expr.arguments()) {
            String key = arg.isNamed() ? arg.name().lexeme() : "$" + positionalIndex++;
            arguments.put(key, evaluate(arg.value()));
        }

        return function.call(this, arguments, expr.paren());
    }

    @Override
    public Object visitGetExpression(GetExpression expr) {
        Object object = evaluate(expr.object());
        if (object instanceof TVScriptInstance instance) {
            return instance.get(expr.name());
        }

        if (object instanceof TVScriptClass klass) {
            Object classMember = klass.getClassMember(expr.name().lexeme(), this);
            if (classMember != TVScriptClass.MISSING_MEMBER) {
                return classMember;
            }
            throw new RuntimeError(expr.name(), "Undefined static member '" + expr.name().lexeme() + "'.");
        }

        if (object instanceof TVScriptTrait trait) {
            Object value = trait.getConstantField(expr.name().lexeme());
            if (value != null) return value;
            throw new RuntimeError(expr.name(), "Undefined trait constant '" + expr.name().lexeme() + "'.");
        }

        if (object instanceof TVList list) {
            return getListProperty(list, expr.name());
        }

        if (object instanceof TVSet set) {
            return getSetProperty(set, expr.name());
        }

        if (object instanceof TVMap map) {
            return getMapProperty(map, expr.name());
        }

        throw new RuntimeError(expr.name(), "Only instances, classes, and traits have properties.");
    }

    @Override
    public Object visitSetExpression(SetExpression expr) {
        Object object = evaluate(expr.object());

        if (!(object instanceof TVScriptInstance)) {
            throw new RuntimeError(expr.name(), "Only instances have fields.");
        }

        Object value = evaluate(expr.value());
        ((TVScriptInstance) object).set(expr.name(), value);
        return value;
    }

    @Override
    public Object visitIndexExpression(IndexExpression expr) {
        Object object = evaluate(expr.object());
        Object index = evaluate(expr.index());

        if (object instanceof TVList list) {
            if (!(index instanceof Integer)) {
                throw new RuntimeError(expr.bracket(), "List index must be an integer.");
            }
            int resolvedIndex = resolveListIndex(expr.bracket(), (int) index, list.values().size());
            return list.values().get(resolvedIndex);
        }

        if (object instanceof TVMap map) {
            if (!map.values().containsKey(index)) {
                throw new RuntimeError(expr.bracket(), "Map key not found: " + stringify(index) + ".");
            }
            return map.values().get(index);
        }

        throw new RuntimeError(expr.bracket(), "Only lists and maps support index access.");
    }

    @Override
    public Object visitIndexSetExpression(IndexSetExpression expr) {
        Object object = evaluate(expr.object());
        Object index = evaluate(expr.index());
        Object value = evaluate(expr.value());

        if (object instanceof TVList list) {
            if (!(index instanceof Integer)) {
                throw new RuntimeError(expr.bracket(), "List index must be an integer.");
            }
            int resolvedIndex = resolveListIndex(expr.bracket(), (int) index, list.values().size());
            ensureListElementType(list, value, expr.bracket());
            list.values().set(resolvedIndex, value);
            return value;
        }

        if (object instanceof TVMap map) {
            ensureMapEntryType(map, index, value, expr.bracket());
            map.values().put(index, value);
            return value;
        }

        throw new RuntimeError(expr.bracket(), "Only lists and maps support index assignment.");
    }

    @Override
    public Object visitSliceExpression(SliceExpression expr) {
        Object object = evaluate(expr.object());
        if (!(object instanceof TVList list)) {
            throw new RuntimeError(expr.bracket(), "Only lists support slicing.");
        }

        int size = list.values().size();
        if (size == 0) {
            return new TVList(new ArrayList<>(), list.elementType());
        }

        Integer startValue = evaluateOptionalIndex(expr.bracket(), expr.start(), true);
        Integer endValue = evaluateOptionalIndex(expr.bracket(), expr.end(), true);

        int start = startValue == null ? 0 : startValue;
        int end = endValue == null ? size - 1 : endValue;

        if (start >= size) {
            throw new RuntimeError(expr.bracket(), "List index " + start + " is out of bounds for size " + size + ".");
        }
        if (end >= size) {
            throw new RuntimeError(expr.bracket(), "List index " + end + " is out of bounds for size " + size + ".");
        }

        if (start > end) {
            return new TVList(new ArrayList<>(), list.elementType());
        }

        return new TVList(new ArrayList<>(list.values().subList(start, end + 1)), list.elementType());
    }

    @Override
    public Object visitCollectionLiteralExpression(CollectionLiteralExpression expr) {
        if (expr.collectionType().type() == TokenType.LIST) {
            if (expr.size() != null) {
                Object sizeValue = evaluate(expr.size());
                if (!(sizeValue instanceof Integer integerSize)) {
                    throw new RuntimeError(expr.keyword(), "List size must be an integer.");
                }
                if (integerSize < 0) {
                    throw new RuntimeError(expr.keyword(), "List size must be non-negative.");
                }

                List<Object> values = new ArrayList<>();
                for (int i = 0; i < integerSize; i++) {
                    values.add(null);
                }
                return new TVList(values, null);
            }

            List<Object> elements = new ArrayList<>();
            for (Expression element : expr.elements()) {
                elements.add(evaluate(element));
            }
            return new TVList(elements, null);
        }

        if (expr.collectionType().type() == TokenType.SET) {
            Set<Object> values = new LinkedHashSet<>();
            for (Expression element : expr.elements()) {
                values.add(evaluate(element));
            }
            return new TVSet(values, null);
        }

        if (expr.collectionType().type() == TokenType.MAP) {
            Map<Object, Object> values = new LinkedHashMap<>();
            for (MapEntry entry : expr.entries()) {
                values.put(evaluate(entry.key()), evaluate(entry.value()));
            }
            return new TVMap(values, null, null);
        }

        throw new RuntimeError(expr.keyword(), "Unsupported collection type.");
    }

    @Override
    public Object visitThisExpression(ThisExpression expr) {
        return environment.get(expr.keyword());
    }

    @Override
    public Object visitNewExpression(NewExpression expr) {
        Object callee = evaluate(expr.callee());

        if (!(callee instanceof TVScriptClass)) {
            throw new RuntimeError(expr.keyword(), "Can only use 'new' with classes.");
        }

        TVScriptClass klass = (TVScriptClass) callee;
        Map<String, Object> arguments = new HashMap<>();
        int positionalIndex = 0;
        for (Argument argument : expr.arguments()) {
            String key = argument.isNamed() ? argument.name().lexeme() : "$" + positionalIndex++;
            arguments.put(key, evaluate(argument.value()));
        }

        return klass.instantiate(this, arguments, expr.keyword());
    }

    @Override
    public Object visitSuperExpression(SuperExpression expr) {
        // Find the object instance ('this') and its class
        TVScriptInstance instance = (TVScriptInstance) environment.get(new Token(TokenType.THIS, "this", null, 0));

        if (expr.traitName() != null) {
            // Trait.super.method()
            Object traitObj = environment.get(expr.traitName());
            if (!(traitObj instanceof TVScriptTrait)) {
                throw new RuntimeError(expr.traitName(), "Identifier before '.super' must be a trait.");
            }
            TVScriptTrait trait = (TVScriptTrait) traitObj;
            TVScriptFunction method = trait.findMethod(expr.method().lexeme());
            if (method == null) {
                throw new RuntimeError(expr.method(), "Undefined method '" + expr.method().lexeme() + "' in trait '" + trait.getName() + "'.");
            }
            return method.bind(instance);
        } else {
            // super.method()
            TVScriptClass superclass = instance.getType().superclass;
            if (superclass == null) {
                throw new RuntimeError(expr.keyword(), "Class has no superclass.");
            }
            TVScriptFunction method = superclass.findMethod(expr.method().lexeme());
            if (method == null) {
                throw new RuntimeError(expr.method(), "Undefined method '" + expr.method().lexeme() + "' in superclass '" + superclass.name + "'.");
            }
            return method.bind(instance);
        }
    }

    @Override
    public Object visitTypeBinaryExpression(TypeBinaryExpression expr) {
        Object left = evaluate(expr.left());

        // Resolve the type
        Object type = null;
        try {
            type = environment.get(expr.typeName());
        } catch (RuntimeError e) {
            // It might be a basic type like 'integer'
            // We'll handle this in checkType
        }

        switch (expr.operator().type()) {
            case IS:
                return checkType(left, expr.typeName(), type);
            case HAS:
                if (!(type instanceof TVScriptTrait)) {
                    throw new RuntimeError(expr.typeName(), "Expected trait name after 'has'.");
                }
                return checkHasTrait(left, (TVScriptTrait) type);
            case AS:
                if (checkType(left, expr.typeName(), type)) {
                    return left;
                }
                throw new RuntimeError(expr.operator(), "Cannot cast '" + stringify(left) + "' to '" + expr.typeName().lexeme() + "'.");
        }
        return null;
    }

    private boolean checkType(Object value, Token typeToken, Object resolvedType) {
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

    private void applyDeclaredCollectionTypes(Token declaredType, Object value, Token errorToken) {
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

    private void preserveCollectionTypeConstraints(Object previousValue, Object newValue, Token errorToken) {
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

    private void ensureListElementType(TVList list, Object value, Token token) {
        if (list.elementType() == null || value == null) {
            return;
        }

        if (!matchesTypeName(value, list.elementType())) {
            throw new RuntimeError(token,
                    "List expects elements of type '" + list.elementType() + "' but got '" + runtimeTypeName(value) + "'.");
        }
    }

    private void ensureSetElementType(TVSet set, Object value, Token token) {
        if (set.elementType() == null || value == null) {
            return;
        }

        if (!matchesTypeName(value, set.elementType())) {
            throw new RuntimeError(token,
                    "Set expects elements of type '" + set.elementType() + "' but got '" + runtimeTypeName(value) + "'.");
        }
    }

    private void ensureMapEntryType(TVMap map, Object key, Object value, Token token) {
        if (map.keyType() != null && key != null && !matchesTypeName(key, map.keyType())) {
            throw new RuntimeError(token,
                    "Map expects keys of type '" + map.keyType() + "' but got '" + runtimeTypeName(key) + "'.");
        }

        if (map.valueType() != null && value != null && !matchesTypeName(value, map.valueType())) {
            throw new RuntimeError(token,
                    "Map expects values of type '" + map.valueType() + "' but got '" + runtimeTypeName(value) + "'.");
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

    private boolean matchesTypeName(Object value, String typeName) {
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
            return new ParsedRuntimeType(baseName, splitTopLevel(argumentsText, '|'));
        }

        return new ParsedRuntimeType(baseName, splitTopLevel(argumentsText, ','));
    }

    private List<String> splitTopLevel(String value, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '<' || current == '[') {
                depth++;
            } else if (current == '>' || current == ']') {
                depth--;
            }

            if (current == separator && depth == 0) {
                parts.add(currentPart.toString().trim());
                currentPart.setLength(0);
                continue;
            }

            currentPart.append(current);
        }

        if (!currentPart.isEmpty()) {
            parts.add(currentPart.toString().trim());
        }

        return parts;
    }

    private boolean isClassOrSubclass(TVScriptClass actual, TVScriptClass expected) {
        if (actual == expected) return true;
        if (actual.superclass != null) return isClassOrSubclass(actual.superclass, expected);
        return false;
    }

    private boolean checkHasTrait(Object value, TVScriptTrait trait) {
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

    private boolean isTraitOrSupertrait(TVScriptTrait actual, TVScriptTrait expected) {
        if (actual == expected) return true;
        for (TVScriptTrait t : actual.supertraits) {
            if (isTraitOrSupertrait(t, expected)) return true;
        }
        return false;
    }

    @Override
    public Object visitFunctionExpression(FunctionExpression expr) {
        return new TVScriptFunction(expr, environment);
    }

    private boolean matchPattern(Object condition, Object pattern) {
        if (pattern instanceof RangeValue) {
            RangeValue range = (RangeValue) pattern;
            if (condition instanceof Integer) {
                int val = (int) condition;
                return val >= range.start && val <= range.end;
            }
            if (condition instanceof Double) {
                double val = (double) condition;
                return val >= range.start && val <= range.end;
            }
        }
        return isEqual(condition, pattern);
    }

    @Override
    public Void visitBlockStatement(BlockStatement stmt) {
        executeBlock(stmt.statements(), new Environment(environment));
        return null;
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

    @Override
    public Void visitExpressionStatement(ExpressionStatement stmt) {
        evaluate(stmt.expression());
        return null;
    }

    @Override
    public Void visitIfStatement(IfStatement stmt) {
        // Special case for pattern matching alias: if obj is Type -> alias:
        if (stmt.condition() instanceof TypeBinaryExpression && ((TypeBinaryExpression) stmt.condition()).alias() != null) {
            TypeBinaryExpression tbe = (TypeBinaryExpression) stmt.condition();
            Object value = evaluate(tbe.left());
            Object resolvedType = null;
            try {
                resolvedType = environment.get(tbe.typeName());
            } catch (RuntimeError e) {}

            if (checkType(value, tbe.typeName(), resolvedType)) {
                Environment previous = environment;
                environment = new Environment(environment);
                try {
                    environment.define(tbe.alias(), value, inferType(value), true);
                    execute(stmt.thenBranch());
                } finally {
                    environment = previous;
                }
                return null;
            }
        }

        if (isTruthy(stmt.keyword(), evaluate(stmt.condition()))) {
            execute(stmt.thenBranch());
        } else if (stmt.elseBranch() != null) {
            execute(stmt.elseBranch());
        }
        return null;
    }

    @Override
    public Void visitWhileStatement(WhileStatement stmt) {
        try {
            while (isTruthy(stmt.keyword(), evaluate(stmt.condition()))) {
                try {
                    execute(stmt.body());
                } catch (ContinueException e) {
                    // Do nothing, just continue
                }
            }
        } catch (BreakException e) {
            // Do nothing, just break
        }
        return null;
    }

    @Override
    public Void visitForStatement(ForStatement stmt) {
        Object iterableObj = evaluate(stmt.range());

        Environment previous = this.environment;
        try {
            if (iterableObj instanceof RangeValue range) {
                if (stmt.valueName() != null) {
                    throw new RuntimeError(stmt.keyword(), "Range iteration supports only a single loop variable.");
                }
                executeRangeLoop(stmt, previous, range);
            } else if (iterableObj instanceof TVList list) {
                if (stmt.valueName() != null) {
                    throw new RuntimeError(stmt.keyword(), "List iteration supports only a single loop variable.");
                }
                executeValueLoop(stmt, previous, list.values());
            } else if (iterableObj instanceof TVSet set) {
                if (stmt.valueName() != null) {
                    throw new RuntimeError(stmt.keyword(), "Set iteration supports only a single loop variable.");
                }
                executeValueLoop(stmt, previous, set.values());
            } else if (iterableObj instanceof TVMap map) {
                executeMapLoop(stmt, previous, map.values());
            } else {
                throw new RuntimeError(stmt.keyword(), "Expected range, list, set, or map in for loop.");
            }
        } catch (BreakException e) {
            // break
        } finally {
            this.environment = previous;
        }
        return null;
    }

    private void executeRangeLoop(ForStatement stmt, Environment previous, RangeValue range) {
        for (int i = range.start; i <= range.end; i++) {
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

    private void executeValueLoop(ForStatement stmt, Environment previous, Iterable<?> values) {
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

    private void executeMapLoop(ForStatement stmt, Environment previous, Map<Object, Object> values) {
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

    @Override
    public Void visitBreakStatement(BreakStatement stmt) {
        throw new BreakException();
    }

    @Override
    public Void visitContinueStatement(ContinueStatement stmt) {
        throw new ContinueException();
    }

    @Override
    public Void visitFunctionStatement(FunctionStatement stmt) {
        TVScriptFunction function = new TVScriptFunction(stmt, environment);
        environment.define(stmt.name(), function, TokenType.FUNCTION, true);
        return null;
    }

    @Override
    public Void visitClassStatement(ClassStatement stmt) {
        NativeClass nativeClassBinding = null;
        if (stmt.isNative()) {
            nativeClassBinding = environment.getNativeClass(stmt.name().lexeme());
            if (nativeClassBinding == null) {
                throw new RuntimeError(stmt.name(), "'" + stmt.name().lexeme() + "' not defined as a native class type on the global environment.");
            }
        }

        TVScriptClass superclass = null;
        if (stmt.superclass() != null) {
            Object obj = environment.get(stmt.superclass());
            if (!(obj instanceof TVScriptClass)) {
                throw new RuntimeError(stmt.superclass(), "Superclass must be a class.");
            }
            superclass = (TVScriptClass) obj;
        }

        List<TVScriptTrait> traits = new ArrayList<>();
        for (Token traitToken : stmt.traits()) {
            Object obj = environment.get(traitToken);
            if (!(obj instanceof TVScriptTrait)) {
                throw new RuntimeError(traitToken, "Only traits can be implemented.");
            }
            traits.add((TVScriptTrait) obj);
        }

        Map<String, TVScriptFunction> methods = new HashMap<>();
        for (FunctionStatement method : stmt.methods()) {
            TVScriptFunction function = new TVScriptFunction(method, environment);
            methods.put(method.name().lexeme(), function);
        }

        Map<String, TVScriptFunction> staticMethods = new HashMap<>();
        for (FunctionStatement staticMethod : stmt.staticMethods()) {
            TVScriptFunction function = new TVScriptFunction(staticMethod, environment);
            staticMethods.put(staticMethod.name().lexeme(), function);
        }

        List<VarStatement> instanceFields = new ArrayList<>();
        Map<String, Object> classConstants = new HashMap<>();
        for (VarStatement field : stmt.fields()) {
            if (field.isConst()) {
                Object constantValue = field.initializer() == null ? null : evaluate(field.initializer());
                classConstants.put(field.name().lexeme(), constantValue);
            } else {
                instanceFields.add(field);
            }
        }

        if (nativeClassBinding != null) {
            for (NativeClass.ConstantBinding constantBinding : nativeClassBinding.constants().values()) {
                classConstants.putIfAbsent(constantBinding.name(), constantBinding.value());
            }
        }

        List<TVScriptFunction> constructors = new ArrayList<>();
        if (!stmt.isNative()) {
            for (FunctionStatement constructorStmt : stmt.constructors()) {
                constructors.add(new TVScriptFunction(constructorStmt, environment));
            }
        }

        TVScriptClass klass = new TVScriptClass(
                stmt.name().lexeme(),
                superclass,
                traits,
                instanceFields,
                methods,
                staticMethods,
                constructors,
                new HashMap<>(),
                false,
                nativeClassBinding,
                classConstants
        );
        environment.define(stmt.name(), klass, TokenType.CLASS, true);
        return null;
    }

    @Override
    public Void visitTypeStatement(TypeStatement stmt) {
        List<TVScriptTrait> traits = new ArrayList<>();
        for (Token traitToken : stmt.traits()) {
            Object obj = environment.get(traitToken);
            if (!(obj instanceof TVScriptTrait)) {
                throw new RuntimeError(traitToken, "Only traits can be implemented.");
            }
            traits.add((TVScriptTrait) obj);
        }

        Map<String, TVScriptFunction> methods = new HashMap<>();
        for (FunctionStatement method : stmt.methods()) {
            TVScriptFunction function = new TVScriptFunction(method, environment);
            methods.put(method.name().lexeme(), function);
        }

        Map<String, List<TVScriptFunction>> operators = new HashMap<>();
        for (FunctionStatement operator : stmt.operators()) {
            TVScriptFunction function = new TVScriptFunction(operator, environment);
            operators.computeIfAbsent(operator.name().lexeme(), k -> new ArrayList<>()).add(function);
        }

        TVScriptClass type = new TVScriptClass(
                stmt.name().lexeme(),
                null,
                traits,
                stmt.fields(),
                methods,
                new HashMap<>(),
                new ArrayList<>(),
                operators,
                true
        );
        environment.define(stmt.name(), type, TokenType.CLASS, true);
        return null;
    }

    @Override
    public Void visitTraitStatement(TraitStatement stmt) {
        List<TVScriptTrait> traits = new ArrayList<>();
        for (Token traitToken : stmt.traits()) {
            Object obj = environment.get(traitToken);
            if (!(obj instanceof TVScriptTrait)) {
                throw new RuntimeError(traitToken, "Only traits can be extended by other traits.");
            }
            traits.add((TVScriptTrait) obj);
        }

        Map<String, TVScriptFunction> methods = new HashMap<>();
        for (FunctionStatement method : stmt.methods()) {
            TVScriptFunction function = new TVScriptFunction(method, environment);
            methods.put(method.name().lexeme(), function);
        }

        Map<String, Object> constantFields = new HashMap<>();
        for (VarStatement field : stmt.fields()) {
            Object value = null;
            if (field.initializer() != null) {
                value = evaluate(field.initializer());
            }
            constantFields.put(field.name().lexeme(), value);
        }

        TVScriptTrait trait = new TVScriptTrait(stmt.name().lexeme(), traits, stmt.fields(), methods, constantFields);
        environment.define(stmt.name(), trait, TokenType.TRAIT, true);
        return null;
    }

    @Override
    public Void visitReturnStatement(ReturnStatement stmt) {
        Object value = null;
        if (stmt.value() != null) value = evaluate(stmt.value());

        throw new TVScriptFunction.Return(value);
    }

    @Override
    public Void visitPrintStatement(PrintStatement stmt) {
        Object value = evaluate(stmt.expression());
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStatement(VarStatement stmt) {
        Object value = null;
        if (stmt.initializer() != null) {
            value = evaluate(stmt.initializer());
        }

        TokenType type;
        if (stmt.type().type() == TokenType.VAR || stmt.type().type() == TokenType.CONST) {
            type = inferType(value);
            if (type == null) {
                throw new RuntimeError(stmt.name(), "Cannot infer type from 'none'.");
            }
        } else {
            type = stmt.type().type();
        }

        applyDeclaredCollectionTypes(stmt.type(), value, stmt.name());

        environment.define(stmt.name(), value, type, stmt.isConst());
        return null;
    }

    @Override
    public Void visitPassStatement(PassStatement stmt) {
        return null;
    }

    @Override
    public Void visitMatchStatement(MatchStatement stmt) {
        Object condition = evaluate(stmt.condition());

        for (Case matchCase : stmt.cases()) {
            for (Expression pattern : matchCase.patterns()) {
                Object patternValue = evaluate(pattern);
                if (matchPattern(condition, patternValue)) {
                    execute(matchCase.branch());
                    return null;
                }
            }
        }

        if (stmt.defaultBranch() != null) {
            execute(stmt.defaultBranch());
        }

        return null;
    }

    @Override
    public Void visitImportStatement(ImportStatement stmt) {
        return null;
    }

    private TokenType inferType(Object value) {
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

    private List<Object> extractPositionalArguments(Map<String, Object> arguments, Token callToken, String methodName) {
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

    private int resolveListIndex(Token token, int index, int size) {
        int resolvedIndex = index < 0 ? size + index : index;
        if (resolvedIndex < 0 || resolvedIndex >= size) {
            throw new RuntimeError(token, "List index " + index + " is out of bounds for size " + size + ".");
        }
        return resolvedIndex;
    }

    private int resolveListInsertIndex(Token token, int index, int size) {
        int resolvedIndex = index < 0 ? size + index : index;
        if (resolvedIndex < 0 || resolvedIndex > size) {
            throw new RuntimeError(token, "List index " + index + " is out of bounds for size " + size + ".");
        }
        return resolvedIndex;
    }

    private Integer evaluateOptionalIndex(Token token, Expression expression, boolean disallowNegative) {
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

    private Object getListProperty(TVList list, Token name) {
        String property = name.lexeme();
        return switch (property) {
            case "size" -> list.values().size();
            case "add" -> new CollectionMethod("add", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    ensureListElementType(list, arguments.get(0), callToken);
                    list.values().add(arguments.get(0));
                    return null;
                }
            };
            case "insert" -> new CollectionMethod("insert", 2) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    Object indexValue = arguments.get(0);
                    if (!(indexValue instanceof Integer)) {
                        throw new RuntimeError(callToken, "List index must be an integer.");
                    }
                    int index = resolveListInsertIndex(callToken, (int) indexValue, list.values().size());
                    ensureListElementType(list, arguments.get(1), callToken);
                    list.values().add(index, arguments.get(1));
                    return null;
                }
            };
            case "remove" -> new CollectionMethod("remove", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    Object indexValue = arguments.get(0);
                    if (!(indexValue instanceof Integer)) {
                        throw new RuntimeError(callToken, "List index must be an integer.");
                    }
                    int index = resolveListIndex(callToken, (int) indexValue, list.values().size());
                    return list.values().remove(index);
                }
            };
            case "pop" -> new CollectionMethod("pop", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    if (list.values().isEmpty()) {
                        throw new RuntimeError(callToken, "Cannot pop from an empty list.");
                    }
                    return list.values().remove(list.values().size() - 1);
                }
            };
            case "clear" -> new CollectionMethod("clear", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    list.values().clear();
                    return null;
                }
            };
            case "reverse" -> new CollectionMethod("reverse", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    Collections.reverse(list.values());
                    return null;
                }
            };
            case "contains" -> new CollectionMethod("contains", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return list.values().contains(arguments.get(0));
                }
            };
            default -> throw new RuntimeError(name, "Undefined list property '" + property + "'.");
        };
    }

    private Object getSetProperty(TVSet set, Token name) {
        String property = name.lexeme();
        return switch (property) {
            case "size" -> set.values().size();
            case "add" -> new CollectionMethod("add", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    ensureSetElementType(set, arguments.get(0), callToken);
                    set.values().add(arguments.get(0));
                    return null;
                }
            };
            case "remove" -> new CollectionMethod("remove", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    set.values().remove(arguments.get(0));
                    return null;
                }
            };
            case "clear" -> new CollectionMethod("clear", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    set.values().clear();
                    return null;
                }
            };
            case "contains" -> new CollectionMethod("contains", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return set.values().contains(arguments.get(0));
                }
            };
            default -> throw new RuntimeError(name, "Undefined set property '" + property + "'.");
        };
    }

    private Object getMapProperty(TVMap map, Token name) {
        String property = name.lexeme();
        return switch (property) {
            case "size" -> map.values().size();
            case "containsKey" -> new CollectionMethod("containsKey", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return map.values().containsKey(arguments.get(0));
                }
            };
            case "remove" -> new CollectionMethod("remove", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return map.values().remove(arguments.get(0));
                }
            };
            case "keys" -> new CollectionMethod("keys", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return new TVList(new ArrayList<>(map.values().keySet()), map.keyType());
                }
            };
            case "values" -> new CollectionMethod("values", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return new TVList(new ArrayList<>(map.values().values()), map.valueType());
                }
            };
            case "clear" -> new CollectionMethod("clear", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    map.values().clear();
                    return null;
                }
            };
            default -> throw new RuntimeError(name, "Undefined map property '" + property + "'.");
        };
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Integer || operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if ((left instanceof Integer || left instanceof Double) &&
            (right instanceof Integer || right instanceof Double)) return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isTruthy(Token operator, Object object) {
        if (object instanceof Boolean) return (boolean) object;
        if (operator == null) return false;
        throw new RuntimeError(operator, "Expected boolean value.");
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "none";

        return object.toString();
    }
}
