package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogManager.RemoveBlogHook;
import org.briarproject.briar.api.blog.BlogSharingManager;
import org.briarproject.briar.api.client.MessageTracker;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class BlogSharingManagerImpl extends SharingManagerImpl<Blog>
		implements BlogSharingManager, RemoveBlogHook {

	private final IdentityManager identityManager;
	private final BlogManager blogManager;

	@Inject
	BlogSharingManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			MetadataParser metadataParser, MessageParser<Blog> messageParser,
			SessionEncoder sessionEncoder, SessionParser sessionParser,
			MessageTracker messageTracker,
			ContactGroupFactory contactGroupFactory,
			ProtocolEngine<Blog> engine,
			InvitationFactory<Blog, BlogInvitationResponse> invitationFactory,
			IdentityManager identityManager, BlogManager blogManager) {
		super(db, clientHelper, metadataParser, messageParser, sessionEncoder,
				sessionParser, messageTracker, contactGroupFactory, engine,
				invitationFactory);
		this.identityManager = identityManager;
		this.blogManager = blogManager;
	}

	@Override
	protected ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		super.addingContact(txn, c);
		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		Blog ourBlog = blogManager.getPersonalBlog(localAuthor);
		Blog theirBlog = blogManager.getPersonalBlog(c.getAuthor());
		try {
			initializeSharedSession(txn, c, ourBlog);
			initializeSharedSession(txn, c, theirBlog);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void removingBlog(Transaction txn, Blog b) throws DbException {
		removingShareable(txn, b);
	}

}
