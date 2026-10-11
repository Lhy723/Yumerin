package com.lhy.yumerin.data.local

import android.content.Context
import com.lhy.yumerin.domain.repository.AvatarStore
import java.io.File

class AndroidAvatarStore(context: Context) : AvatarStore {
    private val filesRoot = context.filesDir.canonicalFile

    override suspend fun delete(uri: String) {
        runCatching {
            val file = File(uri).canonicalFile
            if (file.path.startsWith(filesRoot.path + File.separator)) file.delete()
        }
    }
}
