package org.briarproject.briar.android.blog;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.transition.Fade;
import android.transition.Transition;
import android.view.MenuItem;

import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;

import static org.briarproject.briar.android.blog.BasePostFragment.POST_ID;

public class ReblogActivity extends BriarActivity implements
		BaseFragmentListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (Build.VERSION.SDK_INT >= 21) {
			setTransition();
		}

		Intent intent = getIntent();
		byte[] groupId = intent.getByteArrayExtra(GROUP_ID);
		if (groupId == null)
			throw new IllegalArgumentException("No group ID in intent");
		byte[] postId = intent.getByteArrayExtra(POST_ID);
		if (postId == null)
			throw new IllegalArgumentException("No post message ID in intent");

		setContentView(R.layout.activity_fragment_container);

		if (savedInstanceState == null) {
			ReblogFragment f = ReblogFragment
					.newInstance(new GroupId(groupId), new MessageId(postId));
			getSupportFragmentManager()
					.beginTransaction()
					.add(R.id.fragmentContainer, f)
					.commit();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onFragmentCreated(String tag) {

	}

	@TargetApi(21)
	private void setTransition() {
		Transition fade = new Fade();
		fade.excludeTarget(android.R.id.statusBarBackground, true);
		fade.excludeTarget(R.id.action_bar_container, true);
		fade.excludeTarget(android.R.id.navigationBarBackground, true);
		getWindow().setExitTransition(fade);
		getWindow().setEnterTransition(fade);
	}
}
