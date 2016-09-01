package org.briarproject.android.blogs;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import javax.inject.Inject;

import static android.view.View.FOCUS_DOWN;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.android.BriarActivity.GROUP_ID;
import static org.briarproject.android.blogs.BlogActivity.POST_ID;

public class ReblogFragment extends BaseFragment {

	public static final String TAG = ReblogFragment.class.getName();


	private BaseFragmentListener listener;
	private ViewHolder ui;
	private GroupId blogId;
	private MessageId postId;
	private BlogPostItem item;

	@Inject
	FeedController feedController;

	static ReblogFragment newInstance(GroupId groupId, MessageId messageId) {
		ReblogFragment f = new ReblogFragment();

		Bundle args = new Bundle();
		args.putByteArray(GROUP_ID, groupId.getBytes());
		args.putByteArray(POST_ID, messageId.getBytes());
		f.setArguments(args);

		return f;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			listener = (BaseFragmentListener) context;
		} catch (ClassCastException e) {
			throw new ClassCastException(
					"Using class must implement BaseFragmentListener");
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setHasOptionsMenu(true);

		Bundle args = getArguments();
		blogId = new GroupId(args.getByteArray(GROUP_ID));
		postId = new MessageId(args.getByteArray(POST_ID));

		View v = inflater.inflate(R.layout.fragment_reblog, container,
				false);
		ui = new ViewHolder(v);
		ui.post.setTransitionName(postId);
		showProgressBar();

		return v;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		listener.getActivityComponent().inject(this);
	}

	@Override
	public void onStart() {
		super.onStart();

		feedController.loadBlogPost(blogId, postId,
				new UiResultExceptionHandler<BlogPostItem, DbException>(
						getActivity()) {
					@Override
					public void onResultUi(BlogPostItem result) {
						item = result;
						bindViewHolder();
					}
					@Override
					public void onExceptionUi(DbException exception) {
						// TODO
						finish();
					}
				});
	}

	private void bindViewHolder() {
		if (item == null) return;

		ui.post.bindItem(item);
		ui.post.hideReblogButton();

		ui.publish.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				send();
				finish();
			}
		});
		ui.publish.setEnabled(true);
		hideProgressBar();
		ui.scrollView.post(new Runnable() {
			@Override
			public void run() {
				ui.scrollView.fullScroll(FOCUS_DOWN);
			}
		});
	}

	private void send() {
		String comment = getComment();
		feedController.repeatPost(item, comment,
				new UiResultExceptionHandler<Void, DbException>(getActivity()) {
					@Override
					public void onResultUi(Void result) {
						// do nothing, this fragment is gone already
					}
					@Override
					public void onExceptionUi(DbException exception) {
						// do nothing, this fragment is gone already
					}
				});
	}

	@Nullable
	private String getComment() {
		if (ui.input.getText().length() == 0) return null;
		return ui.input.getText().toString();
	}

	private void showProgressBar() {
		ui.progressBar.setVisibility(VISIBLE);
		ui.input.setVisibility(GONE);
		ui.publish.setVisibility(GONE);
	}

	private void hideProgressBar() {
		ui.progressBar.setVisibility(INVISIBLE);
		ui.input.setVisibility(VISIBLE);
		ui.publish.setVisibility(VISIBLE);
	}

	private static class ViewHolder {
		private final ScrollView scrollView;
		private final ProgressBar progressBar;
		private final BlogPostViewHolder post;
		private final EditText input;
		private final Button publish;

		private ViewHolder(View v) {
			scrollView = (ScrollView) v.findViewById(R.id.scrollView);
			progressBar = (ProgressBar) v.findViewById(R.id.progressBar);
			post = new BlogPostViewHolder(v.findViewById(R.id.postLayout));
			input = (EditText) v.findViewById(R.id.inputText);
			publish = (Button) v.findViewById(R.id.publishButton);
		}
	}
}
