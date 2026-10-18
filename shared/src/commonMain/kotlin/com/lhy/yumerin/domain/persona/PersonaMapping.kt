package com.lhy.yumerin.domain.persona

import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.PersonaPackage

/** Bidirectional mapping between the persona asset and the editable character profile. */
object PersonaMapping {
    /** Derives the persona asset from the product's editable character profile. */
    fun fromCharacterProfile(profile: CharacterProfile): PersonaPackage = PersonaPackage(
        name = profile.name,
        description = profile.description,
        personality = profile.personality,
        scenario = profile.setting,
        relationshipStateTemplate = profile.relationshipFraming
    )

    /**
     * Maps an imported persona onto an existing profile, keeping identity and
     * lifecycle fields (id, avatar, model assignment, archive state) intact.
     */
    fun applyTo(profile: CharacterProfile, persona: PersonaPackage): CharacterProfile = profile.copy(
        name = persona.name.ifBlank { profile.name },
        description = persona.description,
        personality = persona.personality,
        setting = persona.scenario,
        backstory = buildString {
            if (persona.description.isNotBlank()) append(persona.description)
            if (persona.mesExample.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(persona.mesExample)
            }
        },
        relationshipFraming = persona.relationshipStateTemplate?.takeIf { it.isNotBlank() }
            ?: profile.relationshipFraming
    )
}
