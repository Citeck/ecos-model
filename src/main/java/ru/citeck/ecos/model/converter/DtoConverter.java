package ru.citeck.ecos.model.converter;

import org.springframework.core.convert.converter.Converter;

public interface DtoConverter<S, T> extends Converter<S, T> {

    T dtoToEntity(S s);

    S entityToDto(T t);
}
