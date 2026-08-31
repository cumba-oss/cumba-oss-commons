package net.cumba.web.api.dev;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
        Object val = map.get(aFieldName);
        if (val == null)
        {
            return Optional.empty();
        }
        return Optional.of(val.toString());
    }


    @Override
    public OptionalInt getInt(String aFieldName)
    {
        Object val = map.get(aFieldName);
        if (val instanceof Number num)
        {
            return OptionalInt.of(num.intValue());
        }
        return OptionalInt.empty();
    }


    @Override
    public OptionalLong getLong(String aFieldName)
    {
        Object val = map.get(aFieldName);
        if (val instanceof Number num)
        {
            return OptionalLong.of(num.longValue());
        }
        return OptionalLong.empty();
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

            // NullAway: the ApiResource#getStringList contract returns List<String> with non-null
            // elements, but a null source element is mapped to a null entry to preserve positional
            // alignment with the backing list (pre-existing behaviour).
            @SuppressWarnings("NullAway")
            @Override
            public String get(int index)
            {
                Object element = list.get(index);
                return element != null ? element.toString() : null;
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


    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof MapResource other)
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
