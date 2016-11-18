package org.briarproject.android.privategroup.conversation;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.UiThread;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.privategroup.reveal.RevealContactsActivity;
import org.briarproject.android.threaded.BaseThreadItemViewHolder;
import org.briarproject.android.threaded.ThreadItemAdapter;
import org.briarproject.android.threaded.ThreadItemAdapter.ThreadItemListener;
import org.briarproject.api.nullsafety.NotNullByDefault;

import static org.briarproject.android.BriarActivity.GROUP_ID;
import static org.briarproject.android.privategroup.VisibilityHelper.getVisibilityIcon;
import static org.briarproject.android.privategroup.VisibilityHelper.getVisibilityString;
import static org.briarproject.api.identity.Author.Status.OURSELVES;
import static org.briarproject.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.api.privategroup.Visibility.INVISIBLE;

@UiThread
@NotNullByDefault
class JoinMessageItemViewHolder
		extends BaseThreadItemViewHolder<GroupMessageItem> {

	private final boolean isCreator;
	private final ImageView icon;
	private final TextView info;
	private final Button options;

	public JoinMessageItemViewHolder(View v, boolean isCreator) {
		super(v);
		this.isCreator = isCreator;
		icon = (ImageView) v.findViewById(R.id.icon);
		info = (TextView) v.findViewById(R.id.info);
		options = (Button) v.findViewById(R.id.optionsButton);
	}

	@Override
	public void bind(ThreadItemAdapter<GroupMessageItem> adapter,
			ThreadItemListener<GroupMessageItem> listener,
			GroupMessageItem item, int pos) {
		super.bind(adapter, listener, item, pos);

		if (isCreator) bindForCreator((JoinMessageItem) item);
		else bind((JoinMessageItem) item);
	}

	private void bindForCreator(final JoinMessageItem item) {
		if (item.isInitial()) {
			textView.setText(R.string.groups_member_created_you);
		} else {
			textView.setText(
					getContext().getString(R.string.groups_member_joined,
							item.getAuthor().getName()));
		}
		icon.setVisibility(View.GONE);
		info.setVisibility(View.GONE);
		options.setVisibility(View.GONE);
	}

	private void bind(final JoinMessageItem item) {
		final Context ctx = getContext();

		if (item.isInitial()) {
			textView.setText(ctx.getString(R.string.groups_member_created,
					item.getAuthor().getName()));
		} else {
			if (item.getStatus() == OURSELVES) {
				textView.setText(R.string.groups_member_joined_you);
			} else {
				textView.setText(ctx.getString(R.string.groups_member_joined,
						item.getAuthor().getName()));
			}
		}

		if (item.getStatus() == OURSELVES || item.getStatus() == UNKNOWN) {
			icon.setVisibility(View.GONE);
			info.setVisibility(View.GONE);
			options.setVisibility(View.GONE);
		} else {
			icon.setVisibility(View.VISIBLE);
			icon.setImageResource(getVisibilityIcon(item.getVisibility()));
			info.setVisibility(View.VISIBLE);
			info.setText(getVisibilityString(getContext(), item.getVisibility(),
					item.getAuthor().getName()));
			if (item.getVisibility() == INVISIBLE) {
				options.setVisibility(View.VISIBLE);
				options.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent i =
								new Intent(ctx, RevealContactsActivity.class);
						i.putExtra(GROUP_ID, item.getGroupId().getBytes());
						ctx.startActivity(i);
					}
				});
			} else {
				options.setVisibility(View.GONE);
			}
		}
	}

}
