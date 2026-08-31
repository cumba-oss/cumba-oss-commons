package net.cumba.web.api.xml;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Stream;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;

/**
 * Read-only {@link ApiArrayResource} implementation backed by a list of DOM {@link Element} nodes.
 *
 * <p>
 * This represents repeated XML child elements with the same tag name — the natural XML equivalent
 * of a JSON array. Each element in the list is accessible via index-based typed accessors.
 * </p>
 */
public final class XmlChildListResource implements ApiArrayResource
{

    private final List<Element> elements;

    /**
     * Creates a new array resource backed by the given list of elements.
     *
     * @param aElements
     *            the backing element list (must not be {@code null})
     */
    public XmlChildListResource(List<Element> aElements)
    {
        this.elements = Objects.requireNonNull(aElements, "elements must not be null");
    }

    // --- Static factories ---


    /**
     * Wraps a list of elements as a plain {@link ApiArrayResource}.
     */
    public static ApiArrayResource of(List<Element> aElements)
    {
        return new XmlChildListResource(aElements);
    }


    /**
     * Wraps a list of elements as a domain-specific {@link ApiArrayResource} subtype.
     *
     * @param aElements
     *            the element list
     * @param aType
     *            the target interface (must extend {@code ApiArrayResource})
     * @param <T>
     *            the target type
     * @return a proxy instance
     */
    @SuppressWarnings("unchecked")
    public static <T extends ApiArrayResource> T of(List<Element> aElements, Class<T> aType)
    {
        if (aType == ApiArrayResource.class)
        {
            return (T) new XmlChildListResource(aElements);
        }
        XmlChildListResource delegate = new XmlChildListResource(aElements);
        return (T) Proxy.newProxyInstance(aType.getClassLoader(), new Class<?>[]
        {
                aType
        }, new ArrayResourceInvocationHandler(delegate, aType));
    }

    // --- Helpers ---


    private @Nullable Element safeGet(int aIndex)
    {
        if (aIndex < 0 || aIndex >= elements.size())
        {
            return null;
        }
        return elements.get(aIndex);
    }


    private @Nullable String safeGetText(int aIndex)
    {
        Element elem = safeGet(aIndex);
        return elem != null ? elem.getTextContent() : null;
    }


    private static @Nullable Double tryParseDouble(@Nullable String aValue)
    {
        if (aValue == null)
        {
            return null;
        }
        try
        {
            return Double.parseDouble(aValue);
        }
        catch (NumberFormatException _)
        {
            return null;
        }
    }


    private static @Nullable Long tryParseLong(@Nullable String aValue)
    {
        if (aValue == null)
        {
            return null;
        }
        try
        {
            return Long.parseLong(aValue);
        }
        catch (NumberFormatException _)
        {
            return null;
        }
    }

    // --- ApiArrayResource implementation ---


    @Override
    public int getLength()
    {
        return elements.size();
    }


    @Override
    public boolean isString(int aIndex)
    {
        return safeGet(aIndex) != null;
    }


    @Override
    public Optional<String> getString(int aIndex)
    {
        String text = safeGetText(aIndex);
        return text != null ? Optional.of(text) : Optional.empty();
    }


    @Override
    public boolean isInt(int aIndex)
    {
        return tryParseLong(safeGetText(aIndex)) != null;
    }


    @Override
    public OptionalInt getInt(int aIndex)
    {
        Long val = tryParseLong(safeGetText(aIndex));
        return val != null ? OptionalInt.of(val.intValue()) : OptionalInt.empty();
    }


    @Override
    public boolean isLong(int aIndex)
    {
        return tryParseLong(safeGetText(aIndex)) != null;
    }


    @Override
    public OptionalLong getLong(int aIndex)
    {
        Long val = tryParseLong(safeGetText(aIndex));
        return val != null ? OptionalLong.of(val) : OptionalLong.empty();
    }


    @Override
    public boolean isDouble(int aIndex)
    {
        return tryParseDouble(safeGetText(aIndex)) != null;
    }


    @Override
    public OptionalDouble getDouble(int aIndex)
    {
        Double val = tryParseDouble(safeGetText(aIndex));
        return val != null ? OptionalDouble.of(val) : OptionalDouble.empty();
    }


    @Override
    public boolean isBoolean(int aIndex)
    {
        String text = safeGetText(aIndex);
        if (text == null)
        {
            return false;
        }
        return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")
                || text.equalsIgnoreCase("yes") || text.equalsIgnoreCase("no");
    }


    @Override
    public Optional<Boolean> getBoolean(int aIndex)
    {
        String text = safeGetText(aIndex);
        if (text == null)
        {
            return Optional.empty();
        }
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes"))
        {
            return Optional.of(Boolean.TRUE);
        }
        if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("no"))
        {
            return Optional.of(Boolean.FALSE);
        }
        return Optional.empty();
    }


    @Override
    public boolean isNumber(int aIndex)
    {
        return tryParseDouble(safeGetText(aIndex)) != null;
    }


    @Override
    public Optional<Number> getNumber(int aIndex)
    {
        String raw = safeGetText(aIndex);
        if (raw == null)
        {
            return Optional.empty();
        }
        Long longVal = tryParseLong(raw);
        if (longVal != null)
        {
            return Optional.of(longVal);
        }
        Double dblVal = tryParseDouble(raw);
        if (dblVal != null)
        {
            return Optional.of(dblVal);
        }
        return Optional.empty();
    }


    @Override
    public boolean isObject(int aIndex)
    {
        Element elem = safeGet(aIndex);
        if (elem == null)
        {
            return false;
        }
        // An element is considered an "object" if it has attributes or child elements
        return elem.hasAttributes() || elem.getChildNodes().getLength() > 0;
    }


    @Override
    public <T extends ApiResource> Optional<T> getObject(int aIndex, Class<T> aType)
    {
        Element elem = safeGet(aIndex);
        if (elem == null)
        {
            return Optional.empty();
        }
        return Optional.of(XmlElementResource.of(elem, aType));
    }


    @Override
    public boolean isArray(int aIndex)
    {
        // Individual elements in the list are not arrays themselves
        return false;
    }


    @Override
    public <T extends ApiArrayResource> Optional<T> getArray(int aIndex, Class<T> aType)
    {
        return Optional.empty();
    }


    @Override
    public List<String> getStringList(int aIndex)
    {
        Element elem = safeGet(aIndex);
        if (elem == null)
        {
            return Collections.emptyList();
        }
        // Collect text content of child elements
        XmlElementResource wrapper = new XmlElementResource(elem);
        return wrapper.getStringList(
                elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName());
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
        if (o instanceof XmlChildListResource other)
        {
            return elements.equals(other.elements);
        }
        return false;
    }


    @Override
    public int hashCode()
    {
        return elements.hashCode();
    }


    @Override
    public String toString()
    {
        return "XmlChildListResource[size=" + elements.size() + "]";
    }

    // --- Internal helpers ---

    private record ArrayResourceInvocationHandler(XmlChildListResource delegate,
            Class<? extends ApiArrayResource> targetType) implements InvocationHandler
    {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            if (method.getDeclaringClass() == Object.class)
            {
                return switch (method.getName())
                {
                case "equals" -> delegate.equals(args[0]);
                case "hashCode" -> delegate.hashCode();
                case "toString" -> targetType.getSimpleName() + "[size=" + delegate.elements.size()
                        + "]";
                default -> method.invoke(delegate, args);
                };
            }

            if (method.isDefault())
            {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

            return method.invoke(delegate, args);
        }
    }
}
