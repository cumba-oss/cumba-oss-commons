package net.cumba.web.api.dev;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
        Object val = safeGet(aIndex);
        if (val == null)
        {
            return Optional.empty();
        }
        return Optional.of(val.toString());
    }


    @Override
    public boolean isInt(int aIndex)
    {
        return safeGet(aIndex) instanceof Number;
    }


    @Override
    public OptionalInt getInt(int aIndex)
    {
        Object val = safeGet(aIndex);
        if (val instanceof Number num)
        {
            return OptionalInt.of(num.intValue());
        }
        return OptionalInt.empty();
    }


    @Override
    public boolean isLong(int aIndex)
    {
        return safeGet(aIndex) instanceof Number;
    }


    @Override
    public OptionalLong getLong(int aIndex)
    {
        Object val = safeGet(aIndex);
        if (val instanceof Number num)
        {
            return OptionalLong.of(num.longValue());
        }
        return OptionalLong.empty();
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

            // NullAway: the ApiArrayResource#getStringList contract returns List<String> with
            // non-null elements, but a null source element is mapped to a null entry to preserve
            // positional alignment with the backing list (pre-existing behaviour).
            @SuppressWarnings("NullAway")
            @Override
            public String get(int index)
            {
                Object element = childList.get(index);
                return element != null ? element.toString() : null;
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


    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof ListResource other)
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
