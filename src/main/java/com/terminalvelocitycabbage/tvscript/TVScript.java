package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.ast.Statement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class TVScript {

    static boolean hadError = false;
    static boolean hadRuntimeError = false;
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
        run(new String(bytes, Charset.defaultCharset()));

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
            run(line);
            hadError = false;
        }
    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Statement> statements = parser.parseStatements();

        // Stop if there was a syntax error.
        if (hadError) return;

        TypeChecker typeChecker = new TypeChecker();
        typeChecker.check(statements);

        // Stop if there was a static analysis error.
        if (hadError) return;

        try {
            interpreter.interpret(statements);
        } catch (RuntimeError error) {
            // Already reported.
        }
    }

    static void error(int line, String message) {
        report(line, "", message);
    }

    static void error(Token token, String message) {
        if (token.getType() == TokenType.EOF) {
            report(token.getLine(), " at end", message);
        } else {
            report(token.getLine(), " at '" + token.getLexeme() + "'", message);
        }
    }

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() +
                "\n[line " + error.token.getLine() + "]");
        hadRuntimeError = true;
    }

    static void compileError(CompileError error) {
        if (error.token.getType() == TokenType.EOF) {
            report(error.token.getLine(), " at end", error.getMessage());
        } else {
            report(error.token.getLine(), " at '" + error.token.getLexeme() + "'", error.getMessage());
        }
    }

    static void warning(Token token, String message) {
        if (token.getType() == TokenType.EOF) {
            System.err.println("[line " + token.getLine() + "] Warning at end: " + message);
        } else {
            System.err.println("[line " + token.getLine() + "] Warning at '" + token.getLexeme() + "': " + message);
        }
    }

    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }
}
