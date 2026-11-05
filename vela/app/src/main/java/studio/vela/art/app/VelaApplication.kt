package studio.vela.art.app

import android.app.Application

class VelaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Remove stale temporary archives so a crashed save cannot accumulate storage.
        container.projectRepository.cleanupTemporaryFiles()
    }
}
