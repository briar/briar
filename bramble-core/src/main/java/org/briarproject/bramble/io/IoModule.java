package org.briarproject.bramble.io;

import org.briarproject.bramble.api.io.MessageInputStreamFactory;

import dagger.Module;
import dagger.Provides;

@Module
public class IoModule {

	@Provides
	MessageInputStreamFactory provideMessageInputStreamFactory(
			MessageInputStreamFactoryImpl messageInputStreamFactory) {
		return messageInputStreamFactory;
	}
}
