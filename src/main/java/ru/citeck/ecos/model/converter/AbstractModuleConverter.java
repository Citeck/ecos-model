package ru.citeck.ecos.model.converter;

public abstract class AbstractModuleConverter<S, T> implements ModuleConverter<S, T> {

    @Override
    public abstract T moduleToDto(S module);

    @Override
    public T sourceToTarget(S module) {
        return moduleToDto(module);
    }

    @Override
    public S targetToSource(T t) {
        throw new UnsupportedOperationException("Converting to module is not supported");
    }

    protected String extractIdFromModuleId(String refId) {
        if (refId != null && refId.contains("$")) {
            int delimiterIndex = refId.indexOf("$");
            return refId.substring(delimiterIndex + 1);
        }
        return refId;
    }

}
