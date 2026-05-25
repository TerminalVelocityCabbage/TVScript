package com.terminalvelocitycabbage.tvscript.analysis;

import com.terminalvelocitycabbage.tvscript.TVScript;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import static com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;
import com.terminalvelocitycabbage.tvscript.ast.VisibleElement;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.execution.NativeClass;
import com.terminalvelocitycabbage.tvscript.execution.TVScriptNativeFunction;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.ArrayList;
import java.util.Collection;
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
    private final Map<String, String> classScriptPaths = new HashMap<>();
    private final Map<String, String> classModules = new HashMap<>();
    private final Map<String, TraitStatement> traits = new HashMap<>();
    private final Map<String, TypeStatement> types = new HashMap<>();
    private final Map<String, ConstraintStatement> constraints = new HashMap<>();
    private final Map<String, EventStatement> events = new HashMap<>();
    private final Map<String, FunctionStatement> functions = new HashMap<>();
    private final Map<String, VarStatement> globalVars = new HashMap<>();
    private final Map<String, String> varScriptPaths = new HashMap<>();
    private final Map<String, String> varModules = new HashMap<>();
    private final Map<String, String> functionScriptPaths = new HashMap<>();
    private final Map<String, String> functionModules = new HashMap<>();
    private final Map<String, Map<String, String>> scriptImports = new HashMap<>();
    private final Map<String, Map<String, String>> scriptQualifiedImports = new HashMap<>();
    private final Set<String> nativeFunctionNames = new HashSet<>();
    private final Map<String, NativeClass> nativeClasses = new HashMap<>();
    private int loopDepth = 0;

    private ClassStatement currentClass = null;
    private TypeStatement currentType = null;
    private String currentScriptPath = "default";
    private String currentModule = "default";

    private boolean checkVisibility(Token name, TokenType visibility, String targetScriptPath, String targetModule) {
        if (visibility == TokenType.PUBLIC) return true;
        
        if (visibility == TokenType.PRIVATE) {
            return currentScriptPath.equals(targetScriptPath);
        }
        if (visibility == TokenType.PROTECTED) {
            // "all scripts in this folder have access"
            String currentFolder = getFolder(currentScriptPath);
            String targetFolder = getFolder(targetScriptPath);
            return currentFolder.equals(targetFolder);
        }
        if (visibility == TokenType.MODULE) {
            return currentModule.equals(targetModule);
        }
        return true; // Should not happen
    }

    private String getFolder(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash == -1) return "";
        return path.substring(0, lastSlash);
    }

    private static class VariableStaticInfo {
        final TokenType type;
        final boolean isConst;
        final TokenType returnType;
        final String namedType;

        VariableStaticInfo(TokenType type, boolean isConst) {
            this(type, isConst, null, null);
        }

        VariableStaticInfo(TokenType type, boolean isConst, TokenType returnType) {
            this(type, isConst, returnType, null);
        }

        VariableStaticInfo(TokenType type, boolean isConst, TokenType returnType, String namedType) {
            this.type = type;
            this.isConst = isConst;
            this.returnType = returnType;
            this.namedType = namedType;
        }
    }

    public TypeChecker() {
        this(List.of(), List.of());
    }

    public TypeChecker(Collection<TVScriptNativeFunction> nativeFunctions) {
        this(nativeFunctions, List.of());
    }

    public TypeChecker(Collection<TVScriptNativeFunction> nativeFunctions, Collection<NativeClass> nativeClasses) {
        Map<String, VariableStaticInfo> globalScope = new HashMap<>();
        for (TVScriptNativeFunction nativeFunction : nativeFunctions) {
            String name = nativeFunction.name();
            globalScope.put(name, new VariableStaticInfo(TokenType.FUNCTION, true, nativeFunction.returnType()));
            nativeFunctionNames.add(name);
            
            // Also register them in our definitions for name resolution
            functions.put(name, new FunctionStatement(
                    new Token(TokenType.IDENTIFIER, name, null, 0),
                    List.of(), // Parameters not needed for visibility check
                    new Token(nativeFunction.returnType(), "", null, 0),
                    null, List.of(), false, false, 
                    new Token(TokenType.PUBLIC, "public", null, 0) // Natives are always public
            ));
        }
        for (NativeClass nativeClass : nativeClasses) {
            String name = nativeClass.scriptName();
            this.nativeClasses.put(name, nativeClass);
            
            // Also register them in our definitions for name resolution
            classes.put(name, new ClassStatement(
                    new Token(TokenType.IDENTIFIER, name, null, 0),
                    List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), true,
                    new Token(TokenType.PUBLIC, "public", null, 0) // Natives are always public
            ));
        }
        scopes.add(globalScope);
    }

    /**
     * Checks a list of statements for type errors.
     * @param statements The statements to check.
     */
    public void check(List<Statement> statements) {
        check(statements, "default", "default");
    }

    public void check(List<Statement> statements, String scriptPath, String module) {
        this.currentScriptPath = scriptPath;
        this.currentModule = module;

        registerDefinitions(statements, scriptPath, module);

        // Second pass: check bodies and inheritance rules
        for (Statement statement : statements) {
            if (statement != null) check(statement);
        }
    }

    private String getScriptIdentifier(String path) {
        if (path == null) return "default";
        String id = path;
        if (id.endsWith(".tvs")) id = id.substring(0, id.length() - 4);
        id = id.replace('/', '.').replace('\\', '.');
        // Strip leading dots if any
        while (id.startsWith(".")) id = id.substring(1);
        return id;
    }

    public void registerDefinitions(List<Statement> statements, String scriptPath, String module) {
        String scriptId = getScriptIdentifier(scriptPath);
        // First pass: collect class and trait definitions
        for (Statement statement : statements) {
            if (statement instanceof ClassStatement klass) {
                String className = klass.name().lexeme();
                String fullName = scriptId + "." + className;
                if (classes.containsKey(fullName) && !classes.get(fullName).equals(statement)) {
                    TVScript.compileError(new CompileError(klass.name(), "Class '" + fullName + "' is already defined."));
                }
                classes.put(fullName, klass);
                classScriptPaths.put(fullName, scriptPath);
                classModules.put(fullName, module);
                
                // For backward compatibility and simple script usage, also keep the simple name 
                // but only if it doesn't conflict or if we are in the same script.
                // Actually, let's keep it but be aware of collisions.
                if (!classes.containsKey(className)) {
                    classes.put(className, klass);
                    classScriptPaths.put(className, scriptPath);
                    classModules.put(className, module);
                }
            } else if (statement instanceof TraitStatement trait) {
                String name = trait.name().lexeme();
                traits.put(scriptId + "." + name, trait);
                if (!traits.containsKey(name)) traits.put(name, trait);
            } else if (statement instanceof TypeStatement typeStmt) {
                String name = typeStmt.name().lexeme();
                types.put(scriptId + "." + name, typeStmt);
                if (!types.containsKey(name)) types.put(name, typeStmt);
            } else if (statement instanceof ConstraintStatement constraint) {
                String name = constraint.name().lexeme();
                constraints.put(scriptId + "." + name, constraint);
                if (!constraints.containsKey(name)) constraints.put(name, constraint);
            } else if (statement instanceof EventStatement event) {
                String name = event.name().lexeme();
                events.put(scriptId + "." + name, event);
                if (!events.containsKey(name)) events.put(name, event);
            } else if (statement instanceof FunctionStatement func) {
                String funcName = func.name().lexeme();
                String fullName = scriptId + "." + funcName;
                functions.put(fullName, func);
                functionScriptPaths.put(fullName, scriptPath);
                functionModules.put(fullName, module);
                if (!functions.containsKey(funcName)) {
                    functions.put(funcName, func);
                    functionScriptPaths.put(funcName, scriptPath);
                    functionModules.put(funcName, module);
                }
            } else if (statement instanceof VarStatement var) {
                String varName = var.name().lexeme();
                String fullName = scriptId + "." + varName;
                globalVars.put(fullName, var);
                varScriptPaths.put(fullName, scriptPath);
                varModules.put(fullName, module);
                if (!globalVars.containsKey(varName)) {
                    globalVars.put(varName, var);
                    varScriptPaths.put(varName, scriptPath);
                    varModules.put(varName, module);
                }
            }
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
        TokenType iterableType = check(stmt.range());
        if (iterableType != TokenType.TYPE_RANGE
                && iterableType != TokenType.IDENTIFIER
                && iterableType != TokenType.LIST
                && iterableType != TokenType.SET
                && iterableType != TokenType.MAP
                && iterableType != null) {
            TVScript.compileError(new CompileError(stmt.keyword(), "For loop expects a range or collection."));
        }

        if (stmt.valueName() != null && iterableType == TokenType.TYPE_RANGE) {
            TVScript.compileError(new CompileError(stmt.keyword(), "Range iteration supports only a single loop variable."));
        }

        beginScope();
        if (stmt.name() != null) {
            declare(stmt.name(), stmt.type().type(), false);
        }
        if (stmt.valueName() != null) {
            declare(stmt.valueName(), stmt.valueType().type(), false);
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
    public Void visitImportStatement(ImportStatement stmt) {
        String modulePath = stmt.module().lexeme();
        Map<String, String> currentScriptImports = scriptImports.computeIfAbsent(currentScriptPath, k -> new HashMap<>());
        Map<String, String> currentQualifiedImports = scriptQualifiedImports.computeIfAbsent(currentScriptPath, k -> new HashMap<>());

        if (stmt.items().isEmpty()) {
            // Whole module OR single item import
            // Try to see if it's a member of a script
            int lastDot = modulePath.lastIndexOf('.');
            if (lastDot != -1) {
                String fullPath = modulePath;
                VisibleElement element = null;
                String targetPath = null;
                String targetModule = null;

                if (classes.containsKey(fullPath)) {
                    element = classes.get(fullPath);
                    targetPath = classScriptPaths.get(fullPath);
                    targetModule = classModules.get(fullPath);
                } else if (functions.containsKey(fullPath)) {
                    element = functions.get(fullPath);
                    targetPath = functionScriptPaths.get(fullPath);
                    targetModule = functionModules.get(fullPath);
                } else if (globalVars.containsKey(fullPath)) {
                    element = globalVars.get(fullPath);
                    targetPath = varScriptPaths.get(fullPath);
                    targetModule = varModules.get(fullPath);
                }

                if (element != null) {
                    checkImportVisibility(stmt.module(), element, targetPath, targetModule);
                    String alias = stmt.alias() != null ? stmt.alias().lexeme() : modulePath.substring(lastDot + 1);
                    currentScriptImports.put(alias, fullPath);
                    return null;
                }
            }

            // Whole module import: import a.b.c as d
            String alias = stmt.alias() != null ? stmt.alias().lexeme() : modulePath.substring(modulePath.lastIndexOf('.') + 1);
            currentQualifiedImports.put(alias, modulePath);
        } else {
            // Selective import: import a.b.c : [x as y, z]
            for (ImportStatement.ImportItem item : stmt.items()) {
                String originalName = item.name().lexeme();
                String fullPath = modulePath + "." + originalName;
                
                // Validate visibility of the imported item
                if (classes.containsKey(fullPath)) {
                    ClassStatement klass = classes.get(fullPath);
                    checkImportVisibility(item.name(), klass, classScriptPaths.get(fullPath), classModules.get(fullPath));
                } else if (functions.containsKey(fullPath)) {
                    FunctionStatement func = functions.get(fullPath);
                    checkImportVisibility(item.name(), func, functionScriptPaths.get(fullPath), functionModules.get(fullPath));
                } else if (globalVars.containsKey(fullPath)) {
                    VarStatement var = globalVars.get(fullPath);
                    checkImportVisibility(item.name(), var, varScriptPaths.get(fullPath), varModules.get(fullPath));
                }

                String alias = item.alias() != null ? item.alias().lexeme() : originalName;
                currentScriptImports.put(alias, fullPath);
            }
        }
        return null;
    }

    private void checkImportVisibility(Token name, VisibleElement element, String targetPath, String targetModule) {
        if (element != null && element.visibility() != null) {
            if (targetPath == null) targetPath = currentScriptPath;
            if (targetModule == null) targetModule = currentModule;
            if (!checkVisibility(name, element.visibility().type(), targetPath, targetModule)) {
                TVScript.compileError(new CompileError(name, 
                    element.visibility().type().name().toLowerCase() + " " + name.lexeme() + " is not accessible from here."));
            }
        }
    }

    private void checkQualifiedVisibility(Token name, VisibleElement element, String targetPath, String targetModule) {
        if (element != null && element.visibility() != null) {
            if (targetPath == null) targetPath = currentScriptPath;
            if (targetModule == null) targetModule = currentModule;
            if (!checkVisibility(name, element.visibility().type(), targetPath, targetModule)) {
                TVScript.compileError(new CompileError(name, 
                    element.visibility().type().name().toLowerCase() + " " + name.lexeme() + " is not accessible from here."));
            }
        }
    }

    @Override
    public Void visitFunctionStatement(FunctionStatement stmt) {
        if (stmt.name().type() != TokenType.CONSTRUCTOR) {
            declare(stmt.name(), TokenType.FUNCTION, true, stmt.returnType() != null ? stmt.returnType().type() : null);
            functions.put(stmt.name().lexeme(), stmt);
        }
        beginScope();
        for (FunctionStatement.Parameter param : stmt.parameters()) {
            declare(param.name(), param.type().type(), param.type().type() == TokenType.IDENTIFIER ? param.type().lexeme() : null, false);
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

    @Override
    public Void visitClassStatement(ClassStatement stmt) {
        ClassStatement previousClass = currentClass;
        currentClass = stmt;
        NativeClass nativeClass = null;
        if (stmt.isNative()) {
            nativeClass = nativeClasses.get(stmt.name().lexeme());
            if (nativeClass == null) {
                TVScript.compileError(new CompileError(stmt.name(), "'" + stmt.name().lexeme() + "' not defined as a native class type on the global environment."));
                currentClass = previousClass;
                return null;
            }

            if (!stmt.constructors().isEmpty()) {
                TVScript.compileError(new CompileError(stmt.name(), "Native classes cannot declare constructors."));
            }
            for (VarStatement field : stmt.fields()) {
                if (!field.isConst()) {
                    TVScript.compileError(new CompileError(field.name(), "Native classes cannot declare instance fields."));
                }
            }
            for (FunctionStatement method : stmt.methods()) {
                if (nativeClass.methods().containsKey(method.name().lexeme())) {
                    TVScript.compileError(new CompileError(method.name(),
                            "Native class '" + stmt.name().lexeme() + "' cannot override native member '" + method.name().lexeme() + "'."));
                }
            }
        }

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

    @Override
    public Void visitTypeStatement(TypeStatement stmt) {
        TypeStatement previousType = currentType;
        currentType = stmt;

        declare(stmt.name(), TokenType.CLASS, stmt.name().lexeme(), true);

        for (Token traitToken : stmt.traits()) {
            if (!traits.containsKey(traitToken.lexeme())) {
                TVScript.compileError(new CompileError(traitToken, "Only traits can be implemented."));
            }
        }

        checkTypeTraitImplementations(stmt);

        beginScope();
        declare(new Token(TokenType.THIS, "this", null, 0), TokenType.IDENTIFIER, stmt.name().lexeme(), true);

        for (VarStatement field : stmt.fields()) {
            if (!field.isConst()) {
                TVScript.compileError(new CompileError(field.name(), "Type fields are immutable and must be declared as constants."));
            }
            if (field.initializer() != null) {
                check(field.initializer());
            }
            declare(field.name(), field.type().type(), field.type().type() == TokenType.IDENTIFIER ? field.type().lexeme() : null, true);
        }

        for (FunctionStatement method : stmt.methods()) {
            check(method);
        }

        for (FunctionStatement operator : stmt.operators()) {
            validateTypeOperator(stmt, operator);
            beginScope();
            for (FunctionStatement.Parameter parameter : operator.parameters()) {
                declare(parameter.name(), parameter.type().type(), parameter.type().type() == TokenType.IDENTIFIER ? parameter.type().lexeme() : null, false);
            }
            if (operator.body() != null) {
                check(operator.body());
            }
            endScope();
        }

        endScope();
        currentType = previousType;
        return null;
    }

    @Override
    public Void visitConstraintStatement(ConstraintStatement stmt) {
        if (stmt.superclassConstraint() != null) {
            resolveConstraintReference(stmt.superclassConstraint(), new HashSet<>());
        }
        for (Token traitConstraint : stmt.traitConstraints()) {
            if (!traits.containsKey(traitConstraint.lexeme())) {
                TVScript.compileError(new CompileError(traitConstraint,
                        "Unknown trait '" + traitConstraint.lexeme() + "' in constraint declaration."));
            }
        }
        return null;
    }

    @Override
    public Void visitEventStatement(EventStatement stmt) {
        if (stmt.isNative()) {
            if (stmt.fields().size() != 1) {
                TVScript.compileError(new CompileError(stmt.name(), "Native event must have exactly one field."));
            } else {
                VarStatement field = stmt.fields().get(0);
                if (field.type().type() != TokenType.IDENTIFIER || !field.type().lexeme().equals(stmt.name().lexeme())) {
                    TVScript.compileError(new CompileError(field.type(), "Native event field type must match the event name."));
                }
                if (!nativeClasses.containsKey(stmt.name().lexeme())) {
                    TVScript.compileError(new CompileError(stmt.name(), "Unknown native class '" + stmt.name().lexeme() + "' for native event."));
                }
            }
        }
        for (VarStatement field : stmt.fields()) {
            check(field);
        }
        return null;
    }

    @Override
    public Void visitOnStatement(OnStatement stmt) {
        String eventName = stmt.eventName().lexeme();
        if (!events.containsKey(eventName) && !"InitializedEvent".equals(eventName)) {
            TVScript.compileError(new CompileError(stmt.eventName(), "Unknown event '" + eventName + "'."));
        }

        beginScope();
        for (OnStatement.ListenerParameter param : stmt.parameters()) {
            declare(param.name(), param.type().type(), param.type().lexeme(), true);
            if (param.filter() != null) {
                check(param.filter());
            }
        }
        check(stmt.body());
        endScope();
        return null;
    }

    @Override
    public Void visitDispatchStatement(DispatchStatement stmt) {
        String eventName = stmt.eventName().lexeme();
        if (!events.containsKey(eventName) && !"InitializedEvent".equals(eventName)) {
            TVScript.compileError(new CompileError(stmt.eventName(), "Unknown event '" + eventName + "'."));
        }

        for (Expression.Argument arg : stmt.arguments()) {
            check(arg.value());
        }
        return null;
    }

    private void validateTypeOperator(TypeStatement typeStatement, FunctionStatement operator) {
        String operatorName = operator.name().lexeme();
        List<FunctionStatement.Parameter> parameters = operator.parameters();

        if ("negative".equals(operatorName)) {
            if (parameters.size() != 1 || !"right".equals(parameters.get(0).name().lexeme())) {
                TVScript.compileError(new CompileError(operator.name(), "Operator parameter must be named right."));
            }
        } else {
            if (parameters.size() != 2 || !"left".equals(parameters.get(0).name().lexeme()) || !"right".equals(parameters.get(1).name().lexeme())) {
                TVScript.compileError(new CompileError(operator.name(), "Operator parameters must be named left and right."));
            }
        }

        if ("compare".equals(operatorName)) {
            if (operator.returnType() == null || operator.returnType().type() != TokenType.TYPE_DECIMAL) {
                TVScript.compileError(new CompileError(operator.name(), "Operator compare must return decimal."));
            }
        }

        for (FunctionStatement.Parameter parameter : parameters) {
            if (parameter.type().type() == TokenType.IDENTIFIER) {
                String typeName = parameter.type().lexeme();
                if (!classes.containsKey(typeName) && !types.containsKey(typeName)) {
                    TVScript.compileError(new CompileError(parameter.type(), "Unknown type '" + typeName + "' in operator declaration."));
                }
            }
        }

        if (operator.returnType() != null && operator.returnType().type() == TokenType.IDENTIFIER) {
            String returnTypeName = operator.returnType().lexeme();
            if (!classes.containsKey(returnTypeName) && !types.containsKey(returnTypeName)) {
                TVScript.compileError(new CompileError(operator.returnType(), "Unknown return type '" + returnTypeName + "' in operator declaration."));
            }
        }
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
        String declaredNamedType = declaredType == TokenType.IDENTIFIER ? stmt.type().lexeme() : null;
        String inferredNamedType = declaredNamedType;

        if (declaredType == TokenType.IDENTIFIER) {
            validateGenericTypeConstraints(stmt.type());
        }

        if (stmt.initializer() != null) {
            inferredType = check(stmt.initializer());
            inferredNamedType = inferNamedType(stmt.initializer());
            if (declaredType == TokenType.VAR || declaredType == TokenType.CONST) {
                if (inferredType == null) {
                    TVScript.compileError(new CompileError(stmt.name(), "Cannot infer type from none."));
                }
            } else if (declaredType == TokenType.IDENTIFIER) {
                if (inferredNamedType != null && declaredNamedType != null && !isAssignableNamedType(declaredNamedType, inferredNamedType)) {
                    TVScript.compileError(new CompileError(stmt.name(), "Incompatible types in initialization."));
                }
            } else if (inferredType != null && !isCompatible(declaredType, inferredType)) {
                TVScript.compileError(new CompileError(stmt.name(), "Incompatible types in initialization."));
            }
        } else if (stmt.isConst()) {
            TVScript.compileError(new CompileError(stmt.name(), "Constant must be initialized."));
        } else if (declaredType == TokenType.VAR || declaredType == TokenType.CONST) {
            TVScript.compileError(new CompileError(stmt.name(), "Type inference requires an initializer."));
        }

        TokenType finalType = (declaredType == TokenType.VAR || declaredType == TokenType.CONST) ? inferredType : declaredType;
        String finalNamedType = (declaredType == TokenType.VAR || declaredType == TokenType.CONST) ? inferredNamedType : declaredNamedType;
        declare(stmt.name(), finalType, finalNamedType, stmt.isConst());
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

    private String resolveImport(String name) {
        Map<String, String> currentScriptImports = scriptImports.get(currentScriptPath);
        if (currentScriptImports != null && currentScriptImports.containsKey(name)) {
            return currentScriptImports.get(name);
        }
        return null;
    }

    private String resolveQualified(String alias, String member) {
        Map<String, String> currentQualifiedImports = scriptQualifiedImports.get(currentScriptPath);
        if (currentQualifiedImports != null && currentQualifiedImports.containsKey(alias)) {
            return currentQualifiedImports.get(alias) + "." + member;
        }
        return null;
    }

    @Override
    public TokenType visitVariableExpression(VariableExpression expr) {
        VariableStaticInfo info = lookup(expr.name());
        
        // If not in scope, check global classes, functions, etc.
        if (info == null) {
            String name = expr.name().lexeme();

            // Try resolving through imports
            String importedName = resolveImport(name);
            if (importedName != null) {
                name = importedName;
            }
            
            if (classes.containsKey(name)) {
                ClassStatement klass = classes.get(name);
                if (klass.visibility() != null) {
                    String targetPath = classScriptPaths.get(name);
                    String targetModule = classModules.get(name);
                    if (targetPath == null) targetPath = currentScriptPath;
                    if (targetModule == null) targetModule = currentModule;
                    if (!checkVisibility(expr.name(), klass.visibility().type(), targetPath, targetModule)) {
                         TVScript.compileError(new CompileError(expr.name(), 
                            klass.visibility().type().name().toLowerCase() + " class '" + name + "' is not accessible from here."));
                    }
                }
                return TokenType.CLASS;
            }
            
            if (functions.containsKey(name)) {
                FunctionStatement func = functions.get(name);
                if (func.visibility() != null) {
                    String targetPath = functionScriptPaths.get(name);
                    String targetModule = functionModules.get(name);
                    if (targetPath == null) targetPath = currentScriptPath;
                    if (targetModule == null) targetModule = currentModule;
                    if (!checkVisibility(expr.name(), func.visibility().type(), targetPath, targetModule)) {
                         TVScript.compileError(new CompileError(expr.name(), 
                            func.visibility().type().name().toLowerCase() + " function '" + name + "' is not accessible from here."));
                    }
                }
                return TokenType.FUNCTION;
            }

            if (globalVars.containsKey(name)) {
                VarStatement var = globalVars.get(name);
                if (var.visibility() != null) {
                    String targetPath = varScriptPaths.get(name);
                    String targetModule = varModules.get(name);
                    if (targetPath == null) targetPath = currentScriptPath;
                    if (targetModule == null) targetModule = currentModule;
                    if (!checkVisibility(expr.name(), var.visibility().type(), targetPath, targetModule)) {
                        TVScript.compileError(new CompileError(expr.name(),
                                var.visibility().type().name().toLowerCase() + " variable '" + name + "' is not accessible from here."));
                    }
                }
                return var.type().type();
            }
            
            if (traits.containsKey(name)) return TokenType.TRAIT;
            if (types.containsKey(name)) return TokenType.TYPE;
            if (events.containsKey(name)) return TokenType.EVENT;

            TVScript.compileError(new CompileError(expr.name(), "Variable used before declaration or undefined."));
            return null;
        }

        return info.type;
    }

    @Override
    public TokenType visitNativeExpression(NativeExpression expr) {
        String name = expr.name().lexeme();
        if (!nativeFunctionNames.contains(name)) {
            TVScript.compileError(new CompileError(expr.keyword(), "'" + name + "' is not a native function."));
            return null;
        }

        VariableStaticInfo info = lookup(expr.name());
        if (info == null || info.type != TokenType.FUNCTION) {
            TVScript.compileError(new CompileError(expr.keyword(), "'" + name + "' is not a native function."));
            return null;
        }

        return TokenType.FUNCTION;
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
            String calleeName = varExpr.name().lexeme();

            if (expr.nativeCall() && !nativeFunctionNames.contains(calleeName)) {
                TVScript.compileError(new CompileError(varExpr.name(), "'" + calleeName + "' is not a native function."));
            }

            if (!expr.nativeCall() && nativeFunctionNames.contains(calleeName)) {
                TVScript.compileError(new CompileError(varExpr.name(), "Native functions must be called with 'native'."));
            }

            VariableStaticInfo info = lookup(varExpr.name());
            if (info != null && info.type == TokenType.FUNCTION) {
                FunctionStatement functionDefinition = functions.get(calleeName);
                if (functionDefinition != null) {
                    validateFunctionTypeArguments(varExpr.name(), functionDefinition, expr.typeArguments());
                }
                return info.returnType;
            }
        } else if (expr.nativeCall()) {
            TVScript.compileError(new CompileError(expr.paren(), "Native calls must target a global function name."));
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
            declare(param.name(), param.type().type(), param.type().type() == TokenType.IDENTIFIER ? param.type().lexeme() : null, false);
        }
        check(expr.body());
        endScope();
        return TokenType.FUNCTION;
    }

    private String flattenQualifiedName(Expression expr) {
        if (expr instanceof VariableExpression var) {
            return var.name().lexeme();
        } else if (expr instanceof GetExpression get) {
            String prefix = flattenQualifiedName(get.object());
            if (prefix == null) return null;
            return prefix + "." + get.name().lexeme();
        }
        return null;
    }

    @Override
    public TokenType visitGetExpression(GetExpression expr) {
        // Try resolving as a qualified name first: path.to.Script.Member or Alias.Member
        String fullName = flattenQualifiedName(expr);
        if (fullName != null) {
            if (classes.containsKey(fullName)) {
                ClassStatement klass = classes.get(fullName);
                checkQualifiedVisibility(expr.name(), klass, classScriptPaths.get(fullName), classModules.get(fullName));
                return TokenType.CLASS;
            }
            if (functions.containsKey(fullName)) {
                FunctionStatement func = functions.get(fullName);
                checkQualifiedVisibility(expr.name(), func, functionScriptPaths.get(fullName), functionModules.get(fullName));
                return TokenType.FUNCTION;
            }
            if (globalVars.containsKey(fullName)) {
                VarStatement var = globalVars.get(fullName);
                checkQualifiedVisibility(expr.name(), var, varScriptPaths.get(fullName), varModules.get(fullName));
                return var.type().type();
            }

            // Check if the object is an alias
            if (expr.object() instanceof VariableExpression varExpr) {
                String alias = varExpr.name().lexeme();
                Map<String, String> currentQualifiedImports = scriptQualifiedImports.get(currentScriptPath);
                if (currentQualifiedImports != null && currentQualifiedImports.containsKey(alias)) {
                    String resolvedPrefix = currentQualifiedImports.get(alias);
                    String resolvedName = resolvedPrefix + "." + expr.name().lexeme();
                    if (classes.containsKey(resolvedName)) {
                        ClassStatement klass = classes.get(resolvedName);
                        checkQualifiedVisibility(expr.name(), klass, classScriptPaths.get(resolvedName), classModules.get(resolvedName));
                        return TokenType.CLASS;
                    }
                    if (functions.containsKey(resolvedName)) {
                        FunctionStatement func = functions.get(resolvedName);
                        checkQualifiedVisibility(expr.name(), func, functionScriptPaths.get(resolvedName), functionModules.get(resolvedName));
                        return TokenType.FUNCTION;
                    }
                    if (globalVars.containsKey(resolvedName)) {
                        VarStatement var = globalVars.get(resolvedName);
                        checkQualifiedVisibility(expr.name(), var, varScriptPaths.get(resolvedName), varModules.get(resolvedName));
                        return var.type().type();
                    }
                }
            }
        }

        TokenType objectType = check(expr.object());

        if (expr.object() instanceof ThisExpression) {
            VariableStaticInfo info = lookup(expr.name());
            if (info != null) return info.type;
            
            // Check if it's a method
            if (currentClass != null && hasMethod(currentClass, expr.name().lexeme())) {
                return TokenType.FUNCTION;
            }
            if (currentType != null && hasTypeMethod(currentType, expr.name().lexeme())) {
                return TokenType.FUNCTION;
            }
            
            TVScript.compileError(new CompileError(expr.name(), "Undefined property '" + expr.name().lexeme() + "' on 'this'."));
            return null;
        }

        if (expr.object() instanceof VariableExpression varExpr) {
            String variableName = varExpr.name().lexeme();

            if (traits.containsKey(variableName)) {
                TraitStatement trait = traits.get(variableName);
                TokenType fieldType = findFieldInTrait(trait, expr.name().lexeme());
                if (fieldType != null) return fieldType;
                
                TVScript.compileError(new CompileError(expr.name(), "Undefined trait constant '" + expr.name().lexeme() + "'."));
            }

            VariableStaticInfo info = lookup(varExpr.name());
            if (info != null && info.namedType != null) {
                TypeStatement typeStatement = types.get(info.namedType);
                if (typeStatement != null) {
                    for (VarStatement field : typeStatement.fields()) {
                        if (field.name().lexeme().equals(expr.name().lexeme())) {
                            return field.type().type();
                        }
                    }
                    if (hasTypeMethod(typeStatement, expr.name().lexeme())) {
                        return TokenType.FUNCTION;
                    }
                    TVScript.compileError(new CompileError(expr.name(), "Undefined property '" + expr.name().lexeme() + "' on type '" + info.namedType + "'."));
                    return null;
                }
                
                ClassStatement classStatement = classes.get(info.namedType);
                if (classStatement != null) {
                    // Check instance fields
                    for (VarStatement field : classStatement.fields()) {
                        if (field.name().lexeme().equals(expr.name().lexeme())) {
                            // ENFORCE VISIBILITY
                            TokenType visibility = field.visibility() != null ? field.visibility().type() : TokenType.PRIVATE;
                            
                            // Get the target script path and module for this class. 
                            String targetScriptPath = classScriptPaths.get(info.namedType);
                            String targetModule = classModules.get(info.namedType);
                            
                            // Hack for single-file tests: if targetScriptPath is null, it's the current one
                            if (targetScriptPath == null) targetScriptPath = currentScriptPath;
                            if (targetModule == null) targetModule = currentModule;

                            if (!checkVisibility(expr.name(), visibility, targetScriptPath, targetModule)) {
                                TVScript.compileError(new CompileError(expr.name(), 
                                    visibility.name().toLowerCase() + " field '" + expr.name().lexeme() + "' is not accessible from here."));
                            }
                            return field.type().type();
                        }
                    }
                    
                    // Check instance methods
                    for (FunctionStatement method : classStatement.methods()) {
                         if (method.name().lexeme().equals(expr.name().lexeme())) {
                             TokenType visibility = method.visibility() != null ? method.visibility().type() : TokenType.PRIVATE;
                             String targetScriptPath = classScriptPaths.get(info.namedType);
                             String targetModule = classModules.get(info.namedType);
                             if (targetScriptPath == null) targetScriptPath = currentScriptPath;
                             if (targetModule == null) targetModule = currentModule;
                             
                             if (!checkVisibility(expr.name(), visibility, targetScriptPath, targetModule)) {
                                 TVScript.compileError(new CompileError(expr.name(), 
                                     visibility.name().toLowerCase() + " method '" + expr.name().lexeme() + "' is not accessible from here."));
                             }
                             return TokenType.FUNCTION;
                         }
                    }
                }
            }
        }

        return null;
    }

    private boolean hasMethod(ClassStatement stmt, String name) {
        if (stmt.isNative()) {
            NativeClass nativeClass = nativeClasses.get(stmt.name().lexeme());
            if (nativeClass != null) {
                if (nativeClass.methods().containsKey(name)) {
                    return true;
                }
                if (nativeClass.properties().containsKey(name)) {
                    return true;
                }
            }
        }
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

    private boolean hasTypeMethod(TypeStatement stmt, String name) {
        for (FunctionStatement method : stmt.methods()) {
            if (method.name().lexeme().equals(name)) {
                return true;
            }
        }
        return hasTypeTraitMethod(stmt, name);
    }

    private boolean hasTypeTraitMethod(TypeStatement stmt, String name) {
        for (Token traitToken : stmt.traits()) {
            TraitStatement trait = traits.get(traitToken.lexeme());
            if (trait != null && hasTraitMethod(trait, name)) {
                return true;
            }
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
        if (expr.object() instanceof VariableExpression varExpr) {
            VariableStaticInfo info = lookup(varExpr.name());
            if (info != null && info.namedType != null && types.containsKey(info.namedType)) {
                TVScript.compileError(new CompileError(expr.name(), "Type fields are immutable."));
            }
        }
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

        if (expr.callee() instanceof VariableExpression variableExpression) {
            String className = variableExpression.name().lexeme();
            ClassStatement classStatement = classes.get(className);
            if (classStatement != null) {
                // ENFORCE VISIBILITY for Class and its Constructors
                // Check class visibility
                if (classStatement.visibility() != null) {
                    String targetPath = classScriptPaths.get(className);
                    String targetModule = classModules.get(className);
                    if (targetPath == null) targetPath = currentScriptPath;
                    if (targetModule == null) targetModule = currentModule;
                    
                    if (!checkVisibility(variableExpression.name(), classStatement.visibility().type(), targetPath, targetModule)) {
                        TVScript.compileError(new CompileError(variableExpression.name(), 
                            "Cannot access " + classStatement.visibility().type().name().toLowerCase() + " class '" + className + "' from outside its scope."));
                    }
                }
                
                // Check constructors (assuming first match for now, or just all of them have same visibility usually)
                // For simplicity, we can check if there's any accessible constructor
                // or just check the one being called (finding best is complex here).
                // Let's at least check if the FIRST constructor is accessible if it's the only one.
                if (!classStatement.constructors().isEmpty()) {
                     // Check if ANY constructor is accessible? Usually they all are public if it's a public class.
                     // But if some are private, we should check.
                }

                validateClassTypeArguments(variableExpression.name(), classStatement, expr.typeArguments());
                if (classStatement.isNative()) {
                    NativeClass nativeClass = nativeClasses.get(className);
                    if (nativeClass != null && nativeClass.constructors().isEmpty()) {
                        TVScript.compileError(new CompileError(expr.keyword(), "Native class '" + className + "' has no constructors."));
                    }
                }
            }
        }

        for (Argument arg : expr.arguments()) {
            check(arg.value());
        }
        return TokenType.CLASS;
    }

    private String inferNamedType(Expression initializer) {
        if (initializer instanceof NewExpression newExpression && newExpression.callee() instanceof VariableExpression variableExpression) {
            if (!newExpression.typeArguments().isEmpty()) {
                StringBuilder value = new StringBuilder(variableExpression.name().lexeme()).append("<");
                for (int i = 0; i < newExpression.typeArguments().size(); i++) {
                    if (i > 0) {
                        value.append(", ");
                    }
                    value.append(newExpression.typeArguments().get(i).lexeme());
                }
                value.append(">");
                return value.toString();
            }
            return variableExpression.name().lexeme();
        }
        return null;
    }

    private boolean isAssignableNamedType(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }

        if (expected.equals(actual)) {
            return true;
        }

        ParsedNamedType expectedType = parseNamedType(expected);
        ParsedNamedType actualType = parseNamedType(actual);

        if (expectedType.baseName().equals(actualType.baseName())) {
            if (expectedType.arguments().isEmpty() || actualType.arguments().isEmpty()) {
                return true;
            }
            if (expectedType.arguments().size() != actualType.arguments().size()) {
                return false;
            }
            for (int i = 0; i < expectedType.arguments().size(); i++) {
                if (!isAssignableNamedType(expectedType.arguments().get(i), actualType.arguments().get(i))) {
                    return false;
                }
            }
            return true;
        }

        ClassStatement actualClass = classes.get(actualType.baseName());
        while (actualClass != null && actualClass.superclass() != null) {
            if (actualClass.superclass().lexeme().equals(expectedType.baseName())) {
                return true;
            }
            actualClass = classes.get(actualClass.superclass().lexeme());
        }

        return false;
    }

    private void validateFunctionTypeArguments(Token callSite, FunctionStatement functionDefinition, List<Token> typeArguments) {
        int expected = functionDefinition.genericParameters().size();
        if (typeArguments.isEmpty()) {
            return;
        }

        if (expected != typeArguments.size()) {
            TVScript.compileError(new CompileError(callSite,
                    "Type argument count mismatch for function '" + functionDefinition.name().lexeme()
                            + "': expected " + expected + ", got " + typeArguments.size() + "."));
        }
    }

    private void validateGenericTypeConstraints(Token typeToken) {
        ParsedNamedType declaredType = parseNamedType(typeToken.lexeme());
        ClassStatement classStatement = classes.get(declaredType.baseName());
        if (classStatement == null) {
            return;
        }

        if (declaredType.arguments().isEmpty()) {
            return;
        }

        validateTypeArgumentsAgainstParameters(typeToken, classStatement.name().lexeme(), classStatement.genericParameters(), declaredType.arguments());
    }

    private void validateClassTypeArguments(Token callSite, ClassStatement classStatement, List<Token> typeArguments) {
        if (typeArguments.isEmpty()) {
            return;
        }

        List<String> argumentNames = new ArrayList<>();
        for (Token argument : typeArguments) {
            argumentNames.add(argument.lexeme());
        }
        validateTypeArgumentsAgainstParameters(callSite, classStatement.name().lexeme(), classStatement.genericParameters(), argumentNames);
    }

    private void validateTypeArgumentsAgainstParameters(Token callSite, String ownerName, List<GenericParameter> parameters, List<String> argumentNames) {
        if (parameters.size() != argumentNames.size()) {
            TVScript.compileError(new CompileError(callSite,
                    "Type argument count mismatch for '" + ownerName + "': expected " + parameters.size()
                            + ", got " + argumentNames.size() + "."));
            return;
        }

        for (int i = 0; i < parameters.size(); i++) {
            GenericParameter parameter = parameters.get(i);
            EffectiveGenericConstraint effectiveConstraint = resolveGenericConstraints(parameter);
            if (effectiveConstraint == null) {
                continue;
            }
            ParsedNamedType argumentType = parseNamedType(argumentNames.get(i));
            String argumentBase = argumentType.baseName();

            if (effectiveConstraint.superclassConstraint() != null
                    && !isAssignableNamedType(effectiveConstraint.superclassConstraint().lexeme(), argumentBase)) {
                TVScript.compileError(new CompileError(callSite,
                        "Type argument '" + argumentNames.get(i) + "' violates constraint for '"
                                + parameter.name().lexeme() + "'."));
                continue;
            }

            for (Token traitConstraint : effectiveConstraint.traitConstraints()) {
                if (!classImplementsTrait(argumentBase, traitConstraint.lexeme())) {
                    TVScript.compileError(new CompileError(callSite,
                            "Type argument '" + argumentNames.get(i) + "' violates constraint for '"
                                    + parameter.name().lexeme() + "': missing trait '"
                                    + traitConstraint.lexeme() + "'."));
                }
            }
        }
    }

    private EffectiveGenericConstraint resolveGenericConstraints(GenericParameter parameter) {
        List<Token> resolvedTraits = new ArrayList<>(parameter.traitConstraints());
        Token resolvedSuperclass = null;
        if (parameter.superclassConstraint() != null) {
            EffectiveGenericConstraint resolvedConstraintReference =
                    resolveConstraintReference(parameter.superclassConstraint(), new HashSet<>());
            if (resolvedConstraintReference == null) {
                return null;
            }
            resolvedSuperclass = resolvedConstraintReference.superclassConstraint();
            resolvedTraits.addAll(resolvedConstraintReference.traitConstraints());
        }
        return new EffectiveGenericConstraint(resolvedSuperclass, resolvedTraits);
    }

    private EffectiveGenericConstraint resolveConstraintAlias(ConstraintStatement constraint, Set<String> visited) {
        if (constraint == null) {
            return null;
        }

        String name = constraint.name().lexeme();
        if (!visited.add(name)) {
            TVScript.compileError(new CompileError(constraint.name(),
                    "Circular constraint reference detected for '" + name + "'."));
            return null;
        }

        List<Token> resolvedTraits = new ArrayList<>(constraint.traitConstraints());
        Token resolvedSuperclass = null;
        if (constraint.superclassConstraint() != null) {
            EffectiveGenericConstraint resolvedParent =
                    resolveConstraintReference(constraint.superclassConstraint(), visited);
            if (resolvedParent == null) {
                return null;
            }
            resolvedSuperclass = resolvedParent.superclassConstraint();
            resolvedTraits.addAll(resolvedParent.traitConstraints());
        }

        return new EffectiveGenericConstraint(resolvedSuperclass, resolvedTraits);
    }

    private EffectiveGenericConstraint resolveConstraintReference(Token constraintOrClassName, Set<String> visited) {
        String name = constraintOrClassName.lexeme();
        ConstraintStatement constraint = constraints.get(name);
        if (constraint == null) {
            if (!classes.containsKey(name)) {
                TVScript.compileError(new CompileError(constraintOrClassName,
                        "Unknown class or constraint '" + name + "'."));
                return null;
            }
            return new EffectiveGenericConstraint(constraintOrClassName, List.of());
        }

        return resolveConstraintAlias(constraint, visited);
    }

    private record EffectiveGenericConstraint(Token superclassConstraint, List<Token> traitConstraints) {}

    private boolean classImplementsTrait(String className, String traitName) {
        ClassStatement current = classes.get(className);
        while (current != null) {
            for (Token traitToken : current.traits()) {
                if (traitToken.lexeme().equals(traitName) || traitExtendsTrait(traitToken.lexeme(), traitName, new HashSet<>())) {
                    return true;
                }
            }

            if (current.superclass() == null) {
                break;
            }
            current = classes.get(current.superclass().lexeme());
        }
        return false;
    }

    private boolean traitExtendsTrait(String traitName, String expectedTrait, Set<String> visited) {
        if (!visited.add(traitName)) {
            return false;
        }

        TraitStatement trait = traits.get(traitName);
        if (trait == null) {
            return false;
        }

        for (Token superTrait : trait.traits()) {
            if (superTrait.lexeme().equals(expectedTrait)
                    || traitExtendsTrait(superTrait.lexeme(), expectedTrait, visited)) {
                return true;
            }
        }

        return false;
    }

    private ParsedNamedType parseNamedType(String rawTypeName) {
        int angleStart = rawTypeName.indexOf('<');
        int squareStart = rawTypeName.indexOf('[');

        int bracketStart;
        int endIndex;
        if (angleStart >= 0 && rawTypeName.endsWith(">")) {
            bracketStart = angleStart;
            endIndex = rawTypeName.length() - 1;
        } else if (squareStart >= 0 && rawTypeName.endsWith("]")) {
            bracketStart = squareStart;
            endIndex = rawTypeName.length() - 1;
        } else {
            return new ParsedNamedType(rawTypeName.trim(), List.of());
        }

        String baseName = rawTypeName.substring(0, bracketStart).trim();
        String argumentsText = rawTypeName.substring(bracketStart + 1, endIndex).trim();
        if (argumentsText.isEmpty()) {
            return new ParsedNamedType(baseName, List.of());
        }

        return new ParsedNamedType(baseName, splitTopLevel(argumentsText));
    }

    private List<String> splitTopLevel(String value) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '<' || c == '[') {
                depth++;
            } else if (c == '>' || c == ']') {
                depth--;
            }

            if (c == ',' && depth == 0) {
                parts.add(current.toString().trim());
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }

        return parts;
    }

    private record ParsedNamedType(String baseName, List<String> arguments) {}

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

    private void checkTypeTraitImplementations(TypeStatement stmt) {
        Map<String, Token> availableMethods = new HashMap<>();
        Map<String, List<String>> traitProviders = new HashMap<>();

        for (Token traitToken : stmt.traits()) {
            TraitStatement trait = traits.get(traitToken.lexeme());
            if (trait != null) {
                collectTraitMethods(trait, availableMethods, traitProviders);
            }
        }

        Set<String> typeMethods = new HashSet<>();
        for (FunctionStatement method : stmt.methods()) {
            typeMethods.add(method.name().lexeme());
        }

        for (Map.Entry<String, List<String>> entry : traitProviders.entrySet()) {
            String methodName = entry.getKey();
            List<String> providers = entry.getValue();

            if (providers.size() > 1 && !typeMethods.contains(methodName)) {
                TVScript.compileError(new CompileError(stmt.name(),
                        "Type '" + stmt.name().lexeme() + "' must override method '" + methodName +
                                "' because it is provided by multiple traits: " + providers));
            }
        }

        Map<String, AbstractMethodInfo> abstractMethods = new HashMap<>();
        collectAbstractTraitMethods(stmt, abstractMethods);
        for (Map.Entry<String, AbstractMethodInfo> entry : abstractMethods.entrySet()) {
            if (!typeMethods.contains(entry.getKey())) {
                AbstractMethodInfo info = entry.getValue();
                TVScript.compileError(new CompileError(stmt.name(),
                        "Type '" + stmt.name().lexeme() + "' must implement method '" + entry.getKey() + "' from trait " + info.traitName() + "."));
            }
        }
    }

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

    private void collectAbstractTraitMethods(TypeStatement stmt, Map<String, AbstractMethodInfo> abstractMethods) {
        for (Token traitToken : stmt.traits()) {
            TraitStatement trait = traits.get(traitToken.lexeme());
            if (trait != null) {
                collectAbstractMethodsFromTrait(trait, abstractMethods);
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
        declare(name, type, null, isConst, null);
    }

    private void declare(Token name, TokenType type, boolean isConst, TokenType returnType) {
        declare(name, type, null, isConst, returnType);
    }

    private void declare(Token name, TokenType type, String namedType, boolean isConst) {
        declare(name, type, namedType, isConst, null);
    }

    private void declare(Token name, TokenType type, String namedType, boolean isConst, TokenType returnType) {
        if (scopes.isEmpty()) return;
        
        // Redefinition in the same scope is always an error
        Map<String, VariableStaticInfo> scope = scopes.get(scopes.size() - 1);
        if (scope.containsKey(name.lexeme())) {
            TVScript.compileError(new CompileError(name, "Variable '" + name.lexeme() + "' is already defined in this scope."));
            return;
        }

        scope.put(name.lexeme(), new VariableStaticInfo(type, isConst, returnType, namedType));
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
