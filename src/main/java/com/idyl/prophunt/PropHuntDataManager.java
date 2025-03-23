package com.idyl.prophunt;

import com.google.gson.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.io.IOException;
import java.util.*;


@Slf4j
@Singleton
public class PropHuntDataManager {
    @Getter
    public String DEFAULT_URL = "http://3.133.56.193";
    private String app1Url = DEFAULT_URL + ":8080";
    private String app2Url = DEFAULT_URL + ":5000";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Inject
    private PropHuntPlugin plugin;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    public void setBaseUrl(String baseUrl) {
        this.DEFAULT_URL = baseUrl;
        log.debug("Setting base url: " + DEFAULT_URL);
    }

    protected void updatePropHuntApi(PropHuntPlayerData data)
    {
        String username = urlifyString(data.username);
        String url = app1Url.concat("/prop-hunters/"+username);

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
                    .url(app1Url.concat("/prop-hunters/".concat(playersString)))
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
            if (!jsonElement.isJsonObject()) {
                continue;
            }

            JsonObject jObj = jsonElement.getAsJsonObject();

            if (jObj.get("username") == null || jObj.get("username").isJsonNull()) {
                continue;
            }

            String username = jObj.get("username").getAsString();

            Boolean hiding = jObj.has("hiding") ? jObj.get("hiding").getAsBoolean() : null;
            Integer modelID = jObj.has("modelID") ? (jObj.get("modelID").isJsonNull() ? null : jObj.get("modelID").getAsInt()) : null;
            Integer orientation = jObj.has("orientation") ? (jObj.get("orientation").isJsonNull() ? null : jObj.get("orientation").getAsInt()) : null;

            if (hiding == null || modelID == null || orientation == null) {
                continue;
            }

            PropHuntPlayerData d = new PropHuntPlayerData(username, hiding, modelID, orientation);
            l.put(username, d);
        }

        return l;
    }

    public void createLobby(String user, String data) {
        String username = urlifyString(user);
        String url = app2Url.concat("/lobbies/" + username);

        List<String> playerList;
        if (data != null && !data.trim().isEmpty()) {
            playerList = Arrays.asList(data.trim().split(","));
        } else {
            playerList = new ArrayList<>();
        }

        try {
            Request r = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(playerList)))
                    .build();

            okHttpClient.newCall(r).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.debug("Error sending post data", e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (response.isSuccessful()) {
                        log.debug("Successfully sent prop hunt data");
                        response.close();
                    } else {
                        log.debug("Post request unsuccessful");
                        response.close();
                    }
                }
            });
        } catch (IllegalArgumentException e) {
            log.error("Bad URL given: " + e.getLocalizedMessage());
        }
    }

    public void fetchPlayers(String lobby_id){
        if (lobby_id == null || lobby_id.isEmpty()) {
            plugin.updatePlayerList(null);
            lobby_id = "";
        }
        try {
            Request r = new Request.Builder()
                    .url(app2Url.concat("/lobbies/".concat(urlifyString(lobby_id))))
                    .get()
                    .build();

            okHttpClient.newCall(r).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.info("Error getting lobby by lobby ID.", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            JsonArray j = gson.fromJson(response.body().string(), JsonArray.class);
                            String[] playerList = new String[j.size()];
                            for (int i = 0; i < j.size(); i++){
                                JsonElement element = j.get(i);
                                if (element != null) {
                                    playerList[i] = element.getAsString();
                                }
                            }
                            plugin.updatePlayerList(playerList);
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

    private String urlifyString(String str) {
        return str.trim().replaceAll("\\s", "%20");
    }
}
