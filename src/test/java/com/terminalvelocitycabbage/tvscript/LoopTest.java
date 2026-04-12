package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.ast.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoopTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private void run(String source) {
        TVScript.hadError = false;
        TVScript.hadRuntimeError = false;

        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Statement> statements = parser.parseStatements();
        if (TVScript.hadError) {
            originalOut.println(outContent.toString());
            originalErr.println(errContent.toString());
            throw new RuntimeException("Parse error");
        }

        TypeChecker typeChecker = new TypeChecker();
        typeChecker.check(statements);
        if (TVScript.hadError) {
            originalOut.println(outContent.toString());
            originalErr.println(errContent.toString());
            throw new RuntimeException("Type check error");
        }

        Interpreter interpreter = new Interpreter();
        interpreter.interpret(statements);
    }

    @Test
    void testWhileLoop() {
        run("""
            integer a = 3
            while a > 0:
                print a
                a = a - 1""");
        assertEquals("3\n2\n1\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testForLoop() {
        run("""
            for [integer i] in 1..3:
                print i""");
        assertEquals("1\n2\n3\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testForLoopNoVar() {
        run("""
            for 1..3:
                print "loop\"""");
        assertEquals("loop\nloop\nloop\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testBreak() {
        run("""
            integer a = 0
            while true:
                a = a + 1
                if a == 2: break
                print a""");
        assertEquals("1\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testContinue() {
        run("""
            for [integer i] in 1..3:
                if i == 2:
                    continue
                print i""");
        assertEquals("1\n3\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testForShadowingError() {
        assertThrows(RuntimeException.class, () -> run("""
            integer i = 0
            for [integer i] in 1..3:
                print i"""));
    }

    @Test
    void testInfiniteLoopDetectionConstant() {
        run("""
            if false:
                while true:
                    pass""");
        assertTrue(errContent.toString().contains("Potential infinite loop: constant true condition."));
    }

    @Test
    void testInfiniteLoopDetectionNoMutation() {
        run("""
            if false:
                integer a = 0
                while a < 10:
                    print "hi\"""");
        assertTrue(errContent.toString().contains("Potential infinite loop: condition variables are not mutated in the loop body."));
    }

    @Test
    void testBreakOutsideLoop() {
        assertThrows(RuntimeException.class, () -> run("break"));
    }

    @Test
    void testContinueOutsideLoop() {
        assertThrows(RuntimeException.class, () -> run("continue"));
    }
}
