package org.briarproject.briar.android.blog;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.UiThread;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.blog.BlogController.BlogSharingListener;
import org.briarproject.briar.android.blog.BlogPostAdapter.OnBlogPostClickListener;
import org.briarproject.briar.android.controller.SharingController;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.sharing.BlogSharingStatusActivity;
import org.briarproject.briar.android.sharing.ShareBlogActivity;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.api.blog.BlogPostHeader;

import java.util.Collection;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_SHARE_BLOG;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_WRITE_BLOG_POST;
import static org.briarproject.briar.android.controller.SharingController.SharingListener;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class BlogFragment extends BaseFragment
		implements BlogSharingListener, SharingListener,
		OnBlogPostClickListener {

	private final static String TAG = BlogFragment.class.getName();

	@Inject
	BlogController blogController;
	@Inject
	SharingController sharingController;

	private GroupId groupId;
	private BlogPostAdapter adapter;
	private BriarRecyclerView list;
	private MenuItem writeButton, deleteButton;
	private boolean isMyBlog = false, canDeleteBlog = false;

	static BlogFragment newInstance(GroupId groupId) {

		BlogFragment f = new BlogFragment();

		Bundle bundle = new Bundle();
		bundle.putByteArray(GROUP_ID, groupId.getBytes());

		f.setArguments(bundle);
		return f;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		Bundle args = getArguments();
		byte[] b = args.getByteArray(GROUP_ID);
		if (b == null) throw new IllegalStateException("No group ID in args");
		groupId = new GroupId(b);

		View v = inflater.inflate(R.layout.fragment_blog, container, false);

		adapter = new BlogPostAdapter(getActivity(), this);
		list = (BriarRecyclerView) v.findViewById(R.id.postList);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.showProgressBar();
		list.setEmptyText(getString(R.string.blogs_other_blog_empty_state));

		return v;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		blogController.setBlogSharingListener(this);
		sharingController.setSharingListener(this);
	}

	@Override
	public void onStart() {
		super.onStart();
		sharingController.onStart();
		loadBlog();
		loadSharedContacts();
		loadBlogPosts(false);
		list.startPeriodicUpdate();
	}

	@Override
	public void onStop() {
		super.onStop();
		sharingController.onStop();
		list.stopPeriodicUpdate();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.blogs_blog_actions, menu);
		writeButton = menu.findItem(R.id.action_write_blog_post);
		if (isMyBlog) writeButton.setVisible(true);
		deleteButton = menu.findItem(R.id.action_blog_delete);
		if (canDeleteBlog) deleteButton.setEnabled(true);

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_write_blog_post:
				Intent i = new Intent(getActivity(),
						WriteBlogPostActivity.class);
				i.putExtra(GROUP_ID, groupId.getBytes());
				startActivityForResult(i, REQUEST_WRITE_BLOG_POST);
				return true;
			case R.id.action_blog_share:
				Intent i2 = new Intent(getActivity(), ShareBlogActivity.class);
				i2.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				i2.putExtra(GROUP_ID, groupId.getBytes());
				startActivityForResult(i2, REQUEST_SHARE_BLOG);
				return true;
			case R.id.action_blog_sharing_status:
				Intent i3 = new Intent(getActivity(),
						BlogSharingStatusActivity.class);
				i3.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				i3.putExtra(GROUP_ID, groupId.getBytes());
				startActivity(i3);
				return true;
			case R.id.action_blog_delete:
				showDeleteDialog();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);

		if (request == REQUEST_WRITE_BLOG_POST && result == RESULT_OK) {
			displaySnackbar(R.string.blogs_blog_post_created, true);
			loadBlogPosts(true);
		} else if (request == REQUEST_SHARE_BLOG && result == RESULT_OK) {
			displaySnackbar(R.string.blogs_sharing_snackbar, false);
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onBlogPostAdded(BlogPostHeader header, final boolean local) {
		blogController.loadBlogPost(header,
				new UiResultExceptionHandler<BlogPostItem, DbException>(
						this) {
					@Override
					public void onResultUi(BlogPostItem post) {
						adapter.add(post);
						if (local) {
							list.scrollToPosition(0);
							displaySnackbar(R.string.blogs_blog_post_created,
									false);
						} else {
							displaySnackbar(R.string.blogs_blog_post_received,
									true);
						}
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				}
		);
	}

	@Override
	public void onBlogPostClick(BlogPostItem post) {
		BlogPostFragment f = BlogPostFragment.newInstance(post.getId());
		showNextFragment(f);
	}

	private void loadBlogPosts(final boolean reload) {
		blogController.loadBlogPosts(
				new UiResultExceptionHandler<Collection<BlogPostItem>, DbException>(
						this) {
					@Override
					public void onResultUi(Collection<BlogPostItem> posts) {
						if (posts.isEmpty()) {
							list.showData();
						} else {
							adapter.addAll(posts);
							if (reload) list.scrollToPosition(0);
						}
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
	}

	private void loadBlog() {
		blogController.loadBlog(
				new UiResultExceptionHandler<BlogItem, DbException>(this) {
					@Override
					public void onResultUi(BlogItem blog) {
						setToolbarTitle(blog.getBlog().getAuthor());
						if (blog.isOurs())
							showWriteButton();
						if (blog.canBeRemoved())
							enableDeleteButton();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
	}

	private void setToolbarTitle(Author a) {
		String title = getString(R.string.blogs_personal_blog, a.getName());
		getActivity().setTitle(title);
	}

	private void loadSharedContacts() {
		blogController.loadSharingContacts(
				new UiResultExceptionHandler<Collection<ContactId>, DbException>(this) {
					@Override
					public void onResultUi(Collection<ContactId> contacts) {
						sharingController.addAll(contacts);
						int online = sharingController.getOnlineCount();
						setToolbarSubTitle(contacts.size(), online);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
	}

	@Override
	public void onBlogInvitationAccepted(ContactId c) {
		sharingController.add(c);
		setToolbarSubTitle(sharingController.getTotalCount(),
				sharingController.getOnlineCount());
	}

	@Override
	public void onBlogLeft(ContactId c) {
		sharingController.remove(c);
		setToolbarSubTitle(sharingController.getTotalCount(),
				sharingController.getOnlineCount());
	}

	@Override
	public void onSharingInfoUpdated(int total, int online) {
		setToolbarSubTitle(total, online);
	}

	private void setToolbarSubTitle(int total, int online) {
		ActionBar actionBar =
				((BriarActivity) getActivity()).getSupportActionBar();
		if (actionBar != null) {
			actionBar.setSubtitle(
					getString(R.string.shared_with, total, online));
		}
	}

	private void showWriteButton() {
		isMyBlog = true;
		if (writeButton != null)
			writeButton.setVisible(true);
	}

	private void enableDeleteButton() {
		canDeleteBlog = true;
		if (deleteButton != null)
			deleteButton.setEnabled(true);
	}

	private void displaySnackbar(int stringId, boolean scroll) {
		Snackbar snackbar =
				Snackbar.make(list, stringId, Snackbar.LENGTH_LONG);
		snackbar.getView().setBackgroundResource(R.color.briar_primary);
		if (scroll) {
			View.OnClickListener onClick = new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					list.smoothScrollToPosition(0);
				}
			};
			snackbar.setActionTextColor(ContextCompat
					.getColor(getContext(),
							R.color.briar_button_positive));
			snackbar.setAction(R.string.blogs_blog_post_scroll_to, onClick);
		}
		snackbar.show();
	}

	private void showDeleteDialog() {
		DialogInterface.OnClickListener okListener =
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						deleteBlog();
					}
				};
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
				R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.blogs_remove_blog));
		builder.setMessage(
				getString(R.string.blogs_remove_blog_dialog_message));
		builder.setPositiveButton(R.string.cancel, null);
		builder.setNegativeButton(R.string.blogs_remove_blog_ok, okListener);
		builder.show();
	}

	private void deleteBlog() {
		blogController.deleteBlog(
				new UiResultExceptionHandler<Void, DbException>(this) {
					@Override
					public void onResultUi(Void result) {
						Toast.makeText(getActivity(),
								R.string.blogs_blog_removed, LENGTH_SHORT)
								.show();
						finish();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
	}

	@Override
	public void onBlogRemoved() {
		finish();
	}

}
