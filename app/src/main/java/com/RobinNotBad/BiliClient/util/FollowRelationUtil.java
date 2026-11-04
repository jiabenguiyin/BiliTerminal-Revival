package com.RobinNotBad.BiliClient.util;

public final class FollowRelationUtil {
    public static final int NONE = 0;
    public static final int FOLLOW_BACK = 1;
    public static final int FOLLOWING = 2;
    public static final int MUTUAL = 3;

    private FollowRelationUtil() {
    }

    public static boolean isBlockedAttribute(int attribute) {
        return attribute>=0&&(attribute&128)!=0;
    }

    public static boolean isFollowingAttribute(int attribute) {
        return attribute == 1 || attribute == 2 || attribute == 6;
    }

    public static int getUnfollowAction(int attribute) {
        return attribute == 1 ? 4 : 2;
    }

    public static int getState(boolean following, boolean followsMe) {
        if (following && followsMe) return MUTUAL;
        if (following) return FOLLOWING;
        if (followsMe) return FOLLOW_BACK;
        return NONE;
    }

    public static String getButtonText(boolean following, boolean followsMe) {
        switch (getState(following, followsMe)) {
            case MUTUAL:
                return "互粉";
            case FOLLOWING:
                return "已关注";
            case FOLLOW_BACK:
                return "回关";
            default:
                return "关注";
        }
    }

    public static String getButtonText(int relationAttribute, int beRelationAttribute) {
        if(isBlockedAttribute(relationAttribute))return "已拉黑";
        return getButtonText(
                isFollowingAttribute(relationAttribute),
                isFollowingAttribute(beRelationAttribute));
    }
}
