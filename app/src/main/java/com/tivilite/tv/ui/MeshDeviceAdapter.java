package com.tivilite.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tivilite.tv.R;
import com.tivilite.tv.mesh.CloudMeshManager;

import java.util.ArrayList;
import java.util.List;

public class MeshDeviceAdapter extends RecyclerView.Adapter<MeshDeviceAdapter.ViewHolder> {

    public interface OnHandoffClickListener {
        void onHandoff(CloudMeshManager.PeerDevice peer);
    }

    private final List<CloudMeshManager.PeerDevice> peerList = new ArrayList<>();
    private final OnHandoffClickListener listener;

    public MeshDeviceAdapter(OnHandoffClickListener listener) {
        this.listener = listener;
    }

    public void setPeers(List<CloudMeshManager.PeerDevice> peers) {
        this.peerList.clear();
        if (peers != null) {
            this.peerList.addAll(peers);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mesh_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CloudMeshManager.PeerDevice peer = peerList.get(position);
        holder.tvDeviceName.setText("📺 " + peer.name);
        holder.tvDeviceIp.setText("局域网 IP: " + peer.ip + ":" + peer.port);
        if (peer.currentChannel != null && !peer.currentChannel.isEmpty()) {
            holder.tvPlayingChannel.setText("正在播放: " + peer.currentChannel);
        } else {
            holder.tvPlayingChannel.setText("当前待机就绪");
        }

        holder.btnHandoff.setOnClickListener(v -> {
            if (listener != null) listener.onHandoff(peer);
        });
    }

    @Override
    public int getItemCount() {
        return peerList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeviceName;
        TextView tvDeviceIp;
        TextView tvPlayingChannel;
        Button btnHandoff;

        ViewHolder(View itemView) {
            super(itemView);
            tvDeviceName = itemView.findViewById(R.id.tvDeviceName);
            tvDeviceIp = itemView.findViewById(R.id.tvDeviceIp);
            tvPlayingChannel = itemView.findViewById(R.id.tvPlayingChannel);
            btnHandoff = itemView.findViewById(R.id.btnHandoff);
        }
    }
}
