package com.terminalvelocitycabbage.tvscript.analysis;

import com.terminalvelocitycabbage.tvscript.TVScript;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import static com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import com.terminalvelocitycabbage.tvscript.stdlib.NativeFunctions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Performs static type checking on the AST.
 */
public class TypeChecker implements Statement.Visitor<Void>, Expression.Visitor<TokenType> {

    private final List<Map<String, VariableStaticInfo>> scopes = new ArrayList<>();
    private final Map<String, ClassStatement> classes = new HashMap<>();
    private final Map<String, TraitStatement> traits = new HashMap<>();
    private int loopDepth = 0;

    private static class VariableStaticInfo {
        final TokenType type;
        final boolean isConst;
        final TokenType returnType;

        VariableStaticInfo(TokenType type, boolean isConst) {
            this(type, isConst, null);
        }

        VariableStaticInfo(TokenType type, boolean isConst, TokenType returnType) {
            this.type = type;
            this.isConst = isConst;
            this.returnType = returnType;
        }
    }

    public TypeChecker() {
        Map<String, VariableStaticInfo> globalScope = new HashMap<>();
        for (NativeFunctions.NativeFunctionDescriptor descriptor : NativeFunctions.getAll()) {
            globalScope.put(descriptor.name(), new VariableStaticInfo(TokenType.FUNCTION, true, descriptor.returnType()));
        }
        scopes.add(globalScope);
    }

    /**
     * Checks a list of statements for type errors.
     * @param statements The statements to check.
     */
    public void check(List<Statement> statements) {
        // First pass: collect class and trait definitions
        for (Statement statement : statements) {
            if (statement instanceof ClassStatement) {
                classes.put(((ClassStatement) statement).name().lexeme(), (ClassStatement) statement);
            } else if (statement instanceof TraitStatement) {
                traits.put(((TraitStatement) statement).name().lexeme(), (TraitStatement) statement);
            }
        }

        // Second pass: check bodies and inheritance rules
        for (Statement statement : statements) {
            if (statement != null) check(statement);
        }
    }

    private void check(Statement stmt) {
        stmt.accept(this);
    }

    private TokenType check(Expression expr) {
        return expr.accept(this);
    }

    @Override
    public Void visitBlockStatement(BlockStatement stmt) {
        beginScope();
        check(stmt.statements());
        endScope();
        return null;
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatement stmt) {
        check(stmt.expression());
        return null;
    }

    @Override
    public Void visitIfStatement(IfStatement stmt) {
        // Special case for pattern matching alias: if obj is Type -> alias:
        if (stmt.condition() instanceof TypeBinaryExpression tbe && tbe.alias() != null) {
            check(tbe.left());
            
            beginScope();
            TokenType type = tbe.typeName().type();
            if (type == TokenType.IDENTIFIER) {
                type = TokenType.CLASS;
            }
            declare(tbe.alias(), type, true);
            
            check(stmt.thenBranch());
            endScope();
            
            if (stmt.elseBranch() != null) {
                check(stmt.elseBranch());
            }
            return null;
        }

        TokenType conditionType = check(stmt.condition());
        if (conditionType != TokenType.TYPE_BOOLEAN) {
            TVScript.compileError(new CompileError(stmt.keyword(), "Condition must be boolean."));
        }
        check(stmt.thenBranch());
        if (stmt.elseBranch() != null) {
            check(stmt.elseBranch());
        }
        return null;
    }

    @Override
    public Void visitWhileStatement(WhileStatement stmt) {
        TokenType conditionType = check(stmt.condition());
        if (conditionType != TokenType.TYPE_BOOLEAN) {
            TVScript.compileError(new CompileError(stmt.keyword(), "While condition must be a boolean."));
        }

        // Infinite loop detection
        if (stmt.condition() instanceof LiteralExpression) {
            Object value = ((LiteralExpression) stmt.condition()).value();
            if (Boolean.TRUE.equals(value)) {
                TVScript.warning(stmt.keyword(), "Potential infinite loop: constant true condition.");
            }
        } else {
            List<String> vars = getVariablesUsed(stmt.condition());
            if (!vars.isEmpty() && !isMutated(stmt.body(), vars)) {
                TVScript.warning(stmt.keyword(), "Potential infinite loop: condition variables are not mutated in the loop body.");
            }
        }

        loopDepth++;
        check(stmt.body());
        loopDepth--;

        return null;
    }

    @Override
    public Void visitForStatement(ForStatement stmt) {
        TokenType rangeType = check(stmt.range());
        if (rangeType != TokenType.TYPE_RANGE) {
            TVScript.compileError(new CompileError(stmt.keyword(), "For loop expects a range."));
        }

        beginScope();
        if (stmt.name() != null) {
            declare(stmt.name(), stmt.type().type(), false);
        }

        loopDepth++;
        check(stmt.body());
        loopDepth--;

        endScope();
        return null;
    }

