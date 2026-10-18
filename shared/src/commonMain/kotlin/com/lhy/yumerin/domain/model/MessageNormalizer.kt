package com.lhy.yumerin.domain.model

import com.lhy.yumerin.core.model.MessagePart
import com.lhy.yumerin.core.model.ModelCapability
import com.lhy.yumerin.core.model.ModelProfile

/**
 * Normalizes message parts to what the selected model can consume.
 *
 * Images: passed through when the model declares VISION; otherwise the caller
 * must run an [ImageDescriber] first and the part degrades to a text
 * description note (the original file stays on the device, nothing is sent).
 * Audio: requires AUDIO_IN; otherwise the caller must transcribe first.
 * Files: never sent to the model; only their names surface in text.
 */
object MessageNormalizer {
    enum class ImagePlan { PASS_THROUGH, DESCRIBE_FIRST, UNAVAILABLE }
    enum class AudioPlan { PASS_THROUGH, TRANSCRIBE_FIRST, UNAVAILABLE }

    data class Plan(
        val imagePlan: ImagePlan = ImagePlan.PASS_THROUGH,
        val audioPlan: AudioPlan = AudioPlan.PASS_THROUGH,
        /** User-visible note when normalization degraded a part. */
        val normalizationNote: String? = null
    )

    fun plan(parts: List<MessagePart>, model: ModelProfile?): Plan {
        if (parts.none { it !is MessagePart.Text }) return Plan()
        var imagePlan = ImagePlan.PASS_THROUGH
        var audioPlan = AudioPlan.PASS_THROUGH
        var note: String? = null
        parts.forEach { part ->
            when (part) {
                is MessagePart.Image -> {
                    val vision = model?.capabilities?.contains(ModelCapability.VISION) == true
                    if (!vision) {
                        imagePlan = if (model == null) ImagePlan.UNAVAILABLE else ImagePlan.DESCRIBE_FIRST
                        note = (note?.let { "$it; " } ?: "") + "图片将先转为文字描述再发送"
                    }
                }
                is MessagePart.Audio -> {
                    val audioIn = model?.capabilities?.contains(ModelCapability.AUDIO_IN) == true
                    if (!audioIn) {
                        audioPlan = if (model == null) AudioPlan.UNAVAILABLE else AudioPlan.TRANSCRIBE_FIRST
                        note = (note?.let { "$it; " } ?: "") + "语音将先转写为文字再发送"
                    }
                }
                is MessagePart.File -> {
                    note = (note?.let { "$it; " } ?: "") + "附件不会直接发送给模型"
                }
                is MessagePart.Text -> Unit
            }
        }
        return Plan(imagePlan, audioPlan, note)
    }
}

/**
 * Produces a text description of an image when the model cannot see.
 * V1 ships the interface with no implementation wired; a local or remote
 * VLM backend plugs in here without changing message storage.
 */
fun interface ImageDescriber {
    suspend fun describe(imageUri: String): String?
}
