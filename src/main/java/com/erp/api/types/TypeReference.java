package com.erp.api.types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Type token для збереження generic типів
 * <p>
 * Вирішує проблему type erasure в Java, дозволяючи зберігати
 * повну інформацію про generic типи типу List<ResourceResponse>
 * <p>
 * Usage:
 * <pre>
 * // Для простого типу
 * TypeReference<ResourceResponse> type = new TypeReference<ResourceResponse>() {};
 *
 * // Для generic типу (список)
 * TypeReference<List<ResourceResponse>> listType = new TypeReference<List<ResourceResponse>>() {};
 *
 * // Для складних generic типів
 * TypeReference<Map<String, List<ResourceResponse>>> complexType =
 *     new TypeReference<Map<String, List<ResourceResponse>>>() {};
 * </pre>
 */
public abstract class TypeReference<T> {

    private final Type type;
    private final Class<T> rawType;

    @SuppressWarnings("unchecked")
    protected TypeReference() {
        Type superclass = getClass().getGenericSuperclass();

        if (!(superclass instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "TypeReference must be constructed with actual type parameter. " +
                            "Use: new TypeReference<YourType>() {}"
            );
        }

        ParameterizedType parameterized = (ParameterizedType) superclass;
        this.type = parameterized.getActualTypeArguments()[0];
        this.rawType = (Class<T>) extractRawType(this.type);
    }

    /**
     * Get full Type including generic parameters
     */
    public Type getType() {
        return type;
    }

    /**
     * Get raw class (without generic parameters)
     * <p>
     * For List<ResourceResponse> returns List.class
     */
    public Class<T> getRawType() {
        return rawType;
    }

    /**
     * Get generic type arguments if type is parameterized
     * <p>
     * For List<ResourceResponse> returns [ResourceResponse.class]
     */
    public Type[] getTypeArguments() {
        if (type instanceof ParameterizedType) {
            return ((ParameterizedType) type).getActualTypeArguments();
        }
        return new Type[0];
    }

    /**
     * Check if type is a generic type
     */
    public boolean isParameterized() {
        return type instanceof ParameterizedType;
    }

    /**
     * Check if type is a Collection (List, Set, etc.)
     */
    public boolean isCollection() {
        return java.util.Collection.class.isAssignableFrom(rawType);
    }

    /**
     * Get element type for collections
     * <p>
     * For List<ResourceResponse> returns ResourceResponse.class
     */
    @SuppressWarnings("unchecked")
    public Class<?> getElementType() {
        if (!isCollection() || !isParameterized()) {
            return null;
        }

        Type[] typeArgs = getTypeArguments();
        if (typeArgs.length > 0) {
            return (Class<?>) extractRawType(typeArgs[0]);
        }

        return null;
    }

    /**
     * Get string representation of type
     */
    public String getTypeDescription() {
        if (isParameterized()) {
            StringBuilder sb = new StringBuilder(rawType.getSimpleName());
            sb.append("<");
            Type[] args = getTypeArguments();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(extractRawType(args[i]).getSimpleName());
            }
            sb.append(">");
            return sb.toString();
        }
        return rawType.getSimpleName();
    }

    /**
     * Extract raw class from Type
     */
    private static Class<?> extractRawType(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        throw new IllegalArgumentException("Cannot extract raw type from: " + type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TypeReference)) return false;
        TypeReference<?> other = (TypeReference<?>) obj;
        return type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        return "TypeReference<" + getTypeDescription() + ">";
    }
}