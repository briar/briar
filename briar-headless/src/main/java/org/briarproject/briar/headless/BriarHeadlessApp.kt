package org.briarproject.briar.headless

import dagger.Component
import org.briarproject.bramble.BrambleCoreEagerSingletons
import org.briarproject.bramble.BrambleCoreModule
import org.briarproject.bramble.BrambleJavaEagerSingletons
import org.briarproject.bramble.BrambleJavaModule
import org.briarproject.briar.BriarCoreEagerSingletons
import org.briarproject.briar.BriarCoreModule
import java.security.SecureRandom
import javax.inject.Singleton

@Component(
    modules = [
        BrambleCoreModule::class,
        BrambleJavaModule::class,
        BriarCoreModule::class,
        HeadlessModule::class
    ]
)
@Singleton
internal interface BriarHeadlessApp : BrambleCoreEagerSingletons, BriarCoreEagerSingletons,
    BrambleJavaEagerSingletons, HeadlessEagerSingletons {

    fun getRouter(): Router

    fun getSecureRandom(): SecureRandom
}
