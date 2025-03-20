package com.idyl.prophunt;

import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY;

public class PropHuntOverlay extends OverlayPanel {
    public static OverlayMenuEntry RESET_ENTRY = new OverlayMenuEntry(RUNELITE_OVERLAY, "Reset", "Counter");

    private PropHuntPlugin plugin;
    private PropHuntConfig config;

    @Inject
    private PropHuntOverlay(PropHuntPlugin plugin, PropHuntConfig config) {
        this.plugin = plugin;
        this.config = config;
        getMenuEntries().add(RESET_ENTRY);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
        setPosition(OverlayPosition.BOTTOM_LEFT);

        setClearChildren(false);
    }

    @Override
    public Dimension render(Graphics2D graphics) {

        String[] playerNames = plugin.getPlayerNames();

        if (playerNames != null && playerNames.length > 0) {

            int boxWidth = 200;
            int boxHeight = 20 * playerNames.length + 40;
            int yPosition = getBoxYPosition() - boxHeight - 5;
            int textYPosition = yPosition + 10;
            String title = "ACTIVE HIDERS";
            List<String> activeHiders = new ArrayList<>();

            for (String playerName : playerNames) {
                if (playerName != null){
                    if (!playerName.trim().isEmpty() && plugin.isHiding(playerName)) {
                        activeHiders.add(playerName);
                    }
                }
            }

            panelComponent.getChildren().removeIf(component -> component instanceof LineComponent);
            if(activeHiders.isEmpty()){
                title = "NO ACTIVE HIDERS";
            }
            if (config.playerList()){
                LineComponent playerTitleLine = LineComponent.builder()
                        .left(title)
                        .leftColor(Color.YELLOW)
                        .leftFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD))
                        .build();
                playerTitleLine.setPreferredLocation(new Point(10, textYPosition));
                panelComponent.getChildren().add(playerTitleLine);
                textYPosition += 20;

                for (String playerName : activeHiders) {
                    if (playerName != null) {
                        LineComponent playerNameLine = LineComponent.builder()
                                .left(playerName.trim())
                                .leftColor(Color.WHITE)
                                .build();

                        playerNameLine.setPreferredLocation(new Point(10, textYPosition));
                        panelComponent.getChildren().add(playerNameLine);

                        textYPosition += 20;
                    }
                }
                if(config.limitRightClicks()) {
                    LineComponent sepLine = LineComponent.builder()
                            .left("\n")
                            .leftColor(Color.YELLOW)
                            .build();

                    sepLine.setPreferredLocation(new Point(10, textYPosition));
                    panelComponent.getChildren().add(sepLine);
                    textYPosition += 60;
                    sepLine.setPreferredLocation(new Point(10, textYPosition));
                    panelComponent.getChildren().add(sepLine);
                    textYPosition += 60;
                }
            }
            if(config.limitRightClicks()) {
                graphics.setFont(FontManager.getRunescapeFont());
                LineComponent rightClicksRemainingComponent = LineComponent.builder()
                        .left("GUESSES LEFT")
                        .leftColor(Color.WHITE)
                        .leftFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD))
                        .right("" + getClicksRemaining() + "")
                        .rightColor(getColor())
                        .build();
                rightClicksRemainingComponent.setPreferredLocation(new Point(10, textYPosition));
                panelComponent.getChildren().add(rightClicksRemainingComponent);
            }
        }

        return super.render(graphics);
    }

    private int getBoxYPosition() {
        return 0;
    }

    private Color getColor() {
        return getClicksRemaining() > 3 ? Color.GREEN : getClicksRemaining() > 0 ? Color.YELLOW : Color.RED;
    }

    private int getClicksRemaining() {
        return config.maxRightClicks() - plugin.getRightClickCounter();
    }
}
