package com.tivilite.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.tivilite.tv.R;
import com.tivilite.tv.model.Channel;
import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    public interface OnChannelLongClickListener {
        void onChannelLongClick(Channel channel);
    }

    private List<Channel> channels = new ArrayList<>();
    private final OnChannelClickListener listener;
    private OnChannelLongClickListener longClickListener;
    private Channel currentPlayingChannel;

    public ChannelAdapter(OnChannelClickListener listener) {
        this.listener = listener;
    }

    public void setOnChannelLongClickListener(OnChannelLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    public void setChannels(List<Channel> channels) {
        this.channels = channels != null ? channels : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setCurrentPlayingChannel(Channel channel) {
        this.currentPlayingChannel = channel;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel channel = channels.get(position);
        holder.channelNumber.setText(String.format("%02d", channel.getNumber()));
        
        boolean isFav = com.tivilite.tv.favorite.FavoriteManager.isFavorite(holder.itemView.getContext(), channel.getName());
        holder.channelName.setText(isFav ? "⭐ " + channel.getName() : channel.getName());
        holder.channelCurrentProgram.setText(channel.hasCatchup() ? "支持 7 天时移回看" : "高清实时直播");

        if (channel.hasCatchup()) {
            holder.channelCatchupBadge.setVisibility(View.VISIBLE);
        } else {
            holder.channelCatchupBadge.setVisibility(View.GONE);
        }

        if (channel.getLogoUrl() != null && !channel.getLogoUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(channel.getLogoUrl())
                    .placeholder(R.drawable.app_banner)
                    .error(R.drawable.app_banner)
                    .into(holder.channelLogo);
        } else {
            holder.channelLogo.setImageResource(R.drawable.app_banner);
        }

        boolean isPlaying = currentPlayingChannel != null && currentPlayingChannel.getUrl().equals(channel.getUrl());
        holder.itemView.setSelected(isPlaying);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onChannelClick(channel);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onChannelLongClick(channel);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView channelNumber;
        ImageView channelLogo;
        TextView channelName;
        TextView channelCurrentProgram;
        TextView channelCatchupBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            channelNumber = itemView.findViewById(R.id.channelNumber);
            channelLogo = itemView.findViewById(R.id.channelLogo);
            channelName = itemView.findViewById(R.id.channelName);
            channelCurrentProgram = itemView.findViewById(R.id.channelCurrentProgram);
            channelCatchupBadge = itemView.findViewById(R.id.channelCatchupBadge);
        }
    }
}
