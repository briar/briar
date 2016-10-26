package org.briarproject.android.blogs;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.blogs.BaseController.OnBlogPostAddedListener;
import org.briarproject.android.blogs.BlogPostAdapter.OnBlogPostClickListener;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.sharing.ShareBlogActivity;
import org.briarproject.android.sharing.SharingStatusBlogActivity;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

import javax.inject.Inject;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.support.design.widget.Snackbar.LENGTH_LONG;
import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;
import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.android.BriarActivity.GROUP_ID;
import static org.briarproject.android.blogs.BlogActivity.BLOG_NAME;
import static org.briarproject.android.blogs.BlogActivity.IS_NEW_BLOG;
import static org.briarproject.android.blogs.BlogActivity.REQUEST_SHARE;
import static org.briarproject.android.blogs.BlogActivity.REQUEST_WRITE_POST;

public class BlogFragment extends BaseFragment implements
		OnBlogPostAddedListener {

	public final static String TAG = BlogFragment.class.getName();

	@Inject
	BlogController blogController;

	private GroupId groupId;
	private String blogName;
	private BlogPostAdapter adapter;
	private BriarRecyclerView list;
	private MenuItem writeButton, deleteButton;
	private boolean isMyBlog = false, canDeleteBlog = false;

	static BlogFragment newInstance(GroupId groupId, @Nullable String name,
			boolean isNew) {

		BlogFragment f = new BlogFragment();

		Bundle bundle = new Bundle();
		bundle.putByteArray(GROUP_ID, groupId.getBytes());
		bundle.putString(BLOG_NAME, name);
		bundle.putBoolean(IS_NEW_BLOG, isNew);

		f.setArguments(bundle);
		return f;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		Bundle args = getArguments();
		byte[] b = args.getByteArray(GROUP_ID);
		if (b == null) throw new IllegalStateException("No group ID in args");
		groupId = new GroupId(b);
		blogName = args.getString(BLOG_NAME);
		boolean isNew = args.getBoolean(IS_NEW_BLOG);

		View v = inflater.inflate(R.layout.fragment_blog, container, false);

		adapter = new BlogPostAdapter(getActivity(),
				(OnBlogPostClickListener) getActivity());
		list = (BriarRecyclerView) v.findViewById(R.id.postList);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.showProgressBar();
		list.setEmptyText(getString(R.string.blogs_other_blog_empty_state));

		// show snackbar if this blog was just created
		if (isNew) {
			Snackbar s = Snackbar.make(list, R.string.blogs_my_blogs_created,
					LENGTH_LONG);
			s.getView().setBackgroundResource(R.color.briar_primary);
			s.show();

			// show only once
			args.putBoolean(IS_NEW_BLOG, false);
		}
		return v;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		blogController.setOnBlogPostAddedListener(this);
	}

	@Override
	public void onStart() {
		super.onStart();
		loadBlog();
		loadBlogPosts(false);
		list.startPeriodicUpdate();
	}

	@Override
	public void onStop() {
		super.onStop();
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
		ActivityOptionsCompat options =
				makeCustomAnimation(getActivity(),
						android.R.anim.slide_in_left,
						android.R.anim.slide_out_right);
		switch (item.getItemId()) {
			case R.id.action_write_blog_post:
				Intent i = new Intent(getActivity(),
						WriteBlogPostActivity.class);
				i.putExtra(GROUP_ID, groupId.getBytes());
				i.putExtra(BLOG_NAME, blogName);
				startActivityForResult(i, REQUEST_WRITE_POST,
						options.toBundle());
				return true;
			case R.id.action_blog_share:
				Intent i2 = new Intent(getActivity(), ShareBlogActivity.class);
				i2.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i2.putExtra(GROUP_ID, groupId.getBytes());
				startActivityForResult(i2, REQUEST_SHARE, options.toBundle());
				return true;
			case R.id.action_blog_sharing_status:
				Intent i3 = new Intent(getActivity(),
						SharingStatusBlogActivity.class);
				i3.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i3.putExtra(GROUP_ID, groupId.getBytes());
				startActivity(i3, options.toBundle());
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

		if (request == REQUEST_WRITE_POST && result == RESULT_OK) {
			displaySnackbar(R.string.blogs_blog_post_created, true);
			loadBlogPosts(true);
		} else if (request == REQUEST_SHARE && result == RESULT_OK) {
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
						listener) {
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
						// TODO: Decide how to handle errors in the UI
						finish();
					}
				}
		);
	}

	void loadBlogPosts(final boolean reload) {
		blogController.loadBlogPosts(
				new UiResultExceptionHandler<Collection<BlogPostItem>, DbException>(
						listener) {
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
						// TODO: Decide how to handle errors in the UI
						finish();
					}
				});
	}

	private void loadBlog() {
		blogController.loadBlog(
				new UiResultExceptionHandler<BlogItem, DbException>(listener) {
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
						// TODO: Decide how to handle errors in the UI
						finish();
					}
				});
	}

	private void setToolbarTitle(Author a) {
		String title = getString(R.string.blogs_personal_blog, a.getName());
		getActivity().setTitle(title);

		// safe title in intent, so it can be restored automatically
		Intent intent = getActivity().getIntent();
		intent.putExtra(BLOG_NAME, title);
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
				new UiResultExceptionHandler<Void, DbException>(listener) {
					@Override
					public void onResultUi(Void result) {
						Toast.makeText(getActivity(),
								R.string.blogs_blog_removed, LENGTH_SHORT)
								.show();
						finish();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO: Decide how to handle errors in the UI
						finish();
					}
				});
	}

	@Override
	public void onBlogRemoved() {
		finish();
	}
}
