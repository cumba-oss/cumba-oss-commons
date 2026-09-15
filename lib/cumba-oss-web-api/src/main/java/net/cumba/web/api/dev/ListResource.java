package net.cumba.web.api.dev;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Stream;

import lombok.NonNull;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;
import org.jspecify.annotations.Nullable;

/**
 * Read-only {@link ApiArrayResource} implementation backed by a plain {@code List<?>}.
 *
 * <p>
 * This is the list-based counterpart to {@code JsonArrayResource}. It wraps a plain {@code List<?>}
 * (as typically produced by Jackson deserialization into generic types) and provides the same typed
 * index-based accessor contract.
 * </p>
 *
 * <p>
 * Elements that are {@code Map<String, Object>} are automatically wrapped as {@link MapResource}
 * instances when accessed via {@link #getObject(int, Class)}.
 * </p>
 */
public class ListResource implements ApiArrayResource
{

    private final List<?> list;

    /**
     * Creates a new array resource backed by the given list.
     *
     * @param aList
     *            the backing list (must not be {@code null})
     */
    public ListResource(@NonNull List<?> aList)
    {
        list = aList;
    }

    // --- Static factories ---


    /**
     * Wraps a list as a plain {@link ApiArrayResource}.
     */
    public static ApiArrayResource of(List<?> aList)
    {
        return new ListResource(aList);
    }


    /**
     * Wraps a list as a domain-specific {@link ApiArrayResource} subtype.
     *
     * @param aList
     *            the list data
     * @param aType
     *            the target interface (must extend {@code ApiArrayResource})
     * @param <T>
     *            the target type
     * @return a proxy instance implementing both {@code T} and {@code ApiArrayResource}
     */
    @SuppressWarnings("unchecked")
    public static <T extends ApiArrayResource> T of(List<?> aList, Class<T> aType)
    {
        if (aType == ApiArrayResource.class)
        {
            return (T) new ListResource(aList);
        }
        ListResource delegate = new ListResource(aList);
        return (T) Proxy.newProxyInstance(aType.getClassLoader(), new Class<?>[]
        {
                aType
        }, new ArrayResourceInvocationHandler(delegate, aType));
    }

    // --- Helpers ---


    private @Nullable Object safeGet(int aIndex)
    {
        if (aIndex < 0 || aIndex >= list.size())
        {
            return null;
        }
        return list.get(aIndex);
    }

    // --- ApiArrayResource implementation ---


    @Override
    public int getLength()
    {
        return list.size();
    }


    @Override
    public boolean isString(int aIndex)
    {
        return safeGet(aIndex) instanceof String;
    }


