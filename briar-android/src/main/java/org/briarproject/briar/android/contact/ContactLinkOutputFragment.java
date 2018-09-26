package org.briarproject.briar.android.contact;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.annotation.Nullable;

import static android.content.Context.CLIPBOARD_SERVICE;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.EXTRA_TEXT;
import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.bramble.util.StringUtils.getRandomBase32String;

@NotNullByDefault
public class ContactLinkOutputFragment extends BaseFragment {

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		getActivity().setTitle(R.string.send_link_title);

		View v = inflater.inflate(R.layout.fragment_contact_link_output,
				container, false);

		String link = "briar://" + getRandomBase32String(64);

		TextView linkView = v.findViewById(R.id.linkView);
		linkView.setText(link);

		ClipboardManager clipboard = (ClipboardManager)
				getContext().getSystemService(CLIPBOARD_SERVICE);
		if (clipboard == null) throw new AssertionError();
		ClipData clip = ClipData.newPlainText(
				getString(R.string.link_clip_label), link);

		Button copyButton = v.findViewById(R.id.copyButton);
		copyButton.setOnClickListener(view -> {
			clipboard.setPrimaryClip(clip);
			Toast.makeText(getContext(), R.string.link_copied_toast,
					LENGTH_SHORT).show();
		});

		Button shareButton = v.findViewById(R.id.shareButton);
		shareButton.setOnClickListener(view -> {
			Intent i = new Intent(ACTION_SEND);
			i.putExtra(EXTRA_TEXT, link);
			i.setType("text/plain");
			startActivity(i);
		});
		return v;
	}

	public static final String TAG = ContactLinkOutputFragment.class.getName();

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}


}
