package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.analysis.TypeChecker;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.errors.DefaultDiagnosticReporter;
import com.terminalvelocitycabbage.tvscript.errors.DiagnosticReporter;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.parsing.Parser;
import com.terminalvelocitycabbage.tvscript.parsing.Scanner;
import com.terminalvelocitycabbage.tvscript.parsing.Token;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Main entry point for the TVScript language.
 */
public class TVScript {

    private static final DiagnosticReporter globalReporter = new DefaultDiagnosticReporter();
    public static boolean hadError = false;
    public static boolean hadRuntimeError = false;

    private final DiagnosticReporter reporter;
    private final Interpreter interpreter;

    public TVScript() {
        this(new DefaultDiagnosticReporter());
    }

    public TVScript(DiagnosticReporter reporter) {
        this.reporter = reporter;
        this.interpreter = new Interpreter(reporter);
    }

    public TVScript(Interpreter interpreter) {
        this.interpreter = interpreter;
        this.reporter = interpreter.getReporter();
    }

    public static void main(String[] args) throws IOException {
        TVScript tvScript = new TVScript();
        if (args.length > 1) {
            System.out.println("Usage: tvscript [script]");
            System.exit(64);
        } else if (args.length == 1) {
            tvScript.runFile(args[0]);
        } else {
            tvScript.runPrompt();
        }
    }

    public void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        try {
            runScript(new String(bytes, Charset.defaultCharset()));
        } catch (CompileError error) {
            System.exit(65);
        } catch (RuntimeError error) {
            System.exit(70);
        }

        if (reporter.hasError()) System.exit(65);
        if (reporter.hasRuntimeError()) System.exit(70);
    }

    public void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            try {
                runScript(line);
            } catch (CompileError | RuntimeError error) {
                // Already reported.
            }
            reporter.reset();
        }
    }

    public void runScript(String source) {
        Scanner scanner = new Scanner(source, reporter);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens, reporter);
        List<Statement> statements = parser.parseStatements();

        // Stop if there was a syntax error.
        if (reporter.hasError()) {
            CompileError first = reporter.getFirstCompileError();
            if (first != null) throw first;
            return;
        }

        TypeChecker typeChecker = new TypeChecker(interpreter.getNativeFunctions(), interpreter.getEnvironment().getNativeClasses(), reporter);
        typeChecker.check(statements);

        // Stop if there was a static analysis error.
        if (reporter.hasError()) {
            CompileError first = reporter.getFirstCompileError();
            if (first != null) throw first;
            return;
        }

        interpreter.interpret(statements);
    }

    /**
     * Convenience static method to run a script with a fresh interpreter.
     * Note: This does not share state and is for simple usage.
     */
    public static void run(String source) {
        TVScript tv = new TVScript();
        tv.runScript(source);
        hadError = tv.getReporter().hasError();
        hadRuntimeError = tv.getReporter().hasRuntimeError();
    }

    /**
     * Convenience static method to run a script with a specific interpreter.
     */
    public static void run(String source, Interpreter interpreter) {
        TVScript tv = new TVScript(interpreter);
        tv.runScript(source);
        hadError = tv.getReporter().hasError();
        hadRuntimeError = tv.getReporter().hasRuntimeError();
    }

    public Interpreter getInterpreter() {
        return interpreter;
    }

    public DiagnosticReporter getReporter() {
        return reporter;
    }
}
