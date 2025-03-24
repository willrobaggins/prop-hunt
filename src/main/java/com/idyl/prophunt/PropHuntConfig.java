package com.idyl.prophunt;

import net.runelite.api.Client;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import javax.inject.Inject;

@ConfigGroup("prophunt")
public interface PropHuntConfig extends Config {
	@ConfigSection(
			name = "Lobby Setup",
			description = "Setup for the game instance.",
			position = 0
	)
	String setupSettings = "setupSettings";

	@ConfigSection(
			name = "Seeker Settings",
			description = "Settings relating to seeking (non-hider).",
			position = 1
	)
	String seekerSettings = "seekerSettings";

	@ConfigSection(
			name = "Advanced",
			description = "Advanced settings.",
			closedByDefault = true,
			position = 2
	)
	String advancedSettings = "advancedSettings";

	@ConfigItem(
			keyName = "lobby",
			name = "Lobby ID (RSN of host)",
			description = "Copy another user's player list (using their RSN)",
			position = 0,
			section = setupSettings,
			secret = true
	)
	default String lobby() {
		return "";
	}

	@ConfigItem(
			keyName = "players",
			name = "Player Names",
			description = "Names of the players you are playing with (comma separated & CaseSensitive)",
			position = 1,
			section = setupSettings
	)
	default String players() {
		return "";
	}

	@ConfigItem(
			keyName = "playerList",
			name = "On-Screen Player List",
			description = "Display player list in overlay.",
			section = setupSettings,
			position = 2
	)
	default boolean playerList() { return true; }

	@ConfigItem(
			keyName = "hideMinimapDots",
			name = "Hide Minimap Dots",
			description = "Toggle whether minimap dots are hidden. (Recommended for seekers)",
			position = 3,
			section = seekerSettings
	)
	default boolean hideMinimapDots() {
		return false;
	}

	@ConfigItem(
			keyName = "depriorizteMenuOptions",
			name = "Deprioritize Menu Options",
			description = "Forces 'Walk Here' to the top of every menu to better hide props. (Recommended for seekers)",
			position = 5,
			section = seekerSettings
	)
	default boolean depriorizteMenuOptions() {
		return false;
	}

	@ConfigItem(
			keyName = "limitRightClicks",
			name = "Limit Right Clicks",
			description = "Limit the number of right clicks a seeker can do. (Guesses they may take)",
			position = 6,
			section = seekerSettings
	)
	default boolean limitRightClicks() {
		return false;
	}

	@ConfigItem(
			keyName = "maxRightClicks",
			name = "Maximum Right Clicks",
			description = "The number of guesses a seeker can make.",
			position = 7,
			section = seekerSettings
	)
	default int maxRightClicks() {
		return 10;
	}

	@ConfigItem(
			keyName = "sound",
			name = "Sound",
			description = "Play a sound when you find a hider.",
			position = 0,
			section = advancedSettings
	)
	default boolean sound() {
		return false;
	}

	@ConfigItem(
			keyName = "alternate",
			name = "Use alternate API server",
			description = "Toggle use of alternate api server. (ADVANCED)",
			position = 1,
			section = advancedSettings
	)
	default boolean alternate() {
		return false;
	}

	@ConfigItem(
			keyName = "apiURL",
			name = "Alternate API URL",
			description = "URL to alternate API server. (ADVANCED)",
			position = 2,
			section = advancedSettings
	)
	default String apiURL() {
		return "";
	}

	/**
	 * NOT IN USE
	 **/
	@ConfigItem(
			keyName = "models",
			name = "Custom Model List",
			description = "Models that you want to play with (formatted: name: id, ...)",
			position = 2,
			hidden = true,
			section = setupSettings
	)
	default String models() {
		return "Bush: 1565, Crate: 12125, Rock Pile: 1391";
	}

	/** HIDDEN **/
	@ConfigItem(
			keyName = "modelID",
			name = "Model ID",
			description = "The ID of the model you'd like to become.",
			position = 8,
			hidden = true
	)
	default int modelID() {
		return 1565;
	}

	@ConfigItem(
			keyName = "randMinID",
			name = "Min Random Model ID",
			description = "The minimum randomised ID of the model you'd like to become",
			position = 9,
			hidden = true,
			section = setupSettings
	)
	default int randMinID() {
		return 1078;
	}

	@ConfigItem(
			keyName = "randMaxID",
			name = "Max Random Model ID",
			description = "The maximum randomised ID of the model you'd like to become",
			position = 10,
			hidden = true,
			section = setupSettings
	)
	default int randMaxID() {
		return 1724;
	}

	@ConfigItem(
			keyName = "orientation",
			name = "Orientation",
			description = "orientation",
			hidden = true
	)
	default int orientation() {
		return 0;
	}

	@ConfigItem(
			keyName = "hideMode",
			name = "Hide Mode",
			description = "Toggle whether you are currently hiding or not.",
			hidden = true
	)
	default boolean hideMode() {
		return false;
	}

}

