package com.idyl.prophunt;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import net.runelite.api.Perspective;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.LocalPoint;

import joptsimple.util.RegexMatcher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "Prop Hunt"
)
public class PropHuntPlugin extends Plugin
{
	public final String CONFIG_KEY = "prophunt";
	public final Pattern modelEntry = Pattern.compile("[a-zA-Z]+:[ ]?[0-9]+");

	@Inject
	private Client client;

	@Inject
	private PropHuntConfig config;

	@Inject
	private Hooks hooks;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PropHuntDataManager propHuntDataManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PropHuntOverlay propHuntOverlay;

	@Inject
	private ClientToolbar clientToolbar;

	private PropHuntPanel panel;
	private NavigationButton navButton;

	private RuneLiteObject localDisguise;

	private HashMap<String, RuneLiteObject> playerDisguises = new HashMap<>();

	private String[] players;
	private HashMap<String, PropHuntPlayerData> playersData;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	private final long SECONDS_BETWEEN_GET = 5;
	private static final int DOT_PLAYER = 2;
	private static final int DOT_FRIEND = 3;
	private static final int DOT_TEAM = 4;
	private static final int DOT_FRIENDSCHAT = 5;
	private static final int DOT_CLAN = 6;

	private SpritePixels[] originalDotSprites;

	@Getter
	private int rightClickCounter = 0;

	@Override
	protected void startUp() throws Exception
	{
		playersData = new HashMap<>();
		hooks.registerRenderableDrawListener(drawListener);
		clientThread.invokeLater(() -> transmogPlayer(client.getLocalPlayer()));
		setPlayersFromString(config.players());
		getPlayerConfigs();
		storeOriginalDots();
		hideMinimapDots();

		panel = new PropHuntPanel(this);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Prop Hunt")
				.priority(5)
				.icon(icon)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);
		updateDropdown();
		overlayManager.add(propHuntOverlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientThread.invokeLater(this::removeAllTransmogs);
		overlayManager.remove(propHuntOverlay);
		hooks.unregisterRenderableDrawListener(drawListener);
		clientToolbar.removeNavigation(navButton);
		restoreOriginalDots();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (GameState.LOGGED_IN.equals(event.getGameState()))
		{
			if(config.hideMode()) clientThread.invokeLater(() -> transmogPlayer(client.getLocalPlayer()));

			if(client.getLocalPlayer().getName() != null)
				propHuntDataManager.updatePropHuntApi(new PropHuntPlayerData(client.getLocalPlayer().getName(),
					config.hideMode(), getModelID(), config.orientation()));
		}

		if (event.getGameState() == GameState.LOGIN_SCREEN && originalDotSprites == null)
		{
			storeOriginalDots();
			if(config.hideMinimapDots()) hideMinimapDots();
		}
	}

	@Subscribe
	public void onConfigChanged(final ConfigChanged event) {
		clientThread.invokeLater(this::removeAllTransmogs);
		if(config.hideMode()) {
			clientThread.invokeLater(() -> transmogPlayer(client.getLocalPlayer()));
		}

		if(event.getKey().equals("players")) {
			removeTransmogs();
			setPlayersFromString(config.players());
			getPlayerConfigs();
		}

		if(event.getKey().equals("hideMinimapDots")) {
			if (config.hideMinimapDots()) {
				hideMinimapDots();
			} else {
				restoreOriginalDots();
			}
		}

		if(event.getKey().equals("models")) {
			updateDropdown();
		}

		if(event.getKey().equals("apiURL")) {
			if(config.alternate()) {
				propHuntDataManager.setBaseUrl(config.apiURL());
			}
		}

		if(event.getKey().equals("alternate")) {
			if(config.alternate()) {
				propHuntDataManager.setBaseUrl(config.apiURL());
			}
			else {
				propHuntDataManager.setBaseUrl(propHuntDataManager.DEFAULT_URL);
			}
		}

		if(event.getKey().equals("limitRightClicks")) {
			rightClickCounter = 0;
		}

		if(client.getLocalPlayer() != null) {
			propHuntDataManager.updatePropHuntApi(new PropHuntPlayerData(client.getLocalPlayer().getName(),
					config.hideMode(), getModelID(), config.orientation()));
			clientThread.invokeLater(this::transmogOtherPlayers);
		}
	}

	@Subscribe
	public void onClientTick(final ClientTick event) {
		// Update the disguise for the local player if hideMode is enabled
		if (config.hideMode() && localDisguise != null) {
			LocalPoint playerPoint = client.getLocalPlayer().getLocalLocation();
			localDisguise.setLocation(playerPoint, client.getPlane());
		} else {
			removeLocalTransmog();
		}

		// Update disguises for all other players
		transmogOtherPlayers();
		client.getPlayers().forEach(this::updatePlayerDisguiseLocation);
	}

