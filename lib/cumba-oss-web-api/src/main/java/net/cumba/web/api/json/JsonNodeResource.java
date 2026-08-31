package net.cumba.web.api.json;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * Default read-only {@link ApiResource} implementation backed by a Jackson {@link JsonNode}.
 *
 * <p>
 * All accessors silently return empty results on missing fields or type mismatches. No exceptions
 * are thrown for structural problems in the JSON.
 * </p>
 *
 * <p>
 * Domain-specific interfaces can be obtained via the static factory {@link #of(JsonNode, Class)},
 * which creates a JDK dynamic proxy that combines this implementation with the default methods of
 * the target interface.
 * </p>
 */
public final class JsonNodeResource implements ApiResource
{

    private final JsonNode node;

    /**
     * Creates a new resource backed by the given JSON node.
     *
     * @param node
     *            the backing JSON node (must not be {@code null})
     */
    public JsonNodeResource(JsonNode node)
    {
        this.node = Objects.requireNonNull(node, "node must not be null");
    }

    // --- Static factories ---


    /**
     * Wraps a JsonNode as a plain {@link ApiResource}.
     */
    public static ApiResource of(JsonNode node)
    {
        return new JsonNodeResource(node);
    }


    /**
     * Wraps a JsonNode as a domain-specific {@link ApiResource} subtype. The returned proxy
     * delegates generic accessors to {@link JsonNodeResource} and domain-specific default methods
     * to the interface itself.
     *
     * @param node
     *            the JSON data
     * @param type
     *            the target interface (must extend {@code ApiResource})
     * @param <T>
     *            the target type
     * @return a proxy instance implementing both {@code T} and {@code ApiResource}
     */
    @SuppressWarnings("unchecked")
    public static <T extends ApiResource> T of(JsonNode node, Class<T> type)
    {
        if (type == ApiResource.class)
        {
            return (T) new JsonNodeResource(node);
        }
        JsonNodeResource delegate = new JsonNodeResource(node);
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]
        {
                type
        }, new ResourceInvocationHandler(delegate, type));
    }

    // --- ApiResource implementation ---


    @Override
    public boolean isString(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isTextual();
    }


    @Override
    public boolean isNumber(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isNumber();
    }


    @Override
    public boolean isBoolean(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isBoolean();
    }


    @Override
    public boolean isObject(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isObject();
    }


    @Override
    public boolean isArray(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isArray();
    }


    @Override
    public boolean isNull(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isNull();
    }


    @Override
    public Set<String> getFieldNames()
    {
        return new AbstractSet<>()
        {

            @Override
            public Iterator<String> iterator()
            {
                return node.fieldNames();
            }


            @Override
            public int size()
            {
                return node.size();
            }


            @Override
            public boolean contains(Object o)
            {
                return o instanceof String s && node.has(s);
            }
        };
    }


    @Override
    public boolean containsFieldName(String aFieldName)
    {
        return node.has(aFieldName);
    }


    @Override
    public int getFieldCount()
    {
        return node.size();
    }


    @Override
    public Optional<String> getString(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isTextual() ? Optional.of(field.asText()) : Optional.empty();
    }


    @Override
    public OptionalInt getInt(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isNumber() ? OptionalInt.of(field.asInt())
                : OptionalInt.empty();
    }


    @Override
    public OptionalLong getLong(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isNumber() ? OptionalLong.of(field.asLong())
                : OptionalLong.empty();
    }


    @Override
    public OptionalDouble getDouble(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isNumber() ? OptionalDouble.of(field.asDouble())
                : OptionalDouble.empty();
    }


    @Override
    public Optional<Boolean> getBoolean(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isBoolean() ? Optional.of(field.asBoolean())
                : Optional.empty();
    }


    @Override
    public Optional<Number> getNumber(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isNumber() ? Optional.of(field.numberValue())
                : Optional.empty();
    }


    @Override
    public <T extends ApiResource> Optional<T> getObject(String fieldName, Class<T> type)
    {
        JsonNode field = node.get(fieldName);
        return field != null && field.isObject() ? Optional.of(of(field, type)) : Optional.empty();
    }


    @Override
    public <T extends ApiArrayResource> Optional<T> getArray(String aFieldName, Class<T> aType)
    {
        JsonNode field = node.get(aFieldName);
        if (field == null || !field.isArray())
        {
            return Optional.empty();
        }
        return Optional.of(JsonArrayResource.of(field, aType));
    }


    @Override
    public <T extends ApiResource> Stream<T> getStream(String fieldName, Class<T> type)
    {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isArray())
        {
            return Stream.empty();
        }
        JsonNode array = field;

        return IntStream.range(0, array.size()).mapToObj(array::get).map(e -> of(e, type));

    }


    @Override
    public <T extends ApiResource> List<T> getList(String fieldName, Class<T> type)
    {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isArray())
        {
            return Collections.emptyList();
        }
        return new AbstractList<>()
        {

            @Override
            public T get(int index)
            {
                JsonNode element = field.get(index);
                return of(element, type);
            }


            @Override
            public int size()
            {
                return field.size();
            }
        };
    }


    @Override
    public List<String> getStringList(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isArray())
        {
            return Collections.emptyList();
        }
        return new AbstractList<>()
        {

            @Override
            public String get(int index)
            {
                return field.get(index).asText();
            }


            @Override
            public int size()
            {
                return field.size();
            }
        };
    }


    @Override
    public Stream<String> getStringStream(String fieldName)
    {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isArray())
        {
            return Stream.empty();
        }

        return IntStream.range(0, field.size())//
                .mapToObj(field::get)//
                .map(JsonNode::asText);
    }


    @Override
    public Optional<Link> getLink(String rel)
    {
        JsonNode linksNode = node.get("_links");
        if (linksNode == null || !linksNode.isObject())
        {
            return Optional.empty();
        }
        JsonNode relNode = linksNode.get(rel);
        if (relNode == null)
        {
            return Optional.empty();
        }
        // Single link object
        if (relNode.isObject())
        {
            return Optional.of(of(relNode, Link.class));
        }
        // Array of links — return the first one
        if (relNode.isArray() && !relNode.isEmpty())
        {
            return Optional.of(of(relNode.get(0), Link.class));
        }
        return Optional.empty();
    }


    @Override
    public List<Link> getLinks(String rel)
    {
        JsonNode linksNode = node.get("_links");
        if (linksNode == null || !linksNode.isObject())
        {
            return Collections.emptyList();
        }
        JsonNode relNode = linksNode.get(rel);
        if (relNode == null)
        {
            return Collections.emptyList();
        }
        // Single link → wrap in singleton list
        if (relNode.isObject())
        {
            return List.of(of(relNode, Link.class));
        }
        // Array of links
        if (relNode.isArray())
        {
            List<Link> result = new ArrayList<>(relNode.size());
            for (JsonNode linkNode : relNode)
            {
                result.add(of(linkNode, Link.class));
            }
            return Collections.unmodifiableList(result);
        }
        return Collections.emptyList();
    }

    // --- Object overrides ---


    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof JsonNodeResource other)
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
        return "JsonNodeResource[" + node + "]";
    }

    // --- Internal helpers ---

    /**
     * InvocationHandler that delegates {@link ApiResource} methods to the {@link JsonNodeResource}
     * instance and interface default methods to the interface itself.
     */
    private record ResourceInvocationHandler(JsonNodeResource delegate,
            Class<? extends ApiResource> targetType) implements InvocationHandler
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

            // Everything else → delegate to JsonNodeResource
            return method.invoke(delegate, args);
        }
    }
}
