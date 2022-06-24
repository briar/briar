package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.bramble.test.BrambleIntegrationTestComponent;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class
})
interface MailboxIntegrationTestComponent extends
		BrambleIntegrationTestComponent {
}
