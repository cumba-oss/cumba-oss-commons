package net.cumba.web.api.dev;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Stream;
import lombok.NonNull;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;
import org.jspecify.annotations.Nullable;

/**
 * Read-only {@link ApiResource} implementation backed by a {@code Map<String, Object>}.
 *
 * <p>
 * This is the map-based counterpart to {@code JsonNodeResource}. It wraps a plain
 * {@code Map<String, Object>} (as typically produced by Jackson deserialization into generic types)
 * and provides the same typed accessor contract.
 * </p>
 *
 * <p>
 * Nested maps are automatically wrapped as {@code HashMapResource} instances. Lists of maps are
 * wrapped as lists of {@code HashMapResource} proxies.
 * </p>
 */
public class MapResource implements ApiResource
{

    private final Map<?, ?> map;

    /**
     * Creates a new resource backed by the given map.
     *
     * @param aMap
     *            the backing map (must not be {@code null})
     */
    public MapResource(@NonNull Map<?, ?> aMap)
    {
        map = aMap;
    }

    // --- Static factories ---


    /**
     * Wraps a map as a plain {@link ApiResource}.
     */
    public static ApiResource of(Map<?, ?> aMap)
    {
        return new MapResource(aMap);
    }


    /**
     * Wraps a map as a domain-specific {@link ApiResource} subtype. The returned proxy delegates
     * generic accessors to {@link MapResource} and domain-specific default methods to the interface
     * itself.
     *
     * @param aMap
     *            the map data
     * @param aType
     *            the target interface (must extend {@code ApiResource})
     * @param <T>
     *            the target type
     * @return a proxy instance implementing both {@code T} and {@code ApiResource}
     */
    @SuppressWarnings("unchecked")
    public static <T extends ApiResource> T of(Map<?, ?> aMap, Class<T> aType)
    {
        if (aType == ApiResource.class)
        {
            return (T) new MapResource(aMap);
        }
        MapResource delegate = new MapResource(aMap);
        return (T) Proxy.newProxyInstance(aType.getClassLoader(), new Class<?>[]
        {
                aType
        }, new ResourceInvocationHandler(delegate, aType));
    }

    // --- ApiResource implementation: type checks ---


    @Override
    public boolean isString(String aFieldName)
    {
        Object val = map.get(aFieldName);
        return val instanceof String;
    }


    @Override
    public boolean isNumber(String aFieldName)
    {
        Object val = map.get(aFieldName);
        return val instanceof Number;
    }


    @Override
    public boolean isBoolean(String aFieldName)
    {
        Object val = map.get(aFieldName);
        return val instanceof Boolean;
    }


    @Override
    public boolean isObject(String aFieldName)
    {
        Object val = map.get(aFieldName);
        return val instanceof Map;
    }


    @Override
    public boolean isArray(String aFieldName)
    {
        Object val = map.get(aFieldName);
        return val instanceof List;
    }


    @Override
    public boolean isNull(String aFieldName)
    {
        return map.containsKey(aFieldName) && map.get(aFieldName) == null;
    }

    // --- Scalar accessors ---


    @Override
    public Optional<String> getString(String aFieldName)
    {
        // Q13 (2026-09-11): a value that is not a string is not read as one. The interface
        // contract is "empty if the field is missing or not textual", and JsonNodeResource has
        // always honoured it; this copy used to answer toString(), so a fixture holding the
        // number 42 read back as "42" where the JSON it stands in for answers empty - a fixture
        // in a shape the real source never produces, passing a test the live path would fail.
        Object val = map.get(aFieldName);
        return val instanceof String text ? Optional.of(text) : Optional.empty();
    }


    /**
     * Returns the value as an exact integer, or {@code null} when it is not an integer.
     *
     * <p>
     * Q12 (2026-09-11), <i>strict on both sides</i>: what counts as an integer is the value's
     * <b>type</b>, not whether its fraction happens to be zero. A {@link Double} {@code 3.0} is a
     * floating-point value and answers {@code null} here, because that is what
     * {@code JsonNodeResource} answers for the JSON {@code 3.0} (Jackson types it
     * {@code DoubleNode}, whose {@code isIntegralNumber()} is false) and what
     * {@code XmlElementResource} answers for the text {@code "3.0"} ({@code Long.parseLong} rejects
     * it). Until this was tightened, a map-backed resource read {@code 3.0} as the int {@code 3}
     * while the other four implementations of the same method answered empty.
     * </p>
     *
     * <p>
     * The accepted types mirror Jackson's {@code isIntegralNumber()} exactly, so a fixture built by
     * hand answers what the same document answers once it has been through the parser. Any other
     * {@link Number} — including {@code BigDecimal}, {@code NaN} and the infinities, and any
     * third-party implementation whose value this class cannot read exactly — answers {@code null}
     * rather than a converted approximation.
     * </p>
     */
    private static @Nullable BigInteger exactInteger(@Nullable Object aValue)
    {
        if (aValue instanceof BigInteger big)
        {
            return big;
        }
        if (aValue instanceof Byte || aValue instanceof Short || aValue instanceof Integer
                || aValue instanceof Long)
        {
            return BigInteger.valueOf(((Number) aValue).longValue());
        }
        return null;
    }


