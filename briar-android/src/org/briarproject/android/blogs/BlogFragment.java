package org.briarproject.android.blogs;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
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
import org.briarproject.android.blogs.BlogController.BlogPostListener;
import org.briarproject.android.blogs.BlogPostAdapter.OnBlogPostClickListener;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.sharing.ShareActivity;
import org.briarproject.android.sharing.SharingStatusActivity;
import org.briarproject.android.util.BriarRecyclerView;
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
import static org.briarproject.android.blogs.BlogActivity.IS_MY_BLOG;
import static org.briarproject.android.blogs.BlogActivity.IS_NEW_BLOG;
import static org.briarproject.android.blogs.BlogActivity.REQUEST_SHARE;
import static org.briarproject.android.blogs.BlogActivity.REQUEST_WRITE_POST;
import static org.briarproject.android.sharing.ShareActivity.BLOG;
import static org.briarproject.android.sharing.ShareActivity.SHAREABLE;

public class BlogFragment extends BaseFragment implements BlogPostListener {

	public final static String TAG = BlogFragment.class.getName();

	@Inject
	BlogController blogController;

	private GroupId groupId;
	private String blogName;
	private boolean myBlog;
	private BlogPostAdapter adapter;
	private BriarRecyclerView list;

	static BlogFragment newInstance(GroupId groupId, String name,
			boolean myBlog, boolean isNew) {

		BlogFragment f = new BlogFragment();

		Bundle bundle = new Bundle();
		bundle.putByteArray(GROUP_ID, groupId.getBytes());
		bundle.putString(BLOG_NAME, name);
		bundle.putBoolean(IS_MY_BLOG, myBlog);
		bundle.putBoolean(IS_NEW_BLOG, isNew);

		f.setArguments(bundle);
		return f;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setHasOptionsMenu(true);

		Bundle args = getArguments();
		byte[] b = args.getByteArray(GROUP_ID);
		if (b == null) throw new IllegalStateException("No Group found.");
		groupId = new GroupId(b);
		blogName = args.getString(BLOG_NAME);
		myBlog = args.getBoolean(IS_MY_BLOG);
		boolean isNew = args.getBoolean(IS_NEW_BLOG);

		View v = inflater.inflate(R.layout.fragment_blog, container, false);

		adapter = new BlogPostAdapter(getActivity(),
				(OnBlogPostClickListener) getActivity());
		list = (BriarRecyclerView) v.findViewById(R.id.postList);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		if (myBlog) {
			list.setEmptyText(
					getString(R.string.blogs_my_blogs_blog_empty_state));
		} else {
			list.setEmptyText(getString(R.string.blogs_other_blog_empty_state));
		}

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
	}

	@Override
	public void onStart() {
		super.onStart();
		loadData(false);
	}

	@Override
	public void onResume() {
		super.onResume();
		list.startPeriodicUpdate();
	}

	@Override
	public void onPause() {
		super.onPause();
		list.stopPeriodicUpdate();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		if (myBlog) {
			inflater.inflate(R.menu.blogs_my_blog_actions, menu);
		} else {
			inflater.inflate(R.menu.blogs_blog_actions, menu);
		}
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		ActivityOptionsCompat options =
				makeCustomAnimation(getActivity(),
						android.R.anim.slide_in_left,
						android.R.anim.slide_out_right);
		switch (item.getItemId()) {
			case android.R.id.home:
				getActivity().onBackPressed();
				return true;
			case R.id.action_write_blog_post:
				Intent i =
						new Intent(getActivity(), WriteBlogPostActivity.class);
				i.putExtra(GROUP_ID, groupId.getBytes());
				i.putExtra(BLOG_NAME, blogName);
				ActivityCompat.startActivityForResult(getActivity(), i,
						REQUEST_WRITE_POST, options.toBundle());
				return true;
			case R.id.action_blog_share:
				Intent i2 = new Intent(getActivity(), ShareActivity.class);
				i2.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i2.putExtra(GROUP_ID, groupId.getBytes());
				i2.putExtra(SHAREABLE, BLOG);
				startActivityForResult(i2, REQUEST_SHARE, options.toBundle());
				return true;
			case R.id.action_blog_sharing_status:
				Intent i3 =
						new Intent(getActivity(), SharingStatusActivity.class);
				i3.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i3.putExtra(GROUP_ID, groupId.getBytes());
				i3.putExtra(SHAREABLE, BLOG);
				startActivity(i3, options.toBundle());
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);

		if (request == REQUEST_SHARE && result == RESULT_OK) {
			displaySnackbar(R.string.blogs_sharing_snackbar);
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onBlogPostAdded(BlogPostItem post, boolean local) {
		adapter.add(post);
		if (local) list.scrollToPosition(0);
	}

	private void loadData(final boolean reload) {
		blogController.loadBlog(groupId, reload,
				new UiResultHandler<Boolean>(getActivity()) {
					@Override
					public void onResultUi(Boolean result) {
						if (result) {
							Collection<BlogPostItem> posts =
									blogController.getBlogPosts();
							if (posts.size() > 0) {
								adapter.addAll(posts);
								if (reload) list.scrollToPosition(0);
							} else {
								list.showData();
							}
						} else {
							Toast.makeText(getActivity(),
									R.string.blogs_blog_failed_to_load,
									LENGTH_SHORT).show();
							getActivity().supportFinishAfterTransition();
						}
					}
				});
	}

	void reload() {
		loadData(true);
	}

	private void displaySnackbar(int stringId) {
		Snackbar snackbar =
				Snackbar.make(list, stringId, Snackbar.LENGTH_SHORT);
		snackbar.getView().setBackgroundResource(R.color.briar_primary);
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
		builder.setTitle(getString(R.string.blogs_delete_blog));
		builder.setMessage(
				getString(R.string.blogs_delete_blog_dialog_message));
		builder.setPositiveButton(R.string.blogs_delete_blog_cancel, null);
		builder.setNegativeButton(R.string.blogs_delete_blog_ok, okListener);
		builder.show();
	}

	private void deleteBlog() {
		blogController.deleteBlog(
				new UiResultHandler<Boolean>(getActivity()) {
					@Override
					public void onResultUi(Boolean result) {
						if (!result) return;
						Toast.makeText(getActivity(),
								R.string.blogs_blog_deleted, LENGTH_SHORT)
								.show();
						getActivity().supportFinishAfterTransition();
					}
				});
	}

}
