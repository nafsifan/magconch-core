package org.magconch.bot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.magconch.bot.BotConfig;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MojangAPI {

    private static final Map<String, String[]> CACHE = new ConcurrentHashMap<>();

    public static String[] getSkin(String name) {
        if (org.magconch.bot.BotConfig.useSkinCache && CACHE.containsKey(name)) {
            return CACHE.get(name);
        }

        String[] values = pullFromAPI(name);
        if (values != null) {
            CACHE.put(name, values);
        }
        return values;
    }

    public static String[] pullFromAPI(String name) {
        try {
            HttpURLConnection conn1 = (HttpURLConnection) URI.create("https://api.mojang.com/users/profiles/minecraft/" + name).toURL().openConnection();
            conn1.setConnectTimeout(4000);
            conn1.setReadTimeout(4000);
            if (conn1.getResponseCode() != 200) {
                return null;
            }
            String uuid = JsonParser.parseReader(new InputStreamReader(conn1.getInputStream(), StandardCharsets.UTF_8))
                .getAsJsonObject().get("id").getAsString();

            HttpURLConnection conn2 = (HttpURLConnection) URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false").toURL().openConnection();
            conn2.setConnectTimeout(4000);
            conn2.setReadTimeout(4000);
            if (conn2.getResponseCode() != 200) {
                return null;
            }
            JsonObject property = JsonParser.parseReader(new InputStreamReader(conn2.getInputStream(), StandardCharsets.UTF_8))
                .getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();
            return new String[]{property.get("value").getAsString(), property.get("signature").getAsString()};
        } catch (Exception e) {
            return null;
        }
    }
}
