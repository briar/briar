package org.briarproject.bramble.client;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.BdfReader;
import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.data.BdfWriter;
import org.briarproject.bramble.api.data.BdfWriterFactory;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.util.StringUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ClientHelperImplTest extends BrambleTestCase {

	private final Mockery context = new Mockery();
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final MessageFactory messageFactory =
			context.mock(MessageFactory.class);
	private final BdfReaderFactory bdfReaderFactory =
			context.mock(BdfReaderFactory.class);
	private final BdfWriterFactory bdfWriterFactory =
			context.mock(BdfWriterFactory.class);
	private final MetadataParser metadataParser =
			context.mock(MetadataParser.class);
	private final MetadataEncoder metadataEncoder =
			context.mock(MetadataEncoder.class);
	private final CryptoComponent cryptoComponent =
			context.mock(CryptoComponent.class);
	private final AuthorFactory authorFactory =
			context.mock(AuthorFactory.class);

	private final GroupId groupId = new GroupId(getRandomId());
	private final BdfDictionary dictionary = new BdfDictionary();
	private final long timestamp = 42L;
	private final byte[] rawMessage = getRandomBytes(42);
	private final MessageId messageId = new MessageId(getRandomId());
	private final Message message =
			new Message(messageId, groupId, timestamp, rawMessage);
	private final Metadata metadata = new Metadata();
	private final BdfList list = BdfList.of("Sign this!", getRandomBytes(42));
	private final String label = StringUtils.getRandomString(5);
	private final Author author = getAuthor();

	private final ClientHelper clientHelper = new ClientHelperImpl(db,
			messageFactory, bdfReaderFactory, bdfWriterFactory, metadataParser,
			metadataEncoder, cryptoComponent, authorFactory);

	@Test
	public void testAddLocalMessage() throws Exception {
		boolean shared = new Random().nextBoolean();
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(metadata));
			oneOf(db).addLocalMessage(txn, message, metadata, shared);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		clientHelper.addLocalMessage(message, dictionary, shared);
		context.assertIsSatisfied();
	}

	@Test
	public void testCreateMessage() throws Exception {
		byte[] bytes = expectToByteArray(list);

		context.checking(new Expectations() {{
			oneOf(messageFactory).createMessage(groupId, timestamp, bytes);
		}});

		clientHelper.createMessage(groupId, timestamp, list);
		context.assertIsSatisfied();
	}

	@Test
	public void testGetMessageAsList() throws Exception {
		Transaction txn = new Transaction(null, true);

		expectToList(true);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getRawMessage(txn, messageId);
			will(returnValue(rawMessage));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		clientHelper.getMessageAsList(messageId);
		context.assertIsSatisfied();
	}

	@Test
	public void testGetGroupMetadataAsDictionary() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroupMetadata(txn, groupId);
			will(returnValue(metadata));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(dictionary,
				clientHelper.getGroupMetadataAsDictionary(groupId));
		context.assertIsSatisfied();
	}

	@Test
	public void testGetMessageMetadataAsDictionary() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessageMetadata(txn, messageId);
			will(returnValue(metadata));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(dictionary,
				clientHelper.getMessageMetadataAsDictionary(messageId));
		context.assertIsSatisfied();
	}

	@Test
	public void testGetMessageMetadataAsDictionaryMap() throws Exception {
		Map<MessageId, BdfDictionary> map = new HashMap<>();
		map.put(messageId, dictionary);
		Transaction txn = new Transaction(null, true);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessageMetadata(txn, groupId);
			will(returnValue(Collections.singletonMap(messageId, metadata)));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(map, clientHelper.getMessageMetadataAsDictionary(groupId));
		context.assertIsSatisfied();
	}

	@Test
	public void testGetMessageMetadataAsDictionaryQuery() throws Exception {
		Map<MessageId, BdfDictionary> map = new HashMap<>();
		map.put(messageId, dictionary);
		BdfDictionary query =
				BdfDictionary.of(new BdfEntry("query", "me"));
		Metadata queryMetadata = new Metadata();
		queryMetadata.put("query", getRandomBytes(42));
		Transaction txn = new Transaction(null, true);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(metadataEncoder).encode(query);
			will(returnValue(queryMetadata));
			oneOf(db).getMessageMetadata(txn, groupId, queryMetadata);
			will(returnValue(Collections.singletonMap(messageId, metadata)));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(map,
				clientHelper.getMessageMetadataAsDictionary(groupId, query));
		context.assertIsSatisfied();
	}

	@Test
	public void testMergeGroupMetadata() throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(metadata));
			oneOf(db).mergeGroupMetadata(txn, groupId, metadata);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		clientHelper.mergeGroupMetadata(groupId, dictionary);
		context.assertIsSatisfied();
	}

	@Test
	public void testMergeMessageMetadata() throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(metadata));
			oneOf(db).mergeMessageMetadata(txn, messageId, metadata);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		clientHelper.mergeMessageMetadata(messageId, dictionary);
		context.assertIsSatisfied();
	}

	@Test
	public void testToByteArray() throws Exception {
		byte[] bytes = expectToByteArray(list);

		assertArrayEquals(bytes, clientHelper.toByteArray(list));
		context.assertIsSatisfied();
	}

	@Test
	public void testToList() throws Exception {
		expectToList(true);

		assertEquals(list, clientHelper.toList(rawMessage));
		context.assertIsSatisfied();
	}

	@Test
	public void testToListWithNoEof() throws Exception {
		expectToList(false); // no EOF after list

		try {
			clientHelper.toList(rawMessage);
			fail();
		} catch (FormatException e) {
			// expected
			context.assertIsSatisfied();
		}
	}

	@Test
	public void testSign() throws Exception {
		byte[] privateKey = getRandomBytes(42);
		byte[] signed = getRandomBytes(42);

		byte[] bytes = expectToByteArray(list);
		context.checking(new Expectations() {{
			oneOf(cryptoComponent).sign(label, bytes, privateKey);
			will(returnValue(signed));
		}});

		assertArrayEquals(signed, clientHelper.sign(label, list, privateKey));
		context.assertIsSatisfied();
	}

	@Test
	public void testVerifySignature() throws Exception {
		byte[] signature = getRandomBytes(MAX_SIGNATURE_LENGTH);
		byte[] publicKey = getRandomBytes(42);
		byte[] signed = expectToByteArray(list);

		context.checking(new Expectations() {{
			oneOf(cryptoComponent).verifySignature(signature, label, signed,
					publicKey);
			will(returnValue(true));
		}});

		clientHelper.verifySignature(signature, label, list, publicKey);
		context.assertIsSatisfied();
	}

	@Test
	public void testVerifyWrongSignature() throws Exception {
		byte[] signature = getRandomBytes(MAX_SIGNATURE_LENGTH);
		byte[] publicKey = getRandomBytes(42);
		byte[] signed = expectToByteArray(list);

		context.checking(new Expectations() {{
			oneOf(cryptoComponent).verifySignature(signature, label, signed,
					publicKey);
			will(returnValue(false));
		}});

		try {
			clientHelper.verifySignature(signature, label, list, publicKey);
			fail();
		} catch (GeneralSecurityException e) {
			// expected
			context.assertIsSatisfied();
		}
	}

	@Test
	public void testParsesAndEncodesAuthor() throws Exception {
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(author.getFormatVersion(),
					author.getName(), author.getPublicKey());
			will(returnValue(author));
		}});

		BdfList authorList = clientHelper.toList(author);
		assertEquals(author, clientHelper.parseAndValidateAuthor(authorList));
	}

	@Test
	public void testAcceptsValidAuthor() throws Exception {
		BdfList authorList = BdfList.of(
				author.getFormatVersion(),
				author.getName(),
				author.getPublicKey()
		);

		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(author.getFormatVersion(),
					author.getName(), author.getPublicKey());
			will(returnValue(author));
		}});

		assertEquals(author, clientHelper.parseAndValidateAuthor(authorList));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortAuthor() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				author.getName()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongAuthor() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				author.getName(),
				author.getPublicKey(),
				"foo"
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithNullFormatVersion() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				null,
				author.getName(),
				author.getPublicKey()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithNonIntegerFormatVersion()
			throws Exception {
		BdfList invalidAuthor = BdfList.of(
				"foo",
				author.getName(),
				author.getPublicKey()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithUnknownFormatVersion() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion() + 1,
				author.getName(),
				author.getPublicKey()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithTooShortName() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				"",
				author.getPublicKey()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithTooLongName() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				getRandomString(MAX_AUTHOR_NAME_LENGTH + 1),
				author.getPublicKey()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithNullName() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				null,
				author.getPublicKey()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithNonStringName() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				getRandomBytes(5),
				author.getPublicKey()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithTooShortPublicKey() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				author.getName(),
				new byte[0]
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithTooLongPublicKey() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				author.getName(),
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH + 1)
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithNullPublicKey() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				author.getName(),
				null
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithNonRawPublicKey() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				author.getName(),
				"foo"
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	private byte[] expectToByteArray(BdfList list) throws Exception {
		BdfWriter bdfWriter = context.mock(BdfWriter.class);

		context.checking(new Expectations() {{
			oneOf(bdfWriterFactory)
					.createWriter(with(any(ByteArrayOutputStream.class)));
			will(returnValue(bdfWriter));
			oneOf(bdfWriter).writeList(list);
		}});
		return new byte[0];
	}

	private void expectToList(boolean eof) throws Exception {
		BdfReader bdfReader = context.mock(BdfReader.class);

		context.checking(new Expectations() {{
			oneOf(bdfReaderFactory)
					.createReader(with(any(InputStream.class)));
			will(returnValue(bdfReader));
			oneOf(bdfReader).readList();
			will(returnValue(list));
			oneOf(bdfReader).eof();
			will(returnValue(eof));
		}});
	}
}
