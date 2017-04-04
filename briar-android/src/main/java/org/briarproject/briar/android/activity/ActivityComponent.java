package org.briarproject.briar.android.activity;

import android.app.Activity;

import org.briarproject.briar.android.AndroidComponent;
import org.briarproject.briar.android.blog.BlogActivity;
import org.briarproject.briar.android.blog.BlogFragment;
import org.briarproject.briar.android.blog.BlogModule;
import org.briarproject.briar.android.blog.BlogPostFragment;
import org.briarproject.briar.android.blog.FeedFragment;
import org.briarproject.briar.android.blog.FeedPostFragment;
import org.briarproject.briar.android.blog.ReblogActivity;
import org.briarproject.briar.android.blog.ReblogFragment;
import org.briarproject.briar.android.blog.RssFeedImportActivity;
import org.briarproject.briar.android.blog.RssFeedManageActivity;
import org.briarproject.briar.android.blog.WriteBlogPostActivity;
import org.briarproject.briar.android.contact.ContactListFragment;
import org.briarproject.briar.android.contact.ContactModule;
import org.briarproject.briar.android.contact.ConversationActivity;
import org.briarproject.briar.android.forum.CreateForumActivity;
import org.briarproject.briar.android.forum.ForumActivity;
import org.briarproject.briar.android.forum.ForumListFragment;
import org.briarproject.briar.android.forum.ForumModule;
import org.briarproject.briar.android.introduction.ContactChooserFragment;
import org.briarproject.briar.android.introduction.IntroductionActivity;
import org.briarproject.briar.android.introduction.IntroductionMessageFragment;
import org.briarproject.briar.android.invitation.AddContactActivity;
import org.briarproject.briar.android.keyagreement.IntroFragment;
import org.briarproject.briar.android.keyagreement.KeyAgreementActivity;
import org.briarproject.briar.android.keyagreement.ShowQrCodeFragment;
import org.briarproject.briar.android.login.ChangePasswordActivity;
import org.briarproject.briar.android.login.PasswordActivity;
import org.briarproject.briar.android.login.SetupActivity;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.briarproject.briar.android.panic.PanicPreferencesActivity;
import org.briarproject.briar.android.panic.PanicResponderActivity;
import org.briarproject.briar.android.privategroup.conversation.GroupActivity;
import org.briarproject.briar.android.privategroup.conversation.GroupConversationModule;
import org.briarproject.briar.android.privategroup.creation.CreateGroupActivity;
import org.briarproject.briar.android.privategroup.creation.CreateGroupFragment;
import org.briarproject.briar.android.privategroup.creation.CreateGroupMessageFragment;
import org.briarproject.briar.android.privategroup.creation.GroupCreateModule;
import org.briarproject.briar.android.privategroup.creation.GroupInviteActivity;
import org.briarproject.briar.android.privategroup.creation.GroupInviteFragment;
import org.briarproject.briar.android.privategroup.invitation.GroupInvitationActivity;
import org.briarproject.briar.android.privategroup.invitation.GroupInvitationModule;
import org.briarproject.briar.android.privategroup.list.GroupListFragment;
import org.briarproject.briar.android.privategroup.list.GroupListModule;
import org.briarproject.briar.android.privategroup.memberlist.GroupMemberListActivity;
import org.briarproject.briar.android.privategroup.memberlist.GroupMemberModule;
import org.briarproject.briar.android.privategroup.reveal.GroupRevealModule;
import org.briarproject.briar.android.privategroup.reveal.RevealContactsActivity;
import org.briarproject.briar.android.privategroup.reveal.RevealContactsFragment;
import org.briarproject.briar.android.settings.SettingsActivity;
import org.briarproject.briar.android.sharing.BlogInvitationActivity;
import org.briarproject.briar.android.sharing.BlogSharingStatusActivity;
import org.briarproject.briar.android.sharing.ForumInvitationActivity;
import org.briarproject.briar.android.sharing.ForumSharingStatusActivity;
import org.briarproject.briar.android.sharing.ShareBlogActivity;
import org.briarproject.briar.android.sharing.ShareBlogFragment;
import org.briarproject.briar.android.sharing.ShareBlogMessageFragment;
import org.briarproject.briar.android.sharing.ShareForumActivity;
import org.briarproject.briar.android.sharing.ShareForumFragment;
import org.briarproject.briar.android.sharing.ShareForumMessageFragment;
import org.briarproject.briar.android.sharing.SharingModule;
import org.briarproject.briar.android.splash.SplashScreenActivity;

import dagger.Component;

@ActivityScope
@Component(
		modules = {ActivityModule.class, ForumModule.class, SharingModule.class,
				BlogModule.class, ContactModule.class, GroupListModule.class,
				GroupCreateModule.class, GroupInvitationModule.class,
				GroupConversationModule.class, GroupMemberModule.class,
				GroupRevealModule.class},
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

	void inject(RevealContactsActivity activity);

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

	// Fragments
	void inject(ContactListFragment fragment);

	void inject(CreateGroupFragment fragment);

	void inject(CreateGroupMessageFragment fragment);

	void inject(GroupListFragment fragment);

	void inject(GroupInviteFragment fragment);

	void inject(RevealContactsFragment activity);

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