    @Override
    public Void visitBreakStatement(BreakStatement stmt) {
        if (loopDepth == 0) {
            TVScript.compileError(new CompileError(stmt.keyword(), "Cannot use 'break' outside of a loop."));
        }
        return null;
    }

    @Override
    public Void visitContinueStatement(ContinueStatement stmt) {
        if (loopDepth == 0) {
            TVScript.compileError(new CompileError(stmt.keyword(), "Cannot use 'continue' outside of a loop."));
        }
        return null;
    }

    @Override
    public Void visitMatchStatement(MatchStatement stmt) {
        TokenType conditionType = check(stmt.condition());

        for (MatchStatement.Case matchCase : stmt.cases()) {
            for (Expression pattern : matchCase.patterns()) {
                TokenType patternType = check(pattern);
                if (patternType != null && !isCompatible(conditionType, patternType) && !isCompatible(patternType, conditionType)) {
                     TVScript.compileError(new CompileError(stmt.keyword(), "Pattern type " + patternType + " is not compatible with condition type " + conditionType + "."));
                }
            }
            check(matchCase.branch());
        }

        if (stmt.defaultBranch() != null) {
            check(stmt.defaultBranch());
        } else if (!isExhaustive(conditionType, stmt.cases())) {
            TVScript.compileError(new CompileError(stmt.keyword(), "Match statement must be exhaustive. Add a 'default' case."));
        }

        return null;
    }

    @Override
    public Void visitFunctionStatement(FunctionStatement stmt) {
        if (stmt.name().type() != TokenType.CONSTRUCTOR) {
            declare(stmt.name(), TokenType.FUNCTION, true, stmt.returnType() != null ? stmt.returnType().type() : null);
        }
        beginScope();
        for (FunctionStatement.Parameter param : stmt.parameters()) {
            declare(param.name(), param.type().type(), false);
        }
        if (stmt.body() != null) {
            check(stmt.body());
        }
        endScope();
        return null;
    }

    @Override
    public Void visitReturnStatement(ReturnStatement stmt) {
        if (stmt.value() != null) {
            check(stmt.value());
        }
        return null;
    }

    private ClassStatement currentClass = null;

    @Override
    public Void visitClassStatement(ClassStatement stmt) {
        ClassStatement previousClass = currentClass;
        currentClass = stmt;
        declare(stmt.name(), TokenType.CLASS, true);
        
        // Check trait conflicts and missing implementations
        checkTraitImplementations(stmt);
        
        // Scope for instance fields and methods
        beginScope();
        declare(new Token(TokenType.THIS, "this", null, 0), TokenType.CLASS, true);
        if (stmt.superclass() != null) {
            declare(new Token(TokenType.SUPER, "super", null, 0), TokenType.CLASS, true);
        }
        
        // Declare fields from superclasses
        declareInheritedFields(stmt);
        
        for (VarStatement field : stmt.fields()) {
            declare(field.name(), field.type().type(), field.isConst());
        }
        
        for (VarStatement field : stmt.fields()) {
            if (field.initializer() != null) {
                check(field.initializer());
            }
        }
        
        for (FunctionStatement method : stmt.methods()) {
            check(method);
        }
        for (FunctionStatement constructor : stmt.constructors()) {
            check(constructor);
        }
        endScope();

        // Check static methods outside the instance scope where 'this' is defined
        for (FunctionStatement staticMethod : stmt.staticMethods()) {
            beginScope();
            check(staticMethod);
            endScope();
        }

        currentClass = previousClass;
        return null;
    }

    private void declareInheritedFields(ClassStatement stmt) {
        if (stmt.superclass() != null) {
            ClassStatement superclass = classes.get(stmt.superclass().lexeme());
            if (superclass != null) {
                declareInheritedFields(superclass);
                for (VarStatement field : superclass.fields()) {
                    declare(field.name(), field.type().type(), field.isConst());
                }
            }
        }
    }


    @Override
    public Void visitTraitStatement(TraitStatement stmt) {
        declare(stmt.name(), TokenType.TRAIT, true);
        beginScope();
        for (VarStatement field : stmt.fields()) {
            if (field.initializer() != null) check(field.initializer());
            declare(field.name(), field.type().type(), field.isConst());
        }
        for (FunctionStatement method : stmt.methods()) {
            check(method);
        }
        endScope();
        return null;
    }

    private boolean isExhaustive(TokenType type, List<?> cases) {
        // TODO: Implement actual exhaustiveness checking
        return false; 
    }

