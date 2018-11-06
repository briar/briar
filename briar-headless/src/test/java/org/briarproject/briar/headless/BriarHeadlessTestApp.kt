package org.briarproject.briar.headless

import dagger.Component
import org.briarproject.bramble.BrambleCoreEagerSingletons
import org.briarproject.bramble.BrambleCoreModule
import org.briarproject.bramble.account.AccountModule
import org.briarproject.bramble.test.TestSecureRandomModule
import org.briarproject.briar.BriarCoreEagerSingletons
import org.briarproject.briar.BriarCoreModule
import org.briarproject.briar.api.test.TestDataCreator
import javax.inject.Singleton

@Component(
    modules = [
        BrambleCoreModule::class,
        BriarCoreModule::class,
        TestSecureRandomModule::class,
        AccountModule::class,
        HeadlessTestModule::class
    ]
)
@Singleton
internal interface BriarHeadlessTestApp : BrambleCoreEagerSingletons, BriarCoreEagerSingletons {
    fun getRouter(): Router

    fun getTestDataCreator(): TestDataCreator
}
