package ru.citeck.ecos.model.converter;

public interface DtoConverter<S, T> extends Converter<S, T> {

    T dtoToEntity(S s);

    S entityToDto(T t);
}
