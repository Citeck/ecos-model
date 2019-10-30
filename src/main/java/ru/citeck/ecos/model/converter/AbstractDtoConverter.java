package ru.citeck.ecos.model.converter;

/*
 * Extension of default interface Converter, whose purpose to converting dto to entity and conversely.
 */
public abstract class AbstractDtoConverter<S, T> implements Converter<S, T> {

    @Override
    public T sourceToTarget(S s) {
        return dtoToEntity(s);
    }

    @Override
    public S targetToSource(T t) {
        return entityToDto(t);
    }

    /*
     * Alias for 'sourceToTarget'
     */
    protected abstract T dtoToEntity(S s);


    /*
     * Alias for 'targetToSource'
     */
    protected abstract S entityToDto(T t);

    protected String extractId(String refId) {
        if (refId.contains("$")) {
            return refId.substring(refId.indexOf("$") + 1);
        }
        return refId;
    }
}
