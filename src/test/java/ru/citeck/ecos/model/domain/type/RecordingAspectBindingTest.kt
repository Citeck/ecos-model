package ru.citeck.ecos.model.domain.type

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.json.YamlUtils
import ru.citeck.ecos.model.domain.type.testutils.TypeTestBase
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.io.File

/**
 * recording-aspect moved from citeck-ai to ecos-model (COREDEV-511).
 * The aspect and its binding to call/meeting activities are now shipped by emodel itself,
 * instead of an aspect artifact plus two artifact-patches owned by citeck-ai.
 *
 * [TypeTestBase] exposes the resolved type, but its aspects always come back empty — the
 * artifact-based registry path does not merge them (the same holds for the history-config
 * aspect that planned-activity has declared inline all along). The binding is therefore
 * asserted on the raw type artifact plus the aspects registry: the declared ref must resolve
 * to an aspect emodel actually ships, so a deleted or renamed aspect fails the test.
 * End-to-end resolution of the binding is covered by the acceptance checks on a stand.
 */
class RecordingAspectBindingTest : TypeTestBase() {

    companion object {
        private const val RECORDING_ASPECT_ID = "recording-aspect"
        private const val TYPES_DIR = "./src/main/resources/eapps/artifacts/model/type/activity"

        private val RECORDING_ATTS = listOf(
            "recording",
            "transcription",
            "transcriptionDiarized",
            "summary",
            "recordingDuration",
            "recordingStatus",
            "callPlatform",
            "meetingUrl"
        )
    }

    @Test
    fun aspectShippedByEmodelTest() {
        val aspectInfo = aspectsRegistry.getAspectInfo(ModelUtils.getAspectRef(RECORDING_ASPECT_ID))
        assertThat(aspectInfo).isNotNull
        assertThat(aspectInfo!!.attributes.map { it.id })
            .containsExactlyElementsOf(RECORDING_ATTS.map { "$RECORDING_ASPECT_ID:$it" })
    }

    @ParameterizedTest
    @ValueSource(strings = ["call-activity", "meeting-activity"])
    fun recordingAspectBoundInlineTest(typeId: String) {
        val refs = readTypeArtifact(typeId).aspects.map { it.ref }
        assertThat(refs).containsExactly(ModelUtils.getAspectRef(RECORDING_ASPECT_ID))
        // a binding to an aspect emodel does not ship is dead weight, so follow the ref
        assertThat(aspectsRegistry.getAspectInfo(refs.single())).isNotNull
    }

    @Test
    fun aspectNotBoundToParentTest() {
        // the binding is inline in the two leaf types, the parent must stay untouched
        assertThat(readTypeArtifact("planned-activity").aspects.map { it.ref })
            .doesNotContain(ModelUtils.getAspectRef(RECORDING_ASPECT_ID))
    }

    private fun readTypeArtifact(typeId: String): TypeDef {
        val data = DataValue.of(YamlUtils.read(File("$TYPES_DIR/$typeId.yml").readText()))
        return data.getAs(TypeDef::class.java)!!
    }
}
