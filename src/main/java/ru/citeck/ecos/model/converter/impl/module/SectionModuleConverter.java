package ru.citeck.ecos.model.converter.impl.module;

import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.model.section.SectionModule;
import ru.citeck.ecos.model.converter.AbstractModuleConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Converting section module objects to local dto objects.
 *
 * @see ru.citeck.ecos.apps.app.module.type.model.section.SectionModule
 * @see ru.citeck.ecos.model.dto.SectionDto
 */
@Component
public class SectionModuleConverter extends AbstractModuleConverter<SectionModule, SectionDto> {

    @Override
    public SectionDto moduleToDto(SectionModule module) {

        SectionDto dto = new SectionDto();

        String id = extractIdFromModuleId(module.getId());
        dto.setId(id);

        dto.setName(module.getName());
        dto.setDescription(module.getDescription());
        dto.setTenant(Strings.EMPTY);

        List<ModuleRef> typesModuleRefs = module.getTypes();
        if (typesModuleRefs != null && !typesModuleRefs.isEmpty()) {
            Set<RecordRef> sectionRecordRefs = typesModuleRefs.stream()
                .map(t -> {
                    String typeId = extractIdFromModuleId(t.getId());
                    return RecordRef.create(TypeRecordsDao.ID, typeId);
                })
                .collect(Collectors.toSet());
            dto.setTypes(sectionRecordRefs);
        } else {
            dto.setTypes(Collections.emptySet());
        }

        return dto;
    }
}
