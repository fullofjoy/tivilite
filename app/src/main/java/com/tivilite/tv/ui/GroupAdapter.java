package com.tivilite.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.tivilite.tv.R;
import java.util.List;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.ViewHolder> {

    public interface OnGroupClickListener {
        void onGroupClick(String groupName);
    }

    private final List<String> groups;
    private final OnGroupClickListener listener;
    private int selectedPosition = 0;

    public GroupAdapter(List<String> groups, OnGroupClickListener listener) {
        this.groups = groups;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String group = groups.get(position);
        holder.groupName.setText(group);
        holder.itemView.setSelected(position == selectedPosition);

        holder.itemView.setOnClickListener(v -> {
            int prev = selectedPosition;
            selectedPosition = holder.getBindingAdapterPosition();
            notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
            if (listener != null) listener.onGroupClick(group);
        });

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                int prev = selectedPosition;
                selectedPosition = holder.getBindingAdapterPosition();
                notifyItemChanged(prev);
                notifyItemChanged(selectedPosition);
                if (listener != null) listener.onGroupClick(group);
            }
        });
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView groupName;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            groupName = itemView.findViewById(R.id.groupName);
        }
    }
}
