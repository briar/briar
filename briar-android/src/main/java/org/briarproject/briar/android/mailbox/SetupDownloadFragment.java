package org.briarproject.briar.android.mailbox;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.EXTRA_TEXT;
import static android.widget.Toast.LENGTH_LONG;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SetupDownloadFragment extends Fragment {

	static final String TAG = SetupDownloadFragment.class.getName();

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_mailbox_setup_download,
				container, false);
		Button shareLinkButton = v.findViewById(R.id.shareLinkButton);
		Button scanButton = v.findViewById(R.id.scanButton);
		shareLinkButton.setOnClickListener(this::shareLink);
		scanButton.setOnClickListener(this::scanCode);
		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.mailbox_setup_title);
	}

	private void shareLink(View v) {
		Context ctx = requireContext();
		String fdroid = ctx.getString(R.string.mailbox_share_fdroid);
		String gplay = ctx.getString(R.string.mailbox_share_gplay);
		String download = ctx.getString(R.string.mailbox_share_download);
		String text = ctx.getString(R.string.mailbox_share_text, fdroid, gplay,
				download);

		Intent sendIntent = new Intent();
		sendIntent.setAction(ACTION_SEND);
		sendIntent.putExtra(EXTRA_TEXT, text);
		sendIntent.setType("text/plain");

		Intent shareIntent = Intent.createChooser(sendIntent, null);
		try {
			startActivity(shareIntent);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(ctx, R.string.error_start_activity, LENGTH_LONG)
					.show();
		}
	}

	private void scanCode(View v) {
		Toast.makeText(requireContext(), "TODO", LENGTH_LONG).show();
	}

}
