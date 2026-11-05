package studio.vela.art.app

import android.content.Context
import studio.vela.art.core.export.Exporter
import studio.vela.art.data.db.VelaDatabase
import studio.vela.art.data.repository.ProjectRepository
import studio.vela.art.data.settings.SettingsRepository
import studio.vela.art.data.storage.ProjectStorage

/**
 * Manual dependency container.
 *
 * A DI framework would add a build-time annotation processor and indirection for what is a
 * five-object graph, so the graph is wired by hand.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: VelaDatabase = VelaDatabase.get(appContext)
    val projectStorage: ProjectStorage = ProjectStorage(appContext)
    val projectRepository: ProjectRepository = ProjectRepository(
        context = appContext,
        dao = database.projectDao(),
        storage = projectStorage,
    )
    val settingsRepository: SettingsRepository = SettingsRepository(appContext)
    val exporter: Exporter = Exporter(appContext, projectStorage)
}