    /** Reports whether an exact integer fits the given inclusive range. */
    private static boolean fits(@Nullable BigInteger aValue, long aMin, long aMax)
    {
        return aValue != null && aValue.compareTo(BigInteger.valueOf(aMin)) >= 0
                && aValue.compareTo(BigInteger.valueOf(aMax)) <= 0;
    }


    @Override
    public OptionalInt getInt(String aFieldName)
    {
        // F-webapi-03: never narrow a value that does not fit an int, and never truncate a
        // fractional one. Until this was extended here, getInt of 2147483648 answered
        // -2147483648 and getInt of 3.7 answered 3 - a different number, reported as a success.
        BigInteger val = exactInteger(map.get(aFieldName));
        if (!fits(val, Integer.MIN_VALUE, Integer.MAX_VALUE))
        {
            return OptionalInt.empty();
        }
        return OptionalInt.of(val.intValue());
    }


    @Override
    public OptionalLong getLong(String aFieldName)
    {
        BigInteger val = exactInteger(map.get(aFieldName));
        if (!fits(val, Long.MIN_VALUE, Long.MAX_VALUE))
        {
            return OptionalLong.empty();
        }
        return OptionalLong.of(val.longValue());
    }


    @Override
    public OptionalDouble getDouble(String aFieldName)
    {
        Object val = map.get(aFieldName);
        if (val instanceof Number num)
        {
            return OptionalDouble.of(num.doubleValue());
        }
        return OptionalDouble.empty();
    }


    @Override
    public Optional<Boolean> getBoolean(String aFieldName)
    {
        Object val = map.get(aFieldName);
        if (val instanceof Boolean bool)
        {
            return Optional.of(bool);
        }
        return Optional.empty();
    }


    @Override
    public Optional<Number> getNumber(String aFieldName)
    {
        Object val = map.get(aFieldName);
        if (val instanceof Number num)
        {
            return Optional.of(num);
        }
        return Optional.empty();
    }


    @Override
    public Set<String> getFieldNames()
    {
        List<String> fieldNames = map.keySet().stream()//
                .filter(String.class::isInstance)//
                .map(String.class::cast)//
                .toList();
        return new HashSet<>(fieldNames);
    }


    @Override
    public boolean containsFieldName(String aFieldName)
    {
        return map.containsKey(aFieldName);
    }


    @Override
    public int getFieldCount()
    {
        return map.size();
    }

    // --- Structural accessors ---


    @Override
    public <T extends ApiResource> Optional<T> getObject(String aFieldName, Class<T> aType)
    {
        Object val = map.get(aFieldName);
        if (val instanceof Map<?, ?> childMap)
        {
            return Optional.of(of((Map<?, ?>) childMap, aType));
        }
        return Optional.empty();
    }


    @Override
    public <T extends ApiArrayResource> Optional<T> getArray(String aFieldName, Class<T> aType)
    {
        Object val = map.get(aFieldName);
        if (val instanceof List<?> childList)
        {
            return Optional.of(ListResource.of(childList, aType));
        }
        return Optional.empty();
    }


    @SuppressWarnings("unchecked")
    @Override
    public <T extends ApiResource> List<T> getList(String aFieldName, Class<T> aType)
    {
        Object val = map.get(aFieldName);
        if (!(val instanceof List<?> list))
        {
            return Collections.emptyList();
        }
        return new AbstractList<>()
        {

            // NullAway: the ApiResource#getList contract returns List<T> with non-null elements,
            // but a null source element is mapped to a null entry to preserve positional alignment
            // with the backing list (pre-existing behaviour).
            @SuppressWarnings("NullAway")
            @Override
            public T get(int index)
            {
                Object element = list.get(index);
                if (element instanceof Map<?, ?> childMap)
                {
                    return of(childMap, aType);
                }
                if (element instanceof ApiResource res)
                {
                    return (T) res;
                }
                if (element == null)
                {
                    return null;
                }
                throw new IllegalArgumentException(
                        "Unsupported element type: " + element.getClass().getName());
            }


            @Override
            public int size()
            {
                return list.size();
            }
        };
    }


