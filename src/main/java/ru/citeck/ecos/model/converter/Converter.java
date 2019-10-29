package ru.citeck.ecos.model.converter;

/*
 *  Converter interface
 *
 *  S - source type
 *  T - target type
 */
public interface Converter<S, T> {

    T sourceToTarget(S s);

    S targetToSource(T t);
}
