package com.terminalvelocitycabbage.tvscript.analysis;

import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.errors.DiagnosticReporter;
import com.terminalvelocitycabbage.tvscript.parsing.Token;

import java.util.*;

import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

/**
 * Checks trait implementations for classes and types.
 */
public class TraitImplementationChecker {

    private final Map<String, ClassStatement> classes;
    private final Map<String, TraitStatement> traits;
    private final DiagnosticReporter reporter;

    public TraitImplementationChecker(Map<String, ClassStatement> classes, Map<String, TraitStatement> traits, DiagnosticReporter reporter) {
        this.classes = classes;
        this.traits = traits;
        this.reporter = reporter;
    }

    public boolean classImplementsTrait(String className, String traitName) {
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

    public boolean traitExtendsTrait(String traitName, String expectedTrait, Set<String> visited) {
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

    public void checkTypeTraitImplementations(TypeStatement stmt) {
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
                reporter.compileError(new CompileError(stmt.name(),
                        "Type '" + stmt.name().lexeme() + "' must override method '" + methodName +
                                "' because it is provided by multiple traits: " + providers));
            }
        }

        Map<String, AbstractMethodInfo> abstractMethods = new HashMap<>();
        collectAbstractTraitMethods(stmt, abstractMethods);
        for (Map.Entry<String, AbstractMethodInfo> entry : abstractMethods.entrySet()) {
            if (!typeMethods.contains(entry.getKey())) {
                AbstractMethodInfo info = entry.getValue();
                reporter.compileError(new CompileError(stmt.name(),
                        "Type '" + stmt.name().lexeme() + "' must implement method '" + entry.getKey() + "' from trait " + info.traitName() + "."));
            }
        }
    }

    public void checkTraitImplementations(ClassStatement stmt) {
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
                reporter.compileError(new CompileError(stmt.name(),
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
                reporter.compileError(new CompileError(stmt.name(),
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

        for (Token superTraitToken : trait.traits()) {
            TraitStatement superTrait = traits.get(superTraitToken.lexeme());
            if (superTrait != null) {
                collectAbstractMethodsFromTrait(superTrait, abstractMethods);
            }
        }
    }

    private void collectTraitMethods(TraitStatement trait, Map<String, Token> availableMethods, Map<String, List<String>> traitProviders) {
        for (FunctionStatement method : trait.methods()) {
            String name = method.name().lexeme();
            availableMethods.put(name, method.name());
            traitProviders.computeIfAbsent(name, k -> new ArrayList<>()).add(trait.name().lexeme());
        }

        for (Token superTraitToken : trait.traits()) {
            TraitStatement superTrait = traits.get(superTraitToken.lexeme());
            if (superTrait != null) {
                collectTraitMethods(superTrait, availableMethods, traitProviders);
            }
        }
    }

    private record AbstractMethodInfo(Token name, String traitName) {}
}
