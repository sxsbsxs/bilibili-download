package com.sxsbsxs.bilibilidownload.core.download

import android.content.Context

/** Process-wide coordinator shared by the UI bridge and the foreground download service. */
object DownloadTaskRuntime {
    @Volatile private var coordinator: DownloadTaskCoordinator? = null

    fun get(context: Context): DownloadTaskCoordinator {
        return coordinator ?: synchronized(this) {
            coordinator ?: DownloadTaskCoordinator(context.applicationContext).also { coordinator = it }
        }
    }
}
