package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.execution.Environment;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.stdlib.NativeFunctions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetTest {

    private Interpreter interpreter;
    private static Environment globalEnvironment;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeAll
    static void setupGlobalEnvironment() {
        globalEnvironment = new Environment.GlobalBuilder()
                .withNativeFunctions(NativeFunctions.getAll())
                .build();
    }

    @BeforeEach
    void setUp() {
        interpreter = new Interpreter(globalEnvironment);
        System.setOut(new PrintStream(outContent));
    }

    private void run(String source) {
        TVScript.run(source, interpreter);
    }

    private void assertOutput(String expected) {
        assertEquals(expected.trim(), outContent.toString().replace("\r\n", "\n").trim());
    }

    @Test
    void testSetCreationSizeAndContains() {
        run("""
            set[integer] values = new set[](1, 2, 2, 3)
            print values.size
            print values.contains(2)
            print values.contains(99)
            """);

        assertOutput("""
            3
            true
            false
            """);
    }

    @Test
    void testSetTransformationsAndNoOpRemoveMissing() {
        run("""
            set[integer] values = new set[](1, 2, 3)

            values.add(3)
            values.add(4)
            print values.size

            values.remove(99)
            print values.size

            values.remove(2)
            print values.contains(2)

            values.clear()
            print values.size
            """);

        assertOutput("""
            4
            4
            false
            0
            """);
    }

    @Test
    void testLoopOverSetWithoutOrderAssumptions() {
        run("""
            set[integer] values = new set[](1, 2, 3)
            integer seen = 0
            integer total = 0

            for [integer value] in values:
                seen = seen + 1
                total = total + value

            print seen
            print total
            """);

        assertOutput("""
            3
            6
            """);
    }
}