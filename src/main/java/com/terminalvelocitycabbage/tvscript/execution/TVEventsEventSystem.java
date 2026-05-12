package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvevents.EventBus;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.parsing.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Implementation of the event system using TVEvents.
 */
public class TVEventsEventSystem implements EventSystem {

    private final EventBus eventBus;
    private final Map<String, Statement.EventStatement> eventDefinitions = new HashMap<>();

    public TVEventsEventSystem() {
        this(new EventBus());
    }

    public TVEventsEventSystem(EventBus eventBus) {
        this.eventBus = eventBus;
        eventBus.setStopOnException(true);
    }

    @Override
    public void registerEvent(Statement.EventStatement stmt) {
        eventDefinitions.put(stmt.name().lexeme(), stmt);
    }

    @Override
    public void registerListener(Statement.OnStatement stmt, Interpreter interpreter, Environment closure) {
        String eventName = stmt.eventName().lexeme();
        Statement.EventStatement eventDef = eventDefinitions.get(eventName);

        // Common handler logic
        BiConsumer<Map<String, Object>, String> handler = (fields, receivedEventName) -> {
            if (receivedEventName != null && !receivedEventName.equals(eventName)) return;

            Environment environment = new Environment(closure);
            for (Statement.OnStatement.ListenerParameter param : stmt.parameters()) {
                Object value = fields.get(param.name().lexeme());
                value = interpreter.toScriptValue(value);

                if (param.filter() != null) {
                    environment.define(param.name().lexeme(), value, param.type().type(), true);
                    if (!interpreter.isTruthy(null, interpreter.evaluate(param.filter(), environment))) {
                        return;
                    }
                } else {
                    environment.define(param.name().lexeme(), value, param.type().type(), true);
                }
            }

            interpreter.executeBlock(stmt.body() instanceof Statement.BlockStatement ?
                    ((Statement.BlockStatement) stmt.body()).statements() : List.of(stmt.body()), environment);
        };

        // Subscribe to TVScriptEvent (for script-dispatched events)
        eventBus.subscribe(TVScriptEvent.class).handle((event, status) -> {
            Statement.EventStatement def = eventDefinitions.get(event.getName());
            if (def != null && def.isNative()) return; // Ignore native events, they are handled by the native subscription below

            handler.accept(event.getFields(), event.getName());
        });

        // If native, also subscribe to the native Java class (for Java-dispatched events)
        if (eventDef != null && eventDef.isNative()) {
            NativeClass nativeClass = closure.getNativeClass(eventName);
            if (nativeClass != null) {
                eventBus.subscribe((Class<? extends com.terminalvelocitycabbage.tvevents.Event>) nativeClass.javaClass())
                        .handle((event, status) -> {
                            // Map the raw Java object to the fields expected by the script
                            // In a native event, there is exactly one field whose type matches the event name.
                            String fieldName = eventDef.fields().get(0).name().lexeme();
                            Map<String, Object> fields = new HashMap<>();
                            fields.put(fieldName, event);
                            handler.accept(fields, null); // null name means it matched by class
                        });
            }
        }
    }

    @Override
    public void dispatch(Token eventName, List<Expression.Argument> arguments, Interpreter interpreter, Environment environment) {
        Map<String, Object> evaluatedArguments = new HashMap<>();
        for (Expression.Argument arg : arguments) {
            Object value = interpreter.evaluate(arg.value());
            evaluatedArguments.put(arg.name().lexeme(), interpreter.toNativeValue(value));
        }
        dispatch(eventName.lexeme(), evaluatedArguments);
    }

    @Override
    public void dispatch(String eventName, Map<String, Object> arguments) {
        try {
            eventBus.publish(new TVScriptEvent(eventName, arguments)).now().join();

            Statement.EventStatement eventDef = eventDefinitions.get(eventName);
            if (eventDef != null && eventDef.isNative() && !arguments.isEmpty()) {
                // In a native event, there is exactly one field.
                String fieldName = eventDef.fields().get(0).name().lexeme();
                Object nativeEvent = arguments.get(fieldName);
                if (nativeEvent instanceof com.terminalvelocitycabbage.tvevents.Event) {
                    eventBus.publish((com.terminalvelocitycabbage.tvevents.Event) nativeEvent).now().join();
                }
            }
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }
}
