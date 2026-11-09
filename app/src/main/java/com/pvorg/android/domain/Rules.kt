package com.pvorg.android.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random

/** Reads and writes `pvorg.json` per spec §1.2. */
object ConfigCodec {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = false
    }

    class InvalidConfig(message: String) : Exception(message)

    /** `videosPath` and `galleriesPath` are optional here because Android ignores them (spec §1.1). */
    private val requiredKeys = listOf("tags", "videoRelations")

    fun decode(text: String): AppConfig = try {
        val root = json.parseToJsonElement(text).jsonObject
        requiredKeys.firstOrNull { it !in root }?.let { missing ->
            throw InvalidConfig("pvorg.json isn’t valid: the required key “$missing” is missing.")
        }
        json.decodeFromJsonElement(AppConfig.serializer(), root)
    } catch (error: InvalidConfig) {
        throw error
    } catch (error: MissingFieldException) {
        throw InvalidConfig("pvorg.json isn’t valid: the required key “${error.missingFields.firstOrNull()}” is missing.")
    } catch (error: SerializationException) {
        throw InvalidConfig("pvorg.json isn’t valid: ${error.message?.lineSequence()?.firstOrNull()?.trim()}")
    } catch (error: IllegalArgumentException) {
        throw InvalidConfig("pvorg.json isn’t valid: ${error.message?.lineSequence()?.firstOrNull()?.trim()}")
    }

    /** Pretty-printed, keys sorted, trailing newline. */
    fun encode(config: AppConfig): String =
        json.encodeToString(AppConfig.serializer(), config.copy(videoRelations = config.videoRelations.toSortedMap())) + "\n"

    /** Removes entries from `cache.json`, keeping everything else; `null` when nothing changed. */
    fun removingCacheEntries(text: String, filenames: Set<String>): String? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        if (filenames.none { it in root }) return null
        val kept = JsonObject(root.filterKeys { it !in filenames }.toSortedMap())
        return json.encodeToString(JsonObject.serializer(), kept) + "\n"
    }
}

/** Every write to `pvorg.json` goes through these pure functions (spec §1.2, §3). */
object ConfigRules {
    /** Fills `null` arrays with `[]`; returns the config and how many entries changed. */
    fun normalized(config: AppConfig): Pair<AppConfig, Int> {
        var changed = 0
        val relations = config.videoRelations.mapValues { (_, relation) ->
            val normal = VideoRelation(
                excludedGalleries = relation.excludedGalleries ?: emptyList(),
                excludedTags = relation.excludedTags ?: emptyList(),
                galleries = relation.galleries ?: emptyList(),
                tags = relation.tags ?: emptyList(),
            )
            if (normal != relation) changed++
            normal
        }
        return config.copy(videoRelations = relations) to changed
    }

    /**
     * Applies pending cell changes (spec §3.3): known names in vocabulary
     * order, unknown values kept after them, the other relation pair untouched.
     */
    fun applyingMappings(
        config: AppConfig,
        kind: MappingKind,
        knownColumns: List<String>,
        changes: Map<String, Map<String, TriState>>,
    ): AppConfig {
        val known = knownColumns.toSet()
        val relations = config.videoRelations.toMutableMap()
        for ((filename, fileChanges) in changes) {
            val relation = relations[filename] ?: VideoRelation.Empty
            val originalYes = if (kind == MappingKind.GALLERIES) relation.yesGalleries else relation.yesTags
            val originalNo = if (kind == MappingKind.GALLERIES) relation.noGalleries else relation.noTags
            val yes = originalYes.toMutableSet()
            val no = originalNo.toMutableSet()
            for ((column, state) in fileChanges) {
                if (column !in known) continue
                when (state) {
                    TriState.YES -> { yes += column; no -= column }
                    TriState.NO -> { no += column; yes -= column }
                    TriState.UNDECIDED -> { yes -= column; no -= column }
                }
            }
            val newYes = knownColumns.filter { it in yes } + originalYes.filter { it !in known && it in yes }
            val newNo = knownColumns.filter { it in no } + originalNo.filter { it !in known && it in no }
            relations[filename] = when (kind) {
                MappingKind.GALLERIES -> VideoRelation(
                    excludedGalleries = newNo,
                    excludedTags = relation.excludedTags ?: emptyList(),
                    galleries = newYes,
                    tags = relation.tags ?: emptyList(),
                )
                MappingKind.TAGS -> VideoRelation(
                    excludedGalleries = relation.excludedGalleries ?: emptyList(),
                    excludedTags = newNo,
                    galleries = relation.galleries ?: emptyList(),
                    tags = newYes,
                )
            }
        }
        return config.copy(videoRelations = relations)
    }

    /** Writes "No" for the gallery, exactly as Gallery Mapping would (spec §3.6). */
    fun excludingPairing(config: AppConfig, filename: String, gallery: String, knownGalleries: List<String>): Pair<AppConfig, PairingExclusion> {
        val relation = config.videoRelations[filename]
        val previous = when {
            relation?.yesGalleries?.contains(gallery) == true -> TriState.YES
            relation?.noGalleries?.contains(gallery) == true -> TriState.NO
            else -> TriState.UNDECIDED
        }
        val updated = applyingMappings(config, MappingKind.GALLERIES, knownGalleries, mapOf(filename to mapOf(gallery to TriState.NO)))
        return updated to PairingExclusion(filename, gallery, previous, hadRelation = relation != null, index = null)
    }

    /** Undoes [excludingPairing], removing an entry it created if nothing else was decided since. */
    fun restoringPairing(config: AppConfig, exclusion: PairingExclusion, knownGalleries: List<String>): AppConfig {
        var updated = applyingMappings(
            config,
            MappingKind.GALLERIES,
            knownGalleries,
            mapOf(exclusion.filename to mapOf(exclusion.galleryName to exclusion.previousState)),
        )
        val relation = updated.videoRelations[exclusion.filename]
        if (!exclusion.hadRelation && relation != null && relation.isEmpty) {
            updated = updated.copy(videoRelations = updated.videoRelations - exclusion.filename)
        }
        return updated
    }

    fun staleEntries(config: AppConfig, filesOnDisk: Set<String>): List<String> =
        config.videoRelations.keys.filter { it !in filesOnDisk }.sorted()

    fun removingEntries(config: AppConfig, filenames: Collection<String>): AppConfig =
        config.copy(videoRelations = config.videoRelations - filenames.toSet())
}

/** Combination rules (spec §3.1). */
object CombinationEngine {
    fun includes(video: Video, filters: Filters): Boolean =
        video.studio in filters.studios &&
            (filters.cast.isEmpty() || video.actors.containsAll(filters.cast)) &&
            (filters.tags.isEmpty() || video.tags.containsAll(filters.tags))

    fun includes(gallery: Gallery, video: Video, filters: Filters): Boolean =
        gallery.name in filters.galleries && gallery.name in video.relatedGalleries

    fun generate(library: Library, filters: Filters, random: Random = Random.Default): List<Combination> = buildList {
        for (video in library.videos) {
            if (!includes(video, filters)) continue
            for (gallery in library.galleries) {
                if (includes(gallery, video, filters)) add(Combination(video, gallery))
            }
        }
    }.shuffled(random)
}
