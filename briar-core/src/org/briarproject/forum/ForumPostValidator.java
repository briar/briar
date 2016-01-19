package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageValidator;
import org.briarproject.api.system.Clock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.api.forum.ForumConstants.MAX_CONTENT_TYPE_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;

class ForumPostValidator implements MessageValidator {

	private static final Logger LOG =
			Logger.getLogger(ForumPostValidator.class.getName());

	private final CryptoComponent crypto;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final ObjectReader<Author> authorReader;
	private final MetadataEncoder metadataEncoder;
	private final Clock clock;
	private final KeyParser keyParser;

	@Inject
	ForumPostValidator(CryptoComponent crypto,
			BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory,
			ObjectReader<Author> authorReader,
			MetadataEncoder metadataEncoder, Clock clock) {
		this.crypto = crypto;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.authorReader = authorReader;
		this.metadataEncoder = metadataEncoder;
		this.clock = clock;
		keyParser = crypto.getSignatureKeyParser();
	}

	@Override
	public Metadata validateMessage(Message m) {
		// Reject the message if it's too far in the future
		long now = clock.currentTimeMillis();
		if (m.getTimestamp() - now > MAX_CLOCK_DIFFERENCE) {
			LOG.info("Timestamp is too far in the future");
			return null;
		}
		try {
			// Parse the message body
			byte[] raw = m.getRaw();
			ByteArrayInputStream in = new ByteArrayInputStream(raw,
					MESSAGE_HEADER_LENGTH, raw.length - MESSAGE_HEADER_LENGTH);
			BdfReader r = bdfReaderFactory.createReader(in);
			MessageId parent = null;
			Author author = null;
			String contentType;
			byte[] postBody, sig = null;
			r.readListStart();
			// Read the parent ID, if any
			if (r.hasRaw()) {
				byte[] id = r.readRaw(UniqueId.LENGTH);
				if (id.length < UniqueId.LENGTH) throw new FormatException();
				parent = new MessageId(id);
			} else {
				r.readNull();
			}
			// Read the author, if any
			if (r.hasList()) author = authorReader.readObject(r);
			else r.readNull();
			// Read the content type
			contentType = r.readString(MAX_CONTENT_TYPE_LENGTH);
			// Read the forum post body
			postBody = r.readRaw(MAX_FORUM_POST_BODY_LENGTH);

			// Read the signature, if any
			if (r.hasRaw()) sig = r.readRaw(MAX_SIGNATURE_LENGTH);
			else r.readNull();
			r.readListEnd();
			if (!r.eof()) throw new FormatException();
			// If there's an author there must be a signature and vice versa
			if (author != null && sig == null) {
				LOG.info("Author without signature");
				return null;
			}
			if (author == null && sig != null) {
				LOG.info("Signature without author");
				return null;
			}
			// Verify the signature, if any
			if (author != null) {
				// Parse the public key
				PublicKey key = keyParser.parsePublicKey(author.getPublicKey());
				// Serialise the data to be signed
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				BdfWriter w = bdfWriterFactory.createWriter(out);
				w.writeListStart();
				w.writeRaw(m.getGroupId().getBytes());
				w.writeInteger(m.getTimestamp());
				if (parent == null) w.writeNull();
				else w.writeRaw(parent.getBytes());
				writeAuthor(w, author);
				w.writeString(contentType);
				w.writeRaw(postBody);
				w.writeListEnd();
				// Verify the signature
				Signature signature = crypto.getSignature();
				signature.initVerify(key);
				signature.update(out.toByteArray());
				if (!signature.verify(sig)) {
					LOG.info("Invalid signature");
					return null;
				}
			}
			// Return the metadata
			BdfDictionary d = new BdfDictionary();
			d.put("timestamp", m.getTimestamp());
			if (parent != null) d.put("parent", parent.getBytes());
			if (author != null) {
				BdfDictionary d1 = new BdfDictionary();
				d1.put("id", author.getId().getBytes());
				d1.put("name", author.getName());
				d1.put("publicKey", author.getPublicKey());
				d.put("author", d1);
			}
			d.put("contentType", contentType);
			d.put("read", false);
			return metadataEncoder.encode(d);
		} catch (IOException e) {
			LOG.info("Invalid forum post");
			return null;
		} catch (GeneralSecurityException e) {
			LOG.info("Invalid public key");
			return null;
		}
	}

	private void writeAuthor(BdfWriter w, Author a) throws IOException {
		w.writeListStart();
		w.writeString(a.getName());
		w.writeRaw(a.getPublicKey());
		w.writeListEnd();
	}
}
