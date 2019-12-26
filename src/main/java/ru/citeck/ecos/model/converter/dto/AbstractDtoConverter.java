package ru.citeck.ecos.model.converter.dto;

/*
 * Extension of default interface Converter, whose purpose to converting local dto to entity and conversely.
 */
public abstract class AbstractDtoConverter<S, T> implements DtoConverter<S, T> {

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
    @Override
    public abstract T dtoToEntity(S s);

    /*
     * Alias for 'targetToSource'
     */
    @Override
    public abstract S entityToDto(T t);

}