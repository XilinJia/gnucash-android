/*
 * Copyright (c) 2016 Ngewi Fet <ngewif@gmail.com>
 * Copyright (C) 2022 Xilin Jia https://github.com/XilinJia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.util

import android.util.Log
import org.gnucash.android.db.MigrationHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

/**
 * Moves all files from one directory  into another.
 * The destination directory is assumed to already exist
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */

class RecursiveMoveFiles
/**
 * Constructor, specify origin and target directories
 * @param src Source directory/file. If directory, all files within it will be moved
 * @param dst Destination directory/file. If directory, it should already exist
 */(var mSource: File, var mDestination: File) : Runnable {
    /**
     * Copy file from one location to another.
     * Does not support copying of directories
     * @param src Source file
     * @param dst Destination of the file
     * @return `true` if the file was successfully copied, `false` otherwise
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun copy(src: File, dst: File): Boolean {
        val inChannel = FileInputStream(src).channel
        val outChannel = FileOutputStream(dst).channel
        return try {
            val bytesCopied = inChannel!!.transferTo(0, inChannel.size(), outChannel)
            bytesCopied >= src.length()
        } finally {
            inChannel?.close()
            outChannel.close()
        }
    }

    /**
     * Recursively copy files from one location to another and deletes the origin files after copy.
     * If the source file is a directory, all of the files in it will be moved.
     * This method will create the destination directory if the `src` is also a directory
     * @param src origin file
     * @param dst destination file or directory
     * @return number of files copied (excluding parent directory)
     */
    private fun recursiveMove(src: File, dst: File): Int {
        var copyCount = 0
        if (src.isDirectory && src.listFiles() != null) {
            dst.mkdirs() //we assume it works everytime. Great, right?
            for (file in Objects.requireNonNull(src.listFiles())) {
                val target = File(dst, file.name)
                copyCount += recursiveMove(file, target)
            }
            src.delete()
        } else {
            try {
                if (copy(src, dst)) src.delete()
            } catch (e: IOException) {
                Log.d(MigrationHelper.LOG_TAG, "Error moving file: " + src.absolutePath)
            }
        }
        Log.d("RecursiveMoveFiles", String.format("Moved %d files from %s to %s", copyCount, src.path, dst.path))
        return copyCount
    }

    override fun run() {
        recursiveMove(mSource, mDestination)
    }
}