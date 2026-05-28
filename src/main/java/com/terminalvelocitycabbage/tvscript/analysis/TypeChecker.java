package com.terminalvelocitycabbage.tvscript.analysis;

import com.terminalvelocitycabbage.tvscript.CompilationContext;
import com.terminalvelocitycabbage.tvscript.analysis.types.*;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.errors.DefaultDiagnosticReporter;
import com.terminalvelocitycabbage.tvscript.errors.DiagnosticReporter;
import com.terminalvelocitycabbage.tvscript.execution.NativeClass;
import com.terminalvelocitycabbage.tvscript.execution.TVType;
import com.terminalvelocitycabbage.tvscript.execution.TVScriptNativeFunction;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import com.terminalvelocitycabbage.tvscript.util.AstUtils;

import java.util.*;

import static com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

/**
 * Performs static type checking on the AST.
 */
public class TypeChecker implements Statement.Visitor<Void>, Expression.Visitor<Type> {

    final TypeCheckerState state;
    final DiagnosticReporter reporter;
    final GenericConstraintResolver genericConstraintResolver;
    final TraitImplementationChecker traitImplementationChecker;
    final DeclarationChecker declarationChecker;
    final StatementChecker statementChecker;
    final ExpressionChecker expressionChecker;

    public TypeChecker() {
        this(new DefaultDiagnosticReporter());
    }

    public TypeChecker(DiagnosticReporter reporter) {
        this(new CompilationContext(reporter));
    }

    public TypeChecker(CompilationContext context) {
        this(context.getNativeFunctions(), context.getNativeClasses(), context);
    }

    public TypeChecker(Collection<TVScriptNativeFunction> nativeFunctions, DiagnosticReporter reporter) {
        this(nativeFunctions, List.of(), reporter);
    }

    public TypeChecker(Collection<TVScriptNativeFunction> nativeFunctions, Collection<NativeClass> nativeClasses, DiagnosticReporter reporter) {
        this(nativeFunctions, nativeClasses, new CompilationContext(reporter, nativeFunctions, nativeClasses));
    }

