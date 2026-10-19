package com.tivilite.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.tivilite.tv.R;
import com.tivilite.tv.model.Program;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EpgAdapter extends RecyclerView.Adapter<EpgAdapter.ViewHolder> {

    public interface OnProgramClickListener {
        void onProgramClick(Program program);
    }

    private List<Program> programs = new ArrayList<>();
    private final OnProgramClickListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public EpgAdapter(OnProgramClickListener listener) {
        this.listener = listener;
    }

    public void setPrograms(List<Program> programs) {
        this.programs = programs != null ? programs : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_epg, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Program program = programs.get(position);
        holder.epgTime.setText(timeFormat.format(new Date(program.getStartTime())));
        holder.epgProgramTitle.setText(program.getTitle());

        if (program.isCatchup()) {
            holder.epgBadge.setText("可回看");
            holder.epgBadge.setVisibility(View.VISIBLE);
        } else {
            holder.epgBadge.setText("直播中");
            holder.epgBadge.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onProgramClick(program);
        });
    }

    @Override
    public int getItemCount() {
        return programs.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView epgTime;
        TextView epgProgramTitle;
        TextView epgBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            epgTime = itemView.findViewById(R.id.epgTime);
            epgProgramTitle = itemView.findViewById(R.id.epgProgramTitle);
            epgBadge = itemView.findViewById(R.id.epgBadge);
        }
    }
}
