package ru.citeck.ecos.model.converter.dto;

import ru.citeck.ecos.model.converter.Converter;

public interface DtoConverter<S, T> extends Converter<S, T> {

    T dtoToEntity(S s);

    S entityToDto(T t);
}
