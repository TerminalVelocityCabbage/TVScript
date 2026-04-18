package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.analysis.TypeChecker;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.execution.TVScriptFunction;
import com.terminalvelocitycabbage.tvscript.parsing.Parser;
import com.terminalvelocitycabbage.tvscript.parsing.Scanner;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

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

    public static boolean hadError = false;
    public static boolean hadRuntimeError = false;
    private static CompileError firstCompileError = null;
    private static final Interpreter interpreter = new Interpreter();

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: tvscript [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        try {
            run(new String(bytes, Charset.defaultCharset()));
        } catch (CompileError error) {
            System.exit(65);
        } catch (RuntimeError error) {
            System.exit(70);
        }

        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            try {
                run(line);
            } catch (CompileError | RuntimeError error) {
                // Already reported.
            }
            hadError = false;
        }
    }

    public static void run(String source) {
        run(source, new Interpreter());
    }

    public static void run(String source, Interpreter interpreter) {
        hadError = false;
        hadRuntimeError = false;
        firstCompileError = null;

        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Statement> statements = parser.parseStatements();

        // Stop if there was a syntax error.
        if (hadError) {
            if (firstCompileError != null) throw firstCompileError;
            throw new CompileError(null, "Unknown syntax error");
        }

        TypeChecker typeChecker = new TypeChecker(interpreter.getNativeFunctions());
        typeChecker.check(statements);

        // Stop if there was a static analysis error.
        if (hadError) {
            if (firstCompileError != null) throw firstCompileError;
            throw new CompileError(null, "Unknown compilation error");
        }

        interpreter.interpret(statements);

        // Execute main if it exists
        Object main = null;
        try {
            main = interpreter.getEnvironment().get(new Token(TokenType.MAIN, "main", null, 0));
        } catch (RuntimeError e) {
            // Main not defined, that's okay for some scripts
        }

        if (main instanceof TVScriptFunction) {
            ((TVScriptFunction) main).call(interpreter, java.util.Collections.emptyMap(), new Token(TokenType.MAIN, "main", null, 0));
        }
    }

    /**
     * Reports an error at a specific line.
     * @param line The line number.
     * @param message The error message.
     */
    public static void error(int line, String message) {
        if (firstCompileError == null) firstCompileError = new CompileError(new Token(TokenType.NONE, "", null, line), message);
        report(line, "", message);
    }

    /**
     * Reports an error at a specific token.
     * @param token The token where the error occurred.
     * @param message The error message.
     */
    public static void error(Token token, String message) {
        if (firstCompileError == null) firstCompileError = new CompileError(token, message);
        if (token.type() == TokenType.EOF) {
            report(token.line(), " at end", message);
        } else {
            report(token.line(), " at '" + token.lexeme() + "'", message);
        }
    }

    /**
     * Reports a runtime error.
     * @param error The runtime error.
     */
    public static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() +
                "\n[line " + error.token.line() + "]");
        hadRuntimeError = true;
    }

    /**
     * Reports a compilation error.
     * @param error The compilation error.
     */
    public static void compileError(CompileError error) {
        if (firstCompileError == null) firstCompileError = error;
        if (error.token.type() == TokenType.EOF) {
            report(error.token.line(), " at end", error.getMessage());
        } else {
            report(error.token.line(), " at '" + error.token.lexeme() + "'", error.getMessage());
        }
    }

    /**
     * Reports a warning.
     * @param token The token where the warning occurred.
     * @param message The warning message.
     */
    public static void warning(Token token, String message) {
        if (token.type() == TokenType.EOF) {
            System.err.println("[line " + token.line() + "] Warning at end: " + message);
        } else {
            System.err.println("[line " + token.line() + "] Warning at '" + token.lexeme() + "': " + message);
        }
    }

    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }
}
