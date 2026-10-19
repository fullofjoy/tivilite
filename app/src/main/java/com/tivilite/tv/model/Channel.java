package com.tivilite.tv.model;

import java.util.ArrayList;
import java.util.List;

public class Channel {
    private String id;
    private int number;
    private String name;
    private String logoUrl;
    private String group;
    private String catchupType;
    private String catchupSource;

    private final List<String> urls = new ArrayList<>();
    private int currentUrlIndex = 0;

    public Channel(int number, String name, String group, String url) {
        this.number = number;
        this.name = name;
        this.group = group != null && !group.isEmpty() ? group : "默认分组";
        if (url != null && !url.trim().isEmpty()) {
            this.urls.add(url.trim());
        }
    }

    public String getId() { return id != null ? id : name; }
    public void setId(String id) { this.id = id; }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getUrl() {
        if (urls.isEmpty()) return "";
        if (currentUrlIndex < 0 || currentUrlIndex >= urls.size()) currentUrlIndex = 0;
        return urls.get(currentUrlIndex);
    }

    public void setUrl(String url) {
        this.urls.clear();
        if (url != null && !url.trim().isEmpty()) {
            this.urls.add(url.trim());
        }
        this.currentUrlIndex = 0;
    }

    public void addUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            String trimmed = url.trim();
            if (!urls.contains(trimmed)) {
                urls.add(trimmed);
            }
        }
    }

    public List<String> getUrls() {
        return urls;
    }

    public int getUrlCount() {
        return Math.max(1, urls.size());
    }

    public int getCurrentUrlIndex() {
        return currentUrlIndex;
    }

    public boolean hasAlternativeUrls() {
        return urls.size() > 1;
    }

    public boolean switchToNextUrl() {
        if (urls.size() <= 1) return false;
        currentUrlIndex = (currentUrlIndex + 1) % urls.size();
        return true;
    }

    public boolean switchToPreviousUrl() {
        if (urls.size() <= 1) return false;
        currentUrlIndex = (currentUrlIndex - 1 + urls.size()) % urls.size();
        return true;
    }

    public String getSourceInfo() {
        return "线路 " + (currentUrlIndex + 1) + "/" + Math.max(1, urls.size());
    }

    public boolean isCurrentUrlIpv6() {
        String u = getUrl();
        return u != null && (u.contains("://[") || u.contains("::"));
    }

    public String getCatchupType() { return catchupType; }
    public void setCatchupType(String catchupType) { this.catchupType = catchupType; }

    public String getCatchupSource() { return catchupSource; }
    public void setCatchupSource(String catchupSource) { this.catchupSource = catchupSource; }

    public boolean hasCatchup() {
        return catchupSource != null && !catchupSource.isEmpty();
    }
}