    @Override
    public Optional<String> getString(int aIndex)
    {
        // Q13 (2026-09-11): a value that is not a string is not read as one - the same rule, and
        // the same reason, as MapResource.getString. isString(int) already answered
        // instanceof String, so the predicate and its accessor now agree.
        Object val = safeGet(aIndex);
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
    public boolean isInt(int aIndex)
    {
        // F-webapi-03: an integer that does not fit a Java int is not an int, and neither is a
        // fractional value. Without this, the three predicates isInt / isLong / isDouble were
        // byte-identical `instanceof Number`, so isInt(9_999_999_999L) answered true and getInt
        // then handed back 1215752191 - a different number, reported as a success.
        return fits(exactInteger(safeGet(aIndex)), Integer.MIN_VALUE, Integer.MAX_VALUE);
    }


    @Override
    public OptionalInt getInt(int aIndex)
    {
        BigInteger val = exactInteger(safeGet(aIndex));
        if (!fits(val, Integer.MIN_VALUE, Integer.MAX_VALUE))
        {
            // F-webapi-03: no silent narrowing and no silent truncation - exactly the values
            // isInt rejects.
            return OptionalInt.empty();
        }
        return OptionalInt.of(val.intValue());
    }


    @Override
    public boolean isLong(int aIndex)
    {
        return fits(exactInteger(safeGet(aIndex)), Long.MIN_VALUE, Long.MAX_VALUE);
    }


    @Override
    public OptionalLong getLong(int aIndex)
    {
        BigInteger val = exactInteger(safeGet(aIndex));
        if (!fits(val, Long.MIN_VALUE, Long.MAX_VALUE))
        {
            return OptionalLong.empty();
        }
        return OptionalLong.of(val.longValue());
    }


    @Override
    public boolean isDouble(int aIndex)
    {
        return safeGet(aIndex) instanceof Number;
    }


    @Override
    public OptionalDouble getDouble(int aIndex)
    {
        Object val = safeGet(aIndex);
        if (val instanceof Number num)
        {
            return OptionalDouble.of(num.doubleValue());
        }
        return OptionalDouble.empty();
    }


    @Override
    public boolean isBoolean(int aIndex)
    {
        return safeGet(aIndex) instanceof Boolean;
    }


    @Override
    public Optional<Boolean> getBoolean(int aIndex)
    {
        Object val = safeGet(aIndex);
        if (val instanceof Boolean bool)
        {
            return Optional.of(bool);
        }
        return Optional.empty();
    }


    @Override
    public boolean isNumber(int aIndex)
    {
        return safeGet(aIndex) instanceof Number;
    }


    @Override
    public Optional<Number> getNumber(int aIndex)
    {
        Object val = safeGet(aIndex);
        if (val instanceof Number num)
        {
            return Optional.of(num);
        }
        return Optional.empty();
    }


    @Override
    public boolean isObject(int aIndex)
    {
        Object val = safeGet(aIndex);
        return val instanceof Map;
    }


    @Override
    public <T extends ApiResource> Optional<T> getObject(int aIndex, Class<T> aType)
    {
        Object val = safeGet(aIndex);
        if (val instanceof Map<?, ?> childMap)
        {
            return Optional.of(MapResource.of((Map<?, ?>) childMap, aType));
        }
        return Optional.empty();
    }


    @Override
    public boolean isArray(int aIndex)
    {
        return safeGet(aIndex) instanceof List;
    }


    @Override
    public <T extends ApiArrayResource> Optional<T> getArray(int aIndex, Class<T> aType)
    {
        Object val = safeGet(aIndex);
        if (val instanceof List<?> childList)
        {
            return Optional.of(of(childList, aType));
        }
        return Optional.empty();
    }


    @Override
    public List<String> getStringList(int aIndex)
    {
        Object val = safeGet(aIndex);
        if (!(val instanceof List<?> childList))
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
                return childList.get(index) instanceof String text ? text : "";
            }


            @Override
            public int size()
            {
                return childList.size();
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
     * {@code ListResource} it delegates to, so that {@link #equals(Object)} sees the same object on
     * both sides of a comparison.
     *
     * <p>
     * Without this, {@code equals} was broken for every proxy this class produces. A proxy's own
     * {@code equals} is dispatched to the invocation handler, which forwards it as
     * {@code delegate.equals(theProxy)} — and the proxy is not a {@code ListResource}, so the
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
        if (o != null && unwrapProxy(o) instanceof ListResource other)
        {
            return list.equals(other.list);
        }
        return false;
    }


    @Override
    public int hashCode()
    {
        return list.hashCode();
    }


    @Override
    public String toString()
    {
        return "ListResource[" + list + "]";
    }

    // --- Internal helpers ---

    /**
     * Invocation handler that delegates {@link ApiArrayResource} methods to the
     * {@link ListResource} instance and interface default methods to the interface itself.
     */
    private record ArrayResourceInvocationHandler(ListResource delegate,
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
                case "toString" -> targetType.getSimpleName() + "[" + delegate.list + "]";
                default -> method.invoke(delegate, args);
                };
            }

            // Default methods on the domain interface
            if (method.isDefault())
            {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

            // Everything else → delegate to ListResource
            return method.invoke(delegate, args);
        }
    }
}