    @Override
    public Void visitPrintStatement(PrintStatement stmt) {
        check(stmt.expression());
        return null;
    }

    @Override
    public Void visitVarStatement(VarStatement stmt) {
        TokenType declaredType = stmt.type().type();
        TokenType inferredType = declaredType;

        if (stmt.initializer() != null) {
            inferredType = check(stmt.initializer());
            if (declaredType == TokenType.VAR || declaredType == TokenType.CONST) {
                if (inferredType == null) {
                    TVScript.compileError(new CompileError(stmt.name(), "Cannot infer type from none."));
                }
            } else if (inferredType != null && !isCompatible(declaredType, inferredType)) {
                TVScript.compileError(new CompileError(stmt.name(), "Incompatible types in initialization."));
            }
        } else if (stmt.isConst()) {
            TVScript.compileError(new CompileError(stmt.name(), "Constant must be initialized."));
        } else if (declaredType == TokenType.VAR || declaredType == TokenType.CONST) {
            TVScript.compileError(new CompileError(stmt.name(), "Type inference requires an initializer."));
        }

        declare(stmt.name(), (declaredType == TokenType.VAR || declaredType == TokenType.CONST) ? inferredType : declaredType, stmt.isConst());
        return null;
    }

    @Override
    public Void visitPassStatement(PassStatement stmt) {
        return null;
    }

    @Override
    public TokenType visitBinaryExpression(BinaryExpression expr) {
        TokenType left = check(expr.left());
        TokenType right = check(expr.right());

        switch (expr.operator().type()) {
            case GREATER:
            case GREATER_EQUAL:
            case LESS:
            case LESS_EQUAL:
                return TokenType.TYPE_BOOLEAN;
            case MINUS:
            case SLASH:
            case STAR:
            case PERCENT:
                if (left == TokenType.TYPE_INTEGER && right == TokenType.TYPE_INTEGER) {
                    return TokenType.TYPE_INTEGER;
                }
                return TokenType.TYPE_DECIMAL;
            case PLUS:
                if (left == TokenType.TYPE_STRING || right == TokenType.TYPE_STRING) {
                    return TokenType.TYPE_STRING;
                }
                if (left == TokenType.TYPE_INTEGER && right == TokenType.TYPE_INTEGER) {
                    return TokenType.TYPE_INTEGER;
                }
                return TokenType.TYPE_DECIMAL;
            case BANG_EQUAL:
            case EQUAL_EQUAL:
                return TokenType.TYPE_BOOLEAN;
            default:
                return null;
        }
    }

    @Override
    public TokenType visitGroupingExpression(GroupingExpression expr) {
        return check(expr.expression());
    }

    @Override
    public TokenType visitLiteralExpression(LiteralExpression expr) {
        if (expr.value() instanceof Integer) return TokenType.TYPE_INTEGER;
        if (expr.value() instanceof Double) return TokenType.TYPE_DECIMAL;
        if (expr.value() instanceof String) return TokenType.TYPE_STRING;
        if (expr.value() instanceof Boolean) return TokenType.TYPE_BOOLEAN;
        if (expr.value() == null) return TokenType.NONE;
        return null;
    }

    @Override
    public TokenType visitLogicalExpression(LogicalExpression expr) {
        check(expr.left());
        check(expr.right());
        return TokenType.TYPE_BOOLEAN;
    }

    @Override
    public TokenType visitUnaryExpression(UnaryExpression expr) {
        TokenType right = check(expr.right());
        if (expr.operator().type() == TokenType.BANG) return TokenType.TYPE_BOOLEAN;
        return right;
    }

    @Override
    public TokenType visitTernaryExpression(TernaryExpression expr) {
        check(expr.condition());
        TokenType trueBranch = check(expr.thenBranch());
        TokenType falseBranch = check(expr.elseBranch());
        // TODO: Properly check if branches are compatible
        return trueBranch;
    }

    @Override
    public TokenType visitInterpolationExpression(InterpolationExpression expr) {
        for (Expression e : expr.expressions()) {
            check(e);
        }
        return TokenType.TYPE_STRING;
    }

    @Override
    public TokenType visitVariableExpression(VariableExpression expr) {
        VariableStaticInfo info = lookup(expr.name());
        if (info == null) {
            TVScript.compileError(new CompileError(expr.name(), "Variable used before declaration or undefined."));
            return null;
        }
        return info.type;
    }

