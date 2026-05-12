package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvevents.EventBus;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.parsing.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the event system using TVEvents.
 */
public class TVEventsEventSystem implements EventSystem {

    private final EventBus eventBus = new EventBus();
    private final Map<String, Statement.EventStatement> eventDefinitions = new HashMap<>();

    public TVEventsEventSystem() {
        eventBus.setStopOnException(true);
    }

    @Override
    public void registerEvent(Statement.EventStatement stmt) {
        eventDefinitions.put(stmt.name().lexeme(), stmt);
    }

    @Override
    public void registerListener(Statement.OnStatement stmt, Interpreter interpreter, Environment closure) {
        eventBus.subscribe(TVScriptEvent.class).handle((event, status) -> {
            if (!event.getName().equals(stmt.eventName().lexeme())) return;

            Environment environment = new Environment(closure);
            
            for (Statement.OnStatement.ListenerParameter param : stmt.parameters()) {
                Object value = event.getFields().get(param.name().lexeme());
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
                ((Statement.BlockStatement)stmt.body()).statements() : List.of(stmt.body()), environment);
        });
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
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }
}
