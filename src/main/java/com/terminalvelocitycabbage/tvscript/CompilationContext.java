package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.errors.DiagnosticReporter;
import com.terminalvelocitycabbage.tvscript.execution.NativeClass;
import com.terminalvelocitycabbage.tvscript.execution.TVScriptNativeFunction;

import java.util.Collection;
import java.util.List;

/**
 * Holds common dependencies for the compilation pipeline (Scanner, Parser, TypeChecker).
 */
public class CompilationContext {

    private final DiagnosticReporter reporter;
    private final Collection<TVScriptNativeFunction> nativeFunctions;
    private final Collection<NativeClass> nativeClasses;

    public CompilationContext(DiagnosticReporter reporter) {
        this(reporter, List.of(), List.of());
    }

    public CompilationContext(DiagnosticReporter reporter, Collection<TVScriptNativeFunction> nativeFunctions, Collection<NativeClass> nativeClasses) {
        this.reporter = reporter;
        this.nativeFunctions = nativeFunctions;
        this.nativeClasses = nativeClasses;
    }

    public DiagnosticReporter getReporter() {
        return reporter;
    }

    public Collection<TVScriptNativeFunction> getNativeFunctions() {
        return nativeFunctions;
    }

    public Collection<NativeClass> getNativeClasses() {
        return nativeClasses;
    }
}
