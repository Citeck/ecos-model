package ru.citeck.ecos.model.num.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.citeck.ecos.commons.data.entity.EntityWithMeta;
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
public class NumTemplateWithMetaDto extends NumTemplateDto {

    @JsonIgnore
    @MetaAtt(RecordConstants.ATT_MODIFIER)
    private String modifier;
    @JsonIgnore
    @MetaAtt(RecordConstants.ATT_MODIFIED)
    private Instant modified;
    @JsonIgnore
    @MetaAtt(RecordConstants.ATT_CREATOR)
    private String creator;
    @JsonIgnore
    @MetaAtt(RecordConstants.ATT_CREATED)
    private Instant created;

    public NumTemplateWithMetaDto() {
    }

    public NumTemplateWithMetaDto(String id) {
        super(id);
    }

    public NumTemplateWithMetaDto(NumTemplateDto other) {
        super(other);
    }

    public NumTemplateWithMetaDto(EntityWithMeta<NumTemplateDef> other) {
        super(other.getEntity());
        this.modifier = other.getMeta().getModifier();
        this.modified = other.getMeta().getModified();
        this.creator = other.getMeta().getCreator();
        this.created = other.getMeta().getCreated();
    }

    public NumTemplateWithMetaDto(NumTemplateWithMetaDto other) {
        super(other);

        this.modifier = other.modifier;
        this.modified = other.modified;
        this.creator = other.creator;
        this.created = other.created;
    }
}
