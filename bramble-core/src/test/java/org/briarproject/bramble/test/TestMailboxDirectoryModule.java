package org.briarproject.bramble.test;

import org.briarproject.bramble.api.mailbox.MailboxDirectory;

import java.io.File;

import dagger.Module;
import dagger.Provides;

@Module
public class TestMailboxDirectoryModule {

	@Provides
	@MailboxDirectory
	File provideMailboxDirectory() {
		return new File("mailbox");
	}
}
