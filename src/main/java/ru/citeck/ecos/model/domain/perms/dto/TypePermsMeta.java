package ru.citeck.ecos.model.domain.perms.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

@Data
@RequiredArgsConstructor
public class TypePermsMeta {
    private final Instant modified;
}
