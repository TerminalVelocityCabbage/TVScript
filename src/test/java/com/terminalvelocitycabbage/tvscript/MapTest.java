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

class MapTest {

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
    void testMapCreationGetSetAndSize() {
        run("""
            map[string|integer] ages = new map[|]("brad": 20, "sam": 21)
            print ages.size
            print ages["brad"]

            ages["brad"] = 22
            print ages["brad"]
            """);

        assertOutput("""
            2
            20
            22
            """);
    }

    @Test
    void testMapHelpers() {
        run("""
            map[string|integer] ages = new map[|]("brad": 20, "sam": 21)

            print ages.containsKey("brad")
            print ages.containsKey("nobody")

            integer removed = ages.remove("sam")
            print removed
            print ages.size

            list[string] keys = ages.keys()
            list[integer] values = ages.values()
            print keys.size
            print values.size
            """);

        assertOutput("""
            true
            false
            21
            1
            1
            1
            """);
    }

    @Test
    void testMapMissingKeyThrowsHelpfulError() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> run("""
            map[string|integer] ages = new map[|]("brad": 20)
            print ages["sam"]
            """));

        assertEquals("Map key not found: sam.", error.getMessage());
    }

    @Test
    void testLoopOverMapKeyValuePairs() {
        run("""
            map[string|integer] ages = new map[|]("brad": 20, "sam": 21)
            integer total = 0
            integer count = 0
            boolean sawBrad = false
            boolean sawSam = false

            for [string name | integer age] in ages:
                if name == "brad":
                    sawBrad = true
                if name == "sam":
                    sawSam = true

                total = total + age
                count = count + 1

            print sawBrad
            print sawSam
            print count
            print total
            """);

        assertOutput("""
            true
            true
            2
            41
            """);
    }
}