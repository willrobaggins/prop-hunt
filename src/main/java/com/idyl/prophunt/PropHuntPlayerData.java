package com.idyl.prophunt;

public class PropHuntPlayerData {
    public int modelID;
    public String username;
    public boolean hiding;
    public int orientation;

    public PropHuntPlayerData(String username, boolean hiding, int modelID, int orientation) {
        this.username = username;
        this.hiding = hiding;
        this.modelID = modelID;
        this.orientation = orientation;
    }

    @Override
    public String toString() {
        return "username: "+username+", hiding: "+hiding+", modelID: "+ modelID + ", orientation: "+orientation;
    }
}
