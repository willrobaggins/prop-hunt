package com.idyl.prophunt;

import com.google.gson.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;

@Slf4j
@Singleton
public class PropHuntDataManager {
    @Getter
    public final String DEFAULT_URL = "http://18.117.185.87:8080";
    private String baseUrl = DEFAULT_URL;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Inject
    private PropHuntPlugin plugin;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        log.debug("Setting base url: " + baseUrl);
    }

    protected void updatePropHuntApi(PropHuntPlayerData data)
    {
        String username = urlifyString(data.username);
        String url = baseUrl.concat("/prop-hunters/"+username);

        try
        {
            Request r = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(JSON, gson.toJson(data)))
                    .build();

            okHttpClient.newCall(r).enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, IOException e)
                {
                    log.debug("Error sending post data", e);
                }

                @Override
                public void onResponse(Call call, Response response)
                {
                    if (response.isSuccessful())
                    {
                        log.debug("Successfully sent prop hunt data");
                        response.close();
                    }
                    else
                    {
                        log.debug("Post request unsuccessful");
                        response.close();
                    }
                }
            });
        }
        catch (IllegalArgumentException e)
        {
            log.error("Bad URL given: " + e.getLocalizedMessage());
        }
    }

    public void getPropHuntersByUsernames(String[] players) {
        String playersString = urlifyString(String.join(",", players));
        try {
            Request r = new Request.Builder()
                    .url(baseUrl.concat("/prop-hunters/".concat(playersString)))
                    .get()
                    .build();

            okHttpClient.newCall(r).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.info("Error getting prop hunt data by username", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            JsonArray j = gson.fromJson(response.body().string(), JsonArray.class);
                            HashMap<String, PropHuntPlayerData> playerData = parsePropHuntData(j);
                            plugin.updatePlayerData(playerData);
                        } catch (IOException | NullPointerException | JsonSyntaxException e) {
                            log.error(e.getMessage());
                        }
                    }

                    response.close();
                }
            });
        } catch (IllegalArgumentException e) {
            log.error("Bad URL given: " + e.getLocalizedMessage());
        }
    }

    private HashMap<String, PropHuntPlayerData> parsePropHuntData(JsonArray j) {
        HashMap<String, PropHuntPlayerData> l = new HashMap<>();

        for (JsonElement jsonElement : j)
        {
            // Check if the jsonElement is actually an object
            if (!jsonElement.isJsonObject()) {
                continue; // Skip if the element is not a JsonObject
            }

            JsonObject jObj = jsonElement.getAsJsonObject();

            // Check if required fields are null or missing before attempting to parse
            if (jObj.get("username") == null || jObj.get("username").isJsonNull()) {
                continue; // Skip if "username" is null or missing
            }

            String username = jObj.get("username").getAsString();

            // Check if "hiding", "modelID", and "orientation" are available
            Boolean hiding = jObj.has("hiding") ? jObj.get("hiding").getAsBoolean() : null;
            Integer modelID = jObj.has("modelID") ? (jObj.get("modelID").isJsonNull() ? null : jObj.get("modelID").getAsInt()) : null;
            Integer orientation = jObj.has("orientation") ? (jObj.get("orientation").isJsonNull() ? null : jObj.get("orientation").getAsInt()) : null;

            // If any of these values are null, skip this entry
            if (hiding == null || modelID == null || orientation == null) {
                continue; // Skip if any of the necessary fields are missing or null
            }

            // Create and store the player data if all fields are valid
            PropHuntPlayerData d = new PropHuntPlayerData(username, hiding, modelID, orientation);
            l.put(username, d);
        }

        return l;
    }

    private String urlifyString(String str) {
        return str.trim().replaceAll("\\s", "%20");
    }
}
