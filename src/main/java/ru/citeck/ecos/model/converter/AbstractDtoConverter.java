package ru.citeck.ecos.model.converter;

/*
 * Extension of default interface Converter, whose purpose to converting local dto to entity and conversely.
 */
public abstract class AbstractDtoConverter<S, T> implements DtoConverter<S, T> {

    /*
     * Alias for 'sourceToTarget'
     */
    @Override
    public abstract T dtoToEntity(S s);

    /*
     * Alias for 'targetToSource'
     */
    @Override
    public abstract S entityToDto(T t);

    @Override
    public T convert(S s) {
        return dtoToEntity(s);
    }
}
