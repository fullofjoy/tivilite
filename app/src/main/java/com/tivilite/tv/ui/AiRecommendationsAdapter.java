package com.tivilite.tv.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.tivilite.tv.R;
import com.tivilite.tv.ai.AiRecommendationManager;
import java.util.ArrayList;
import java.util.List;

public class AiRecommendationsAdapter extends RecyclerView.Adapter<AiRecommendationsAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onProgramSelected(AiRecommendationManager.HotProgramItem item);
    }

    private final List<AiRecommendationManager.HotProgramItem> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public AiRecommendationsAdapter(List<AiRecommendationManager.HotProgramItem> data, OnItemClickListener listener) {
        if (data != null) this.items.addAll(data);
        this.listener = listener;
    }

    public void updateData(List<AiRecommendationManager.HotProgramItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_hot, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AiRecommendationManager.HotProgramItem item = items.get(position);
        holder.tag.setText(item.categoryTag);
        try {
            holder.tag.setBackgroundColor(Color.parseColor(item.badgeColor));
        } catch (Exception ignored) {}

        holder.channel.setText(item.channelName);
        holder.time.setText(item.timeSlot);
        holder.score.setText(item.hotScore);
        holder.title.setText(item.programTitle);
        holder.synopsis.setText(item.aiSynopsis);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onProgramSelected(item);
        });

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.02f : 1.0f).scaleY(hasFocus ? 1.02f : 1.0f).setDuration(150).start();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tag;
        TextView channel;
        TextView time;
        TextView score;
        TextView title;
        TextView synopsis;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tag = itemView.findViewById(R.id.itemAiTag);
            channel = itemView.findViewById(R.id.itemAiChannel);
            time = itemView.findViewById(R.id.itemAiTime);
            score = itemView.findViewById(R.id.itemAiScore);
            title = itemView.findViewById(R.id.itemAiTitle);
            synopsis = itemView.findViewById(R.id.itemAiSynopsis);
        }
    }
}