    @Override
    public TokenType visitAssignExpression(AssignExpression expr) {
        TokenType valueType = check(expr.value());
        VariableStaticInfo info = lookup(expr.name());
        if (info != null) {
            if (info.isConst) {
                TVScript.compileError(new CompileError(expr.name(), "Cannot assign to constant variable."));
            }
            if (valueType != null && !isCompatible(info.type, valueType)) {
                 TVScript.compileError(new CompileError(expr.name(), "Incompatible types in assignment."));
            }
        } else {
             TVScript.compileError(new CompileError(expr.name(), "Variable undefined."));
        }
        return valueType;
    }

    @Override
    public TokenType visitRangeExpression(RangeExpression expr) {
        TokenType start = check(expr.start());
        TokenType end = check(expr.end());

        if (start != TokenType.TYPE_INTEGER || end != TokenType.TYPE_INTEGER) {
            TVScript.compileError(new CompileError(expr.operator(), "Range bounds must be integers."));
        }

        return TokenType.TYPE_RANGE;
    }

    @Override
    public TokenType visitMatchExpression(MatchExpression expr) {
        TokenType conditionType = check(expr.condition());
        TokenType resultType = null;

        for (MatchExpression.Case matchCase : expr.cases()) {
            for (Expression pattern : matchCase.patterns()) {
                TokenType patternType = check(pattern);
                if (patternType != null && !isCompatible(conditionType, patternType) && !isCompatible(patternType, conditionType)) {
                    TVScript.compileError(new CompileError(expr.keyword(), "Pattern type " + patternType + " is not compatible with condition type " + conditionType + "."));
                }
            }
            TokenType branchType = check(matchCase.branch());
            if (resultType == null) {
                resultType = branchType;
            } else if (branchType != null && !isCompatible(resultType, branchType)) {
                // Try the other way around if it's decimal/integer
                if (isCompatible(branchType, resultType)) {
                    resultType = branchType;
                } else {
                    TVScript.compileError(new CompileError(expr.keyword(), "Incompatible types in match expression branches."));
                }
            }
        }

        if (expr.defaultBranch() != null) {
            TokenType defaultType = check(expr.defaultBranch());
            if (resultType == null) {
                resultType = defaultType;
            } else if (defaultType != null && !isCompatible(resultType, defaultType)) {
                 if (isCompatible(defaultType, resultType)) {
                    resultType = defaultType;
                } else {
                    TVScript.compileError(new CompileError(expr.keyword(), "Incompatible types in match expression branches."));
                }
            }
        } else if (!isExhaustive(conditionType, expr.cases())) {
            TVScript.compileError(new CompileError(expr.keyword(), "Match expression must be exhaustive. Add a 'default' case."));
        }

        return resultType;
    }

    @Override
    public TokenType visitCallExpression(CallExpression expr) {
        TokenType calleeType = check(expr.callee());
        for (CallExpression.Argument arg : expr.arguments()) {
            check(arg.value());
        }

        if (expr.callee() instanceof VariableExpression varExpr) {
            VariableStaticInfo info = lookup(varExpr.name());
            if (info != null && info.type == TokenType.FUNCTION) {
                return info.returnType;
            }
        } else if (calleeType == TokenType.FUNCTION) {
             // If we don't know the exact return type but it's a function, maybe return FUNCTION?
             // But usually it should return something more specific.
             // For now, let's return null to avoid making things too complex, but it might break var inference.
        }

        return null;
    }

    @Override
    public TokenType visitFunctionExpression(FunctionExpression expr) {
        beginScope();
        for (FunctionStatement.Parameter param : expr.parameters()) {
            declare(param.name(), param.type().type(), false);
        }
        check(expr.body());
        endScope();
        return TokenType.FUNCTION;
    }

    @Override
    public TokenType visitGetExpression(GetExpression expr) {
        TokenType objectType = check(expr.object());

        if (expr.object() instanceof ThisExpression) {
            VariableStaticInfo info = lookup(expr.name());
            if (info != null) return info.type;
            
            // Check if it's a method
            if (currentClass != null && hasMethod(currentClass, expr.name().lexeme())) {
                return TokenType.FUNCTION;
            }
            
            TVScript.compileError(new CompileError(expr.name(), "Undefined property '" + expr.name().lexeme() + "' on 'this'."));
            return null;
        }

        if (expr.object() instanceof VariableExpression varExpr) {
            String name = varExpr.name().lexeme();
            if (traits.containsKey(name)) {
                TraitStatement trait = traits.get(name);
                TokenType fieldType = findFieldInTrait(trait, expr.name().lexeme());
                if (fieldType != null) return fieldType;
                
                TVScript.compileError(new CompileError(expr.name(), "Undefined trait constant '" + expr.name().lexeme() + "'."));
            }
        }

        return null;
    }

