package com.terminalvelocitycabbage.tvscript.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class NativeClass {

    @FunctionalInterface
    public interface NativeConstructor<T> {
        T create(Map<String, Object> arguments);
    }

    @FunctionalInterface
    public interface NativeMethod<T> {
        Object invoke(T self, Map<String, Object> arguments);
    }

    public record Parameter(String name, TVType type) {
        public Parameter {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Parameter name cannot be blank.");
            }
            if (type == null) {
                throw new IllegalArgumentException("Parameter type cannot be null.");
            }
        }
    }

    public static final class ConstructorBinding<T> {
        private final List<Parameter> parameters;
        private final NativeConstructor<T> constructor;

        private ConstructorBinding(List<Parameter> parameters, NativeConstructor<T> constructor) {
            this.parameters = List.copyOf(parameters);
            this.constructor = constructor;
        }

        public List<Parameter> parameters() {
            return parameters;
        }

        public Object create(Map<String, Object> arguments) {
            return constructor.create(arguments);
        }
    }

    public static final class PropertyBinding<T> {
        private final String name;
        private final TVType type;
        private final Function<T, ?> getter;
        private final BiConsumer<T, ?> setter;

        private PropertyBinding(String name, TVType type, Function<T, ?> getter, BiConsumer<T, ?> setter) {
            this.name = name;
            this.type = type;
            this.getter = getter;
            this.setter = setter;
        }

        public String name() {
            return name;
        }

        public TVType type() {
            return type;
        }

        @SuppressWarnings("unchecked")
        public Object get(Object instance) {
            return getter.apply((T) instance);
        }

        @SuppressWarnings("unchecked")
        public void set(Object instance, Object value) {
            ((BiConsumer<T, Object>) setter).accept((T) instance, value);
        }
    }

    public static final class ConstantBinding {
        private final String name;
        private final TVType type;
        private final Object value;

        private ConstantBinding(String name, TVType type, Object value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }

        public String name() {
            return name;
        }

        public TVType type() {
            return type;
        }

        public Object value() {
            return value;
        }
    }

    public static final class MethodBinding<T> {
        private final String name;
        private final List<Parameter> parameters;
        private final TVType returnType;
        private final NativeMethod<T> method;

        private MethodBinding(String name, List<Parameter> parameters, TVType returnType, NativeMethod<T> method) {
            this.name = name;
            this.parameters = List.copyOf(parameters);
            this.returnType = returnType;
            this.method = method;
        }

        public String name() {
            return name;
        }

        public List<Parameter> parameters() {
            return parameters;
        }

        public TVType returnType() {
            return returnType;
        }

        @SuppressWarnings("unchecked")
        public Object invoke(Object self, Map<String, Object> arguments) {
            return method.invoke((T) self, arguments);
        }
    }

    private final String scriptName;
    private final Class<?> javaClass;
    private final List<ConstructorBinding<?>> constructors;
    private final Map<String, PropertyBinding<?>> properties;
    private final Map<String, ConstantBinding> constants;
    private final Map<String, MethodBinding<?>> methods;
    private Map<String, NativeClass> resolvedTypes = null;

    private NativeClass(String scriptName,
                        Class<?> javaClass,
                        List<ConstructorBinding<?>> constructors,
                        Map<String, PropertyBinding<?>> properties,
                        Map<String, ConstantBinding> constants,
                        Map<String, MethodBinding<?>> methods) {
        this.scriptName = scriptName;
        this.javaClass = javaClass;
        this.constructors = List.copyOf(constructors);
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        this.constants = Collections.unmodifiableMap(new LinkedHashMap<>(constants));
        this.methods = Collections.unmodifiableMap(new LinkedHashMap<>(methods));
    }

    public static <T> Builder<T> builder(String scriptName, Class<T> javaClass) {
        return new Builder<>(scriptName, javaClass);
    }

    public static Parameter param(String name, TVType type) {
        return new Parameter(name, type);
    }

    public static List<Parameter> params(Parameter... parameters) {
        return parameters == null ? List.of() : List.of(parameters);
    }

    public String scriptName() {
        return scriptName;
    }

    public Class<?> javaClass() {
        return javaClass;
    }

    public Collection<ConstructorBinding<?>> constructors() {
        return constructors;
    }

    public Map<String, PropertyBinding<?>> properties() {
        return properties;
    }

    public Map<String, ConstantBinding> constants() {
        return constants;
    }

    public Map<String, MethodBinding<?>> methods() {
        return methods;
    }

    public void resolveReferences(Map<String, NativeClass> registeredClasses) {
        for (ConstructorBinding<?> constructor : constructors) {
            for (Parameter parameter : constructor.parameters()) {
                parameter.type().resolve(this, registeredClasses);
            }
        }
        for (PropertyBinding<?> property : properties.values()) {
            property.type().resolve(this, registeredClasses);
        }
        for (ConstantBinding constant : constants.values()) {
            constant.type().resolve(this, registeredClasses);
        }
        for (MethodBinding<?> method : methods.values()) {
            for (Parameter parameter : method.parameters()) {
                parameter.type().resolve(this, registeredClasses);
            }
            method.returnType().resolve(this, registeredClasses);
        }
        this.resolvedTypes = Map.copyOf(registeredClasses);
    }

    public TVType.ResolvedType resolveType(TVType type) {
        if (resolvedTypes == null) {
            throw new IllegalStateException("Native class '" + scriptName + "' has not been resolved in an environment builder.");
        }
        return type.resolve(this, resolvedTypes);
    }

    public static final class Builder<T> {
        private final String scriptName;
        private final Class<T> javaClass;
        private final List<ConstructorBinding<?>> constructors = new ArrayList<>();
        private final Map<String, PropertyBinding<?>> properties = new LinkedHashMap<>();
        private final Map<String, ConstantBinding> constants = new LinkedHashMap<>();
        private final Map<String, MethodBinding<?>> methods = new LinkedHashMap<>();

        private Builder(String scriptName, Class<T> javaClass) {
            if (scriptName == null || scriptName.isBlank()) {
                throw new IllegalArgumentException("Native class script name cannot be blank.");
            }
            if (javaClass == null) {
                throw new IllegalArgumentException("Java class cannot be null.");
            }
            this.scriptName = scriptName;
            this.javaClass = javaClass;
        }

        public Builder<T> constructor(List<Parameter> parameters, NativeConstructor<T> constructor) {
            if (constructor == null) {
                throw new IllegalArgumentException("Constructor callback cannot be null.");
            }
            constructors.add(new ConstructorBinding<>(parameters == null ? List.of() : parameters, constructor));
            return this;
        }

        public <V> Builder<T> property(String name, TVType type, Function<T, V> getter, BiConsumer<T, V> setter) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Property name cannot be blank.");
            }
            if (type == null || getter == null || setter == null) {
                throw new IllegalArgumentException("Property type/getter/setter cannot be null.");
            }
            properties.put(name, new PropertyBinding<>(name, type, getter, setter));
            return this;
        }

        public <V> Builder<T> constant(String name, TVType type, V value) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Constant name cannot be blank.");
            }
            if (type == null) {
                throw new IllegalArgumentException("Constant type cannot be null.");
            }
            constants.put(name, new ConstantBinding(name, type, value));
            return this;
        }

        public Builder<T> method(String name, List<Parameter> parameters, TVType returnType, NativeMethod<T> method) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Method name cannot be blank.");
            }
            if (returnType == null || method == null) {
                throw new IllegalArgumentException("Method return type and callback cannot be null.");
            }
            methods.put(name, new MethodBinding<>(name, parameters == null ? List.of() : parameters, returnType, method));
            return this;
        }

        public NativeClass build() {
            return new NativeClass(scriptName, javaClass, constructors, properties, constants, methods);
        }
    }
}