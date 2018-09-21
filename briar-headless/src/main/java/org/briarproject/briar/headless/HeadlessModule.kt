package org.briarproject.briar.headless

import dagger.Module
import dagger.Provides
import org.briarproject.bramble.api.crypto.CryptoComponent
import org.briarproject.bramble.api.crypto.PublicKey
import org.briarproject.bramble.api.db.DatabaseConfig
import org.briarproject.bramble.api.event.EventBus
import org.briarproject.bramble.api.lifecycle.IoExecutor
import org.briarproject.bramble.api.network.NetworkManager
import org.briarproject.bramble.api.plugin.BackoffFactory
import org.briarproject.bramble.api.plugin.PluginConfig
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory
import org.briarproject.bramble.api.reporting.DevConfig
import org.briarproject.bramble.api.reporting.ReportingConstants.DEV_ONION_ADDRESS
import org.briarproject.bramble.api.reporting.ReportingConstants.DEV_PUBLIC_KEY_HEX
import org.briarproject.bramble.api.system.Clock
import org.briarproject.bramble.api.system.LocationUtils
import org.briarproject.bramble.api.system.ResourceProvider
import org.briarproject.bramble.network.JavaNetworkModule
import org.briarproject.bramble.plugin.tor.CircumventionModule
import org.briarproject.bramble.plugin.tor.CircumventionProvider
import org.briarproject.bramble.plugin.tor.LinuxTorPluginFactory
import org.briarproject.bramble.system.JavaSystemModule
import org.briarproject.bramble.util.StringUtils.fromHexString
import org.briarproject.briar.headless.blogs.HeadlessBlogModule
import org.briarproject.briar.headless.contact.HeadlessContactModule
import org.briarproject.briar.headless.event.HeadlessEventModule
import org.briarproject.briar.headless.forums.HeadlessForumModule
import org.briarproject.briar.headless.messaging.HeadlessMessagingModule
import java.io.File
import java.security.GeneralSecurityException
import java.util.Collections.emptyList
import java.util.concurrent.Executor
import javax.inject.Singleton
import javax.net.SocketFactory

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
internal class HeadlessModule(private val appDir: File) {

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
        locationUtils: LocationUtils, eventBus: EventBus,
        resourceProvider: ResourceProvider,
        circumventionProvider: CircumventionProvider, clock: Clock
    ): PluginConfig {
        val torDirectory = File(appDir, "tor")
        val tor = LinuxTorPluginFactory(
            ioExecutor,
            networkManager, locationUtils, eventBus, torSocketFactory,
            backoffFactory, resourceProvider, circumventionProvider, clock,
            torDirectory
        )
        val duplex = listOf<DuplexPluginFactory>(tor)
        return object : PluginConfig {
            override fun getDuplexFactories(): Collection<DuplexPluginFactory> {
                return duplex
            }

            override fun getSimplexFactories(): Collection<SimplexPluginFactory> {
                return emptyList()
            }

            override fun shouldPoll(): Boolean {
                return true
            }
        }
    }

    @Provides
    @Singleton
    internal fun provideDevConfig(crypto: CryptoComponent): DevConfig {
        return object : DevConfig {
            override fun getDevPublicKey(): PublicKey {
                try {
                    return crypto.messageKeyParser
                        .parsePublicKey(fromHexString(DEV_PUBLIC_KEY_HEX))
                } catch (e: GeneralSecurityException) {
                    throw RuntimeException(e)
                }

            }

            override fun getDevOnionAddress(): String {
                return DEV_ONION_ADDRESS
            }

            override fun getReportDir(): File {
                return File(appDir, "reportDir")
            }
        }
    }

}
