package com.tivilite.tv.favorite;

import android.content.Context;
import android.content.SharedPreferences;
import com.tivilite.tv.model.Channel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavoriteManager {

    private static final String PREF_NAME = "tivilite_prefs";
    private static final String KEY_FAVORITES = "favorite_channel_names";

    public static boolean isFavorite(Context context, String channelName) {
        if (channelName == null) return false;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> favs = sp.getStringSet(KEY_FAVORITES, Collections.emptySet());
        return favs != null && favs.contains(channelName.trim());
    }

    public static boolean toggleFavorite(Context context, String channelName) {
        if (channelName == null || channelName.trim().isEmpty()) return false;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> oldFavs = sp.getStringSet(KEY_FAVORITES, Collections.emptySet());
        Set<String> newFavs = new HashSet<>(oldFavs != null ? oldFavs : Collections.emptySet());
        String name = channelName.trim();
        boolean isNowFav;
        if (newFavs.contains(name)) {
            newFavs.remove(name);
            isNowFav = false;
        } else {
            newFavs.add(name);
            isNowFav = true;
        }
        sp.edit().putStringSet(KEY_FAVORITES, newFavs).apply();
        return isNowFav;
    }

    public static Set<String> getFavoriteNames(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> favs = sp.getStringSet(KEY_FAVORITES, Collections.emptySet());
        return favs != null ? favs : Collections.emptySet();
    }

    public static void setFavoriteNames(Context context, Set<String> names) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putStringSet(KEY_FAVORITES, names).apply();
    }

    public static List<Channel> getFavorites(Context context, List<Channel> allChannels) {
        List<Channel> list = new ArrayList<>();
        if (allChannels == null || allChannels.isEmpty()) return list;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> favs = sp.getStringSet(KEY_FAVORITES, Collections.emptySet());
        if (favs == null || favs.isEmpty()) return list;
        for (Channel c : allChannels) {
            if (favs.contains(c.getName())) {
                list.add(c);
            }
        }
        return list;
    }
}
