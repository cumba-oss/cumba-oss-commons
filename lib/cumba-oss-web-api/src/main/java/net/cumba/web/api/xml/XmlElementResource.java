package net.cumba.web.api.xml;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Stream;
import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Read-only {@link ApiResource} implementation backed by a DOM {@link Element}.
 *
 * <p>
 * Field lookups follow attribute-first resolution: {@code getString("OID")} checks XML attributes
 * first, then child element text content. This matches the natural XML convention where attributes
 * hold metadata/identifiers and child elements hold structured content.
 * </p>
 *
 * <p>
 * Repeated child elements with the same local name are treated as arrays and accessible via
 * {@link #getList(String, Class)}. This is how XML naturally represents collections.
 * </p>
 *
 * <p>
 * Since XML is inherently untyped (all values are text), numeric and boolean accessors attempt to
 * parse the text value and return empty on parse failure.
 * </p>
 */
public final class XmlElementResource implements ApiResource
{

    private final Element element;

    /**
     * Creates a new resource backed by the given DOM element.
     *
     * @param aElement
     *            the backing DOM element (must not be {@code null})
     */
    public XmlElementResource(Element aElement)
    {
        this.element = Objects.requireNonNull(aElement, "element must not be null");
    }

    // --- Static factories ---


    /**
     * Wraps a DOM element as a plain {@link ApiResource}.
     */
    public static ApiResource of(Element aElement)
    {
        return new XmlElementResource(aElement);
    }


    /**
     * Wraps a DOM element as a domain-specific {@link ApiResource} subtype. The returned proxy
     * delegates generic accessors to {@link XmlElementResource} and domain-specific default methods
     * to the interface itself.
     *
     * @param aElement
     *            the DOM element
     * @param aType
     *            the target interface (must extend {@code ApiResource})
     * @param <T>
     *            the target type
     * @return a proxy instance implementing both {@code T} and {@code ApiResource}
     */
    @SuppressWarnings("unchecked")
    public static <T extends ApiResource> T of(Element aElement, Class<T> aType)
    {
        if (aType == ApiResource.class)
        {
            return (T) new XmlElementResource(aElement);
        }
        XmlElementResource delegate = new XmlElementResource(aElement);
        return (T) Proxy.newProxyInstance(aType.getClassLoader(), new Class<?>[]
        {
                aType
        }, new ResourceInvocationHandler(delegate, aType));
    }


    /**
     * Returns the underlying DOM element.
     *
     * @return the backing element
     */
    public Element getElement()
    {
        return element;
    }


    /**
     * Returns the text content of this element (not a field lookup).
     *
     * @return the text content, or empty string if none
     */
    public String getTextContent()
    {
        return element.getTextContent();
    }

    // --- Internal helpers ---


    /**
     * Resolves a field value by checking attribute first, then child element text content.
     *
     * @return the resolved text value, or {@code null} if not found
     */
    private @Nullable String resolveField(String aFieldName)
    {
        // 1. Check attribute
        if (element.hasAttribute(aFieldName))
        {
            return element.getAttribute(aFieldName);
        }

        // 2. Check first child element with matching local name
        Element child = findFirstChildElement(aFieldName);
        if (child != null)
        {
            return child.getTextContent();
        }

        return null;
    }


    /**
     * Finds the first child element with the given local name.
     */
    private @Nullable Element findFirstChildElement(String aLocalName)
    {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node node = children.item(i);
            if (node instanceof Element childElem && matchesName(childElem, aLocalName))
            {
                return childElem;
            }
        }
        return null;
    }


    /**
     * Finds all child elements with the given local name.
     */
    private List<Element> findChildElements(String aLocalName)
    {
        List<Element> result = new ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node node = children.item(i);
            if (node instanceof Element childElem && matchesName(childElem, aLocalName))
            {
                result.add(childElem);
            }
        }
        return result;
    }


    /**
     * Checks whether the element matches the given name. Uses local name matching (ignoring
     * namespace prefixes) with fallback to tag name.
     */
    private static boolean matchesName(Element aElement, String aName)
    {
        String localName = aElement.getLocalName();
        if (localName != null)
        {
            return localName.equals(aName);
        }
        return aElement.getTagName().equals(aName);
    }


    /**
     * Checks whether a child element is "complex" (has child elements or attributes beyond xmlns).
     */
    private static boolean isComplexElement(Element aElement)
    {
        // Has non-xmlns attributes?
        NamedNodeMap attrs = aElement.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++)
        {
            String attrName = attrs.item(i).getNodeName();
            if (!attrName.startsWith("xmlns"))
            {
                return true;
            }
        }

        // Has child elements?
        NodeList children = aElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            if (children.item(i) instanceof Element)
            {
                return true;
            }
        }

        return false;
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

    // --- ApiResource implementation: type checks ---


    @Override
    public boolean isString(String aFieldName)
    {
        return element.hasAttribute(aFieldName) || findFirstChildElement(aFieldName) != null;
    }


    @Override
    public boolean isNumber(String aFieldName)
    {
        return tryParseDouble(resolveField(aFieldName)) != null;
    }


    @Override
    public boolean isBoolean(String aFieldName)
    {
        String val = resolveField(aFieldName);
        if (val == null)
        {
            return false;
        }
        return val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false")
                || val.equalsIgnoreCase("yes") || val.equalsIgnoreCase("no");
    }


    @Override
    public boolean isObject(String aFieldName)
    {
        Element child = findFirstChildElement(aFieldName);
        return child != null && isComplexElement(child);
    }


    @Override
    public boolean isArray(String aFieldName)
    {
        return findChildElements(aFieldName).size() > 1;
    }


    @Override
    public boolean isNull(String aFieldName)
    {
        return !element.hasAttribute(aFieldName) && findFirstChildElement(aFieldName) == null;
    }

    // --- Scalar accessors ---


    @Override
    public Optional<String> getString(String aFieldName)
    {
        String val = resolveField(aFieldName);
        return val != null ? Optional.of(val) : Optional.empty();
    }


    @Override
    public OptionalInt getInt(String aFieldName)
    {
        Long val = tryParseLong(resolveField(aFieldName));
        return val != null ? OptionalInt.of(val.intValue()) : OptionalInt.empty();
    }


    @Override
    public OptionalLong getLong(String aFieldName)
    {
        Long val = tryParseLong(resolveField(aFieldName));
        return val != null ? OptionalLong.of(val) : OptionalLong.empty();
    }


    @Override
    public OptionalDouble getDouble(String aFieldName)
    {
        Double val = tryParseDouble(resolveField(aFieldName));
        return val != null ? OptionalDouble.of(val) : OptionalDouble.empty();
    }


    @Override
    public Optional<Boolean> getBoolean(String aFieldName)
    {
        String val = resolveField(aFieldName);
        if (val == null)
        {
            return Optional.empty();
        }
        if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("yes"))
        {
            return Optional.of(Boolean.TRUE);
        }
        if (val.equalsIgnoreCase("false") || val.equalsIgnoreCase("no"))
        {
            return Optional.of(Boolean.FALSE);
        }
        return Optional.empty();
    }


    @Override
    public Optional<Number> getNumber(String aFieldName)
    {
        String raw = resolveField(aFieldName);
        if (raw == null)
        {
            return Optional.empty();
        }
        // Try integer first, then floating point
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
    public Set<String> getFieldNames()
    {
        Set<String> names = new LinkedHashSet<>();

        // Attributes
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++)
        {
            String attrName = attrs.item(i).getNodeName();
            if (!attrName.startsWith("xmlns"))
            {
                names.add(attrName);
            }
        }

        // Distinct child element local names
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node node = children.item(i);
            if (node instanceof Element childElem)
            {
                String localName = childElem.getLocalName();
                names.add(localName != null ? localName : childElem.getTagName());
            }
        }

        return Collections.unmodifiableSet(names);
    }


    @Override
    public boolean containsFieldName(String aFieldName)
    {
        return element.hasAttribute(aFieldName) || findFirstChildElement(aFieldName) != null;
    }


    @Override
    public int getFieldCount()
    {
        return getFieldNames().size();
    }

    // --- Structural accessors ---


    @Override
    public <T extends ApiResource> Optional<T> getObject(String aFieldName, Class<T> aType)
    {
        Element child = findFirstChildElement(aFieldName);
        if (child == null)
        {
            return Optional.empty();
        }
        return Optional.of(of(child, aType));
    }


    @Override
    public <T extends ApiArrayResource> Optional<T> getArray(String aFieldName, Class<T> aType)
    {
        List<Element> children = findChildElements(aFieldName);
        if (children.isEmpty())
        {
            return Optional.empty();
        }
        return Optional.of(XmlChildListResource.of(children, aType));
    }


    @Override
    public <T extends ApiResource> List<T> getList(String aFieldName, Class<T> aType)
    {
        List<Element> children = findChildElements(aFieldName);
        if (children.isEmpty())
        {
            return Collections.emptyList();
        }
        return new AbstractList<>()
        {

            @Override
            public T get(int index)
            {
                return of(children.get(index), aType);
            }


            @Override
            public int size()
            {
                return children.size();
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
        List<Element> children = findChildElements(aFieldName);
        if (children.isEmpty())
        {
            return Collections.emptyList();
        }
        return new AbstractList<>()
        {

            @Override
            public String get(int index)
            {
                return children.get(index).getTextContent();
            }


            @Override
            public int size()
            {
                return children.size();
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
        Element linksElem = findFirstChildElement("_links");
        if (linksElem == null)
        {
            return Optional.empty();
        }
        XmlElementResource linksResource = new XmlElementResource(linksElem);
        Element relElem = linksResource.findFirstChildElement(aRel);
        if (relElem == null)
        {
            return Optional.empty();
        }
        return Optional.of(of(relElem, Link.class));
    }


    @Override
    public List<Link> getLinks(String aRel)
    {
        Element linksElem = findFirstChildElement("_links");
        if (linksElem == null)
        {
            return Collections.emptyList();
        }
        XmlElementResource linksResource = new XmlElementResource(linksElem);
        List<Element> relElems = linksResource.findChildElements(aRel);
        if (relElems.isEmpty())
        {
            return Collections.emptyList();
        }
        List<Link> result = new ArrayList<>(relElems.size());
        for (Element relElem : relElems)
        {
            result.add(of(relElem, Link.class));
        }
        return Collections.unmodifiableList(result);
    }

    // --- Object overrides ---


    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof XmlElementResource other)
        {
            return element.equals(other.element);
        }
        return false;
    }


    @Override
    public int hashCode()
    {
        return element.hashCode();
    }


    @Override
    public String toString()
    {
        String tagName = element.getLocalName();
        if (tagName == null)
        {
            tagName = element.getTagName();
        }
        return "XmlElementResource[<" + tagName + ">]";
    }

    // --- Internal helpers ---

    /**
     * InvocationHandler that delegates {@link ApiResource} methods to the
     * {@link XmlElementResource} instance and interface default methods to the interface itself.
     */
    private record ResourceInvocationHandler(XmlElementResource delegate,
            Class<? extends ApiResource> targetType) implements InvocationHandler
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
                case "toString" -> targetType.getSimpleName() + "[<"
                        + (delegate.element.getLocalName() != null ? delegate.element.getLocalName()
                                : delegate.element.getTagName())
                        + ">]";
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
