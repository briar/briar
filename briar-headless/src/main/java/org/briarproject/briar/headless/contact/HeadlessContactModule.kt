package org.briarproject.briar.headless.contact

import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class HeadlessContactModule {

    @Provides
    @Singleton
    internal fun provideContactController(contactController: ContactControllerImpl): ContactController {
        return contactController
    }

}
