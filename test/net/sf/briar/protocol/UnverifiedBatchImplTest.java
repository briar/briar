package net.sf.briar.protocol;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.UnverifiedBatch;
import net.sf.briar.crypto.CryptoModule;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class UnverifiedBatchImplTest extends BriarTestCase {

	private final CryptoComponent crypto;
	private final byte[] raw, raw1;
	private final String subject;
	private final long timestamp;

	public UnverifiedBatchImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		Random r = new Random();
		raw = new byte[123];
		r.nextBytes(raw);
		raw1 = new byte[1234];
		r.nextBytes(raw1);
		subject = "Unit tests are exciting";
		timestamp = System.currentTimeMillis();
	}

	@Test
	public void testIds() throws Exception {
		// Calculate the expected batch and message IDs
		MessageDigest messageDigest = crypto.getMessageDigest();
		messageDigest.update(raw);
		messageDigest.update(raw1);
		BatchId batchId = new BatchId(messageDigest.digest());
		messageDigest.update(raw);
		MessageId messageId = new MessageId(messageDigest.digest());
		messageDigest.update(raw1);
		MessageId messageId1 = new MessageId(messageDigest.digest());
		// Verify the batch
		Mockery context = new Mockery();
		final UnverifiedMessage message =
			context.mock(UnverifiedMessage.class, "message");
		final UnverifiedMessage message1 =
			context.mock(UnverifiedMessage.class, "message1");
		context.checking(new Expectations() {{
			// First message
			oneOf(message).getRaw();
			will(returnValue(raw));
			oneOf(message).getAuthor();
			will(returnValue(null));
			oneOf(message).getGroup();
			will(returnValue(null));
			oneOf(message).getParent();
			will(returnValue(null));
			oneOf(message).getSubject();
			will(returnValue(subject));
			oneOf(message).getTimestamp();
			will(returnValue(timestamp));
			oneOf(message).getBodyStart();
			will(returnValue(10));
			oneOf(message).getBodyLength();
			will(returnValue(100));
			// Second message
			oneOf(message1).getRaw();
			will(returnValue(raw1));
			oneOf(message1).getAuthor();
			will(returnValue(null));
			oneOf(message1).getGroup();
			will(returnValue(null));
			oneOf(message1).getParent();
			will(returnValue(null));
			oneOf(message1).getSubject();
			will(returnValue(subject));
			oneOf(message1).getTimestamp();
			will(returnValue(timestamp));
			oneOf(message1).getBodyStart();
			will(returnValue(10));
			oneOf(message1).getBodyLength();
			will(returnValue(1000));
		}});
		Collection<UnverifiedMessage> messages =
			Arrays.asList(new UnverifiedMessage[] {message, message1});
		UnverifiedBatch batch = new UnverifiedBatchImpl(crypto, messages);
		Batch verifiedBatch = batch.verify();
		// Check that the batch and message IDs match
		assertEquals(batchId, verifiedBatch.getId());
		Collection<Message> verifiedMessages = verifiedBatch.getMessages();
		assertEquals(2, verifiedMessages.size());
		Iterator<Message> it = verifiedMessages.iterator();
		Message verifiedMessage = it.next();
		assertEquals(messageId, verifiedMessage.getId());
		Message verifiedMessage1 = it.next();
		assertEquals(messageId1, verifiedMessage1.getId());
		context.assertIsSatisfied();
	}

	@Test
	public void testSignatures() throws Exception {
		final int signedByAuthor = 100, signedByGroup = 110;
		final KeyPair authorKeyPair = crypto.generateSignatureKeyPair();
		final KeyPair groupKeyPair = crypto.generateSignatureKeyPair();
		Signature signature = crypto.getSignature();
		// Calculate the expected author and group signatures
		signature.initSign(authorKeyPair.getPrivate());
		signature.update(raw, 0, signedByAuthor);
		final byte[] authorSignature = signature.sign();
		signature.initSign(groupKeyPair.getPrivate());
		signature.update(raw, 0, signedByGroup);
		final byte[] groupSignature = signature.sign();
		// Verify the batch
		Mockery context = new Mockery();
		final UnverifiedMessage message =
			context.mock(UnverifiedMessage.class, "message");
		final Author author = context.mock(Author.class);
		final Group group = context.mock(Group.class);
		final UnverifiedMessage message1 =
			context.mock(UnverifiedMessage.class, "message1");
		context.checking(new Expectations() {{
			// First message
			oneOf(message).getRaw();
			will(returnValue(raw));
			oneOf(message).getAuthor();
			will(returnValue(author));
			oneOf(author).getPublicKey();
			will(returnValue(authorKeyPair.getPublic().getEncoded()));
			oneOf(message).getLengthSignedByAuthor();
			will(returnValue(signedByAuthor));
			oneOf(message).getAuthorSignature();
			will(returnValue(authorSignature));
			oneOf(message).getGroup();
			will(returnValue(group));
			exactly(2).of(group).getPublicKey();
			will(returnValue(groupKeyPair.getPublic().getEncoded()));
			oneOf(message).getLengthSignedByGroup();
			will(returnValue(signedByGroup));
			oneOf(message).getGroupSignature();
			will(returnValue(groupSignature));
			oneOf(author).getId();
			will(returnValue(new AuthorId(TestUtils.getRandomId())));
			oneOf(group).getId();
			will(returnValue(new GroupId(TestUtils.getRandomId())));
			oneOf(message).getParent();
			will(returnValue(null));
			oneOf(message).getSubject();
			will(returnValue(subject));
			oneOf(message).getTimestamp();
			will(returnValue(timestamp));
			oneOf(message).getBodyStart();
			will(returnValue(10));
			oneOf(message).getBodyLength();
			will(returnValue(100));
			// Second message
			oneOf(message1).getRaw();
			will(returnValue(raw1));
			oneOf(message1).getAuthor();
			will(returnValue(null));
			oneOf(message1).getGroup();
			will(returnValue(null));
			oneOf(message1).getParent();
			will(returnValue(null));
			oneOf(message1).getSubject();
			will(returnValue(subject));
			oneOf(message1).getTimestamp();
			will(returnValue(timestamp));
			oneOf(message1).getBodyStart();
			will(returnValue(10));
			oneOf(message1).getBodyLength();
			will(returnValue(1000));
		}});
		Collection<UnverifiedMessage> messages =
			Arrays.asList(new UnverifiedMessage[] {message, message1});
		UnverifiedBatch batch = new UnverifiedBatchImpl(crypto, messages);
		batch.verify();
		context.assertIsSatisfied();
	}

	@Test
	public void testExceptionThrownIfMessageIsModified() throws Exception {
		final int signedByAuthor = 100;
		final KeyPair authorKeyPair = crypto.generateSignatureKeyPair();
		Signature signature = crypto.getSignature();
		// Calculate the expected author signature
		signature.initSign(authorKeyPair.getPrivate());
		signature.update(raw, 0, signedByAuthor);
		final byte[] authorSignature = signature.sign();
		// Modify the message
		raw[signedByAuthor / 2] ^= 0xff;
		// Verify the batch
		Mockery context = new Mockery();
		final UnverifiedMessage message =
			context.mock(UnverifiedMessage.class, "message");
		final Author author = context.mock(Author.class);
		final UnverifiedMessage message1 =
			context.mock(UnverifiedMessage.class, "message1");
		context.checking(new Expectations() {{
			// First message - verification will fail at the author's signature
			oneOf(message).getRaw();
			will(returnValue(raw));
			oneOf(message).getAuthor();
			will(returnValue(author));
			oneOf(author).getPublicKey();
			will(returnValue(authorKeyPair.getPublic().getEncoded()));
			oneOf(message).getLengthSignedByAuthor();
			will(returnValue(signedByAuthor));
			oneOf(message).getAuthorSignature();
			will(returnValue(authorSignature));
		}});
		Collection<UnverifiedMessage> messages =
			Arrays.asList(new UnverifiedMessage[] {message, message1});
		UnverifiedBatch batch = new UnverifiedBatchImpl(crypto, messages);
		try {
			batch.verify();
			fail();
		} catch(GeneralSecurityException expected) {}
		context.assertIsSatisfied();
	}
}
