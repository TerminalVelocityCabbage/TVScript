package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import com.terminalvelocitycabbage.tvscript.execution.values.*;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.util.AstUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExpressionInterpreter implements Expression.Visitor<Object> {

    private final Interpreter interpreter;

    public ExpressionInterpreter(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    @Override
    public Object visitTernaryExpression(TernaryExpression expr) {
        Object condition = interpreter.evaluate(expr.condition());

        if (interpreter.isTruthy(expr.operator(), condition)) {
            return interpreter.evaluate(expr.thenBranch());
        } else {
            return interpreter.evaluate(expr.elseBranch());
        }
    }

    @Override
    public Object visitInterpolationExpression(InterpolationExpression expr) {
        StringBuilder builder = new StringBuilder();
        for (Expression expression : expr.expressions()) {
            builder.append(interpreter.stringify(interpreter.evaluate(expression)));
        }
        return builder.toString();
    }

    @Override
    public Object visitVariableExpression(VariableExpression expr) {
        String name = expr.name().lexeme();

        // Try resolving through selective imports
        Map<String, String> currentImports = interpreter.scriptImports.get(interpreter.currentScriptPath);
        if (currentImports != null && currentImports.containsKey(name)) {
            name = currentImports.get(name);
        }

        return interpreter.environment.get(name);
    }

    @Override
    public Object visitNativeExpression(NativeExpression expr) {
        Object value = interpreter.environment.get(expr.name());
        if (!interpreter.environment.isNativeFunctionName(expr.name().lexeme()) || !(value instanceof TVScriptNativeFunction)) {
            throw new RuntimeError(expr.keyword(), "'" + expr.name().lexeme() + "' is not a native function.");
        }
        return value;
    }

    @Override
    public Object visitAssignExpression(AssignExpression expr) {
        Object value = interpreter.evaluate(expr.value());
        Object previousValue = interpreter.environment.get(expr.name());
        interpreter.preserveCollectionTypeConstraints(previousValue, value, expr.name());
        interpreter.environment.assign(expr.name(), value);
        return value;
    }

    @Override
    public Object visitRangeExpression(RangeExpression expr) {
        Object start = interpreter.evaluate(expr.start());
        Object end = expr.end() == null ? null : interpreter.evaluate(expr.end());

        if (!(start instanceof Integer) || (end != null && !(end instanceof Integer))) {
            throw new RuntimeError(expr.operator(), "Range bounds must be integers.");
        }

        return new RangeValue((int) start, end == null ? Integer.MIN_VALUE : (int) end);
    }

    // ... more visit methods will be moved here ...

    @Override
    public Object visitBinaryExpression(BinaryExpression expr) {
        Object left = interpreter.evaluate(expr.left());
        Object right = interpreter.evaluate(expr.right());

        Object overloadedResult = interpreter.tryEvaluateOverloadedBinary(expr.operator(), left, right);
        if (overloadedResult != Interpreter.NO_OPERATOR_OVERLOAD) {
            return overloadedResult;
        }

        return switch (expr.operator().type()) {
            case GREATER, GREATER_EQUAL, LESS, LESS_EQUAL -> {
                interpreter.checkNumberOperands(expr.operator(), left, right);
                double l = ((Number) left).doubleValue();
                double r = ((Number) right).doubleValue();
                yield interpreter.evaluateComparisonResult(expr.operator(), l - r);
            }
            case BANG_EQUAL -> !interpreter.isEqual(left, right);
            case EQUAL_EQUAL -> interpreter.isEqual(left, right);
            case MINUS -> {
                interpreter.checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) yield (int) left - (int) right;
                yield ((Number) left).doubleValue() - ((Number) right).doubleValue();
            }
            case PLUS -> {
                if (left instanceof Integer && right instanceof Integer) yield (int) left + (int) right;
                if (left instanceof String && right instanceof String) yield (String) left + (String) right;
                interpreter.checkNumberOperands(expr.operator(), left, right);
                yield ((Number) left).doubleValue() + ((Number) right).doubleValue();
            }
            case SLASH -> {
                interpreter.checkNumberOperands(expr.operator(), left, right);
                if (right instanceof Integer && (int) right == 0) throw new RuntimeError(expr.operator(), "Division by zero.");
                if (right instanceof Double && (double) right == 0) throw new RuntimeError(expr.operator(), "Division by zero.");
                if (left instanceof Integer && right instanceof Integer) yield (int) left / (int) right;
                yield ((Number) left).doubleValue() / ((Number) right).doubleValue();
            }
            case STAR -> {
                interpreter.checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) yield (int) left * (int) right;
                yield ((Number) left).doubleValue() * ((Number) right).doubleValue();
            }
            case PERCENT -> {
                interpreter.checkNumberOperands(expr.operator(), left, right);
                if (right instanceof Integer && (int) right == 0) throw new RuntimeError(expr.operator(), "Modulo by zero.");
                if (right instanceof Double && (double) right == 0) throw new RuntimeError(expr.operator(), "Modulo by zero.");
                if (left instanceof Integer && right instanceof Integer) yield (int) left % (int) right;
                yield ((Number) left).doubleValue() % ((Number) right).doubleValue();
            }
            default -> null;
        };
    }

    @Override
    public Object visitGroupingExpression(GroupingExpression expr) {
        return interpreter.evaluate(expr.expression());
    }

    @Override
    public Object visitLiteralExpression(LiteralExpression expr) {
        return expr.value();
    }

    @Override
    public Object visitLogicalExpression(LogicalExpression expr) {
        Object left = interpreter.evaluate(expr.left());

        if (expr.operator().type() == TokenType.OR) {
            if (interpreter.isTruthy(expr.operator(), left)) return left;
        } else {
            if (!interpreter.isTruthy(expr.operator(), left)) return left;
        }

        Object right = interpreter.evaluate(expr.right());
        interpreter.isTruthy(expr.operator(), right); // Ensure right side is also a boolean
        return right;
    }

    @Override
    public Object visitUnaryExpression(UnaryExpression expr) {
        Object right = interpreter.evaluate(expr.right());

        Object overloadedResult = interpreter.tryEvaluateOverloadedUnary(expr.operator(), right);
        if (overloadedResult != Interpreter.NO_OPERATOR_OVERLOAD) {
            return overloadedResult;
        }

        return switch (expr.operator().type()) {
            case BANG -> !interpreter.isTruthy(expr.operator(), right);
            case MINUS -> {
                interpreter.checkNumberOperand(expr.operator(), right);
                if (right instanceof Integer) yield -(int) right;
                yield -(double) right;
            }
            default -> null;
        };
    }

    @Override
    public Object visitMatchExpression(MatchExpression expr) {
        Object condition = interpreter.evaluate(expr.condition());

        for (MatchExpression.Case matchCase : expr.cases()) {
            for (Expression pattern : matchCase.patterns()) {
                Object patternValue = interpreter.evaluate(pattern);
                if (interpreter.matchPattern(condition, patternValue)) {
                    return interpreter.evaluate(matchCase.branch());
                }
            }
        }

        if (expr.defaultBranch() != null) {
            return interpreter.evaluate(expr.defaultBranch());
        }

        throw new RuntimeError(expr.keyword(), "Match expression not exhaustive.");
    }
    @Override
    public Object visitCallExpression(CallExpression expr) {
        Object callee = interpreter.evaluate(expr.callee());

        if (expr.nativeCall()) {
            if (!(expr.callee() instanceof VariableExpression variableExpression)) {
                throw new RuntimeError(expr.paren(), "Native calls must target a global function name.");
            }

            if (!interpreter.environment.isNativeFunctionName(variableExpression.name().lexeme()) || !(callee instanceof TVScriptNativeFunction)) {
                throw new RuntimeError(expr.paren(), "'" + variableExpression.name().lexeme() + "' is not a native function.");
            }
        } else if (expr.callee() instanceof VariableExpression variableExpression
                && interpreter.environment.isNativeFunctionName(variableExpression.name().lexeme())
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
            arguments.put(key, interpreter.evaluate(arg.value()));
        }

        return function.call(interpreter, arguments, expr.paren());
    }

    @Override
    public Object visitGetExpression(GetExpression expr) {
        // Try resolving as a qualified name first: path.to.Script.Member or Alias.Member
        String fullName = AstUtils.flattenQualifiedName(expr);
        if (fullName != null) {
            if (interpreter.environment.isAlreadyDefinedAnywhere(fullName)) {
                return interpreter.environment.get(fullName);
            }

            // Check if the object is an alias
            if (expr.object() instanceof VariableExpression varExpr) {
                String alias = varExpr.name().lexeme();
                Map<String, String> currentQualifiedImports = interpreter.scriptQualifiedImports.get(interpreter.currentScriptPath);
                if (currentQualifiedImports != null && currentQualifiedImports.containsKey(alias)) {
                    String resolvedPrefix = currentQualifiedImports.get(alias);
                    String resolvedName = resolvedPrefix + "." + expr.name().lexeme();
                    if (interpreter.environment.isAlreadyDefinedAnywhere(resolvedName)) {
                        return interpreter.environment.get(resolvedName);
                    }
                }
            }
        }

        Object object = interpreter.evaluate(expr.object());
        if (object instanceof ScriptValue scriptValue) {
            return scriptValue.get(interpreter, expr.name());
        }

        throw new RuntimeError(expr.name(), "Object of type '" + interpreter.runtimeTypeName(object) + "' does not have properties.");
    }

    @Override
    public Object visitSetExpression(SetExpression expr) {
        Object object = interpreter.evaluate(expr.object());
        Object value = interpreter.evaluate(expr.value());

        if (object instanceof ScriptValue scriptValue) {
            scriptValue.set(interpreter, expr.name(), value);
            return value;
        }

        throw new RuntimeError(expr.name(), "Object of type '" + interpreter.runtimeTypeName(object) + "' does not support property assignment.");
    }

    @Override
    public Object visitThisExpression(ThisExpression expr) {
        return interpreter.environment.get(expr.keyword());
    }

    @Override
    public Object visitSuperExpression(SuperExpression expr) {
        // Find the object instance ('this') and its class
        TVScriptInstance instance = (TVScriptInstance) interpreter.environment.get(new Token(TokenType.THIS, "this", null, 0));

        if (expr.traitName() != null) {
            // Trait.super.method()
            Object traitObj = interpreter.environment.get(expr.traitName());
            if (!(traitObj instanceof TVScriptTrait)) {
                throw new RuntimeError(expr.traitName(), "Identifier before '.super' must be a trait.");
            }
            TVScriptTrait trait = (TVScriptTrait) traitObj;
            TVScriptFunction method = trait.findMethod(expr.method().lexeme());

            if (method == null) {
                throw new RuntimeError(expr.method(), "Undefined property '" + expr.method().lexeme() + "' in trait '" + trait.getName() + "'.");
            }

            return method.bind(instance);
        }

        TVScriptClass superclass = instance.getType().superclass;
        if (superclass == null) {
            throw new RuntimeError(expr.keyword(), "Class '" + instance.getType().name + "' has no superclass.");
        }

        TVScriptFunction method = superclass.findMethod(expr.method().lexeme());

        if (method == null) {
            throw new RuntimeError(expr.method(), "Undefined property '" + expr.method().lexeme() + "'.");
        }

        return method.bind(instance);
    }
    @Override
    public Object visitIndexExpression(IndexExpression expr) {
        Object object = interpreter.evaluate(expr.object());
        Object index = interpreter.evaluate(expr.index());

        if (object instanceof ScriptValue scriptValue) {
            return scriptValue.getAt(interpreter, expr.bracket(), index);
        }

        throw new RuntimeError(expr.bracket(), "Object of type '" + interpreter.runtimeTypeName(object) + "' does not support index access.");
    }

    @Override
    public Object visitIndexSetExpression(IndexSetExpression expr) {
        Object object = interpreter.evaluate(expr.object());
        Object index = interpreter.evaluate(expr.index());
        Object value = interpreter.evaluate(expr.value());

        if (object instanceof ScriptValue scriptValue) {
            scriptValue.setAt(interpreter, expr.bracket(), index, value);
            return value;
        }

        throw new RuntimeError(expr.bracket(), "Object of type '" + interpreter.runtimeTypeName(object) + "' does not support index assignment.");
    }

    @Override
    public Object visitSliceExpression(SliceExpression expr) {
        Object object = interpreter.evaluate(expr.object());
        if (!(object instanceof TVList list)) {
            throw new RuntimeError(expr.bracket(), "Only lists support slicing.");
        }

        int size = list.values().size();
        if (size == 0) {
            return new TVList(new ArrayList<>(), list.elementType());
        }

        Integer startValue = interpreter.evaluateOptionalIndex(expr.bracket(), expr.start(), true);
        Integer endValue = interpreter.evaluateOptionalIndex(expr.bracket(), expr.end(), true);

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
    public Object visitFunctionExpression(FunctionExpression expr) {
        return new TVScriptFunction(expr, interpreter.environment, interpreter.currentScriptPath);
    }

    @Override
    public Object visitCollectionLiteralExpression(CollectionLiteralExpression expr) {
        if (expr.collectionType().type() == TokenType.LIST) {
            if (expr.size() != null) {
                Object sizeValue = interpreter.evaluate(expr.size());
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
                elements.add(interpreter.evaluate(element));
            }
            return new TVList(elements, null);
        }

        if (expr.collectionType().type() == TokenType.SET) {
            Set<Object> values = new LinkedHashSet<>();
            for (Expression element : expr.elements()) {
                values.add(interpreter.evaluate(element));
            }
            return new TVSet(values, null);
        }

        if (expr.collectionType().type() == TokenType.MAP) {
            Map<Object, Object> values = new LinkedHashMap<>();
            for (MapEntry entry : expr.entries()) {
                values.put(interpreter.evaluate(entry.key()), interpreter.evaluate(entry.value()));
            }
            return new TVMap(values, null, null);
        }

        throw new RuntimeError(expr.keyword(), "Unsupported collection type.");
    }

    @Override
    public Object visitTypeBinaryExpression(TypeBinaryExpression expr) {
        Object left = interpreter.evaluate(expr.left());

        if (expr.operator().type() == TokenType.IS) {
            boolean matches = interpreter.matchesTypeName(left, expr.typeName().lexeme());
            if (expr.alias() != null && matches) {
                interpreter.environment.define(expr.alias(), left, TokenType.VAR, true);
            }
            return matches;
        }

        if (expr.operator().type() == TokenType.HAS) {
            Object traitObj = interpreter.environment.get(expr.typeName());
            if (!(traitObj instanceof TVScriptTrait trait)) {
                throw new RuntimeError(expr.typeName(), "Expected trait name after 'has'.");
            }
            return interpreter.checkHasTrait(left, trait);
        }

        if (expr.operator().type() == TokenType.AS) {
            if (interpreter.matchesTypeName(left, expr.typeName().lexeme())) {
                return left;
            }
            throw new RuntimeError(expr.operator(), "Cannot cast value to " + expr.typeName().lexeme() + ".");
        }

        return null;
    }

    @Override
    public Object visitNewExpression(NewExpression expr) {
        Object callee = interpreter.evaluate(expr.callee());

        if (!(callee instanceof TVScriptClass)) {
            throw new RuntimeError(expr.keyword(), "Can only use 'new' with classes.");
        }

        TVScriptClass klass = (TVScriptClass) callee;
        Map<String, Object> arguments = new HashMap<>();
        int positionalIndex = 0;
        for (Argument argument : expr.arguments()) {
            String key = argument.isNamed() ? argument.name().lexeme() : "$" + positionalIndex++;
            arguments.put(key, interpreter.evaluate(argument.value()));
        }

        return klass.instantiate(interpreter, arguments, expr.keyword());
    }
}
