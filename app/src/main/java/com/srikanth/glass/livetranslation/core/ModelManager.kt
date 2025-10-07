package com.srikanth.glass.livetranslation.core

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.InputStream

class ModelManager(private val context: Context) {
    data class ModelPack(
        val id: String,
        val sizeBytes: Long
    )

    private val modelsDir: File by lazy { File(context.filesDir, "models").apply { mkdirs() } }

    fun getModelDir(modelId: String): File = File(modelsDir, modelId)

    fun listInstalledPacks(): List<ModelPack> {
        if (!modelsDir.exists()) return emptyList()
        return modelsDir.listFiles()?.map { dir ->
            ModelPack(dir.name, dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum())
        } ?: emptyList()
    }

    fun deletePack(modelId: String): Boolean {
        val dir = getModelDir(modelId)
        if (!dir.exists()) return false
        return dir.deleteRecursively()
    }

    fun assetExists(assetPath: String): Boolean {
        val assetManager = context.assets
        val pathSegments = assetPath.split('/').filter { it.isNotEmpty() }
        val parent = pathSegments.dropLast(1).joinToString("/")
        val name = pathSegments.lastOrNull() ?: return false
        return try {
            assetManager.list(parent)?.contains(name) == true
        } catch (_: Exception) {
            false
        }
    }

    fun installFromAssets(assetDir: String, modelId: String): File? {
        val destDir = getModelDir(modelId)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        copyAssetDir(context.assets, assetDir, destDir)
        return destDir.takeIf { it.exists() }
    }

    private fun copyAssetDir(assetManager: AssetManager, assetDir: String, destDir: File) {
        val items = assetManager.list(assetDir) ?: return
        for (item in items) {
            val sourcePath = if (assetDir.isBlank()) item else "$assetDir/$item"
            val destFile = File(destDir, item)
            if (assetManager.list(sourcePath)?.isNotEmpty() == true) {
                destFile.mkdirs()
                copyAssetDir(assetManager, sourcePath, destFile)
            } else {
                assetManager.open(sourcePath).use { input ->
                    copyStream(input, destFile)
                }
            }
        }
    }

    private fun copyStream(input: InputStream, destFile: File) {
        destFile.parentFile?.mkdirs()
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}
