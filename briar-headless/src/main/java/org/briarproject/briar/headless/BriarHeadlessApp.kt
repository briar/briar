package org.briarproject.briar.headless

import dagger.Component
import org.briarproject.bramble.BrambleCoreEagerSingletons
import org.briarproject.bramble.BrambleCoreModule
import org.briarproject.bramble.account.AccountModule
import org.briarproject.bramble.system.DesktopSecureRandomModule
import org.briarproject.briar.BriarCoreEagerSingletons
import org.briarproject.briar.BriarCoreModule
import javax.inject.Singleton

@Component(
    modules = [
        BrambleCoreModule::class,
        BriarCoreModule::class,
        DesktopSecureRandomModule::class,
        AccountModule::class,
        HeadlessModule::class
    ]
)
@Singleton
internal interface BriarHeadlessApp : BrambleCoreEagerSingletons, BriarCoreEagerSingletons {
    fun router(): Router
}
