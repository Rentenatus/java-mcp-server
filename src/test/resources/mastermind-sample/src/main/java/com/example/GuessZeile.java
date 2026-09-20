package com.example;

class GuessZeile extends AbstractZeile {
    public GuessZeile() {
        super('G', java.awt.Color.YELLOW, false);
    }
    @Override
    public String getDisplayText() {
        return "G";
    }
    @Override
    @Deprecated
    public String getMarker() {
        return "G-MARK";
    }
}
