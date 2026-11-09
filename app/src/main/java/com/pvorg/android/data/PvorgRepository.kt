package com.pvorg.android.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pvorg.android.model.AppConfigDto
import com.pvorg.android.model.GalleryRecord
import com.pvorg.android.model.LoadedSession
import com.pvorg.android.model.SelectedRoot
import com.pvorg.android.model.TriState
import com.pvorg.android.model.VideoRecord
import com.pvorg.android.model.VideoRelationDto
import com.pvorg.android.util.FilenameParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PvorgRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    }

    suspend fun loadSession(rootUri: Uri): LoadedSession = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(appContext, rootUri)
            ?: throw IllegalStateException("Unable to open selected directory.")

        val configFile = root.findRequiredFile("pvorg.json")
        val videosDir = root.findRequiredDirectory("VID")
        val imageDir = videosDir.findRequiredDirectory("img")
        val galleriesDir = root.findRequiredDirectory("GAL")

        val config = loadConfig(configFile.uri)
        val galleries = scanGalleries(galleriesDir)
        val videos = scanVideos(videosDir, imageDir, config, galleries.map { it.name })

        LoadedSession(
            selectedRoot = SelectedRoot(
                rootUri = rootUri,
                rootName = root.name ?: "Selected Folder",
                configUri = configFile.uri,
                videosDirUri = videosDir.uri,
                imageDirUri = imageDir.uri,
                galleriesDirUri = galleriesDir.uri,
            ),
            config = config,
            videos = videos,
            galleries = galleries,
        )
    }

    suspend fun saveGalleryMappings(
        root: SelectedRoot,
        config: AppConfigDto,
        allGalleryNames: List<String>,
        changes: Map<String, Map<String, TriState>>,
    ): AppConfigDto = withContext(Dispatchers.IO) {
        val knownColumns = allGalleryNames.toSet()
        val updatedRelations = config.videoRelations.toMutableMap()

        for ((filename, fileChanges) in changes) {
            val relation = updatedRelations[filename] ?: VideoRelationDto(
                galleries = emptyList(),
                excludedGalleries = emptyList(),
                tags = emptyList(),
                excludedTags = emptyList(),
            )

            val yes = relation.galleries.orEmpty().toMutableSet()
            val no = relation.excludedGalleries.orEmpty().toMutableSet()

            for ((column, state) in fileChanges) {
                if (column !in knownColumns) continue
                when (state) {
                    TriState.YES -> {
                        yes += column
                        no -= column
                    }
                    TriState.NO -> {
                        no += column
                        yes -= column
                    }
                    TriState.UNDECIDED -> {
                        yes -= column
                        no -= column
                    }
                }
            }

            val preservedUnknownYes = relation.galleries.orEmpty().filter { it !in knownColumns && it in yes }
            val preservedUnknownNo = relation.excludedGalleries.orEmpty().filter { it !in knownColumns && it in no }

            updatedRelations[filename] = relation.copy(
                galleries = allGalleryNames.filter { it in yes } + preservedUnknownYes,
                excludedGalleries = allGalleryNames.filter { it in no } + preservedUnknownNo,
                tags = relation.tags.orEmpty(),
                excludedTags = relation.excludedTags.orEmpty(),
            )
        }

        val updatedConfig = config.copy(videoRelations = updatedRelations.toSortedMap())
        saveConfig(root.configUri, updatedConfig)
        updatedConfig
    }

    suspend fun saveTagMappings(
        root: SelectedRoot,
        config: AppConfigDto,
        allTags: List<String>,
        changes: Map<String, Map<String, TriState>>,
    ): AppConfigDto = withContext(Dispatchers.IO) {
        val knownColumns = allTags.toSet()
        val updatedRelations = config.videoRelations.toMutableMap()

        for ((filename, fileChanges) in changes) {
            val relation = updatedRelations[filename] ?: VideoRelationDto(
                galleries = emptyList(),
                excludedGalleries = emptyList(),
                tags = emptyList(),
                excludedTags = emptyList(),
            )

            val yes = relation.tags.orEmpty().toMutableSet()
            val no = relation.excludedTags.orEmpty().toMutableSet()

            for ((column, state) in fileChanges) {
                if (column !in knownColumns) continue
                when (state) {
                    TriState.YES -> {
                        yes += column
                        no -= column
                    }
                    TriState.NO -> {
                        no += column
                        yes -= column
                    }
                    TriState.UNDECIDED -> {
                        yes -= column
                        no -= column
                    }
                }
            }

            val preservedUnknownYes = relation.tags.orEmpty().filter { it !in knownColumns && it in yes }
            val preservedUnknownNo = relation.excludedTags.orEmpty().filter { it !in knownColumns && it in no }

            updatedRelations[filename] = relation.copy(
                galleries = relation.galleries.orEmpty(),
                excludedGalleries = relation.excludedGalleries.orEmpty(),
                tags = allTags.filter { it in yes } + preservedUnknownYes,
                excludedTags = allTags.filter { it in no } + preservedUnknownNo,
            )
        }

        val updatedConfig = config.copy(videoRelations = updatedRelations.toSortedMap())
        saveConfig(root.configUri, updatedConfig)
        updatedConfig
    }

    private fun loadConfig(configUri: Uri): AppConfigDto {
        val text = contentResolver.openInputStream(configUri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Unable to read pvorg.json.")
        return json.decodeFromString<AppConfigDto>(text)
    }

    private fun saveConfig(configUri: Uri, config: AppConfigDto) {
        val orderedRelations = config.videoRelations.toSortedMap()
        val encoded = json.encodeToString(
            AppConfigDto.serializer(),
            config.copy(videoRelations = orderedRelations),
        ) + "\n"
        contentResolver.openOutputStream(configUri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(encoded)
        } ?: throw IllegalStateException("Unable to write pvorg.json.")
    }

    private fun scanGalleries(galleriesDir: DocumentFile): List<GalleryRecord> {
        val imageExtensions = setOf("png", "jpg", "jpeg", "bmp", "gif", "webp")
        return galleriesDir.listFiles()
            .filter { it.isDirectory && !it.name.orEmpty().startsWith(".") }
            .sortedBy { it.name.orEmpty() }
            .map { galleryDir ->
                val imageUris = galleryDir.listFiles()
                    .filter { it.isFile && it.extension() in imageExtensions && !it.name.orEmpty().startsWith(".") }
                    .sortedBy { it.name.orEmpty() }
                    .map { it.uri }
                GalleryRecord(
                    name = galleryDir.name ?: "Unnamed",
                    imageUris = imageUris,
                )
            }
    }

    private fun scanVideos(
        videosDir: DocumentFile,
        imageDir: DocumentFile,
        config: AppConfigDto,
        allGalleryNames: List<String>,
    ): List<VideoRecord> {
        val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "webm")
        val tgpByName = imageDir.listFiles()
            .filter { it.isFile }
            .associateBy { it.name.orEmpty() }

        return videosDir.listFiles()
            .filter { it.isFile && it.extension() in videoExtensions && !it.name.orEmpty().startsWith(".") }
            .sortedBy { it.name.orEmpty() }
            .mapNotNull { videoFile ->
                val filename = videoFile.name ?: return@mapNotNull null
                val tgp = tgpByName["$filename.jpg"] ?: return@mapNotNull null
                val relation = config.videoRelations[filename]
                val parsed = FilenameParser.parse(filename)
                val excludedGalleries = relation?.excludedGalleries.orEmpty().toSet()
                val excludedTags = relation?.excludedTags.orEmpty().toSet()

                VideoRecord(
                    filename = filename,
                    title = parsed.title.ifBlank { filename },
                    studio = parsed.studio,
                    actors = parsed.actors,
                    videoUri = videoFile.uri,
                    tgpUri = tgp.uri,
                    relatedGalleries = allGalleryNames.filter { it !in excludedGalleries },
                    tags = config.tags.filter { it !in excludedTags },
                )
            }
    }
}

private fun DocumentFile.findRequiredDirectory(name: String): DocumentFile {
    return findFile(name)?.takeIf { it.isDirectory }
        ?: throw IllegalStateException("Missing required directory: $name")
}

private fun DocumentFile.findRequiredFile(name: String): DocumentFile {
    return findFile(name)?.takeIf { it.isFile }
        ?: throw IllegalStateException("Missing required file: $name")
}

private fun DocumentFile.extension(): String {
    return name.orEmpty().substringAfterLast('.', "").lowercase()
}
