package com.idyl.prophunt;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import javax.inject.Provider;

import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.geometry.SimplePolygon;
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
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
	name = "Prop Hunt"
)
public class PropHuntPlugin extends Plugin {
	public final String CONFIG_KEY = "prophunt";
	public final Pattern modelEntry = Pattern.compile("[a-zA-Z]+:[ ]?[0-9]+");

	@Getter
    @Inject
	private Client client;

	@Getter
	@Inject
	private PropHuntConfig config;

	@Inject
	private Hooks hooks;

	@Inject
	private Provider<MenuManager> menuManager;

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
	private static final String ADD_HIDER = "Add Hider";
	private static final String GUESS = "Guess";
	private static int tickCounter = 0;
	private static final int TICK_INTERVAL = 250;
	@Getter
	private int rightClickCounter = 0;
	private static final int RANDOM_MODEL_UPDATE_INTERVAL = 5000;
	private long lastRandomModelUpdate = 0;

	private String[] seekerList;
	private boolean suppressLobbyPost = false;

	@Override
	protected void startUp() throws Exception {
		playersData = new HashMap<>();
		hooks.registerRenderableDrawListener(drawListener);
		clientThread.invokeLater(() -> transmogPlayer(client.getLocalPlayer()));
		setPlayersFromString(config.players());
		getPlayerConfigs();
		storeOriginalDots();
		if (config.hideMinimapDots()) {
			hideMinimapDots();
		}
		if (client != null) {
			menuManager.get().addPlayerMenuItem(ADD_HIDER);
		}

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
	protected void shutDown() throws Exception {
		clientThread.invokeLater(this::removeAllTransmogs);
		configManager.setConfiguration(CONFIG_KEY, "hideMode", false);
		PropHuntPlayerData playerData = new PropHuntPlayerData(client.getLocalPlayer().getName(), false, config.modelID(), config.orientation());
		propHuntDataManager.updatePropHuntApi(playerData);
		overlayManager.remove(propHuntOverlay);
		hooks.unregisterRenderableDrawListener(drawListener);
		clientToolbar.removeNavigation(navButton);
		restoreOriginalDots();
		if (client != null) {
			menuManager.get().removePlayerMenuItem(ADD_HIDER);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (GameState.LOGGED_IN.equals(event.getGameState())) {
			if (config.hideMode()) clientThread.invokeLater(() -> transmogPlayer(client.getLocalPlayer()));

			if (client.getLocalPlayer().getName() != null)
				propHuntDataManager.updatePropHuntApi(new PropHuntPlayerData(client.getLocalPlayer().getName(),
						config.hideMode(), getModelId(), config.orientation()));
		}

		if (event.getGameState() == GameState.LOGIN_SCREEN && originalDotSprites == null) {
			storeOriginalDots();
			if (config.hideMinimapDots()) hideMinimapDots();
		}
	}

	@Subscribe
	public void onConfigChanged(final ConfigChanged event) {
		clientThread.invokeLater(this::removeAllTransmogs);

		if (event.getKey().equals("lobby")) {
			clientThread.invokeLater(this::removeAllTransmogs);
			String lobby = config.lobby();
			if (lobby != null && !lobby.isEmpty()) {
				propHuntDataManager.fetchPlayers(lobby);
				propHuntDataManager.fetchSeekers(lobby);
			}
			else {
				propHuntDataManager.fetchPlayers(null);
				propHuntDataManager.fetchSeekers(null);
			}
			getPlayerConfigs();
		}

		if (event.getKey().equals("players")) {
			clientThread.invokeLater(this::removeAllTransmogs);
			setPlayersFromString(config.players());
			if(!suppressLobbyPost) {
				if (config.lobby() == null || config.lobby().isEmpty() || Objects.equals(config.lobby(), client.getLocalPlayer().getName())) {
					propHuntDataManager.createLobby(client.getLocalPlayer().getName(), config.players());
				} else if (isSeeker()) {
					propHuntDataManager.createLobby(config.lobby(), config.players());
				}
			}
			getPlayerConfigs();
		}

		if (event.getKey().equals("hideMinimapDots")) {
			if (config.hideMinimapDots()) {
				hideMinimapDots();
			} else {
				restoreOriginalDots();
			}
		}

		if (event.getKey().equals("models")) {
			updateDropdown();
		}

		if (event.getKey().equals("seekers")) {
			propHuntDataManager.postSeekers(client.getLocalPlayer().getName(), config.seekers());
		}

		if (event.getKey().equals("apiURL")) {
			if (config.alternate()) {
				if (!config.apiURL().isEmpty()) {
					propHuntDataManager.setApp1Url((config.apiURL() + ":8080"));
					propHuntDataManager.setApp2Url((config.apiURL() + ":5000"));
				} else {
					propHuntDataManager.setApp1Url((propHuntDataManager.DEFAULT_URL + ":8080"));
					propHuntDataManager.setApp2Url((propHuntDataManager.DEFAULT_URL + ":5000"));
				}
			}
		}

		if (event.getKey().equals("alternate")) {
			if (config.alternate()) {
				if (!config.apiURL().isEmpty()) {
					propHuntDataManager.setApp1Url((config.apiURL() + ":8080"));
					propHuntDataManager.setApp2Url((config.apiURL() + ":5000"));
				} else {
					propHuntDataManager.setApp1Url((propHuntDataManager.DEFAULT_URL + ":8080"));
					propHuntDataManager.setApp2Url((propHuntDataManager.DEFAULT_URL + ":5000"));
					configManager.setConfiguration("prophunt", "apiURL", "");
				}
			} else {
				propHuntDataManager.setApp1Url((propHuntDataManager.DEFAULT_URL + ":8080"));
				propHuntDataManager.setApp2Url((propHuntDataManager.DEFAULT_URL + ":5000"));
				configManager.setConfiguration("prophunt", "apiURL", "");
			}
		}

		if (event.getKey().equals("limitRightClicks")) {
			rightClickCounter = 0;
		}

		if (client.getLocalPlayer() != null) {
			propHuntDataManager.updatePropHuntApi(new PropHuntPlayerData(client.getLocalPlayer().getName(),
					config.hideMode(), getModelId(), config.orientation()));
			clientThread.invokeLater(this::transmogOtherPlayers);
		}

		if (config.hideMode()) {
			clientThread.invokeLater(() -> {
				if (client.getLocalPlayer() != null) {
					transmogPlayer(client.getLocalPlayer());
				}
			});
		}
		panel.updatePanelWithDefaults();
	}

	@Subscribe
	public void onClientTick(final ClientTick event) {
		if (config.hideMode() && localDisguise != null) {
			LocalPoint playerPoint = client.getLocalPlayer().getLocalLocation();
			localDisguise.setLocation(playerPoint, client.getPlane());
		} else {
			if (!config.hideMode()) {
				removeLocalTransmog();
			}
		}
		if (config.lobby() == "" || config.lobby().isEmpty() || Objects.equals(config.lobby(), client.getLocalPlayer().getName())) {
			configManager.setConfiguration(CONFIG_KEY, "lobby", client.getLocalPlayer().getName());
			if (++tickCounter >= TICK_INTERVAL) {
				propHuntDataManager.fetchPlayers(config.lobby());
				tickCounter = 0;
			}
		} else {
			if (++tickCounter >= TICK_INTERVAL) {
				propHuntDataManager.fetchPlayers(config.lobby());
				propHuntDataManager.fetchSeekers(config.lobby());
				tickCounter = 0;
			}
		}
		transmogOtherPlayers();
		client.getPlayers().forEach(this::updatePlayerDisguiseLocation);
		if(client.isMenuOpen() || client.getGameState() != GameState.LOGGED_IN || rightClickCounter >= config.maxRightClicks()) return;
		addMenu();
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		if (config.limitRightClicks() && !config.hideMode()) {
			if (rightClickCounter >= config.maxRightClicks()) {
				sendHighlightedChatMessage("You have used all of your guesses!");
				return;
			}
			rightClickCounter++;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (playerDisguises == null || playerDisguises.size() == 0) return;

		if (!event.getOption().startsWith("Walk here")) {
			if (config.depriorizteMenuOptions()) event.getMenuEntry().setDeprioritized(true);
			return;
		}

	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event) {
		if (event.getEntry() == PropHuntOverlay.RESET_ENTRY) {
			rightClickCounter = 0;
		}
	}

	private void onMenuOptionClicked(MenuEntry menuEntry) {
		if (Objects.equals(menuEntry.getOption(), GUESS)) {
			String playerName = checkProp();
			if (playerName != null) {
				playerFound(playerName);
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		String playerList = "";
		if (event.getMenuAction() == MenuAction.RUNELITE_PLAYER && event.getMenuOption().equals(ADD_HIDER)) {
			String playerName = event.getMenuEntry().getPlayer().getName();
			if (playerName == null || playerName.isEmpty()) {return;}
			if (Objects.equals(client.getLocalPlayer().getName(), config.lobby()) || config.lobby() == null || config.lobby().isEmpty()) {
				if (config.players().isEmpty() || config.players() == null) {
					playerList = playerName;
				} else if (config.players().contains(playerName)) {
					sendNormalChatMessage(playerName + " is already in the lobby!");
					playerList = config.players();
				} else {
					playerList = config.players() + "\n" + event.getMenuEntry().getPlayer().getName();
				}
				configManager.setConfiguration(CONFIG_KEY, "players", playerList);
			}
		}
	}

	public void addMenu() {
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries.length < 2) return;

		for (MenuEntry menuEntry : menuEntries) {
			if 	   (Objects.equals(menuEntry.getType().toString(), "CC_OP") ||
					Objects.equals(menuEntry.getType().toString(), "RUNELITE_PLAYER") ||
					Objects.equals(menuEntry.getType().toString(), "EXAMINE_NPC") ||
					Objects.equals(menuEntry.getType().toString(), "RUNELITE") ||
					Objects.equals(menuEntry.getType().toString(), "UNKNOWN") ||
					Objects.equals(menuEntry.getType().toString(), "EXAMINE_OBJECT") ||
					Objects.equals(menuEntry.getType().toString(), "WIDGET_TARGET")){
					return;
			}
		}
		client.createMenuEntry(1)
				.setOption(GUESS)
				.setType(MenuAction.GAME_OBJECT_SECOND_OPTION)
				.setDeprioritized(false)
				.setForceLeftClick(false)
				.setIdentifier(0)
				.onClick(this::onMenuOptionClicked);
	}

	private String checkProp(){
		LocalPoint wp = client.getSelectedSceneTile().getLocalLocation();
		for (String player: getPlayerNames()) {
			RuneLiteObject disguise = playerDisguises.get(player);
			if (disguise == null) {
				continue;
			}
			LocalPoint lp = disguise.getLocation();
			Model disguiseModel = client.loadModel(playersData.get(player).modelID);
			if (disguiseModel != null) {
				int minX = lp.getX() - 100;
				int minY = lp.getY() - 100;
				int maxX = lp.getX() + 100;
				int maxY = lp.getY() + 100;
				if (wp.getX() > minX && wp.getX() < maxX && wp.getY() > minY && wp.getY() < maxY)
				{
					return player;
				}
			}
		}
		return null;
	}

	private void playerFound(String playerName) {
		removePlayerTransmog(playerName);

		PropHuntPlayerData playerData = playersData.get(playerName);
		if (playerData != null) {
			playerData.hiding = false;
			String newPlayers = config.players().replace(playerName, "").replaceAll("(?m)^[\\s]*$\\n?", "");;
			if (newPlayers.endsWith("\n")) {
				newPlayers = newPlayers.substring(0, newPlayers.length() - 1);
			}
			configManager.setConfiguration(CONFIG_KEY, "players", newPlayers);
			rightClickCounter--;
		}
		if(config.sound()) {
			client.playSoundEffect(2396);
			client.playSoundEffect(2379);
		}
		sendHighlightedChatMessage(playerName + " has been found!");
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

	@VisibleForTesting
	boolean shouldDraw(Renderable renderable, boolean drawingUI) {
		if (renderable instanceof Player) {
			Player player = (Player) renderable;
			Player local = client.getLocalPlayer();

			if (player == local) {
				return !config.hideMode();
			}

			if (players == null) return true;

			ArrayList<String> playerList = new ArrayList<>(Arrays.asList(players));

			if (!playerList.contains(player.getName())) {
				PropHuntPlayerData data = playersData.get(player.getName());
				if (data != null) {
					if (data.hiding) {
						transmogPlayer(player, 0, 0, true);
						data.hiding = false;
					}
				}
				return true;
			} else {
				PropHuntPlayerData data = playersData.get(player.getName());
				if (data != null && data.hiding) {
					if (!playerDisguises.containsKey(player.getName())) {
						transmogPlayer(player, data.modelID, data.orientation, false);  // Apply disguise
					}
					return false;
				}
			}

			return true;
		}
		return true;
	}

	private void transmogPlayer(Player player) {
		transmogPlayer(player, getModelId(), config.orientation(), true);
	}

	private void transmogPlayer(Player player, int modelId, int orientation, boolean isLocal) {
		int modelID = isLocal ? modelId : playersData.get(player.getName()).modelID;

		if (isLocal) {
			removeLocalTransmog();
		} else {
			removePlayerTransmog(player);
		}

		RuneLiteObject disguise = client.createRuneLiteObject();
		try {
			LocalPoint loc = LocalPoint.fromWorld(client, player.getWorldLocation());
			if (loc == null) {
				return;
			}
		} catch (NullPointerException e){
			return;
		}

		Model model = client.loadModel(modelID);
		if (model == null) {
			log.warn("Failed to load model with ID: {}", modelID);
			return;
		}

		disguise.setModel(model);
		disguise.setLocation(player.getLocalLocation(), player.getWorldLocation().getPlane());
		disguise.setActive(true);
		disguise.setOrientation(orientation);

		if (isLocal) {
			localDisguise = disguise;
		} else {
			playerDisguises.put(player.getName(), disguise);
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
		if (playerDisguises.containsKey(player.getName())) {
			RuneLiteObject disguise = playerDisguises.get(player.getName());
			if (disguise != null) {
				disguise.setActive(false);
				playerDisguises.remove(player.getName());
			}
		}
	}

	private void removePlayerTransmog(String playerName) {
		if (playerDisguises.containsKey(playerName)) {
			RuneLiteObject disguise = playerDisguises.get(playerName);
			if (disguise != null) {
				disguise.setActive(false);
				playerDisguises.remove(playerName);
			}
		}
	}

	private void updatePlayerDisguiseLocation(Player player) {
		if (player == null || player == client.getLocalPlayer()) {
			return;
		}

		RuneLiteObject disguise = playerDisguises.get(player.getName());
		if (disguise != null) {
			disguise.setLocation(player.getLocalLocation(), player.getWorldLocation().getPlane());
		}
	}

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

	public void updatePlayerList(String[] playerList) {
		suppressLobbyPost = true;
		String p = "";
		if (playerList == null) p = "";
		else p = String.join("\n", playerList);

		setPlayersFromString(p);
		configManager.setConfiguration(CONFIG_KEY, "players", p);
		suppressLobbyPost = false;
	}

	public void updateSeekerList(String[] playerList) {
		seekerList = playerList;
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

	public boolean isHiding(String name) {
		if(name == null) return false;
		PropHuntPlayerData playerData = playersData.get(name);
		if(playerData == null) return false;

		return playerData.hiding;
	}

	public boolean isSeeker(){
		if(getSeekers(config.lobby()) == null) {
			if (config.lobby().equals(client.getLocalPlayer().getName())) {
				return true;
			}
			else return false;
		}
		for (String element : seekerList) {
			if (element != null && element.contains(client.getLocalPlayer().getName())) {
				return true;
			}
		}
		return false;
	}

	
	public String[] getSeekers(String lobbyID){
		propHuntDataManager.fetchSeekers(lobbyID);
		return seekerList;
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

	@Schedule(
			period = SECONDS_BETWEEN_GET,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void getPlayerConfigs() {
		if(players.length < 1 || config.players().isEmpty()) return;

		propHuntDataManager.getPropHuntersByUsernames(players);
	}

    public String[] getPlayerNames() {
		return players;
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

	public boolean getHideMode() {
		return config.hideMode();
	}

	private void setPlayersFromString(String playersString) {
		String[] p = playersString.split("[,\\n]");

		for(int i=0;i<p.length;i++) {
			p[i] = p[i].trim();
		}

		players = p;
	}

	public void setHideMode(boolean hideMode) {
		configManager.setConfiguration(CONFIG_KEY, "hideMode", hideMode);

		if (hideMode) {
			clientThread.invokeLater(() -> transmogPlayer(client.getLocalPlayer()));
		} else {
			clientThread.invokeLater(this::removeLocalTransmog);
		}
	}

	public void setRandomModelID() {
		long currentTime = System.currentTimeMillis();
		if (currentTime - lastRandomModelUpdate < RANDOM_MODEL_UPDATE_INTERVAL) {
			return;
		}
		configManager.setConfiguration(CONFIG_KEY, "modelID", ThreadLocalRandom.current().nextInt(config.randMinID(), config.randMaxID() + 1));
		lastRandomModelUpdate = currentTime; // Update the timestamp
	}

	public void setModelID(PropHuntModelId modelData) {
		configManager.setConfiguration(CONFIG_KEY, "modelID", modelData.getId());
	}

	public void setMinModelID(int minModelID) {
		configManager.setConfiguration(CONFIG_KEY, "randMinID", minModelID);
	}

	public void setMaxModelID(int maxModelID) {
		configManager.setConfiguration(CONFIG_KEY, "randMaxID", maxModelID);
	}

	public void rotateModel(int dir) {
		if(localDisguise != null) {
			int orientation = config.orientation() + 500*dir;
			orientation = (((orientation % 2000) + 2000) % 2000);
			localDisguise.setOrientation(orientation);
			configManager.setConfiguration(CONFIG_KEY, "orientation", orientation);
		}
	}

	@Provides
	PropHuntConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PropHuntConfig.class);
	}

}
