package com.terminalvelocitycabbage.tvscript.analysis;

import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.errors.DiagnosticReporter;
import com.terminalvelocitycabbage.tvscript.parsing.Token;

import java.util.*;

import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

/**
 * Resolves generic constraints and their aliases.
 */
public class GenericConstraintResolver {

    private final Map<String, ConstraintStatement> constraints;
    private final Map<String, ClassStatement> classes;
    private final DiagnosticReporter reporter;

    public GenericConstraintResolver(Map<String, ConstraintStatement> constraints, Map<String, ClassStatement> classes, DiagnosticReporter reporter) {
        this.constraints = constraints;
        this.classes = classes;
        this.reporter = reporter;
    }

    public EffectiveGenericConstraint resolveGenericConstraints(GenericParameter parameter) {
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

    public EffectiveGenericConstraint resolveConstraintAlias(ConstraintStatement constraint, Set<String> visited) {
        if (constraint == null) {
            return null;
        }

        String name = constraint.name().lexeme();
        if (!visited.add(name)) {
            reporter.compileError(new CompileError(constraint.name(),
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

    public EffectiveGenericConstraint resolveConstraintReference(Token constraintOrClassName, Set<String> visited) {
        String name = constraintOrClassName.lexeme();
        ConstraintStatement constraint = constraints.get(name);
        if (constraint == null) {
            if (!classes.containsKey(name)) {
                reporter.compileError(new CompileError(constraintOrClassName,
                        "Unknown class or constraint '" + name + "'."));
                return null;
            }
            return new EffectiveGenericConstraint(constraintOrClassName, List.of());
        }

        return resolveConstraintAlias(constraint, visited);
    }

    public record EffectiveGenericConstraint(Token superclassConstraint, List<Token> traitConstraints) {}
}
