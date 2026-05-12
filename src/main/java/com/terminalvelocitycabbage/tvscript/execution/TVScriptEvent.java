package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvevents.Event;
import java.util.Map;

/**
 * A TVScript event that can be dispatched via the event system.
 */
public class TVScriptEvent implements Event {
    private final String name;
    private final Map<String, Object> fields;

    public TVScriptEvent(String name, Map<String, Object> fields) {
        this.name = name;
        this.fields = fields;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getFields() {
        return fields;
    }
}
