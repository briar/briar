package org.briarproject.android;

import android.app.Activity;

import org.briarproject.android.blogs.BlogActivity;
import org.briarproject.android.blogs.BlogFragment;
import org.briarproject.android.blogs.BlogPostFragment;
import org.briarproject.android.blogs.FeedFragment;
import org.briarproject.android.blogs.FeedPostFragment;
import org.briarproject.android.blogs.ReblogActivity;
import org.briarproject.android.blogs.ReblogFragment;
import org.briarproject.android.blogs.RssFeedImportActivity;
import org.briarproject.android.blogs.RssFeedManageActivity;
import org.briarproject.android.blogs.WriteBlogPostActivity;
import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.forum.CreateForumActivity;
import org.briarproject.android.forum.ForumActivity;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.introduction.ContactChooserFragment;
import org.briarproject.android.introduction.IntroductionActivity;
import org.briarproject.android.introduction.IntroductionMessageFragment;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.keyagreement.IntroFragment;
import org.briarproject.android.keyagreement.KeyAgreementActivity;
import org.briarproject.android.keyagreement.ShowQrCodeFragment;
import org.briarproject.android.panic.PanicPreferencesActivity;
import org.briarproject.android.panic.PanicResponderActivity;
import org.briarproject.android.privategroup.conversation.GroupActivity;
import org.briarproject.android.privategroup.creation.CreateGroupActivity;
import org.briarproject.android.privategroup.creation.CreateGroupFragment;
import org.briarproject.android.privategroup.creation.CreateGroupMessageFragment;
import org.briarproject.android.privategroup.creation.GroupInviteActivity;
import org.briarproject.android.privategroup.creation.GroupInviteFragment;
import org.briarproject.android.privategroup.invitation.GroupInvitationActivity;
import org.briarproject.android.privategroup.list.GroupListFragment;
import org.briarproject.android.privategroup.memberlist.GroupMemberListActivity;
import org.briarproject.android.sharing.BlogInvitationActivity;
import org.briarproject.android.sharing.BlogSharingStatusActivity;
import org.briarproject.android.sharing.ForumInvitationActivity;
import org.briarproject.android.sharing.ForumSharingStatusActivity;
import org.briarproject.android.sharing.ShareBlogActivity;
import org.briarproject.android.sharing.ShareBlogFragment;
import org.briarproject.android.sharing.ShareBlogMessageFragment;
import org.briarproject.android.sharing.ShareForumActivity;
import org.briarproject.android.sharing.ShareForumFragment;
import org.briarproject.android.sharing.ShareForumMessageFragment;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider;
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel;

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

	void inject(ForumInvitationActivity activity);

	void inject(BlogInvitationActivity activity);

	void inject(CreateGroupActivity activity);

	void inject(GroupActivity activity);

	void inject(GroupInviteActivity activity);

	void inject(GroupInvitationActivity activity);

	void inject(GroupMemberListActivity activity);

	void inject(CreateForumActivity activity);

	void inject(ShareForumActivity activity);

	void inject(ShareBlogActivity activity);

	void inject(ForumSharingStatusActivity activity);

	void inject(BlogSharingStatusActivity activity);

	void inject(ForumActivity activity);

	void inject(BlogActivity activity);

	void inject(WriteBlogPostActivity activity);

	void inject(BlogFragment fragment);

	void inject(BlogPostFragment fragment);

	void inject(FeedPostFragment fragment);

	void inject(ReblogFragment fragment);

	void inject(ReblogActivity activity);

	void inject(SettingsActivity activity);

	void inject(ChangePasswordActivity activity);

	void inject(IntroductionActivity activity);

	void inject(RssFeedImportActivity activity);

	void inject(RssFeedManageActivity activity);

	void inject(EmojiProvider emojiProvider);

	void inject(RecentEmojiPageModel recentEmojiPageModel);

	// Fragments
	void inject(ContactListFragment fragment);

	void inject(CreateGroupFragment fragment);

	void inject(CreateGroupMessageFragment fragment);

	void inject(GroupListFragment fragment);

	void inject(GroupInviteFragment fragment);

	void inject(ForumListFragment fragment);

	void inject(FeedFragment fragment);

	void inject(IntroFragment fragment);

	void inject(ShowQrCodeFragment fragment);

	void inject(ContactChooserFragment fragment);

	void inject(ShareForumFragment fragment);

	void inject(ShareForumMessageFragment fragment);

	void inject(ShareBlogFragment fragment);

	void inject(ShareBlogMessageFragment fragment);

	void inject(IntroductionMessageFragment fragment);

}
