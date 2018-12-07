package org.briarproject.briar.headless

import com.fasterxml.jackson.databind.ObjectMapper
import dagger.Module
import dagger.Provides
import org.briarproject.bramble.api.crypto.PublicKey
import org.briarproject.bramble.api.db.DatabaseConfig
import org.briarproject.bramble.api.plugin.PluginConfig
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory
import org.briarproject.bramble.api.reporting.DevConfig
import org.briarproject.bramble.network.JavaNetworkModule
import org.briarproject.bramble.plugin.tor.CircumventionModule
import org.briarproject.bramble.system.JavaSystemModule
import org.briarproject.briar.headless.blogs.HeadlessBlogModule
import org.briarproject.briar.headless.contact.HeadlessContactModule
import org.briarproject.briar.headless.event.HeadlessEventModule
import org.briarproject.briar.headless.forums.HeadlessForumModule
import org.briarproject.briar.headless.messaging.HeadlessMessagingModule
import java.io.File
import java.util.Collections.emptyList
import javax.inject.Singleton

@Module(
    includes = [
        JavaNetworkModule::class,
        JavaSystemModule::class,
        CircumventionModule::class,
        HeadlessBlogModule::class,
        HeadlessContactModule::class,
        HeadlessEventModule::class,
        HeadlessForumModule::class,
        HeadlessMessagingModule::class
    ]
)
internal class HeadlessTestModule(private val appDir: File) {

    @Provides
    @Singleton
    internal fun provideBriarService(briarService: BriarTestServiceImpl): BriarService =
        briarService

    @Provides
    @Singleton
    internal fun provideDatabaseConfig(): DatabaseConfig {
        val dbDir = File(appDir, "db")
        val keyDir = File(appDir, "key")
        return HeadlessDatabaseConfig(dbDir, keyDir)
    }

    @Provides
    internal fun providePluginConfig(): PluginConfig {
        return object : PluginConfig {
            override fun getDuplexFactories(): Collection<DuplexPluginFactory> = emptyList()
            override fun getSimplexFactories(): Collection<SimplexPluginFactory> = emptyList()
            override fun shouldPoll(): Boolean = false
        }
    }

    @Provides
    @Singleton
    internal fun provideDevConfig(): DevConfig = object : DevConfig {
        override fun getDevPublicKey(): PublicKey = throw NotImplementedError()
        override fun getDevOnionAddress(): String = throw NotImplementedError()
        override fun getReportDir(): File = throw NotImplementedError()
    }

    @Provides
    @Singleton
    internal fun provideObjectMapper() = ObjectMapper()

}
