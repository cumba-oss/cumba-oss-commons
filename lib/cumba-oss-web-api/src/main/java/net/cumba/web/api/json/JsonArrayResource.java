package net.cumba.web.api.json;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Stream;
import lombok.NonNull;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;

/**
 * Default read-only {@link ApiArrayResource} implementation backed by a Jackson {@link JsonNode}.
 *
 * <p>
 * All accessors silently return empty results on out-of-bounds indices or type mismatches. No
 * exceptions are thrown for structural problems in the JSON.
 * </p>
 *
 * <p>
 * Domain-specific array interfaces can be obtained via the static factory
 * {@link #of(JsonNode, Class)}, which creates a JDK dynamic proxy that combines this implementation
 * with the default methods of the target interface.
 * </p>
 */
public final class JsonArrayResource implements ApiArrayResource
{

    private final JsonNode node;

    /**
     * Creates a new array resource backed by the given array node.
     *
     * @param aNode
     *            the backing JSON array node (must not be {@code null})
     */
    public JsonArrayResource(@NonNull JsonNode aNode)
    {
        node = aNode;
    }

    // --- Static factories ---


    /**
     * Wraps an {@link JsonNode} as a plain {@link ApiArrayResource}.
     *
     * @param node
     *            the JSON array node
     * @return a new array resource
     */
    public static ApiArrayResource of(JsonNode node)
    {
        return new JsonArrayResource(node);
    }


    /**
     * Wraps an {@link JsonNode} as a domain-specific {@link ApiArrayResource} subtype. The returned
     * proxy delegates generic accessors to {@link JsonArrayResource} and domain-specific default
     * methods to the interface itself.
     *
     * @param node
     *            the JSON array data
     * @param type
     *            the target interface (must extend {@code ApiArrayResource})
     * @param <T>
     *            the target type
     * @return a proxy instance implementing both {@code T} and {@code ApiArrayResource}
     */
    @SuppressWarnings("unchecked")
    public static <T extends ApiArrayResource> T of(JsonNode node, Class<T> type)
    {
        if (type == ApiArrayResource.class)
        {
            return (T) new JsonArrayResource(node);
        }
        JsonArrayResource delegate = new JsonArrayResource(node);
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]
        {
                type
        }, new ArrayResourceInvocationHandler(delegate, type));
    }

    // --- ApiArrayResource implementation ---


    @Override
    public int getLength()
    {
        return node.size();
    }


    @Override
    public boolean isString(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        return element != null && element.isTextual();
    }


    @Override
    public Optional<String> getString(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        if (element == null || !element.isTextual())
        {
            return Optional.empty();
        }
        return Optional.of(element.asText());
    }


    @Override
    public boolean isInt(int aIndex)
    {
        // Broad "is-numeric" intent (mirrors test contract: isInt(3.14) is true).
        // Delegates to isNumber so the loose semantics live in one place; the strict
        // typed predicates are isLong (canConvertToLong) and isDouble (isFloatingPointNumber).
        return isNumber(aIndex);
    }


    @Override
    public OptionalInt getInt(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        if (element == null || !element.isNumber())
        {
            return OptionalInt.empty();
        }
        return OptionalInt.of(element.asInt());
    }


    @Override
    public boolean isLong(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        return element != null && element.isIntegralNumber() && element.canConvertToLong();
    }


    @Override
    public OptionalLong getLong(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        if (element == null || !element.isNumber())
        {
            return OptionalLong.empty();
        }
        return OptionalLong.of(element.asLong());
    }


    @Override
    public boolean isDouble(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        return element != null && element.isFloatingPointNumber();
    }


    @Override
    public OptionalDouble getDouble(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        if (element == null || !element.isNumber())
        {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(element.asDouble());
    }


    @Override
    public boolean isBoolean(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        return element != null && element.isBoolean();
    }


    @Override
    public Optional<Boolean> getBoolean(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        if (element == null || !element.isBoolean())
        {
            return Optional.empty();
        }
        return Optional.of(element.asBoolean());
    }


    @Override
    public boolean isNumber(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        return element != null && element.isNumber();
    }


    @Override
    public Optional<Number> getNumber(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        if (element == null || !element.isNumber())
        {
            return Optional.empty();
        }
        return Optional.of(element.numberValue());
    }


    @Override
    public boolean isObject(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        return element != null && element.isObject();
    }


    @Override
    public <T extends ApiResource> Optional<T> getObject(int aIndex, Class<T> aType)
    {
        JsonNode element = node.get(aIndex);
        if (element == null || !element.isObject())
        {
            return Optional.empty();
        }
        return Optional.of(JsonNodeResource.of(element, aType));
    }


    @Override
    public boolean isArray(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        return element != null && element.isArray();
    }


    @Override
    public <T extends ApiArrayResource> Optional<T> getArray(int aIndex, Class<T> aType)
    {
        JsonNode element = node.get(aIndex);
        if (element == null || !element.isArray())
        {
            return Optional.empty();
        }
        return Optional.of(of(element, aType));
    }


    @Override
    public List<String> getStringList(int aIndex)
    {
        JsonNode field = node.get(aIndex);
        if (field == null || !field.isArray())
        {
            return Collections.emptyList();
        }
        JsonNode array = field;
        return new AbstractList<>()
        {

            @Override
            public String get(int index)
            {
                return array.get(index).asText();
            }


            @Override
            public int size()
            {
                return array.size();
            }
        };
    }


    @Override
    public Stream<String> getStringStream(int aIndex)
    {
        return getStringList(aIndex).stream();
    }

    // --- Object overrides ---


    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof JsonArrayResource other)
        {
            return node.equals(other.node);
        }
        return false;
    }


    @Override
    public int hashCode()
    {
        return node.hashCode();
    }


    @Override
    public String toString()
    {
        return "JsonArrayResource[" + node + "]";
    }

    // --- Internal helpers ---

    /**
     * Invocation handler that delegates {@link ApiArrayResource} methods to the
     * {@link JsonArrayResource} instance and interface default methods to the interface itself.
     */
    private record ArrayResourceInvocationHandler(JsonArrayResource delegate,
            Class<? extends ApiArrayResource> targetType) implements InvocationHandler
    {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            // Object methods
            if (method.getDeclaringClass() == Object.class)
            {
                return switch (method.getName())
                {
                case "equals" -> delegate.equals(args[0]);
                case "hashCode" -> delegate.hashCode();
                case "toString" -> targetType.getSimpleName() + "[" + delegate.node + "]";
                default -> method.invoke(delegate, args);
                };
            }

            // Default methods on the domain interface
            if (method.isDefault())
            {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

            // Everything else → delegate to JsonArrayResource
            return method.invoke(delegate, args);
        }
    }
}
