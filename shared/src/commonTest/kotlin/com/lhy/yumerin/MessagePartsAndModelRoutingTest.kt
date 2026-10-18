package com.lhy.yumerin

import com.lhy.yumerin.core.model.MessagePart
import com.lhy.yumerin.core.model.ModelCapability
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.core.model.decodeMessageParts
import com.lhy.yumerin.core.model.encodeMessageParts
import com.lhy.yumerin.domain.model.MessageNormalizer
import com.lhy.yumerin.domain.model.ModelPurpose
import com.lhy.yumerin.domain.model.ModelRouter
import com.lhy.yumerin.domain.repository.ModelProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessagePartsAndModelRoutingTest {
    @Test
    fun messagePartsRoundTripLosslessly() {
        val parts = listOf(
            MessagePart.Text("看看这个"),
            MessagePart.Image("file:///data/0/img.png"),
            MessagePart.Audio("file:///data/0/voice.m4a", 3_500L),
            MessagePart.File("file:///data/0/doc.pdf", "doc.pdf", 12_345L)
        )

        val decoded = decodeMessageParts(encodeMessageParts(parts))

        assertEquals(4, decoded.size)
        assertEquals("看看这个", (decoded[0] as MessagePart.Text).text)
        assertEquals("file:///data/0/img.png", (decoded[1] as MessagePart.Image).uri)
        assertEquals(3_500L, (decoded[2] as MessagePart.Audio).durationMillis)
        assertEquals("doc.pdf", (decoded[3] as MessagePart.File).name)
        assertEquals(12_345L, (decoded[3] as MessagePart.File).sizeBytes)
        assertEquals(emptyList<MessagePart>(), decodeMessageParts(""))
    }

    @Test
    fun normalizerPlansDescribeFirstWithoutVision() {
        val visionModel = model(capabilities = setOf(ModelCapability.VISION))
        val textModel = model(capabilities = emptySet())
        val parts = listOf(MessagePart.Text("看"), MessagePart.Image("file:///img.png"))

        assertEquals(MessageNormalizer.ImagePlan.PASS_THROUGH, MessageNormalizer.plan(parts, visionModel).imagePlan)
        val degraded = MessageNormalizer.plan(parts, textModel)
        assertEquals(MessageNormalizer.ImagePlan.DESCRIBE_FIRST, degraded.imagePlan)
        assertTrue(degraded.normalizationNote?.contains("文字描述") == true)
    }

    @Test
    fun routerPrefersCapabilityMatchAndReportsFallback() = runTest {
        val plain = model("model-plain", capabilities = emptySet())
        val vision = model("model-vision", capabilities = setOf(ModelCapability.VISION))
        val router = ModelRouter(FakeModelRepository(listOf(plain, vision)))

        val exact = router.select(
            ModelPurpose.CONVERSATION,
            preferredProfileId = plain.id,
            requiredCapabilities = setOf(ModelCapability.VISION)
        )
        assertEquals("model-vision", exact.profile?.id)
        assertEquals("preferred-model-missing", exact.fallbackReason)

        val preferred = router.select(ModelPurpose.CONVERSATION, preferredProfileId = plain.id)
        assertEquals("model-plain", preferred.profile?.id)
        assertNull(preferred.fallbackReason)

        val impossible = router.select(
            ModelPurpose.VOICE,
            preferredProfileId = plain.id,
            requiredCapabilities = setOf(ModelCapability.AUDIO_IN)
        )
        assertEquals("model-plain", impossible.profile?.id)
        assertEquals("capabilities-unavailable", impossible.fallbackReason)
    }

    private fun model(
        id: String = "model-test",
        capabilities: Set<ModelCapability>
    ) = ModelProfile(
        id = id, displayName = id, providerKind = com.lhy.yumerin.core.model.ModelProviderKind.OPENAI_COMPATIBLE,
        providerName = "test", baseUrl = null, modelId = "test", credentialAlias = null,
        contextLength = 8_192,
        contextLengthMode = com.lhy.yumerin.core.model.ContextLengthMode.KNOWN,
        generationParameters = com.lhy.yumerin.core.model.GenerationParameters(),
        localModelId = null, localModelVersion = null, enabled = true,
        createdAt = 0L, updatedAt = 0L, capabilities = capabilities
    )

    private class FakeModelRepository(
        profiles: List<ModelProfile>
    ) : ModelProfileRepository {
        private val state = MutableStateFlow(profiles)
        override fun observeProfiles(): Flow<List<ModelProfile>> = state
        override fun observeLocalArtifacts(): Flow<List<com.lhy.yumerin.core.model.LocalModelArtifact>> =
            MutableStateFlow(emptyList())
        override suspend fun getProfiles(): List<ModelProfile> = state.value
        override suspend fun getLocalArtifacts(): List<com.lhy.yumerin.core.model.LocalModelArtifact> = emptyList()
        override suspend fun getProfile(id: String): ModelProfile? = state.value.firstOrNull { it.id == id }
        override suspend fun getLocalArtifact(id: String): com.lhy.yumerin.core.model.LocalModelArtifact? = null
        override suspend fun saveProfile(profile: ModelProfile) {
            state.value = state.value.filterNot { it.id == profile.id } + profile
        }
        override suspend fun deleteProfile(id: String) {
            state.value = state.value.filterNot { it.id == id }
        }
        override suspend fun saveLocalArtifact(artifact: com.lhy.yumerin.core.model.LocalModelArtifact) = Unit
        override suspend fun deleteLocalArtifact(id: String) = Unit
    }
}
