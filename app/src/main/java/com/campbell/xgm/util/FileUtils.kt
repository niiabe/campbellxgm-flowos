package com.campbell.xgm.util

import java.io.File

object FileUtils {
    fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    fun deleteDir(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return false
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { deleteDir(it) }
        }
        return dir.delete()
    }
}
