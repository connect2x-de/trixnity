package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.MSC4027
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

@OptIn(MSC4027::class)
class ReactionEventContentTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    private val imageKey = "mxc://matrix.org/VOczFYqjdGaUKNwkKsTjDwUa"
    private val relation = RelatesTo.Annotation(EventId("\$abcdefg"), imageKey)

    @Test
    fun `serialize image reaction with unstable shortcode`() {
        val content = ReactionEventContent(relation, shortcode = ":partyparrot:")
        val encoded = json.encodeToString(content)
        json.parseToJsonElement(encoded) shouldBe
            json.parseToJsonElement(
                """
            {
                "m.relates_to": {
                    "rel_type": "m.annotation",
                    "event_id": "${'$'}abcdefg",
                    "key": "$imageKey"
                },
                "com.beeper.reaction.shortcode": ":partyparrot:"
            }
            """
            )
        json.decodeFromString<ReactionEventContent>(encoded) shouldBe content
    }

    @Test
    fun `deserialize proposed shortcode name`() {
        val encoded = json.encodeToString(ReactionEventContent(relation, shortcode = ":partyparrot:"))
        json
            .decodeFromString<ReactionEventContent>(encoded.replace("com.beeper.reaction.shortcode", "shortcode"))
            .shortcode shouldBe ":partyparrot:"
    }

    @Test
    fun `preserve keys without a shortcode`() {
        listOf("👍", imageKey, "mxc://", "mxc://matrix.org/missing").forEach { key ->
            val content = ReactionEventContent(RelatesTo.Annotation(EventId("event"), key))
            val encoded = json.encodeToString(content)
            encoded.contains("shortcode") shouldBe false
            json.decodeFromString<ReactionEventContent>(encoded) shouldBe content
        }
    }

    @Test
    fun `copy relation preserves shortcode`() {
        val updatedRelation = RelatesTo.Annotation(EventId("other"), imageKey)
        ReactionEventContent(relation, shortcode = ":partyparrot:").copyWith(updatedRelation) shouldBe
            ReactionEventContent(updatedRelation, shortcode = ":partyparrot:")
    }
}