    private boolean hasMethod(ClassStatement stmt, String name) {
        for (FunctionStatement method : stmt.methods()) {
            if (method.name().lexeme().equals(name)) return true;
        }
        if (stmt.superclass() != null) {
            ClassStatement superclass = classes.get(stmt.superclass().lexeme());
            if (superclass != null && hasMethod(superclass, name)) return true;
        }
        for (Token traitToken : stmt.traits()) {
            TraitStatement trait = traits.get(traitToken.lexeme());
            if (trait != null && hasTraitMethod(trait, name)) return true;
        }
        return false;
    }

    private boolean hasTraitMethod(TraitStatement trait, String name) {
        for (FunctionStatement method : trait.methods()) {
            if (method.name().lexeme().equals(name)) return true;
        }
        for (Token supertraitToken : trait.traits()) {
            TraitStatement supertrait = traits.get(supertraitToken.lexeme());
            if (supertrait != null && hasTraitMethod(supertrait, name)) return true;
        }
        return false;
    }

    private TokenType findFieldInTrait(TraitStatement trait, String name) {
        for (VarStatement field : trait.fields()) {
            if (field.name().lexeme().equals(name)) {
                return field.type().type();
            }
        }
        for (Token supertraitToken : trait.traits()) {
            TraitStatement supertrait = traits.get(supertraitToken.lexeme());
            if (supertrait != null) {
                TokenType type = findFieldInTrait(supertrait, name);
                if (type != null) return type;
            }
        }
        return null;
    }

    @Override
    public TokenType visitSetExpression(SetExpression expr) {
        check(expr.object());
        return check(expr.value());
    }

    @Override
    public TokenType visitThisExpression(ThisExpression expr) {
        VariableStaticInfo info = lookup(expr.keyword());
        if (info == null) {
            TVScript.compileError(new CompileError(expr.keyword(), "Cannot use 'this' outside of a class method."));
            return null;
        }
        return info.type;
    }

    @Override
    public TokenType visitNewExpression(NewExpression expr) {
        check(expr.callee());
        for (Argument arg : expr.arguments()) {
            check(arg.value());
        }
        return TokenType.CLASS;
    }

    @Override
    public TokenType visitSuperExpression(SuperExpression expr) {
        return TokenType.CLASS; // Simple for now
    }

    @Override
    public TokenType visitTypeBinaryExpression(TypeBinaryExpression expr) {
        check(expr.left());
        if (expr.operator().type() == TokenType.AS) {
            TokenType type = expr.typeName().type();
            if (type == TokenType.IDENTIFIER) return TokenType.CLASS;
            return type;
        }
        return TokenType.TYPE_BOOLEAN;
    }

    private record AbstractMethodInfo(Token name, String traitName) {}

    private void checkTraitImplementations(ClassStatement stmt) {
        Map<String, Token> availableMethods = new HashMap<>();
        Map<String, List<String>> traitProviders = new HashMap<>();

        for (Token traitToken : stmt.traits()) {
            TraitStatement trait = traits.get(traitToken.lexeme());
            if (trait != null) {
                collectTraitMethods(trait, availableMethods, traitProviders);
            }
        }

        // Check if class overrides conflicts
        Set<String> classMethods = new HashSet<>();
        for (FunctionStatement method : stmt.methods()) {
            classMethods.add(method.name().lexeme());
        }

        for (Map.Entry<String, List<String>> entry : traitProviders.entrySet()) {
            String methodName = entry.getKey();
            List<String> providers = entry.getValue();

            if (providers.size() > 1 && !classMethods.contains(methodName)) {
                TVScript.compileError(new CompileError(stmt.name(),
                    "Class '" + stmt.name().lexeme() + "' must override method '" + methodName +
                    "' because it is provided by multiple traits: " + providers));
            }
        }

        // Check if all abstract trait methods are overridden
        Map<String, AbstractMethodInfo> abstractMethods = new HashMap<>();
        collectAbstractTraitMethods(stmt, abstractMethods);
        for (Map.Entry<String, AbstractMethodInfo> entry : abstractMethods.entrySet()) {
            if (!classMethods.contains(entry.getKey())) {
                AbstractMethodInfo info = entry.getValue();
                TVScript.compileError(new CompileError(stmt.name(),
                    "Class '" + stmt.name().lexeme() + "' must implement method '" + entry.getKey() + "' from trait " + info.traitName() + "."));
            }
        }
    }

    private void collectAbstractTraitMethods(ClassStatement stmt, Map<String, AbstractMethodInfo> abstractMethods) {
        for (Token traitToken : stmt.traits()) {
            TraitStatement trait = traits.get(traitToken.lexeme());
            if (trait != null) {
                collectAbstractMethodsFromTrait(trait, abstractMethods);
            }
        }
        if (stmt.superclass() != null) {
            ClassStatement superclass = classes.get(stmt.superclass().lexeme());
            if (superclass != null) {
                collectAbstractTraitMethods(superclass, abstractMethods);
            }
        }
    }