    @Override
    public <T extends ApiResource> Stream<T> getStream(String aFieldName, Class<T> aType)
    {
        return getList(aFieldName, aType).stream();
    }


    @Override
    public List<String> getStringList(String aFieldName)
    {
        Object val = map.get(aFieldName);
        if (!(val instanceof List<?> list))
        {
            return Collections.emptyList();
        }
        return new AbstractList<>()
        {

            @Override
            public String get(int index)
            {
                // Q13 (2026-09-11): an element that is not a string answers the empty string and
                // keeps its position. It used to answer toString(), so a null became a null entry
                // inside a List<String> - which is what the NullAway suppression removed here was
                // apologising for - and the number 7 became the term "7".
                return list.get(index) instanceof String text ? text : "";
            }


            @Override
            public int size()
            {
                return list.size();
            }
        };
    }


    @Override
    public Stream<String> getStringStream(String aFieldName)
    {
        return getStringList(aFieldName).stream();
    }

    // --- HATEOAS link navigation ---


    @Override
    public Optional<Link> getLink(String aRel)
    {
        Object linksObj = map.get("_links");
        if (!(linksObj instanceof Map<?, ?> linksMap))
        {
            return Optional.empty();
        }
        Object relObj = linksMap.get(aRel);
        if (relObj instanceof Map<?, ?> linkMap)
        {
            return Optional.of(of((Map<?, ?>) linkMap, Link.class));
        }
        if (relObj instanceof List<?> linkList && !linkList.isEmpty())
        {
            Object first = linkList.getFirst();
            if (first instanceof Map<?, ?> firstMap)
            {
                return Optional.of(of((Map<?, ?>) firstMap, Link.class));
            }
        }
        return Optional.empty();
    }


    @Override
    public List<Link> getLinks(String aRel)
    {
        Object linksObj = map.get("_links");
        if (!(linksObj instanceof Map<?, ?> linksMap))
        {
            return Collections.emptyList();
        }
        Object relObj = linksMap.get(aRel);
        if (relObj instanceof Map<?, ?> linkMap)
        {
            return List.of(of((Map<?, ?>) linkMap, Link.class));
        }
        if (relObj instanceof List<?> linkList)
        {
            List<Link> result = new ArrayList<>(linkList.size());
            for (Object item : linkList)
            {
                if (item instanceof Map<?, ?> itemMap)
                {
                    result.add(of(itemMap, Link.class));
                }
            }
            return Collections.unmodifiableList(result);
        }
        return Collections.emptyList();
    }

    // --- Object overrides ---


    /**
     * Unwraps a domain-typed proxy handed out by the two-argument {@code of} factory to the
     * {@code MapResource} it delegates to, so that {@link #equals(Object)} sees the same object on
     * both sides of a comparison.
     *
     * <p>
     * Without this, {@code equals} was broken for every proxy this class produces. A proxy's own
     * {@code equals} is dispatched to the invocation handler, which forwards it as
     * {@code delegate.equals(theProxy)} — and the proxy is not a {@code MapResource}, so the
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
        if (Proxy.isProxyClass(aOther.getClass())
                && Proxy.getInvocationHandler(aOther) instanceof ResourceInvocationHandler handler)
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
        if (o != null && unwrapProxy(o) instanceof MapResource other)
        {
            return map.equals(other.map);
        }
        return false;
    }


    @Override
    public int hashCode()
    {
        return map.hashCode();
    }


    @Override
    public String toString()
    {
        return "HashMapResource[" + map + "]";
    }

    // --- Internal helpers ---

    /**
     * InvocationHandler that delegates {@link ApiResource} methods to the {@link MapResource}
     * instance and interface default methods to the interface itself.
     */
    private record ResourceInvocationHandler(MapResource delegate,
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
                case "toString" -> targetType.getSimpleName() + "[" + delegate.map + "]";
                default -> method.invoke(delegate, args);
                };
            }

            // Default methods on the domain interface
            if (method.isDefault())
            {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

            // Everything else → delegate to HashMapResource
            return method.invoke(delegate, args);
        }
    }
}
