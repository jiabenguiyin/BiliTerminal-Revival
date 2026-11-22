package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable keys keep a saved order valid when actions are added or removed. */
public final class MySpaceOrder {
    public static final String PREFERENCE = "myspace_button_order";
    public static final class Item {
        public final String key, label;
        public final int viewId;
        private Item(String key, String label, int viewId) {
            this.key = key; this.label = label; this.viewId = viewId;
        }
    }
    private static final List<Item> DEFAULT = Collections.unmodifiableList(Arrays.asList(
            new Item("edit_profile", "个人资料", R.id.edit_profile),
            new Item("follow", "关注", R.id.follow),
            new Item("watchlater", "稍后再看", R.id.watchlater),
            new Item("favorite", "收藏", R.id.favorite),
            new Item("bangumi", "追番列表", R.id.bangumi),
            new Item("history", "历史记录", R.id.history),
            new Item("creative", "创作中心", R.id.creative),
            new Item("vip", "大会员", R.id.vip),
            new Item("account_switch", "切换账号", R.id.account_switch),
            new Item("experience_record", "经验变化记录", R.id.experience_record),
            new Item("login_record", "登录记录", R.id.login_record),
            new Item("logout", "退出登录", R.id.logout)));

    private MySpaceOrder() {}

    public static List<Item> parse(String saved) {
        Map<String, Item> remaining = new LinkedHashMap<>();
        for (Item item : DEFAULT) remaining.put(item.key, item);
        List<Item> result = new ArrayList<>();
        if (saved != null) for (String key : saved.split(";")) {
            Item item = remaining.remove(key.trim());
            if (item != null) result.add(item);
        }
        result.addAll(remaining.values());
        return result;
    }

    public static String encode(List<Item> items) {
        StringBuilder result = new StringBuilder();
        for (Item item : items) {
            if (result.length() > 0) result.append(';');
            result.append(item.key);
        }
        return result.toString();
    }
}
