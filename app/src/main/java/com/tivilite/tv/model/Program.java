package com.tivilite.tv.model;

public class Program {
    private String title;
    private long startTime;
    private long endTime;
    private boolean isCatchup;

    public Program(String title, long startTime, long endTime, boolean isCatchup) {
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isCatchup = isCatchup;
    }

    public String getTitle() { return title; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public boolean isCatchup() { return isCatchup; }
}
