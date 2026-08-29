package com.flockyou.shannon

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager

/**
 * Narrow libsu root service exporting only the root process' file-system binder.
 *
 * The client opens the Shannon diagnostic character device through libsu NIO, keeping
 * privileged I/O inside libsu's managed root process instead of spawning an independent
 * `su -c cat` process. This service is bound only after an explicit root grant exists.
 */
class ShannonRootFileService : RootService() {
    override fun onBind(intent: Intent): IBinder = FileSystemManager.getService()
}