	private void updatePlayerDisguiseLocation(Player player) {
		if (player == null || player == client.getLocalPlayer()) {
			return;  // Skip the local player
		}

		RuneLiteObject disguise = playerDisguises.get(player.getName());
		if (disguise != null) {
			disguise.setLocation(player.getLocalLocation(), player.getWorldLocation().getPlane());  // Update the location
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		if(config.limitRightClicks() && !config.hideMode()) {
			if(rightClickCounter >= config.maxRightClicks()) {
				sendHighlightedChatMessage("You have used all of your right clicks!");
				return;
			}
			rightClickCounter++;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if(playerDisguises == null || playerDisguises.size() == 0) return;

		if(!event.getOption().startsWith("Walk here")) {
			if(config.depriorizteMenuOptions()) event.getMenuEntry().setDeprioritized(true);
			return;
		}
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getEntry() == PropHuntOverlay.RESET_ENTRY) {
			rightClickCounter = 0;
		}
	}

	private void findPlayer(String player) {
		sendNormalChatMessage("You found "+player+"!");
	}

	private void sendNormalChatMessage(String message) {
		ChatMessageBuilder msg = new ChatMessageBuilder()
				.append(ChatColorType.NORMAL)
				.append(message);

		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.ITEM_EXAMINE)
				.runeLiteFormattedMessage(msg.build())
				.build());
	}

	private void sendHighlightedChatMessage(String message) {
		ChatMessageBuilder msg = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(message);

		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(msg.build())
				.build());
	}

	// Hide players who are participating in prop hunt
	@VisibleForTesting
	boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (renderable instanceof Player)
		{
			Player player = (Player) renderable;
			Player local = client.getLocalPlayer();

			if (player == local)
			{
				return !config.hideMode();
			}

			if(players == null) return true;

			ArrayList<String> playerList = new ArrayList<>(Arrays.asList(players));

			if(playerList.contains(player.getName())) {
				PropHuntPlayerData data = playersData.get(player.getName());

				if(data == null) return true;

				if(data.hiding) {
					return !data.hiding;
				}
			}
		}

		return true;
	}

	// Getter for hideMode
	public boolean getHideMode() {
		return config.hideMode();  // Fetch the current 'hideMode' value from config
	}

	// Set the hideMode value via the config
	public void setHideMode(boolean hideMode) {
		// Update the config with the new value
		configManager.setConfiguration(CONFIG_KEY, "hideMode", hideMode);

		// If hideMode is enabled, apply the disguise
		if (hideMode) {
			clientThread.invokeLater(() -> transmogPlayer(client.getLocalPlayer()));
		} else {
			// If hideMode is disabled, remove any disguises (restore normal appearance)
			clientThread.invokeLater(this::removeLocalTransmog);
		}
	}

	private void transmogPlayer(Player player) {
		transmogPlayer(player, getModelID(), config.orientation(), true);
	}

	private void transmogPlayer(Player player, int modelId, int orientation, boolean isLocal) {
		// Use the model ID for the disguise
		int modelID = isLocal ? modelId : playersData.get(player.getName()).modelID;  // Local model ID for the local player, otherwise use the player's modelID from playersData

		// Remove any existing disguise to avoid layering
		if (isLocal) {
			removeLocalTransmog();
		} else {
			removePlayerTransmog(player);  // Ensure this is working properly
		}

		// Create a new disguise (RuneLiteObject)
		RuneLiteObject disguise = client.createRuneLiteObject();

		// Get the player's world location and convert it to local coordinates
		LocalPoint loc = LocalPoint.fromWorld(client, player.getWorldLocation());
		if (loc == null) {
			return; // Return early if the location is invalid
		}

		// Load the model based on modelID
		Model model = client.loadModel(modelID);
		if (model == null) {
			log.warn("Failed to load model with ID: {}", modelID);
			return; // If the model fails to load, exit early
		}

		// Set the model and location for the disguise
		disguise.setModel(model);
		disguise.setLocation(player.getLocalLocation(), player.getWorldLocation().getPlane());
		disguise.setActive(true);  // Activate the disguise
		disguise.setOrientation(orientation);  // Set the disguise orientation

		// Store the disguise: local player or other players
		if (isLocal) {
			localDisguise = disguise;  // Store the disguise for the local player
		} else {
			playerDisguises.put(player.getName(), disguise);  // Store the disguise for other players
		}
	}

	private void transmogOtherPlayers() {
		if(players == null || client.getLocalPlayer() == null) return;

		client.getPlayers().forEach(player -> {
			if(client.getLocalPlayer() == player) return;

			PropHuntPlayerData data = playersData.get(player.getName());

			if(data == null || !data.hiding) return;
			transmogPlayer(player, data.modelID, data.orientation, false);
		});
	}

	private void removeLocalTransmog() {
		if (localDisguise != null)
		{
			localDisguise.setActive(false);
		}
		localDisguise = null;
	}

	private void removeTransmogs()
	{
		playerDisguises.forEach((p, disguise) -> {
			if(disguise == null) return;
			disguise.setActive(false);
		});
	}

	private void removeAllTransmogs() {
		removeTransmogs();
		removeLocalTransmog();
	}

	private void removePlayerTransmog(Player player) {
		RuneLiteObject disguise = playerDisguises.get(player.getName());
		if (disguise != null) {
			disguise.setActive(false);  // Deactivate the disguise
			playerDisguises.remove(player.getName());  // Remove the disguise from the map
		}
	}

	private void updateDisguiseLocation(Player p) {
		RuneLiteObject obj = playerDisguises.get(p.getName());
		if(obj == null) return;

		obj.setLocation(p.getLocalLocation(), p.getWorldLocation().getPlane());
	}

	private void setPlayersFromString(String playersString) {
		String[] p = playersString.split(",");

		for(int i=0;i<p.length;i++) {
			p[i] = p[i].trim();
		}

		players = p;
	}

	@Schedule(
			period = SECONDS_BETWEEN_GET,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void getPlayerConfigs() {
		if(players.length < 1 || config.players().isEmpty()) return;

		propHuntDataManager.getPropHuntersByUsernames(players);
	}

	// Called from PropHuntDataManager
	public void updatePlayerData(HashMap<String, PropHuntPlayerData> data) {
		clientThread.invokeLater(() -> {
			removeTransmogs();
			playersData.clear();
			playerDisguises.clear();
			playersData.putAll(data);
			playersData.values().forEach(player -> playerDisguises.put(player.username, null));
			transmogOtherPlayers();
		});
	}

	private void hideMinimapDots() {
		SpritePixels[] mapDots = client.getMapDots();

		if(mapDots == null) return;

		mapDots[DOT_PLAYER] = client.createSpritePixels(new int[0], 0, 0);
		mapDots[DOT_CLAN] = client.createSpritePixels(new int[0], 0, 0);
		mapDots[DOT_FRIEND] = client.createSpritePixels(new int[0], 0, 0);
		mapDots[DOT_FRIENDSCHAT] = client.createSpritePixels(new int[0], 0, 0);
		mapDots[DOT_TEAM] = client.createSpritePixels(new int[0], 0, 0);
	}

	private void storeOriginalDots()
	{
		SpritePixels[] originalDots = client.getMapDots();

		if (originalDots == null)
		{
			return;
		}

		originalDotSprites = Arrays.copyOf(originalDots, originalDots.length);
	}

	private void restoreOriginalDots()
	{
		SpritePixels[] mapDots = client.getMapDots();

		if (originalDotSprites == null || mapDots == null)
		{
			return;
		}

		System.arraycopy(originalDotSprites, 0, mapDots, 0, mapDots.length);
	}

	private int getModelID() {
		return config.modelID();
	}

	public int getMin() {
		return config.randMinID();
	}

	public int getMax() {
		return config.randMaxID();
	}


	public int getModelId() {
		return config.modelID();
	}

	private static final int RANDOM_MODEL_UPDATE_INTERVAL = 5000; // 5 seconds
	private long lastRandomModelUpdate = 0;

	public void setRandomModelID() {
		long currentTime = System.currentTimeMillis();
		if (currentTime - lastRandomModelUpdate < RANDOM_MODEL_UPDATE_INTERVAL) {
			return; // Skip updating if it's too soon
		}

		configManager.setConfiguration(CONFIG_KEY, "modelID", ThreadLocalRandom.current().nextInt(config.randMinID(), config.randMaxID() + 1));
		lastRandomModelUpdate = currentTime; // Update the timestamp
	}


	private void updateDropdown() {
		String[] modelList = config.models().split(",");
		PropHuntModelId.map.clear();

		for(String model : modelList) {
			model = model.trim();

			if(!modelEntry.matcher(model).matches()) continue;

			String modelName = model.split(":")[0].trim();
			String modelId = model.split(":")[1].trim();

			PropHuntModelId.add(modelName, Integer.parseInt(modelId));
		}

		//panel.updateComboBox();
	}

	public void setModelID(PropHuntModelId modelData) {
		configManager.setConfiguration(CONFIG_KEY, "modelID", modelData.getId());
	}

	public void rotateModel(int dir) {
		if(localDisguise != null) {
			 int orientation = config.orientation() + 500*dir;
			 orientation = (((orientation % 2000) + 2000) % 2000);
			localDisguise.setOrientation(orientation);
			configManager.setConfiguration(CONFIG_KEY, "orientation", orientation);
		}
	}

	public void setMinModelID(int minModelID) {
		configManager.setConfiguration(CONFIG_KEY, "randMinID", minModelID);
	}

	public void setMaxModelID(int maxModelID) {
		configManager.setConfiguration(CONFIG_KEY, "randMaxID", maxModelID);
	}

	@Provides
	PropHuntConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PropHuntConfig.class);
	}

}
