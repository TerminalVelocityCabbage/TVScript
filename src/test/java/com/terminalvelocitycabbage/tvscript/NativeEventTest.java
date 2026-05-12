package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvevents.Event;
import com.terminalvelocitycabbage.tvevents.EventBus;
import com.terminalvelocitycabbage.tvscript.execution.Environment;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.execution.NativeClass;
import com.terminalvelocitycabbage.tvscript.execution.TVEventsEventSystem;
import com.terminalvelocitycabbage.tvscript.execution.TVType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeEventTest {

    public static class TestNativeEvent implements Event {
        private final String message;
        private final int value;

        public TestNativeEvent(String message, int value) {
            this.message = message;
            this.value = value;
        }

        public String getMessage() { return message; }
        public int getValue() { return value; }
    }

    private Interpreter interpreter;
    private ByteArrayOutputStream outputStream;
    private EventBus eventBus;

    @BeforeEach
    public void setUp() {
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        eventBus = new EventBus();
        TVEventsEventSystem eventSystem = new TVEventsEventSystem(eventBus);

        NativeClass testEventClass = NativeClass.builder("TestNativeEvent", TestNativeEvent.class)
                .constructor(NativeClass.params(
                        NativeClass.param("message", TVType.STRING),
                        NativeClass.param("value", TVType.INTEGER)
                ), args -> new TestNativeEvent((String) args.get("message"), (int) args.get("value")))
                .property("message", TVType.STRING, TestNativeEvent::getMessage, (e, v) -> {})
                .property("value", TVType.INTEGER, TestNativeEvent::getValue, (e, v) -> {})
                .build();

        Environment globalEnvironment = new Environment.GlobalBuilder()
                .withClass(testEventClass)
                .build();

        interpreter = new Interpreter(globalEnvironment, eventSystem);
    }

    private void run(String source) {
        TVScript.run(source, interpreter);
    }

    private void assertOutput(String expected) {
        assertEquals(expected.trim().replace("\r\n", "\n"), outputStream.toString().trim().replace("\r\n", "\n"));
    }

    @Test
    public void testNativeEventListeningFromJava() {
        run("""
            native class TestNativeEvent:
                pass

            native event TestNativeEvent:
                TestNativeEvent evt
            
            on TestNativeEvent(TestNativeEvent evt):
                print "Received: {evt.message} with value {evt.value}"
            """);

        eventBus.publish(new TestNativeEvent("Hello Java", 42)).now().join();

        assertOutput("Received: Hello Java with value 42");
    }

    @Test
    public void testNativeEventPatternMatching() {
        run("""
            native class TestNativeEvent:
                pass

            native event TestNativeEvent:
                TestNativeEvent evt
            
            on TestNativeEvent(TestNativeEvent evt: evt.value > 50):
                print "Match: {evt.message}"
            
            on TestNativeEvent(TestNativeEvent evt: evt.value <= 50):
                print "No Match: {evt.message}"
            """);

        eventBus.publish(new TestNativeEvent("High", 100)).now().join();
        eventBus.publish(new TestNativeEvent("Low", 10)).now().join();

        assertOutput("Match: High\nNo Match: Low");
    }

    @Test
    public void testDispatchNativeEventFromScript() {
        final int[] javaCount = {0};
        eventBus.subscribe(TestNativeEvent.class).handle(e -> {
            javaCount[0]++;
        });

        run("""
            native class TestNativeEvent:
                pass

            native event TestNativeEvent:
                TestNativeEvent evt
            
            on InitializedEvent:
                TestNativeEvent myEvent = new TestNativeEvent(message: "From Script", value: 123)
                dispatch TestNativeEvent(evt: myEvent)
            
            on TestNativeEvent(TestNativeEvent evt):
                print "Script received: {evt.message}"
            """);

        assertOutput("Script received: From Script");
        assertEquals(1, javaCount[0], "Java listener should have been triggered by script dispatch");
    }
}
