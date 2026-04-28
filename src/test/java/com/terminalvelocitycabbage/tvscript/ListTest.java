package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.execution.Environment;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.stdlib.NativeFunctions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListTest {

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
    void testListCreationAndSize() {
        run("""
            list[integer] empty = new list[]
            list[integer] withPlaceholders = new list[3]
            list[integer] filled = new list[](1, 2, 3)

            print empty.size
            print withPlaceholders.size
            print withPlaceholders[0] == none
            print filled.size
            """);

        assertOutput("""
            0
            3
            true
            3
            """);
    }

    @Test
    void testListIndexingAssignmentAndSlices() {
        run("""
            list[integer] values = new list[](1, 2, 3, 4, 5)
            print values[0]
            print values[-1]

            values[-1] = 10
            print values[4]

            list[integer] middle = values[1..3]
            list[integer] fromStart = values[..2]
            list[integer] toEnd = values[2..]

            print middle.size
            print middle[0]
            print middle[2]
            print fromStart.size
            print toEnd.size
            """);

        assertOutput("""
            1
            5
            10
            3
            2
            4
            3
            3
            """);
    }

    @Test
    void testListTransformations() {
        run("""
            list[integer] values = new list[](1, 2, 3)

            values.add(4)
            values.insert(1, 9)
            print values.size
            print values[1]

            integer removed = values.remove(1)
            integer popped = values.pop()
            print removed
            print popped

            values.reverse()
            print values[0]
            print values.contains(2)

            values.clear()
            print values.size
            """);

        assertOutput("""
            5
            9
            9
            4
            3
            true
            0
            """);
    }

    @Test
    void testListOutOfBoundsErrors() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> run("""
            list[integer] values = new list[](1, 2, 3)
            print values[3]
            """));
        assertEquals("List index 3 is out of bounds for size 3.", error.getMessage());

        error = assertThrows(RuntimeError.class, () -> run("""
            list[integer] otherValues = new list[](1, 2, 3)
            otherValues[3] = 10
            """));
        assertEquals("List index 3 is out of bounds for size 3.", error.getMessage());
    }

    @Test
    void testListNegativeRangeBoundsAreInvalid() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> run("""
            list[integer] values = new list[](1, 2, 3)
            list[integer] invalid = values[-1..1]
            """));
        assertEquals("List range bounds must be non-negative.", error.getMessage());
    }

    @Test
    void testLoopOverListValues() {
        run("""
            list[integer] values = new list[](1, 2, 3, 4)
            integer total = 0
            integer count = 0

            for [integer value] in values:
                total = total + value
                count = count + 1

            print count
            print total
            """);

        assertOutput("""
            4
            10
            """);
    }
}