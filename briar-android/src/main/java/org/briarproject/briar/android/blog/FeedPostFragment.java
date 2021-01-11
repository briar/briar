package org.briarproject.briar.android.blog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.activity.ActivityComponent;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.lifecycle.ViewModelProvider;

import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class FeedPostFragment extends BasePostFragment {

	private static final String TAG = FeedPostFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private FeedViewModel viewModel;

	static FeedPostFragment newInstance(GroupId blogId, MessageId postId) {
		FeedPostFragment f = new FeedPostFragment();
		Bundle bundle = new Bundle();
		bundle.putByteArray(GROUP_ID, blogId.getBytes());
		bundle.putByteArray(POST_ID, postId.getBytes());
		f.setArguments(bundle);
		return f;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(FeedViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		Bundle args = requireArguments();
		GroupId groupId =
				new GroupId(requireNonNull(args.getByteArray(GROUP_ID)));
		MessageId postId =
				new MessageId(requireNonNull(args.getByteArray(POST_ID)));
		viewModel.loadBlogPost(groupId, postId).observe(getViewLifecycleOwner(),
				result -> result.onError(this::handleException)
						.onSuccess(this::onBlogPostLoaded)
		);
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

}
