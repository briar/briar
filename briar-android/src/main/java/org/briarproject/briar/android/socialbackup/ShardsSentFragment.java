package org.briarproject.briar.android.socialbackup;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

public class ShardsSentFragment extends BaseFragment {

	public static final String TAG = ShardsSentFragment.class.getName();

	interface ShardsSentDismissedListener {

		@UiThread
		void shardsSentDismissed();

	}

	protected ShardsSentDismissedListener listener;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requireActivity().setTitle(R.string.title_distributed_backup);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_shards_sent,
				container, false);

		Button button = view.findViewById(R.id.button);
		button.setOnClickListener(e -> {
			listener.shardsSentDismissed();
		});

		return view;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (ShardsSentDismissedListener) context;
	}


	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

    public void onBackPressed() {
	    listener.shardsSentDismissed();
    }

}
