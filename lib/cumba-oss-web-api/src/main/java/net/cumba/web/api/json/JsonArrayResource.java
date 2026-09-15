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
        // F-webapi-03 ruling (2026-09-08): "isInt should return false if it is not
        // really an int." True only for an integral number that fits a Java int —
        // the same shape as isLong below, and now consistent with the XML
        // implementations of this interface. (This deliberately replaces the earlier
        // broad is-numeric contract under which isInt(3.14) was true.)
        JsonNode element = node.get(aIndex);
        return element != null && element.isIntegralNumber() && element.canConvertToInt();
    }


    @Override
    public OptionalInt getInt(int aIndex)
    {
        JsonNode element = node.get(aIndex);
        if (element == null || !element.isIntegralNumber() || !element.canConvertToInt())
        {
            // F-webapi-03: no silent narrowing — a double or an out-of-int-range long
            // answers empty, exactly the values isInt rejects.
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
        if (element == null || !element.isIntegralNumber() || !element.canConvertToLong())
        {
            // F-webapi-03: no silent truncation and no wrap-around, and exactly the values
            // isLong above rejects. The old test was only !isNumber(), so 3.99 answered
            // OptionalLong.of(3) - isLong said "not a long" and getLong handed one back anyway -
            // and 9223372036854775808 answered -9223372036854775808, the low 64 bits of a value
            // that does not fit, presented as a valid number.
            return OptionalLong.empty();
        }
        return OptionalLong.of(element.asLong());
    }


    @Override
    public boolean isDouble(int aIndex)
    {
        // Q12 (2026-09-11), strict on both sides: this predicate answers exactly what getDouble
        // below answers, which for a double means "readable as one without loss" rather than
        // "written with a fraction". It used to be isFloatingPointNumber(), so isDouble(42) was
        // false while getDouble(42) handed back 42.0 - the same broken pairing F-webapi-03 found
        // between isLong and getLong, inverted, and a caller guarding on the predicate silently
        // skipped a number it could have read. The other two implementations cannot even express
        // the strict reading: XML text is untyped, and ListResource answers instanceof Number, so
        // this is the only semantics all three can agree on.
        JsonNode element = node.get(aIndex);
        return element != null && element.isNumber();
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


    /**
     * Reads one array element as a string: its text when it is a JSON string, and the empty string
     * otherwise.
     *
     * <p>
     * Q13 (2026-09-11). {@code asText()} used to be called directly, which rendered a JSON
     * {@code null} as the four-character string {@code "null"} and the number {@code 7} as
     * {@code "7"} while an object answered {@code ""} — three different answers to one question,
     * and on the controlled-terminology path a vocabulary that silently gained terms nobody
     * published. The element keeps its position (dropping it was considered and rejected) and
     * answers empty, which is what {@link #getString(int)} has always answered for the same
     * element.
     * </p>
     *
     * @param aElement
     *            the array element.
     * @return the string value, or {@code ""} when the element is not a JSON string.
     */
    static String textOf(JsonNode aElement)
    {
        return aElement.isTextual() ? aElement.asText() : "";
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
                return textOf(array.get(index));
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


    /**
     * Unwraps a domain-typed proxy handed out by the two-argument {@code of} factory to the
     * {@code JsonArrayResource} it delegates to, so that {@link #equals(Object)} sees the same
     * object on both sides of a comparison.
     *
     * <p>
     * Without this, {@code equals} was broken for every proxy this class produces. A proxy's own
     * {@code equals} is dispatched to the invocation handler, which forwards it as
     * {@code delegate.equals(theProxy)} — and the proxy is not a {@code JsonArrayResource}, so the
     * {@code instanceof} test below failed. The consequences were not subtle: {@code x.equals(x)}
     * was <b>false</b> for a proxy, which silently breaks {@code List.contains},
     * {@code List.indexOf}, {@code List.remove(Object)}, {@code Set} de-duplication and
     * {@code Stream.distinct} — each of them then reporting "not found" or "no duplicate" instead
     * of failing. Comparison was asymmetric too: {@code proxy.equals(plain)} was {@code true} while
     * {@code plain.equals(proxy)} was {@code false}. {@link #hashCode()} has always hashed the
     * delegate, so it was already consistent with the value-based equality restored here.
     * </p>
     *
     * <p>
     * Only proxies produced by <i>this</i> class are unwrapped: the handler type tested below is
     * private to it, so a proxy from another resource implementation, or any unrelated dynamic
     * proxy, is returned untouched and compares unequal exactly as before.
     * </p>
     *
     * @param aOther
     *            the object being compared against.
     * @return the delegate behind one of this class's proxies, or {@code aOther} unchanged.
     */
    private static Object unwrapProxy(Object aOther)
    {
        if (Proxy.isProxyClass(aOther.getClass()) && Proxy
                .getInvocationHandler(aOther) instanceof ArrayResourceInvocationHandler handler)
        {
            return handler.delegate();
        }
        return aOther;
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o != null && unwrapProxy(o) instanceof JsonArrayResource other)
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
