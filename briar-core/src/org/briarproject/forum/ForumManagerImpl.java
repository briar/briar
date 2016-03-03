package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;
import static org.briarproject.api.identity.Author.Status.ANONYMOUS;
import static org.briarproject.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.api.identity.Author.Status.VERIFIED;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

class ForumManagerImpl implements ForumManager {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"859a7be50dca035b64bd6902fb797097"
					+ "795af837abbf8c16d750b3c2ccc186ea"));

	private static final Logger LOG =
			Logger.getLogger(ForumManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final BdfReaderFactory bdfReaderFactory;
	private final MetadataEncoder metadataEncoder;
	private final MetadataParser metadataParser;

	@Inject
	ForumManagerImpl(DatabaseComponent db, BdfReaderFactory bdfReaderFactory,
			MetadataEncoder metadataEncoder, MetadataParser metadataParser) {
		this.db = db;
		this.bdfReaderFactory = bdfReaderFactory;
		this.metadataEncoder = metadataEncoder;
		this.metadataParser = metadataParser;
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void addLocalPost(ForumPost p) throws DbException {
		try {
			BdfDictionary d = new BdfDictionary();
			d.put("timestamp", p.getMessage().getTimestamp());
			if (p.getParent() != null)
				d.put("parent", p.getParent().getBytes());
			if (p.getAuthor() != null) {
				Author a = p.getAuthor();
				BdfDictionary d1 = new BdfDictionary();
				d1.put("id", a.getId().getBytes());
				d1.put("name", a.getName());
				d1.put("publicKey", a.getPublicKey());
				d.put("author", d1);
			}
			d.put("contentType", p.getContentType());
			d.put("local", true);
			d.put("read", true);
			Metadata meta = metadataEncoder.encode(d);
			Transaction txn = db.startTransaction();
			try {
				db.addLocalMessage(txn, p.getMessage(), CLIENT_ID, meta, true);
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Forum getForum(GroupId g) throws DbException {
		try {
			Group group;
			Transaction txn = db.startTransaction();
			try {
				group = db.getGroup(txn, g);
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			return parseForum(group);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Forum> getForums() throws DbException {
		try {
			Collection<Group> groups;
			Transaction txn = db.startTransaction();
			try {
				groups = db.getGroups(txn, CLIENT_ID);
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			List<Forum> forums = new ArrayList<Forum>();
			for (Group g : groups) forums.add(parseForum(g));
			return Collections.unmodifiableList(forums);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public byte[] getPostBody(MessageId m) throws DbException {
		try {
			byte[] raw;
			Transaction txn = db.startTransaction();
			try {
				raw = db.getRawMessage(txn, m);
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			ByteArrayInputStream in = new ByteArrayInputStream(raw,
					MESSAGE_HEADER_LENGTH, raw.length - MESSAGE_HEADER_LENGTH);
			BdfReader r = bdfReaderFactory.createReader(in);
			r.readListStart();
			if (r.hasRaw()) r.skipRaw(); // Parent ID
			else r.skipNull(); // No parent
			if (r.hasList()) r.skipList(); // Author
			else r.skipNull(); // No author
			r.skipString(); // Content type
			byte[] postBody = r.readRaw(MAX_FORUM_POST_BODY_LENGTH);
			if (r.hasRaw()) r.skipRaw(); // Signature
			else r.skipNull();
			r.readListEnd();
			if (!r.eof()) throw new FormatException();
			return postBody;
		} catch (FormatException e) {
			throw new DbException(e);
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayInputStream
			throw new RuntimeException(e);
		}
	}

	@Override
	public Collection<ForumPostHeader> getPostHeaders(GroupId g)
			throws DbException {
		Set<AuthorId> localAuthorIds = new HashSet<AuthorId>();
		Set<AuthorId> contactAuthorIds = new HashSet<AuthorId>();
		Map<MessageId, Metadata> metadata;
		Transaction txn = db.startTransaction();
		try {
			// Load the IDs of the user's identities
			for (LocalAuthor a : db.getLocalAuthors(txn))
				localAuthorIds.add(a.getId());
			// Load the IDs of contacts' identities
			for (Contact c : db.getContacts(txn))
				contactAuthorIds.add(c.getAuthor().getId());
			// Load the metadata
			metadata = db.getMessageMetadata(txn, g);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		// Parse the metadata
		Collection<ForumPostHeader> headers = new ArrayList<ForumPostHeader>();
		for (Entry<MessageId, Metadata> e : metadata.entrySet()) {
			try {
				BdfDictionary d = metadataParser.parse(e.getValue());
				long timestamp = d.getLong("timestamp");
				Author author = null;
				Author.Status authorStatus = ANONYMOUS;
				BdfDictionary d1 = d.getDictionary("author", null);
				if (d1 != null) {
					AuthorId authorId = new AuthorId(d1.getRaw("id"));
					String name = d1.getString("name");
					byte[] publicKey = d1.getRaw("publicKey");
					author = new Author(authorId, name, publicKey);
					if (localAuthorIds.contains(authorId))
						authorStatus = VERIFIED;
					else if (contactAuthorIds.contains(authorId))
						authorStatus = VERIFIED;
					else authorStatus = UNKNOWN;
				}
				String contentType = d.getString("contentType");
				boolean read = d.getBoolean("read");
				headers.add(new ForumPostHeader(e.getKey(), timestamp, author,
						authorStatus, contentType, read));
			} catch (FormatException ex) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, ex.toString(), ex);
			}
		}
		return headers;
	}

	@Override
	public void setReadFlag(MessageId m, boolean read) throws DbException {
		try {
			BdfDictionary d = new BdfDictionary();
			d.put("read", read);
			Metadata meta = metadataEncoder.encode(d);
			Transaction txn = db.startTransaction();
			try {
				db.mergeMessageMetadata(txn, m, meta);
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private Forum parseForum(Group g) throws FormatException {
		ByteArrayInputStream in = new ByteArrayInputStream(g.getDescriptor());
		BdfReader r = bdfReaderFactory.createReader(in);
		try {
			r.readListStart();
			String name = r.readString(MAX_FORUM_NAME_LENGTH);
			byte[] salt = r.readRaw(FORUM_SALT_LENGTH);
			r.readListEnd();
			if (!r.eof()) throw new FormatException();
			return new Forum(g, name, salt);
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayInputStream
			throw new RuntimeException(e);
		}
	}
}
