package org.briarproject.bramble.client;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyParser;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
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
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.util.StringUtils;
import org.jmock.Expectations;
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
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getSignaturePrivateKey;
import static org.briarproject.bramble.test.TestUtils.getSignaturePublicKey;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ClientHelperImplTest extends BrambleMockTestCase {

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
	private final KeyParser keyParser = context.mock(KeyParser.class);

	private final GroupId groupId = new GroupId(getRandomId());
	private final BdfDictionary dictionary = new BdfDictionary();
	private final Message message = getMessage(groupId);
	private final MessageId messageId = message.getId();
	private final long timestamp = message.getTimestamp();
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

		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(metadata));
			oneOf(db).addLocalMessage(txn, message, metadata, shared, false);
		}});

		clientHelper.addLocalMessage(message, dictionary, shared);
	}

	@Test
	public void testCreateMessage() throws Exception {
		byte[] bytes = expectToByteArray(list);

		context.checking(new Expectations() {{
			oneOf(messageFactory).createMessage(groupId, timestamp, bytes);
		}});

		clientHelper.createMessage(groupId, timestamp, list);
	}

	@Test
	public void testGetMessageAsList() throws Exception {
		Transaction txn = new Transaction(null, true);

		expectToList(true);
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getMessage(txn, messageId);
			will(returnValue(message));
		}});

		clientHelper.getMessageAsList(messageId);
	}

	@Test
	public void testGetGroupMetadataAsDictionary() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getGroupMetadata(txn, groupId);
			will(returnValue(metadata));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
		}});

		assertEquals(dictionary,
				clientHelper.getGroupMetadataAsDictionary(groupId));
	}

	@Test
	public void testGetMessageMetadataAsDictionary() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getMessageMetadata(txn, messageId);
			will(returnValue(metadata));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
		}});

		assertEquals(dictionary,
				clientHelper.getMessageMetadataAsDictionary(messageId));
	}

	@Test
	public void testGetMessageMetadataAsDictionaryMap() throws Exception {
		Map<MessageId, BdfDictionary> map = new HashMap<>();
		map.put(messageId, dictionary);
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getMessageMetadata(txn, groupId);
			will(returnValue(Collections.singletonMap(messageId, metadata)));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
		}});

		assertEquals(map, clientHelper.getMessageMetadataAsDictionary(groupId));
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

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(metadataEncoder).encode(query);
			will(returnValue(queryMetadata));
			oneOf(db).getMessageMetadata(txn, groupId, queryMetadata);
			will(returnValue(Collections.singletonMap(messageId, metadata)));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
		}});

		assertEquals(map,
				clientHelper.getMessageMetadataAsDictionary(groupId, query));
	}

	@Test
	public void testMergeGroupMetadata() throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(metadata));
			oneOf(db).mergeGroupMetadata(txn, groupId, metadata);
		}});

		clientHelper.mergeGroupMetadata(groupId, dictionary);
	}

	@Test
	public void testMergeMessageMetadata() throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(metadata));
			oneOf(db).mergeMessageMetadata(txn, messageId, metadata);
		}});

		clientHelper.mergeMessageMetadata(messageId, dictionary);
	}

	@Test
	public void testToByteArray() throws Exception {
		byte[] bytes = expectToByteArray(list);

		assertArrayEquals(bytes, clientHelper.toByteArray(list));
	}

	@Test
	public void testToList() throws Exception {
		expectToList(true);

		assertEquals(list, clientHelper.toList(getRandomBytes(123)));
	}

	@Test
	public void testToListWithNoEof() throws Exception {
		expectToList(false); // no EOF after list

		try {
			clientHelper.toList(getRandomBytes(123));
			fail();
		} catch (FormatException e) {
			// expected
		}
	}

	@Test
	public void testSign() throws Exception {
		PrivateKey privateKey = getSignaturePrivateKey();
		byte[] signature = getRandomBytes(42);

		byte[] bytes = expectToByteArray(list);
		context.checking(new Expectations() {{
			oneOf(cryptoComponent).sign(label, bytes, privateKey);
			will(returnValue(signature));
		}});

		assertArrayEquals(signature,
				clientHelper.sign(label, list, privateKey));
	}

	@Test
	public void testVerifySignature() throws Exception {
		byte[] signature = getRandomBytes(MAX_SIGNATURE_LENGTH);
		byte[] signed = expectToByteArray(list);
		PublicKey publicKey = getSignaturePublicKey();

		context.checking(new Expectations() {{
			oneOf(cryptoComponent).verifySignature(signature, label, signed,
					publicKey);
			will(returnValue(true));
		}});

		clientHelper.verifySignature(signature, label, list, publicKey);
	}

	@Test
	public void testVerifyWrongSignature() throws Exception {
		byte[] signature = getRandomBytes(MAX_SIGNATURE_LENGTH);
		byte[] signed = expectToByteArray(list);
		PublicKey publicKey = getSignaturePublicKey();

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
		}
	}

	@Test
	public void testParsesAndEncodesAuthor() throws Exception {
		context.checking(new Expectations() {{
			oneOf(cryptoComponent).getSignatureKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser).parsePublicKey(author.getPublicKey().getEncoded());
			will(returnValue(author.getPublicKey()));
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
				author.getPublicKey().getEncoded()
		);

		context.checking(new Expectations() {{
			oneOf(cryptoComponent).getSignatureKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser).parsePublicKey(author.getPublicKey().getEncoded());
			will(returnValue(author.getPublicKey()));
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
				author.getPublicKey().getEncoded(),
				"foo"
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithNullFormatVersion() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				null,
				author.getName(),
				author.getPublicKey().getEncoded()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithNonIntegerFormatVersion()
			throws Exception {
		BdfList invalidAuthor = BdfList.of(
				"foo",
				author.getName(),
				author.getPublicKey().getEncoded()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithUnknownFormatVersion() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion() + 1,
				author.getName(),
				author.getPublicKey().getEncoded()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithTooShortName() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				"",
				author.getPublicKey().getEncoded()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithTooLongName() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				getRandomString(MAX_AUTHOR_NAME_LENGTH + 1),
				author.getPublicKey().getEncoded()
		);
		clientHelper.parseAndValidateAuthor(invalidAuthor);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithNullName() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				null,
				author.getPublicKey().getEncoded()
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

	@Test(expected = FormatException.class)
	public void testRejectsAuthorWithInvalidPublicKey() throws Exception {
		BdfList invalidAuthor = BdfList.of(
				author.getFormatVersion(),
				author.getName(),
				author.getPublicKey().getEncoded()
		);

		context.checking(new Expectations() {{
			oneOf(cryptoComponent).getSignatureKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser).parsePublicKey(author.getPublicKey().getEncoded());
			will(throwException(new GeneralSecurityException()));
		}});

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
