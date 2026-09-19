package com.example;

import java.awt.Color;

public class OStone extends AbstractStone {
    public OStone() {
        super('O', Color.BLUE, false);
    }
    @Override
    public String getDisplayText() {
        return "O";
    }
    @Override
    @Deprecated
    public String getMarker() {
        return "O-MARK";
    }
}
