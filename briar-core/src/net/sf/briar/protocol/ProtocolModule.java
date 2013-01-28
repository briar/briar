package net.sf.briar.protocol;

import java.util.concurrent.Executor;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorFactory;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.MessageFactory;
import net.sf.briar.api.protocol.MessageVerifier;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.UnverifiedMessage;
import net.sf.briar.api.protocol.VerificationExecutor;
import net.sf.briar.api.serial.StructReader;
import net.sf.briar.util.BoundedExecutor;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class ProtocolModule extends AbstractModule {

	// FIXME: Determine suitable values for these constants empirically

	/**
	 * The maximum number of verification tasks that can be queued for
	 * execution before submitting another task will block.
	 */
	private static final int MAX_QUEUED_VERIFIER_TASKS = 10;

	/** The minimum number of verification threads to keep in the pool. */
	private static final int MIN_VERIFIER_THREADS = 1;

	/** The maximum number of verification threads. */
	private static final int MAX_VERIFIER_THREADS =
			Runtime.getRuntime().availableProcessors();

	@Override
	protected void configure() {
		bind(AuthorFactory.class).to(AuthorFactoryImpl.class);
		bind(GroupFactory.class).to(GroupFactoryImpl.class);
		bind(MessageFactory.class).to(MessageFactoryImpl.class);
		bind(MessageVerifier.class).to(MessageVerifierImpl.class);
		bind(ProtocolReaderFactory.class).to(ProtocolReaderFactoryImpl.class);
		bind(ProtocolWriterFactory.class).to(ProtocolWriterFactoryImpl.class);
		// The executor is bounded, so tasks must be independent and short-lived
		bind(Executor.class).annotatedWith(
				VerificationExecutor.class).toInstance(
						new BoundedExecutor(MAX_QUEUED_VERIFIER_TASKS,
								MIN_VERIFIER_THREADS, MAX_VERIFIER_THREADS));
	}

	@Provides
	StructReader<Author> getAuthorReader(CryptoComponent crypto) {
		return new AuthorReader(crypto);
	}

	@Provides
	StructReader<Group> getGroupReader(CryptoComponent crypto) {
		return new GroupReader(crypto);
	}

	@Provides
	StructReader<UnverifiedMessage> getMessageReader(
			StructReader<Group> groupReader,
			StructReader<Author> authorReader) {
		return new MessageReader(groupReader, authorReader);
	}

	@Provides
	StructReader<SubscriptionUpdate> getSubscriptionUpdateReader(
			StructReader<Group> groupReader) {
		return new SubscriptionUpdateReader(groupReader);
	}
}
