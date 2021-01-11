package org.briarproject.briar.android.blog;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.util.BriarSnackbarBuilder;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.api.blog.Blog;

import java.util.List;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static com.google.android.material.snackbar.Snackbar.LENGTH_LONG;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class FeedFragment extends BaseFragment
		implements OnBlogPostClickListener {

	public final static String TAG = FeedFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private FeedViewModel viewModel;
	private BlogPostAdapter adapter;
	private LinearLayoutManager layoutManager;
	private BriarRecyclerView list;
	@Nullable
	private Parcelable layoutManagerState;

	public static FeedFragment newInstance() {
		FeedFragment f = new FeedFragment();

		Bundle args = new Bundle();
		f.setArguments(args);

		return f;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		// TODO don't use NavDrawerActivity scope here
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(FeedViewModel.class);
		// TODO ideally we only do this once when the ViewModel gets created
		viewModel.loadPersonalBlog();
		viewModel.loadAllBlogPosts();
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		requireActivity().setTitle(R.string.blogs_button);

		View v = inflater.inflate(R.layout.fragment_blog, container, false);

		adapter = new BlogPostAdapter(this, getParentFragmentManager());

		layoutManager = new LinearLayoutManager(getActivity());
		list = v.findViewById(R.id.postList);
		list.setLayoutManager(layoutManager);
		list.setAdapter(adapter);
		list.setEmptyImage(R.drawable.ic_empty_state_blog);
		list.setEmptyText(R.string.blogs_feed_empty_state);
		list.setEmptyAction(R.string.blogs_feed_empty_state_action);

		viewModel.getAllBlogPosts().observe(getViewLifecycleOwner(), result ->
				result
						.onError(this::handleException)
						.onSuccess(this::onBlogPostsLoaded)
		);

		if (savedInstanceState != null) {
			layoutManagerState =
					savedInstanceState.getParcelable("layoutManager");
		}

		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		viewModel.blockAllBlogPostNotifications();
		viewModel.clearAllBlogPostNotifications();
		list.startPeriodicUpdate();
	}

	@Override
	public void onStop() {
		super.onStop();
		viewModel.unblockAllBlogPostNotifications();
		list.stopPeriodicUpdate();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (layoutManager != null) {
			layoutManagerState = layoutManager.onSaveInstanceState();
			outState.putParcelable("layoutManager", layoutManagerState);
		}
	}

	private void onBlogPostsLoaded(List<BlogPostItem> items) {
		if (items.isEmpty()) list.showData();
		else adapter.submitList(items, () -> {
			Boolean wasLocal = viewModel.getPostAddedWasLocalAndReset();
			if (wasLocal != null && wasLocal) {
				showSnackBar(R.string.blogs_blog_post_created);
			} else if (wasLocal != null) {
				showSnackBar(R.string.blogs_blog_post_received);
			}
		});
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.blogs_feed_actions, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.action_write_blog_post) {
			Blog personalBlog = viewModel.getPersonalBlog().getValue();
			if (personalBlog == null) return false;
			Intent i = new Intent(getActivity(), WriteBlogPostActivity.class);
			i.putExtra(GROUP_ID, personalBlog.getId().getBytes());
			startActivity(i);
			return true;
		} else if (itemId == R.id.action_rss_feeds_import) {
			Intent i = new Intent(getActivity(), RssFeedImportActivity.class);
			startActivity(i);
			return true;
		} else if (itemId == R.id.action_rss_feeds_manage) {
			Blog personalBlog = viewModel.getPersonalBlog().getValue();
			if (personalBlog == null) return false;
			Intent i = new Intent(getActivity(), RssFeedManageActivity.class);
			i.putExtra(GROUP_ID, personalBlog.getId().getBytes());
			startActivity(i);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBlogPostClick(BlogPostItem post) {
		FeedPostFragment f =
				FeedPostFragment.newInstance(post.getGroupId(), post.getId());
		showNextFragment(f);
	}

	@Override
	public void onAuthorClick(BlogPostItem post) {
		Intent i = new Intent(requireContext(), BlogActivity.class);
		i.putExtra(GROUP_ID, post.getGroupId().getBytes());
		i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
		requireContext().startActivity(i);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	private void showSnackBar(int stringRes) {
		int firstVisible =
				layoutManager.findFirstCompletelyVisibleItemPosition();
		int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
		int count = adapter.getItemCount();
		boolean scroll = count > (lastVisible - firstVisible + 1);

		BriarSnackbarBuilder sb = new BriarSnackbarBuilder();
		if (scroll) {
			sb.setAction(R.string.blogs_blog_post_scroll_to,
					v -> list.smoothScrollToPosition(0));
		}
		sb.make(list, stringRes, LENGTH_LONG).show();
	}

}
