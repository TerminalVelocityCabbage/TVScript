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

    public CompilationResult compile(String source) {
        CompilationContext context = new CompilationContext(
                reporter,
                interpreter.getNativeFunctions(),
                interpreter.getEnvironment().getNativeClasses()
        );
        Scanner scanner = new Scanner(source, context);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens, context);
        List<Statement> statements = parser.parseStatements();

        if (!reporter.hasError()) {
            TypeChecker typeChecker = new TypeChecker(context);
            typeChecker.check(statements);
        }

        return new CompilationResult(statements, reporter);
    }

    public CompilationResult runScript(String source) {
        CompilationResult result = compile(source);

        if (result.hasErrors()) {
            return result;
        }

        interpreter.interpret(result.statements());
        return result;
    }

    /**
     * Convenience static method to run a script with a fresh interpreter.
     * Note: This does not share state and is for simple usage.
     */
    public static void run(String source) {
        TVScript tv = new TVScript();
        tv.runScript(source);
    }

    /**
     * Convenience static method to run a script with a specific interpreter.
     */
    public static void run(String source, Interpreter interpreter) {
        TVScript tv = new TVScript(interpreter);
        tv.runScript(source);
    }

    public Interpreter getInterpreter() {
        return interpreter;
    }

    public DiagnosticReporter getReporter() {
        return reporter;
    }
}
