package org.briarproject.briar.android.privategroup.list;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.privategroup.conversation.GroupActivity;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.android.view.TextAvatarView;

import static android.support.v4.content.ContextCompat.getColor;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_NAME;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class GroupViewHolder extends RecyclerView.ViewHolder {

	private final static float ALPHA = 0.42f;

	private final ViewGroup layout;
	private final TextAvatarView avatar;
	private final TextView name;
	private final TextView creator;
	private final TextView postCount;
	private final TextView date;
	private final TextView status;
	private final Button remove;

	GroupViewHolder(View v) {
		super(v);

		layout = (ViewGroup) v;
		avatar = (TextAvatarView) v.findViewById(R.id.avatarView);
		name = (TextView) v.findViewById(R.id.nameView);
		creator = (TextView) v.findViewById(R.id.creatorView);
		postCount = (TextView) v.findViewById(R.id.messageCountView);
		date = (TextView) v.findViewById(R.id.dateView);
		status = (TextView) v.findViewById(R.id.statusView);
		remove = (Button) v.findViewById(R.id.removeButton);
	}

	void bindView(final Context ctx, final GroupItem group,
			final OnGroupRemoveClickListener listener) {
		// Avatar
		avatar.setText(group.getName().substring(0, 1));
		avatar.setBackgroundBytes(group.getId().getBytes());
		avatar.setUnreadCount(group.getUnreadCount());

		// Group Name
		name.setText(group.getName());

		// Creator
		creator.setText(ctx.getString(R.string.groups_created_by,
				group.getCreator().getName()));

		if (!group.isDissolved()) {
			// full visibility
			avatar.setAlpha(1);
			name.setAlpha(1);
			creator.setAlpha(1);

			// Date and Status
			if (group.isEmpty()) {
				postCount.setVisibility(GONE);
				date.setVisibility(GONE);
				avatar.setProblem(true);
				status
						.setText(ctx.getString(R.string.groups_group_is_empty));
				status.setVisibility(VISIBLE);
			} else {
				// Message Count
				int messageCount = group.getMessageCount();
				postCount.setVisibility(VISIBLE);
				postCount.setText(ctx.getResources()
						.getQuantityString(R.plurals.messages, messageCount,
								messageCount));
				postCount.setTextColor(
						getColor(ctx, R.color.briar_text_secondary));

				long lastUpdate = group.getTimestamp();
				date.setText(UiUtils.formatDate(ctx, lastUpdate));
				date.setVisibility(VISIBLE);
				avatar.setProblem(false);
				status.setVisibility(GONE);
			}
			remove.setVisibility(GONE);
		} else {
			// grey out
			avatar.setAlpha(ALPHA);
			name.setAlpha(ALPHA);
			creator.setAlpha(ALPHA);

			postCount.setVisibility(GONE);
			date.setVisibility(GONE);
			status
					.setText(ctx.getString(R.string.groups_group_is_dissolved));
			status.setVisibility(VISIBLE);
			remove.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					listener.onGroupRemoveClick(group);
				}
			});
			remove.setVisibility(VISIBLE);
		}

		// Open Group on Click
		layout.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(ctx, GroupActivity.class);
				GroupId id = group.getId();
				i.putExtra(GROUP_ID, id.getBytes());
				i.putExtra(GROUP_NAME, group.getName());
				ctx.startActivity(i);
			}
		});
	}

	interface OnGroupRemoveClickListener {
		void onGroupRemoveClick(GroupItem item);
	}

}
