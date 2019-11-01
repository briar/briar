package org.briarproject.briar.headless

import com.fasterxml.jackson.databind.ObjectMapper
import dagger.Module
import dagger.Provides
import org.briarproject.bramble.api.FeatureFlags
import org.briarproject.bramble.api.battery.BatteryManager
import org.briarproject.bramble.api.db.DatabaseConfig
import org.briarproject.bramble.api.event.EventBus
import org.briarproject.bramble.api.lifecycle.IoExecutor
import org.briarproject.bramble.api.network.NetworkManager
import org.briarproject.bramble.api.plugin.BackoffFactory
import org.briarproject.bramble.api.plugin.PluginConfig
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory
import org.briarproject.bramble.api.system.Clock
import org.briarproject.bramble.api.system.LocationUtils
import org.briarproject.bramble.api.system.ResourceProvider
import org.briarproject.bramble.battery.DefaultBatteryManagerModule
import org.briarproject.bramble.event.DefaultEventExecutorModule
import org.briarproject.bramble.network.JavaNetworkModule
import org.briarproject.bramble.plugin.tor.CircumventionModule
import org.briarproject.bramble.plugin.tor.CircumventionProvider
import org.briarproject.bramble.plugin.tor.UnixTorPluginFactory
import org.briarproject.bramble.socks.SocksModule
import org.briarproject.bramble.system.JavaSystemModule
import org.briarproject.bramble.util.OsUtils.isLinux
import org.briarproject.bramble.util.OsUtils.isMac
import org.briarproject.briar.headless.blogs.HeadlessBlogModule
import org.briarproject.briar.headless.contact.HeadlessContactModule
import org.briarproject.briar.headless.event.HeadlessEventModule
import org.briarproject.briar.headless.forums.HeadlessForumModule
import org.briarproject.briar.headless.messaging.HeadlessMessagingModule
import java.io.File
import java.util.Collections.emptyList
import java.util.concurrent.Executor
import javax.inject.Singleton
import javax.net.SocketFactory

@Module(
    includes = [
        JavaNetworkModule::class,
        JavaSystemModule::class,
        CircumventionModule::class,
        DefaultBatteryManagerModule::class,
        DefaultEventExecutorModule::class,
        SocksModule::class,
        HeadlessBlogModule::class,
        HeadlessContactModule::class,
        HeadlessEventModule::class,
        HeadlessForumModule::class,
        HeadlessMessagingModule::class
    ]
)
internal class HeadlessModule(private val appDir: File) {

    @Provides
    @Singleton
    internal fun provideBriarService(briarService: BriarServiceImpl): BriarService = briarService

    @Provides
    @Singleton
    internal fun provideDatabaseConfig(): DatabaseConfig {
        val dbDir = File(appDir, "db")
        val keyDir = File(appDir, "key")
        return HeadlessDatabaseConfig(dbDir, keyDir)
    }

    @Provides
    internal fun providePluginConfig(
        @IoExecutor ioExecutor: Executor, torSocketFactory: SocketFactory,
        backoffFactory: BackoffFactory, networkManager: NetworkManager,
        locationUtils: LocationUtils, eventBus: EventBus, resourceProvider: ResourceProvider,
        circumventionProvider: CircumventionProvider, batteryManager: BatteryManager, clock: Clock
    ): PluginConfig {
        val duplex: List<DuplexPluginFactory> = if (isLinux() || isMac()) {
            val torDirectory = File(appDir, "tor")
            val tor = UnixTorPluginFactory(
                ioExecutor, networkManager, locationUtils, eventBus, torSocketFactory,
                backoffFactory, resourceProvider, circumventionProvider, batteryManager, clock,
                torDirectory
            )
            listOf(tor)
        } else {
            emptyList()
        }
        return object : PluginConfig {
            override fun getDuplexFactories(): Collection<DuplexPluginFactory> = duplex
            override fun getSimplexFactories(): Collection<SimplexPluginFactory> = emptyList()
            override fun shouldPoll(): Boolean = true
        }
    }

    @Provides
    @Singleton
    internal fun provideObjectMapper() = ObjectMapper()

    @Provides
    internal fun provideFeatureFlags() = object : FeatureFlags {
        override fun shouldEnableImageAttachments() = false
        override fun shouldEnablePrivateMessageDeletion() = true
    }
}
