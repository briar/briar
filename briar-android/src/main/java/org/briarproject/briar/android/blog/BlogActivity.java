package org.briarproject.briar.android.blog;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.sharing.BlogSharingStatusActivity;

import javax.inject.Inject;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class BlogActivity extends BriarActivity
		implements BaseFragmentListener {

	static final int REQUEST_WRITE_POST = 2;
	static final int REQUEST_SHARE = 3;

	@Inject
	BlogController blogController;

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		// GroupId from Intent
		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No group ID in intent");
		final GroupId groupId = new GroupId(b);
		blogController.setGroupId(groupId);

		setContentView(R.layout.activity_fragment_container);

		// Open Sharing Status on ActionBar click
		View actionBar = findViewById(R.id.action_bar);
		if (actionBar != null) {
			actionBar.setOnClickListener(
					new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							Intent i = new Intent(BlogActivity.this,
									BlogSharingStatusActivity.class);
							i.putExtra(GROUP_ID, groupId.getBytes());
							startActivity(i);
						}
					});
		}

		if (state == null) {
			showInitialFragment(BlogFragment.newInstance(groupId));
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

}
