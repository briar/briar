package org.briarproject.briar.android.blog;

import android.os.Bundle;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.blog.BaseController.BlogListener;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.api.blog.BlogPostHeader;

import javax.inject.Inject;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class BlogPostFragment extends BasePostFragment implements BlogListener {

	private static final String TAG = BlogPostFragment.class.getName();

	@Inject
	BlogController blogController;

	static BlogPostFragment newInstance(MessageId postId) {
		BlogPostFragment f = new BlogPostFragment();

		Bundle bundle = new Bundle();
		bundle.putByteArray(POST_ID, postId.getBytes());

		f.setArguments(bundle);
		return f;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		blogController.setBlogListener(this);
	}

	@Override
	public void onStart() {
		super.onStart();
		blogController.loadBlogPost(postId,
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

	@Override
	public void onBlogPostAdded(BlogPostHeader header, boolean local) {
		// doesn't matter here
	}

	@Override
	public void onBlogRemoved() {
		finish();
	}

}
