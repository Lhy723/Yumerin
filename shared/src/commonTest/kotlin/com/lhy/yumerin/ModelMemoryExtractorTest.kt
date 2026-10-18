package com.lhy.yumerin

import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.MemoryCategory
import com.lhy.yumerin.core.model.StructuredFact
import com.lhy.yumerin.domain.memory.ExtractedMemoryFact
import com.lhy.yumerin.domain.memory.HybridMemoryExtractor
import com.lhy.yumerin.domain.memory.MemoryExtractor
import com.lhy.yumerin.domain.memory.ModelMemoryExtractor
import com.lhy.yumerin.domain.model.ModelPurpose
import com.lhy.yumerin.domain.model.ModelRouter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelMemoryExtractorTest {
    @Test
    fun parsesFactArrayWithStructuredTriple() = runTest {
        val response = """[{"content":"用户喜欢手冲咖啡","category":"preference","confidence":0.9,
            "importance":0.8,"emotionTag":"开心","subject":"用户","predicate":"喜欢","value":"手冲咖啡"}]"""
        val extractor = ModelMemoryExtractor(routerWithModel()) { _ -> response }

        val facts = extractor.extract("我喜欢手冲咖啡")

        assertEquals(1, facts.size)
        assertEquals("用户喜欢手冲咖啡", facts.single().content)
        assertEquals(MemoryCategory.USER_PREFERENCE, facts.single().category)
        assertEquals(0.9f, facts.single().confidence)
        assertEquals("开心", facts.single().emotionTag)
        assertEquals(StructuredFact("用户", "喜欢", "手冲咖啡"), facts.single().structuredFact)
    }

    @Test
    fun nonArrayOrGarbageResponsesYieldNothing() = runTest {
        val extractor = ModelMemoryExtractor(routerWithModel()) { _ -> "模型运行时还没有连接，请先完成模型配置。" }
        assertTrue(extractor.extract("随便聊聊").isEmpty())
        assertTrue(ModelMemoryExtractor(routerWithModel()) { _ -> "{broken" }.extract("x").isEmpty())
    }

    @Test
    fun hybridMergesLexicalFirstAndDedupesModelFacts() = runTest {
        val lexical = MemoryExtractor { _ ->
            listOf(ExtractedMemoryFact("用户喜欢茶", MemoryCategory.USER_PREFERENCE, 0.92f))
        }
        val model = MemoryExtractor { _ ->
            listOf(
                ExtractedMemoryFact("用户喜欢茶", MemoryCategory.USER_PREFERENCE, 0.6f),
                ExtractedMemoryFact("用户的生日在四月", MemoryCategory.IMPORTANT_EVENT, 0.85f, importance = 0.9f)
            )
        }
        val hybrid = HybridMemoryExtractor(lexical, model)

        val merged = hybrid.extract("任意文本")

        assertEquals(2, merged.size)
        assertEquals("用户喜欢茶", merged[0].content)
        assertEquals(0.92f, merged[0].confidence)
        assertEquals("用户的生日在四月", merged[1].content)
    }

    @Test
    fun missingModelYieldsNothingInsteadOfFailing() = runTest {
        val router = ModelRouter(FakeRepo(emptyList()))
        val extractor = ModelMemoryExtractor(router) { _ -> error("should not be called") }
        assertTrue(extractor.extract("我喜欢咖啡").isEmpty())
    }

    private fun routerWithModel(): ModelRouter = ModelRouter(
        FakeRepo(
            listOf(
                com.lhy.yumerin.core.model.ModelProfile(
                    id = "m", displayName = "cheap", providerKind = com.lhy.yumerin.core.model.ModelProviderKind.OPENAI_COMPATIBLE,
                    providerName = "test", baseUrl = null, modelId = "m", credentialAlias = null,
                    contextLength = null,
                    contextLengthMode = com.lhy.yumerin.core.model.ContextLengthMode.AUTO,
                    generationParameters = com.lhy.yumerin.core.model.GenerationParameters(),
                    localModelId = null, localModelVersion = null, enabled = true,
                    createdAt = 0L, updatedAt = 0L
                )
            )
        )
    )

    private class FakeRepo(private val profiles: List<com.lhy.yumerin.core.model.ModelProfile>) :
        com.lhy.yumerin.domain.repository.ModelProfileRepository {
        private val flow = kotlinx.coroutines.flow.MutableStateFlow(profiles)
        override fun observeProfiles() = flow
        override fun observeLocalArtifacts() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.lhy.yumerin.core.model.LocalModelArtifact>())
        override suspend fun getProfiles() = profiles
        override suspend fun getLocalArtifacts() = emptyList<com.lhy.yumerin.core.model.LocalModelArtifact>()
        override suspend fun getProfile(id: String) = profiles.firstOrNull { it.id == id }
        override suspend fun getLocalArtifact(id: String) = null
        override suspend fun saveProfile(profile: com.lhy.yumerin.core.model.ModelProfile) {}
        override suspend fun saveLocalArtifact(artifact: com.lhy.yumerin.core.model.LocalModelArtifact) {}
        override suspend fun deleteProfile(id: String) {}
        override suspend fun deleteLocalArtifact(id: String) {}
    }
}
