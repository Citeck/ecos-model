import kotlin.Unit
import org.jetbrains.annotations.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import ru.citeck.ecos.apps.artifact.ArtifactMeta
import ru.citeck.ecos.apps.artifact.controller.ArtifactController
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.io.file.EcosFile
import ru.citeck.ecos.commons.json.YamlUtils
import ru.citeck.ecos.commons.utils.ZipUtils

import javax.xml.parsers.ParserConfigurationException
import java.util.stream.Collectors

class WorkspaceTemplateArtifact {
    String id
    DataValue meta
    byte[] artifacts
}

return new ArtifactController<WorkspaceTemplateArtifact, Unit>() {

    private static final Logger log = LoggerFactory.getLogger(ArtifactController.class)

    @Override
    List<WorkspaceTemplateArtifact> read(@NotNull EcosFile root, Unit config) {

        return root.findFiles("**/meta.yml")
            .stream()
            .map(this.&readArtifact)
            .collect(Collectors.toList())
    }

    private WorkspaceTemplateArtifact readArtifact(EcosFile metaFile) {

        try {

            WorkspaceTemplateArtifact artifact = new WorkspaceTemplateArtifact()
            artifact.id = metaFile.parent.name
            artifact.meta = DataValue.create(YamlUtils.read(metaFile))

            def artifactsDir = metaFile.parent.getDir("artifacts")

            if (artifactsDir != null) {
                artifact.artifacts = ZipUtils.writeZipAsBytes(artifactsDir)
            } else {
                artifact.artifacts = null
            }
            return artifact

        } catch (ParserConfigurationException | IOException | SAXException e) {
            log.error("Workspace template reading error. File: " + metaFile.getPath(), e)
            throw new RuntimeException(e)
        }
    }

    @Override
    void write(@NotNull EcosFile root, WorkspaceTemplateArtifact artifact, Unit config) {

        EcosFile dir = root.createDir(artifact.id)

        dir.createFile("meta.yml", YamlUtils.INSTANCE.toString(artifact.meta))
        if (artifact.artifacts != null) {
            ZipUtils.extractZip(new ByteArrayInputStream(artifact.artifacts), dir.createDir("artifacts"))
        }
    }

    @Override
    ArtifactMeta getMeta(@NotNull WorkspaceTemplateArtifact artifact, @NotNull Unit unit) {
        return ArtifactMeta.create()
            .withId(artifact.id)
            .withName(artifact.meta.get("name").getAs(MLText.class) ?: new MLText(artifact.id))
            .build()
    }
}
