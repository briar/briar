package org.briarproject.briar.android.blog;

import android.os.Bundle;
import android.support.annotation.UiThread;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class FeedPostFragment extends BasePostFragment {

	private static final String TAG = FeedPostFragment.class.getName();

	private GroupId blogId;

	@Inject
	FeedController feedController;

	static FeedPostFragment newInstance(GroupId blogId, MessageId postId) {
		FeedPostFragment f = new FeedPostFragment();

		Bundle bundle = new Bundle();
		bundle.putByteArray(GROUP_ID, blogId.getBytes());
		bundle.putByteArray(POST_ID, postId.getBytes());

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
		blogId = new GroupId(b);

		return super.onCreateView(inflater, container, savedInstanceState);
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
	public void onStart() {
		super.onStart();
		feedController.loadBlogPost(blogId, postId,
				new UiResultExceptionHandler<BlogPostItem, DbException>(
						this) {
					@Override
					public void onResultUi(BlogPostItem post) {
						onBlogPostLoaded(post);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
	}

}
