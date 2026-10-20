package com.tivilite.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.tivilite.tv.R;
import com.tivilite.tv.epg.ReminderManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RemindersAdapter extends RecyclerView.Adapter<RemindersAdapter.ViewHolder> {

    public interface OnReminderActionListener {
        void onReminderSelect(ReminderManager.ReminderItem item);
        void onReminderCancel(ReminderManager.ReminderItem item);
    }

    private List<ReminderManager.ReminderItem> items = new ArrayList<>();
    private final OnReminderActionListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public RemindersAdapter(OnReminderActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ReminderManager.ReminderItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reminder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReminderManager.ReminderItem item = items.get(position);
        holder.reminderTime.setText(timeFormat.format(new Date(item.startTime)));
        holder.reminderChannel.setText(item.channelName);
        holder.reminderTitle.setText(item.programTitle);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onReminderSelect(item);
        });

        holder.btnCancelReminder.setOnClickListener(v -> {
            if (listener != null) listener.onReminderCancel(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView reminderTime;
        TextView reminderChannel;
        TextView reminderTitle;
        TextView btnCancelReminder;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            reminderTime = itemView.findViewById(R.id.reminderTime);
            reminderChannel = itemView.findViewById(R.id.reminderChannel);
            reminderTitle = itemView.findViewById(R.id.reminderTitle);
            btnCancelReminder = itemView.findViewById(R.id.btnCancelReminder);
        }
    }
}
