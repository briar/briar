package org.briarproject.android;

import android.app.Activity;

import org.briarproject.android.blogs.BlogActivity;
import org.briarproject.android.blogs.BlogFragment;
import org.briarproject.android.blogs.BlogPostFragment;
import org.briarproject.android.blogs.CreateBlogActivity;
import org.briarproject.android.blogs.MyBlogsFragment;
import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.blogs.WriteBlogPostActivity;
import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.forum.AvailableForumsActivity;
import org.briarproject.android.forum.ContactSelectorFragment;
import org.briarproject.android.forum.CreateForumActivity;
import org.briarproject.android.forum.ForumActivity;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.forum.ForumSharingStatusActivity;
import org.briarproject.android.forum.ShareForumActivity;
import org.briarproject.android.forum.ShareForumMessageFragment;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.identity.CreateIdentityActivity;
import org.briarproject.android.introduction.ContactChooserFragment;
import org.briarproject.android.introduction.IntroductionActivity;
import org.briarproject.android.introduction.IntroductionMessageFragment;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.keyagreement.ChooseIdentityFragment;
import org.briarproject.android.keyagreement.KeyAgreementActivity;
import org.briarproject.android.keyagreement.ShowQrCodeFragment;
import org.briarproject.android.panic.PanicPreferencesActivity;
import org.briarproject.android.panic.PanicResponderActivity;

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

	void inject(AvailableForumsActivity activity);

	void inject(CreateForumActivity activity);

	void inject(ShareForumActivity activity);

	void inject(ForumSharingStatusActivity activity);

	void inject(ForumActivity activity);

	void inject(CreateBlogActivity activity);

	void inject(BlogActivity activity);

	void inject(WriteBlogPostActivity activity);

	void inject(BlogFragment fragment);

	void inject(BlogPostFragment fragment);

	void inject(SettingsActivity activity);

	void inject(ChangePasswordActivity activity);

	void inject(IntroductionActivity activity);

	// Fragments
	void inject(ContactListFragment fragment);
	void inject(ForumListFragment fragment);
	void inject(BaseFragment fragment);
	void inject(MyBlogsFragment fragment);
	void inject(ChooseIdentityFragment fragment);
	void inject(ShowQrCodeFragment fragment);
	void inject(ContactChooserFragment fragment);
	void inject(ContactSelectorFragment fragment);
	void inject(ShareForumMessageFragment fragment);
	void inject(IntroductionMessageFragment fragment);

}
