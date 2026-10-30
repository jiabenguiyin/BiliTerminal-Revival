package com.RobinNotBad.BiliClient.model;

public class FavoriteFolder {
    public long id;
    public String name;
    public String cover;
    public int videoCount;
    public int maxCount;
    public long ownerMid;
    public String intro = "";
    public int attr = -1;

    public boolean isDefault() { return attr >= 0 && (attr & 2) == 0; }
    public boolean isPrivate() { return attr >= 0 && (attr & 1) != 0; }

    public FavoriteFolder() {
    }
}