    private void collectAbstractMethodsFromTrait(TraitStatement trait, Map<String, AbstractMethodInfo> abstractMethods) {
        for (FunctionStatement method : trait.methods()) {
            if (method.body() == null && !method.isDefault()) {
                abstractMethods.put(method.name().lexeme(), new AbstractMethodInfo(method.name(), trait.name().lexeme()));
            } else {
                // If this trait provides a default, it "fills" the abstract method from supertraits
                abstractMethods.remove(method.name().lexeme());
            }
        }
        for (Token supertraitToken : trait.traits()) {
            TraitStatement supertrait = traits.get(supertraitToken.lexeme());
            if (supertrait != null) {
                collectAbstractMethodsFromTrait(supertrait, abstractMethods);
            }
        }
    }

    private void collectTraitMethods(TraitStatement trait, Map<String, Token> availableMethods, Map<String, List<String>> traitProviders) {
        for (FunctionStatement method : trait.methods()) {
            String methodName = method.name().lexeme();
            availableMethods.put(methodName, method.name());
            traitProviders.computeIfAbsent(methodName, k -> new ArrayList<>()).add(trait.name().lexeme());
        }

        for (Token supertraitToken : trait.traits()) {
            TraitStatement supertrait = traits.get(supertraitToken.lexeme());
            if (supertrait != null) {
                collectTraitMethods(supertrait, availableMethods, traitProviders);
            }
        }
    }

    private void beginScope() {
        scopes.add(new HashMap<>());
    }

    private void endScope() {
        scopes.remove(scopes.size() - 1);
    }

    private void declare(Token name, TokenType type, boolean isConst) {
        declare(name, type, isConst, null);
    }

    private void declare(Token name, TokenType type, boolean isConst, TokenType returnType) {
        if (scopes.isEmpty()) return;
        
        // Redefinition in the same scope is always an error
        Map<String, VariableStaticInfo> scope = scopes.get(scopes.size() - 1);
        if (scope.containsKey(name.lexeme())) {
            TVScript.compileError(new CompileError(name, "Variable '" + name.lexeme() + "' is already defined in this scope."));
            return;
        }

        scope.put(name.lexeme(), new VariableStaticInfo(type, isConst, returnType));
    }