    public TypeChecker(Collection<TVScriptNativeFunction> nativeFunctions, Collection<NativeClass> nativeClasses, CompilationContext context) {
        this.state = new TypeCheckerState(context);
        this.reporter = context.getReporter();
        this.genericConstraintResolver = new GenericConstraintResolver(state.constraints, state.classes, reporter);
        this.traitImplementationChecker = new TraitImplementationChecker(state.classes, state.traits, reporter);
        this.declarationChecker = new DeclarationChecker(this, state);
        this.statementChecker = new StatementChecker(this, state);
        this.expressionChecker = new ExpressionChecker(this, state);
        
        Map<String, TypeCheckerState.VariableStaticInfo> globalScope = new HashMap<>();
        for (TVScriptNativeFunction nativeFunction : nativeFunctions) {
            String name = nativeFunction.name();
            globalScope.put(name, new TypeCheckerState.VariableStaticInfo(resolveType(nativeFunction.returnType()), true));
            state.nativeFunctionNames.add(name);
            
            // Also register them in our definitions for name resolution
            state.functions.put(name, new FunctionStatement(
                    new Token(TokenType.IDENTIFIER, name, null, 0),
                    List.of(), // Parameters not needed for visibility check
                    new Token(nativeFunction.returnType(), "", null, 0),
                    null, List.of(), false, false, 
                    new Token(TokenType.PUBLIC, "public", null, 0) // Natives are always public
            ));
        }
        for (NativeClass nativeClass : nativeClasses) {
            String name = nativeClass.scriptName();
            state.nativeClasses.put(name, nativeClass);
            
            // Also register them in our definitions for name resolution
            state.classes.put(name, new ClassStatement(
                    new Token(TokenType.IDENTIFIER, name, null, 0),
                    List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), true,
                    new Token(TokenType.PUBLIC, "public", null, 0) // Natives are always public
            ));
        }
        state.scopes.add(globalScope);
    }

    public void check(List<Statement> statements) {
        check(statements, "default", "default");
    }

    public void check(List<Statement> statements, String scriptPath, String module) {
        state.currentScriptPath = scriptPath;
        state.currentModule = module;

        registerDefinitions(statements, scriptPath, module);

        // Second pass: check bodies and inheritance rules
        for (Statement statement : statements) {
            if (statement != null) check(statement);
        }
    }

    public void registerDefinitions(List<Statement> statements, String scriptPath, String module) {
        String scriptId = AstUtils.getScriptIdentifier(scriptPath);
        for (Statement statement : statements) {
            if (statement instanceof ClassStatement klass) {
                String className = klass.name().lexeme();
                String fullName = scriptId + "." + className;
                if (state.classes.containsKey(fullName) && !state.classes.get(fullName).equals(statement)) {
                    state.reporter.compileError(new CompileError(klass.name(), "Class '" + fullName + "' is already defined."));
                }
                state.classes.put(fullName, klass);
                state.classScriptPaths.put(fullName, scriptPath);
                state.classModules.put(fullName, module);
                
                state.classes.put(className, klass);
                state.classScriptPaths.put(className, scriptPath);
                state.classModules.put(className, module);
            } else if (statement instanceof TraitStatement trait) {
                String name = trait.name().lexeme();
                state.traits.put(scriptId + "." + name, trait);
                if (!state.traits.containsKey(name)) state.traits.put(name, trait);
            } else if (statement instanceof TypeStatement typeStmt) {
                String name = typeStmt.name().lexeme();
                state.types.put(scriptId + "." + name, typeStmt);
                if (!state.types.containsKey(name)) state.types.put(name, typeStmt);
            } else if (statement instanceof ConstraintStatement constraint) {
                String name = constraint.name().lexeme();
                state.constraints.put(scriptId + "." + name, constraint);
                if (!state.constraints.containsKey(name)) state.constraints.put(name, constraint);
            } else if (statement instanceof EventStatement event) {
                String name = event.name().lexeme();
                state.events.put(scriptId + "." + name, event);
                if (!state.events.containsKey(name)) state.events.put(name, event);
            } else if (statement instanceof FunctionStatement func) {
                String funcName = func.name().lexeme();
                String fullName = scriptId + "." + funcName;
                state.functions.put(fullName, func);
                state.functionScriptPaths.put(fullName, scriptPath);
                state.functionModules.put(fullName, module);
                if (!state.functions.containsKey(funcName)) {
                    state.functions.put(funcName, func);
                    state.functionScriptPaths.put(funcName, scriptPath);
                    state.functionModules.put(funcName, module);
                }
            } else if (statement instanceof VarStatement var) {
                String varName = var.name().lexeme();
                String fullName = scriptId + "." + varName;
                state.globalVars.put(fullName, var);
                state.varScriptPaths.put(fullName, scriptPath);
                state.varModules.put(fullName, module);
            }
        }
    }

    public void check(Statement stmt) {
        if (stmt == null) return;
        stmt.accept(this);
    }

    public Type check(Expression expr) {
        if (expr == null) return PrimitiveType.VOID;
        return expr.accept(this);
    }

    public Type resolveType(Token token) {
        return state.resolveType(token);
    }

    public Type resolveType(TokenType type) {
        return state.resolveType(type, null);
    }

    public ClassType resolveClassType(String name) {
        return state.resolveClassType(name);
    }

    public TraitType resolveTraitType(String name) {
        return state.resolveTraitType(name);
    }

    public void beginScope() {
        state.scopes.add(new HashMap<>());
    }

    public void endScope() {
        state.scopes.remove(state.scopes.size() - 1);
    }

    public void declare(Token name, Type type, boolean isConst) {
        if (state.scopes.isEmpty()) return;
        Map<String, TypeCheckerState.VariableStaticInfo> scope = state.scopes.get(state.scopes.size() - 1);
        if (scope.containsKey(name.lexeme())) {
            state.reporter.compileError(new CompileError(name, "Variable '" + name.lexeme() + "' is already defined in this scope."));
            return;
        }
        scope.put(name.lexeme(), new TypeCheckerState.VariableStaticInfo(type, isConst));
    }

    public TypeCheckerState.VariableStaticInfo lookup(Token name) {
        for (int i = state.scopes.size() - 1; i >= 0; i--) {
            if (state.scopes.get(i).containsKey(name.lexeme())) {
                return state.scopes.get(i).get(name.lexeme());
            }
        }
        return null;
    }

    public boolean isCompatible(Type target, Type source) {
        if (target == null || source == null) return false;
        return source.isAssignableTo(target);
    }

    public Type findOperatorOverload(Type left, TokenType operator, Type right) {
        // Simple switch to name mapping
        String methodName = switch (operator) {
            case PLUS -> "add";
            case MINUS -> "subtract";
            case STAR -> "multiply";
            case SLASH -> "divide";
            case PERCENT -> "modulo";
            case EQUAL_EQUAL -> "equals";
            case BANG_EQUAL -> "notEquals";
            case GREATER -> "greaterThan";
            case GREATER_EQUAL -> "greaterThanOrEqual";
            case LESS -> "lessThan";
            case LESS_EQUAL -> "lessThanOrEqual";
            default -> null;
        };
        if (methodName == null) return null;
        
        TypeStatement typeStmt = state.types.get(left.getName());
        if (typeStmt != null) {
            for (FunctionStatement op : typeStmt.operators()) {
                if (op.name().lexeme().equals(methodName)) {
                    if (op.returnType() != null) return resolveType(op.returnType());
                    return PrimitiveType.VOID;
                }
            }
        }
        return null;
    }

    public Type findFieldType(ClassStatement stmt, String name, String lookupName) {
        // Try to find in native classes first
        NativeClass nativeClass = state.nativeClasses.get(lookupName);
        if (nativeClass == null && lookupName.contains(".")) {
            nativeClass = state.nativeClasses.get(lookupName.substring(lookupName.lastIndexOf(".") + 1));
        }

        if (nativeClass != null) {
            NativeClass.PropertyBinding<?> prop = nativeClass.properties().get(name);
            if (prop != null) return state.resolveTVType(prop.type(), nativeClass);
            
            NativeClass.ConstantBinding constant = nativeClass.constants().get(name);
            if (constant != null) return state.resolveTVType(constant.type(), nativeClass);
        }

        for (VarStatement field : stmt.fields()) {
            if (field.name().lexeme().equals(name)) return resolveType(field.type());
        }

        if (stmt.superclass() != null) {
            String superName = stmt.superclass().lexeme();
            ClassStatement superclass = state.classes.get(superName);
            if (superclass != null) return findFieldType(superclass, name, superName);
        }

        return null;
    }

    public Type findMethodType(ClassStatement stmt, String name, String lookupName) {
        // Try to find in native classes first
        NativeClass nativeClass = state.nativeClasses.get(lookupName);
        if (nativeClass == null && lookupName.contains(".")) {
            nativeClass = state.nativeClasses.get(lookupName.substring(lookupName.lastIndexOf(".") + 1));
        }

        if (nativeClass != null) {
            NativeClass.MethodBinding<?> method = nativeClass.methods().get(name);
            if (method != null) return state.resolveTVType(method.returnType(), nativeClass);
        }

        for (FunctionStatement method : stmt.methods()) {
            if (method.name().lexeme().equals(name)) {
                if (method.returnType() != null) return resolveType(method.returnType());
                return PrimitiveType.VOID;
            }
        }

        if (stmt.superclass() != null) {
            String superName = stmt.superclass().lexeme();
            ClassStatement superclass = state.classes.get(superName);
            if (superclass != null) return findMethodType(superclass, name, superName);
        }

        return null;
    }

    public String resolveImport(String name) {
        Map<String, String> currentScriptImports = state.scriptImports.get(state.currentScriptPath);
        if (currentScriptImports != null && currentScriptImports.containsKey(name)) {
            return currentScriptImports.get(name);
        }
        return null;
    }

    public String resolveQualified(String alias, String member) {
        Map<String, String> currentQualifiedImports = state.scriptQualifiedImports.get(state.currentScriptPath);
        if (currentQualifiedImports != null && currentQualifiedImports.containsKey(alias)) {
            return currentQualifiedImports.get(alias) + "." + member;
        }
        return null;
    }

    public boolean checkVisibility(Token name, TokenType visibility, String targetScriptPath, String targetModule) {
        if (visibility == TokenType.PUBLIC) return true;
        if (visibility == TokenType.PRIVATE) return state.currentScriptPath.equals(targetScriptPath);
        if (visibility == TokenType.PROTECTED) {
            String currentFolder = AstUtils.getFolder(state.currentScriptPath);
            String targetFolder = AstUtils.getFolder(targetScriptPath);
            return currentFolder.equals(targetFolder);
        }
        if (visibility == TokenType.MODULE) return state.currentModule.equals(targetModule);
        return true;
    }

    @Override public Void visitBlockStatement(BlockStatement stmt) { return statementChecker.visitBlockStatement(stmt); }
    @Override public Void visitExpressionStatement(ExpressionStatement stmt) { return statementChecker.visitExpressionStatement(stmt); }
    @Override public Void visitIfStatement(IfStatement stmt) { return statementChecker.visitIfStatement(stmt); }
    @Override public Void visitWhileStatement(WhileStatement stmt) { return statementChecker.visitWhileStatement(stmt); }
    @Override public Void visitForStatement(ForStatement stmt) { return statementChecker.visitForStatement(stmt); }
    @Override public Void visitBreakStatement(BreakStatement stmt) { return statementChecker.visitBreakStatement(stmt); }
    @Override public Void visitContinueStatement(ContinueStatement stmt) { return statementChecker.visitContinueStatement(stmt); }
    @Override public Void visitReturnStatement(ReturnStatement stmt) { return statementChecker.visitReturnStatement(stmt); }
    @Override public Void visitPrintStatement(PrintStatement stmt) { return statementChecker.visitPrintStatement(stmt); }
    @Override public Void visitVarStatement(VarStatement stmt) { return statementChecker.visitVarStatement(stmt); }
    @Override public Void visitPassStatement(PassStatement stmt) { return statementChecker.visitPassStatement(stmt); }
    @Override public Void visitImportStatement(ImportStatement stmt) { return statementChecker.visitImportStatement(stmt); }
    @Override public Void visitMatchStatement(MatchStatement stmt) { return statementChecker.visitMatchStatement(stmt); }
    @Override public Void visitFunctionStatement(FunctionStatement stmt) { return declarationChecker.visitFunctionStatement(stmt); }
    @Override public Void visitClassStatement(ClassStatement stmt) { return declarationChecker.visitClassStatement(stmt); }
    @Override public Void visitTraitStatement(TraitStatement stmt) { return declarationChecker.visitTraitStatement(stmt); }
    @Override public Void visitTypeStatement(TypeStatement stmt) { return declarationChecker.visitTypeStatement(stmt); }
    @Override public Void visitConstraintStatement(ConstraintStatement stmt) { return declarationChecker.visitConstraintStatement(stmt); }
    @Override public Void visitEventStatement(EventStatement stmt) { return declarationChecker.visitEventStatement(stmt); }
    @Override public Void visitOnStatement(OnStatement stmt) { return statementChecker.visitOnStatement(stmt); }
    @Override public Void visitDispatchStatement(DispatchStatement stmt) { return statementChecker.visitDispatchStatement(stmt); }

    @Override public Type visitBinaryExpression(BinaryExpression expr) { return expressionChecker.visitBinaryExpression(expr); }
    @Override public Type visitGroupingExpression(GroupingExpression expr) { return expressionChecker.visitGroupingExpression(expr); }
    @Override public Type visitLiteralExpression(LiteralExpression expr) { return expressionChecker.visitLiteralExpression(expr); }
    @Override public Type visitLogicalExpression(LogicalExpression expr) { return expressionChecker.visitLogicalExpression(expr); }
    @Override public Type visitUnaryExpression(UnaryExpression expr) { return expressionChecker.visitUnaryExpression(expr); }
    @Override public Type visitTernaryExpression(TernaryExpression expr) { return expressionChecker.visitTernaryExpression(expr); }
    @Override public Type visitInterpolationExpression(InterpolationExpression expr) { return expressionChecker.visitInterpolationExpression(expr); }
    @Override public Type visitVariableExpression(VariableExpression expr) { return expressionChecker.visitVariableExpression(expr); }
    @Override public Type visitNativeExpression(NativeExpression expr) { return expressionChecker.visitNativeExpression(expr); }
    @Override public Type visitAssignExpression(AssignExpression expr) { return expressionChecker.visitAssignExpression(expr); }
    @Override public Type visitRangeExpression(RangeExpression expr) { return expressionChecker.visitRangeExpression(expr); }
    @Override public Type visitMatchExpression(MatchExpression expr) { return expressionChecker.visitMatchExpression(expr); }
    @Override public Type visitCallExpression(CallExpression expr) { return expressionChecker.visitCallExpression(expr); }
    @Override public Type visitFunctionExpression(FunctionExpression expr) { return expressionChecker.visitFunctionExpression(expr); }
    @Override public Type visitGetExpression(GetExpression expr) { return expressionChecker.visitGetExpression(expr); }
    @Override public Type visitSetExpression(SetExpression expr) { return expressionChecker.visitSetExpression(expr); }
    @Override public Type visitThisExpression(ThisExpression expr) { return expressionChecker.visitThisExpression(expr); }
    @Override public Type visitNewExpression(NewExpression expr) { return expressionChecker.visitNewExpression(expr); }
    @Override public Type visitCollectionLiteralExpression(CollectionLiteralExpression expr) { return expressionChecker.visitCollectionLiteralExpression(expr); }
    @Override public Type visitSliceExpression(SliceExpression expr) { return expressionChecker.visitSliceExpression(expr); }
    @Override public Type visitIndexSetExpression(IndexSetExpression expr) { return expressionChecker.visitIndexSetExpression(expr); }
    @Override public Type visitIndexExpression(IndexExpression expr) { return expressionChecker.visitIndexExpression(expr); }
    @Override public Type visitTypeBinaryExpression(TypeBinaryExpression expr) { return expressionChecker.visitTypeBinaryExpression(expr); }
    @Override public Type visitSuperExpression(SuperExpression expr) { return expressionChecker.visitSuperExpression(expr); }
}
