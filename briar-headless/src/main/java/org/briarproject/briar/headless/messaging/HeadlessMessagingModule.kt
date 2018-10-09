package org.briarproject.briar.headless.messaging

import dagger.Module
import dagger.Provides
import org.briarproject.bramble.api.event.EventBus
import javax.inject.Singleton

@Module
class HeadlessMessagingModule {

    @Provides
    @Singleton
    internal fun provideMessagingController(
        eventBus: EventBus, messagingController: MessagingControllerImpl
    ): MessagingController {
        eventBus.addListener(messagingController)
        return messagingController
    }

}
