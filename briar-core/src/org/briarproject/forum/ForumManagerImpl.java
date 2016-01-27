package org.briarproject.forum;

import com.google.inject.Inject;

import org.briarproject.api.FormatException;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

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
	private final GroupFactory groupFactory;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final MetadataEncoder metadataEncoder;
	private final MetadataParser metadataParser;
	private final SecureRandom random;

	@Inject
	ForumManagerImpl(CryptoComponent crypto, DatabaseComponent db,
			GroupFactory groupFactory, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, MetadataEncoder metadataEncoder,
			MetadataParser metadataParser) {
		this.db = db;
		this.groupFactory = groupFactory;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.metadataEncoder = metadataEncoder;
		this.metadataParser = metadataParser;
		random = crypto.getSecureRandom();
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public Forum createForum(String name) {
		int length = StringUtils.toUtf8(name).length;
		if (length == 0) throw new IllegalArgumentException();
		if (length > MAX_FORUM_NAME_LENGTH)
			throw new IllegalArgumentException();
		byte[] salt = new byte[FORUM_SALT_LENGTH];
		random.nextBytes(salt);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		try {
			w.writeListStart();
			w.writeString(name);
			w.writeRaw(salt);
			w.writeListEnd();
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException(e);
		}
		Group g = groupFactory.createGroup(CLIENT_ID, out.toByteArray());
		return new Forum(g, name);
	}

	@Override
	public boolean addForum(Forum f) throws DbException {
		return db.addGroup(f.getGroup());
	}

	@Override
	public void addLocalPost(ForumPost p) throws DbException {
		BdfDictionary d = new BdfDictionary();
		d.put("timestamp", p.getMessage().getTimestamp());
		if (p.getParent() != null) d.put("parent", p.getParent().getBytes());
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
		try {
			Metadata meta = metadataEncoder.encode(d);
			db.addLocalMessage(p.getMessage(), CLIENT_ID, meta, true);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	@Override
	public Collection<Forum> getAvailableForums() throws DbException {
		// TODO
		return Collections.emptyList();
	}

	private Forum parseForum(Group g) throws FormatException {
		ByteArrayInputStream in = new ByteArrayInputStream(g.getDescriptor());
		BdfReader r = bdfReaderFactory.createReader(in);
		try {
			r.readListStart();
			String name = r.readString(MAX_FORUM_NAME_LENGTH);
			if (name.length() == 0) throw new FormatException();
			byte[] salt = r.readRaw(FORUM_SALT_LENGTH);
			if (salt.length != FORUM_SALT_LENGTH) throw new FormatException();
			r.readListEnd();
			if (!r.eof()) throw new FormatException();
			return new Forum(g, name);
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayInputStream
			throw new RuntimeException(e);
		}
	}

	@Override
	public Forum getForum(GroupId g) throws DbException {
		Group group = db.getGroup(g);
		if (!group.getClientId().equals(CLIENT_ID))
			throw new IllegalArgumentException();
		try {
			return parseForum(group);
		} catch (FormatException e) {
			throw new IllegalArgumentException();
		}
	}

	@Override
	public Collection<Forum> getForums() throws DbException {
		Collection<Group> groups = db.getGroups(CLIENT_ID);
		List<Forum> forums = new ArrayList<Forum>(groups.size());
		for (Group g : groups) {
			try {
				forums.add(parseForum(g));
			} catch (FormatException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		return Collections.unmodifiableList(forums);
	}

	@Override
	public byte[] getPostBody(MessageId m) throws DbException {
		byte[] raw = db.getRawMessage(m);
		ByteArrayInputStream in = new ByteArrayInputStream(raw,
				MESSAGE_HEADER_LENGTH, raw.length - MESSAGE_HEADER_LENGTH);
		BdfReader r = bdfReaderFactory.createReader(in);
		try {
			// Extract the forum post body
			r.readListStart();
			if (r.hasRaw()) r.skipRaw(); // Parent ID
			else r.skipNull(); // No parent
			if (r.hasList()) r.skipList(); // Author
			else r.skipNull(); // No author
			r.skipString(); // Content type
			return r.readRaw(MAX_FORUM_POST_BODY_LENGTH);
		} catch (FormatException e) {
			// Not a valid forum post
			throw new IllegalArgumentException();
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayInputStream
			throw new RuntimeException(e);
		}
	}

	@Override
	public Collection<ForumPostHeader> getPostHeaders(GroupId g)
			throws DbException {
		// Load the IDs of the user's own identities and contacts' identities
		Set<AuthorId> localAuthorIds = new HashSet<AuthorId>();
		for (LocalAuthor a : db.getLocalAuthors())
			localAuthorIds.add(a.getId());
		Set<AuthorId> contactAuthorIds = new HashSet<AuthorId>();
		for (Contact c : db.getContacts())
			contactAuthorIds.add(c.getAuthor().getId());
		// Load and parse the metadata
		Map<MessageId, Metadata> metadata = db.getMessageMetadata(g);
		Collection<ForumPostHeader> headers = new ArrayList<ForumPostHeader>();
		for (Entry<MessageId, Metadata> e : metadata.entrySet()) {
			MessageId messageId = e.getKey();
			Metadata meta = e.getValue();
			try {
				BdfDictionary d = metadataParser.parse(meta);
				long timestamp = d.getInteger("timestamp");
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
				headers.add(new ForumPostHeader(messageId, timestamp, author,
						authorStatus, contentType, read));
			} catch (FormatException ex) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, ex.toString(), ex);
			}
		}
		return headers;
	}

	@Override
	public Collection<Contact> getSubscribers(GroupId g) throws DbException {
		// TODO
		return Collections.emptyList();
	}

	@Override
	public Collection<ContactId> getVisibility(GroupId g) throws DbException {
		return db.getVisibility(g);
	}

	@Override
	public void removeForum(Forum f) throws DbException {
		db.removeGroup(f.getGroup());
	}

	@Override
	public void setReadFlag(MessageId m, boolean read) throws DbException {
		BdfDictionary d = new BdfDictionary();
		d.put("read", read);
		try {
			db.mergeMessageMetadata(m, metadataEncoder.encode(d));
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setVisibility(GroupId g, Collection<ContactId> visible)
			throws DbException {
		db.setVisibility(g, visible);
	}

	@Override
	public void setVisibleToAll(GroupId g, boolean all) throws DbException {
		db.setVisibleToAll(g, all);
	}
}
