package org.briarproject.android;

import android.app.Activity;

import org.briarproject.android.blogs.BlogActivity;
import org.briarproject.android.blogs.BlogFragment;
import org.briarproject.android.blogs.BlogListFragment;
import org.briarproject.android.blogs.BlogPostFragment;
import org.briarproject.android.blogs.BlogsFragment;
import org.briarproject.android.blogs.CreateBlogActivity;
import org.briarproject.android.blogs.FeedFragment;
import org.briarproject.android.blogs.MyBlogsFragment;
import org.briarproject.android.blogs.RssFeedImportActivity;
import org.briarproject.android.blogs.RssFeedManageActivity;
import org.briarproject.android.blogs.WriteBlogPostActivity;
import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.forum.CreateForumActivity;
import org.briarproject.android.forum.ForumActivity;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.identity.CreateIdentityActivity;
import org.briarproject.android.introduction.ContactChooserFragment;
import org.briarproject.android.introduction.IntroductionActivity;
import org.briarproject.android.introduction.IntroductionMessageFragment;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.keyagreement.IntroFragment;
import org.briarproject.android.keyagreement.KeyAgreementActivity;
import org.briarproject.android.keyagreement.ShowQrCodeFragment;
import org.briarproject.android.panic.PanicPreferencesActivity;
import org.briarproject.android.panic.PanicResponderActivity;
import org.briarproject.android.sharing.ContactSelectorFragment;
import org.briarproject.android.sharing.InvitationsBlogActivity;
import org.briarproject.android.sharing.InvitationsForumActivity;
import org.briarproject.android.sharing.ShareBlogActivity;
import org.briarproject.android.sharing.ShareBlogMessageFragment;
import org.briarproject.android.sharing.ShareForumActivity;
import org.briarproject.android.sharing.ShareForumMessageFragment;
import org.briarproject.android.sharing.SharingStatusBlogActivity;
import org.briarproject.android.sharing.SharingStatusForumActivity;

import dagger.Component;

@ActivityScope
@Component(modules = ActivityModule.class,
		dependencies = AndroidComponent.class)
public interface ActivityComponent {

	Activity activity();

	void inject(SplashScreenActivity activity);

	void inject(SetupActivity activity);

	void inject(NavDrawerActivity activity);

	void inject(PasswordActivity activity);

	void inject(PanicResponderActivity activity);

	void inject(PanicPreferencesActivity activity);

	void inject(AddContactActivity activity);

	void inject(KeyAgreementActivity activity);

	void inject(ConversationActivity activity);

	void inject(CreateIdentityActivity activity);

	void inject(InvitationsForumActivity activity);

	void inject(InvitationsBlogActivity activity);

	void inject(CreateForumActivity activity);

	void inject(ShareForumActivity activity);

	void inject(ShareBlogActivity activity);

	void inject(SharingStatusForumActivity activity);

	void inject(SharingStatusBlogActivity activity);

	void inject(ForumActivity activity);

	void inject(CreateBlogActivity activity);

	void inject(BlogActivity activity);

	void inject(WriteBlogPostActivity activity);

	void inject(BlogFragment fragment);

	void inject(BlogPostFragment fragment);

	void inject(SettingsActivity activity);

	void inject(ChangePasswordActivity activity);

	void inject(IntroductionActivity activity);

	void inject(RssFeedImportActivity activity);

	void inject(RssFeedManageActivity activity);

	// Fragments
	void inject(ContactListFragment fragment);
	void inject(ForumListFragment fragment);
	void inject(BlogsFragment fragment);
	void inject(BlogListFragment fragment);
	void inject(FeedFragment fragment);
	void inject(MyBlogsFragment fragment);
	void inject(IntroFragment fragment);
	void inject(ShowQrCodeFragment fragment);
	void inject(ContactChooserFragment fragment);
	void inject(ContactSelectorFragment fragment);
	void inject(ShareForumMessageFragment fragment);
	void inject(ShareBlogMessageFragment fragment);
	void inject(IntroductionMessageFragment fragment);

}