    private boolean isAlreadyDefined(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name)) return true;
        }
        return false;
    }

    private VariableStaticInfo lookup(Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme())) {
                return scopes.get(i).get(name.lexeme());
            }
        }
        return null;
    }

    private boolean isCompatible(TokenType expected, TokenType actual) {
        if (expected == actual) return true;
        if (expected == TokenType.TYPE_DECIMAL && actual == TokenType.TYPE_INTEGER) return true;
        if (expected == TokenType.TYPE_INTEGER && actual == TokenType.TYPE_RANGE) return true;
        if (expected == TokenType.TYPE_DECIMAL && actual == TokenType.TYPE_RANGE) return true;
        if (expected == TokenType.IDENTIFIER && actual == TokenType.CLASS) return true;
        if (expected == TokenType.CLASS && actual == TokenType.IDENTIFIER) return true;
        return false;
    }

    private List<String> getVariablesUsed(Expression expression) {
        List<String> vars = new ArrayList<>();
        expression.accept(new Expression.Visitor<Void>() {
            @Override public Void visitBinaryExpression(BinaryExpression expr) { expr.left().accept(this); expr.right().accept(this); return null; }
            @Override public Void visitGroupingExpression(GroupingExpression expr) { expr.expression().accept(this); return null; }
            @Override public Void visitLiteralExpression(LiteralExpression expr) { return null; }
            @Override public Void visitLogicalExpression(LogicalExpression expr) { expr.left().accept(this); expr.right().accept(this); return null; }
            @Override public Void visitUnaryExpression(UnaryExpression expr) { expr.right().accept(this); return null; }
            @Override public Void visitTernaryExpression(TernaryExpression expr) { expr.condition().accept(this); expr.thenBranch().accept(this); expr.elseBranch().accept(this); return null; }
            @Override public Void visitInterpolationExpression(InterpolationExpression expr) { for (Expression e : expr.expressions()) e.accept(this); return null; }
            @Override public Void visitVariableExpression(VariableExpression expr) { vars.add(expr.name().lexeme()); return null; }
            @Override public Void visitAssignExpression(AssignExpression expr) { vars.add(expr.name().lexeme()); expr.value().accept(this); return null; }
            @Override public Void visitRangeExpression(RangeExpression expr) { expr.start().accept(this); expr.end().accept(this); return null; }
            @Override public Void visitMatchExpression(MatchExpression expr) {
                expr.condition().accept(this);
                for (MatchExpression.Case c : expr.cases()) {
                    for (Expression p : c.patterns()) p.accept(this);
                    c.branch().accept(this);
                }
                if (expr.defaultBranch() != null) expr.defaultBranch().accept(this);
                return null;
            }
            @Override public Void visitCallExpression(CallExpression expr) {
                expr.callee().accept(this);
                for (CallExpression.Argument arg : expr.arguments()) arg.value().accept(this);
                return null;
            }
            @Override public Void visitSuperExpression(SuperExpression expr) { return null; }
            @Override public Void visitTypeBinaryExpression(TypeBinaryExpression expr) { expr.left().accept(this); return null; }
            @Override public Void visitGetExpression(GetExpression expr) { return null; }
            @Override public Void visitSetExpression(SetExpression expr) { return null; }
            @Override public Void visitThisExpression(ThisExpression expr) { return null; }
            @Override public Void visitNewExpression(NewExpression expr) { return null; }
            @Override public Void visitFunctionExpression(FunctionExpression expr) {
                for (FunctionStatement.Parameter p : expr.parameters()) {
                    if (p.defaultValue() != null) p.defaultValue().accept(this);
                }
                expr.body().accept(new Statement.Visitor<Void>() {
                    @Override public Void visitBlockStatement(BlockStatement stmt) { for (Statement s : stmt.statements()) s.accept(this); return null; }
                    @Override public Void visitExpressionStatement(ExpressionStatement stmt) {
                        vars.addAll(getVariablesUsed(stmt.expression()));
                        return null;
                    }
                    // This is getting complicated, let's just use a simpler approach for now
                    @Override public Void visitIfStatement(IfStatement stmt) { return null; }
                    @Override public Void visitPrintStatement(PrintStatement stmt) { return null; }
                    @Override public Void visitVarStatement(VarStatement stmt) { return null; }
                    @Override public Void visitPassStatement(PassStatement stmt) { return null; }
                    @Override public Void visitWhileStatement(WhileStatement stmt) { return null; }
                    @Override public Void visitForStatement(ForStatement stmt) { return null; }
                    @Override public Void visitMatchStatement(MatchStatement stmt) { return null; }
                    @Override public Void visitBreakStatement(BreakStatement stmt) { return null; }
                    @Override public Void visitContinueStatement(ContinueStatement stmt) { return null; }
                    @Override public Void visitFunctionStatement(FunctionStatement stmt) { return null; }
                    @Override public Void visitReturnStatement(ReturnStatement stmt) { if (stmt.value() != null) stmt.value().accept(this.exprVisitor); return null; }
                    @Override public Void visitClassStatement(ClassStatement stmt) { return null; }
                    @Override public Void visitTraitStatement(TraitStatement stmt) { return null; }
                    private final Expression.Visitor<Void> exprVisitor = new Expression.Visitor<Void>() {
                        @Override public Void visitBinaryExpression(BinaryExpression expr) { return null; }
                        @Override public Void visitGroupingExpression(GroupingExpression expr) { return null; }
                        @Override public Void visitLiteralExpression(LiteralExpression expr) { return null; }
                        @Override public Void visitLogicalExpression(LogicalExpression expr) { return null; }
                        @Override public Void visitUnaryExpression(UnaryExpression expr) { return null; }
                        @Override public Void visitTernaryExpression(TernaryExpression expr) { return null; }
                        @Override public Void visitInterpolationExpression(InterpolationExpression expr) { return null; }
                        @Override public Void visitVariableExpression(VariableExpression expr) { vars.add(expr.name().lexeme()); return null; }
                        @Override public Void visitAssignExpression(AssignExpression expr) { vars.add(expr.name().lexeme()); return null; }
                        @Override public Void visitRangeExpression(RangeExpression expr) { return null; }
                        @Override public Void visitMatchExpression(MatchExpression expr) { return null; }
                        @Override public Void visitCallExpression(CallExpression expr) { return null; }
                        @Override public Void visitFunctionExpression(FunctionExpression expr) { return null; }
                        @Override public Void visitSuperExpression(SuperExpression expr) { return null; }
                        @Override public Void visitTypeBinaryExpression(TypeBinaryExpression expr) { return null; }
                        @Override public Void visitGetExpression(GetExpression expr) { return null; }
                        @Override public Void visitSetExpression(SetExpression expr) { return null; }
                        @Override public Void visitThisExpression(ThisExpression expr) { return null; }
                        @Override public Void visitNewExpression(NewExpression expr) { return null; }
                    };
                });
                return null;
            }
        });
        return vars;
    }

    private boolean isMutated(Statement body, List<String> vars) {
        final boolean[] mutated = {false};
        body.accept(new Statement.Visitor<Void>() {
            @Override public Void visitBlockStatement(BlockStatement stmt) { for (Statement s : stmt.statements()) s.accept(this); return null; }
            @Override public Void visitExpressionStatement(ExpressionStatement stmt) { stmt.expression().accept(exprVisitor); return null; }
            @Override public Void visitIfStatement(IfStatement stmt) { stmt.thenBranch().accept(this); if (stmt.elseBranch() != null) stmt.elseBranch().accept(this); return null; }
            @Override public Void visitPrintStatement(PrintStatement stmt) { return null; }
            @Override public Void visitVarStatement(VarStatement stmt) { return null; }
            @Override public Void visitPassStatement(PassStatement stmt) { return null; }
            @Override public Void visitWhileStatement(WhileStatement stmt) { stmt.body().accept(this); return null; }
            @Override public Void visitForStatement(ForStatement stmt) { stmt.body().accept(this); return null; }
            @Override public Void visitMatchStatement(MatchStatement stmt) {
                stmt.condition().accept(exprVisitor);
                for (MatchStatement.Case c : stmt.cases()) {
                    for (Expression p : c.patterns()) p.accept(exprVisitor);
                    c.branch().accept(this);
                }
                if (stmt.defaultBranch() != null) stmt.defaultBranch().accept(this);
                return null;
            }
            @Override public Void visitBreakStatement(BreakStatement stmt) { return null; }
            @Override public Void visitContinueStatement(ContinueStatement stmt) { return null; }
            @Override public Void visitFunctionStatement(FunctionStatement stmt) { return null; }
            @Override public Void visitReturnStatement(ReturnStatement stmt) { if (stmt.value() != null) stmt.value().accept(exprVisitor); return null; }
            @Override public Void visitClassStatement(ClassStatement stmt) { return null; }
            @Override public Void visitTraitStatement(TraitStatement stmt) { return null; }

            private final Expression.Visitor<Void> exprVisitor = new Expression.Visitor<Void>() {
                @Override public Void visitBinaryExpression(BinaryExpression expr) { expr.left().accept(this); expr.right().accept(this); return null; }
                @Override public Void visitGroupingExpression(GroupingExpression expr) { expr.expression().accept(this); return null; }
                @Override public Void visitLiteralExpression(LiteralExpression expr) { return null; }
                @Override public Void visitLogicalExpression(LogicalExpression expr) { expr.left().accept(this); expr.right().accept(this); return null; }
                @Override public Void visitUnaryExpression(UnaryExpression expr) { expr.right().accept(this); return null; }
                @Override public Void visitTernaryExpression(TernaryExpression expr) { expr.condition().accept(this); expr.thenBranch().accept(this); expr.elseBranch().accept(this); return null; }
                @Override public Void visitInterpolationExpression(InterpolationExpression expr) { for (Expression e : expr.expressions()) e.accept(this); return null; }
                @Override public Void visitVariableExpression(VariableExpression expr) { return null; }
                @Override public Void visitAssignExpression(AssignExpression expr) { if (vars.contains(expr.name().lexeme())) mutated[0] = true; expr.value().accept(this); return null; }
                @Override public Void visitRangeExpression(RangeExpression expr) { expr.start().accept(this); expr.end().accept(this); return null; }
                @Override public Void visitMatchExpression(MatchExpression expr) {
                    expr.condition().accept(this);
                    for (MatchExpression.Case c : expr.cases()) {
                        for (Expression p : c.patterns()) p.accept(this);
                        c.branch().accept(this);
                    }
                    if (expr.defaultBranch() != null) expr.defaultBranch().accept(this);
                    return null;
                }
                @Override public Void visitCallExpression(CallExpression expr) {
                    expr.callee().accept(this);
                    for (CallExpression.Argument arg : expr.arguments()) arg.value().accept(this);
                    return null;
                }
                @Override public Void visitFunctionExpression(FunctionExpression expr) {
                    return null;
                }
                @Override public Void visitSuperExpression(SuperExpression expr) { return null; }
                @Override public Void visitTypeBinaryExpression(TypeBinaryExpression expr) { expr.left().accept(this); return null; }
                @Override public Void visitGetExpression(GetExpression expr) { return null; }
                @Override public Void visitSetExpression(SetExpression expr) { return null; }
                @Override public Void visitThisExpression(ThisExpression expr) { return null; }
                @Override public Void visitNewExpression(NewExpression expr) { return null; }
            };
        });
        return mutated[0];
    }
}
