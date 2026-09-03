package com.dentalstudio.notes.data.repo

import com.dentalstudio.notes.data.db.RuleEntity
import com.dentalstudio.notes.data.db.StyleProfileEntity
import com.dentalstudio.notes.learning.LearnedRule
import com.dentalstudio.notes.learning.MinedRule
import com.dentalstudio.notes.learning.RuleType
import com.dentalstudio.notes.learning.StyleProfile

fun RuleEntity.toDomain(): LearnedRule = LearnedRule(
    id = id,
    type = runCatching { RuleType.valueOf(type) }.getOrDefault(RuleType.TERM),
    scope = scope,
    pattern = pattern,
    replacement = replacement,
    occurrences = occurrences,
    contradictions = contradictions,
    enabled = enabled,
    exampleBefore = exampleBefore,
    exampleAfter = exampleAfter,
    createdAt = createdAt,
    lastSeenAt = lastSeenAt,
)

fun MinedRule.toEntity(now: Long): RuleEntity = RuleEntity(
    type = type.name,
    scope = scope,
    pattern = pattern,
    replacement = replacement,
    occurrences = 1,
    contradictions = 0,
    enabled = true,
    exampleBefore = exampleBefore,
    exampleAfter = exampleAfter,
    createdAt = now,
    lastSeenAt = now,
)

fun StyleProfileEntity.toDomain(): StyleProfile = StyleProfile(
    scope = scope,
    samples = samples,
    avgDraftWords = avgDraftWords,
    avgFinalWords = avgFinalWords,
    avgBulletRatio = avgBulletRatio,
    avgSentenceLength = avgSentenceLength,
    avgSimilarity = avgSimilarity,
    sectionOrder = sectionOrder.split('|').filter { it.isNotBlank() },
    updatedAt = updatedAt,
)

fun StyleProfile.toEntity(): StyleProfileEntity = StyleProfileEntity(
    scope = scope,
    samples = samples,
    avgDraftWords = avgDraftWords,
    avgFinalWords = avgFinalWords,
    avgBulletRatio = avgBulletRatio,
    avgSentenceLength = avgSentenceLength,
    avgSimilarity = avgSimilarity,
    sectionOrder = sectionOrder.joinToString("|"),
    updatedAt = updatedAt,
)

const val GLOBAL_SCOPE = MinedRule.SCOPE_ALL
