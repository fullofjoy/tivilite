package com.tivilite.tv.model;

public class Channel {
    private String id;
    private int number;
    private String name;
    private String logoUrl;
    private String group;
    private String url;
    private String catchupType;
    private String catchupSource;

    public Channel(int number, String name, String group, String url) {
        this.number = number;
        this.name = name;
        this.group = group != null && !group.isEmpty() ? group : "默认分组";
        this.url = url;
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

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getCatchupType() { return catchupType; }
    public void setCatchupType(String catchupType) { this.catchupType = catchupType; }

    public String getCatchupSource() { return catchupSource; }
    public void setCatchupSource(String catchupSource) { this.catchupSource = catchupSource; }

    public boolean hasCatchup() {
        return catchupSource != null && !catchupSource.isEmpty();
    }
}
