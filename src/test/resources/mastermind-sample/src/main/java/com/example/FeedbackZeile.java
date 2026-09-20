package com.example;

class FeedbackZeile extends AbstractZeile {
    private int blackPins;
    private int whitePins;

    public FeedbackZeile() {
        super('F', java.awt.Color.ORANGE, false);
    }
    @Override
    public String getDisplayText() {
        return "B:" + blackPins + " W:" + whitePins;
    }
    @Override
    @Deprecated
    public String getMarker() {
        return "F-MARK";
    }
    private int attempts;
    @Deprecated
    public int getBlackPins() {
        return blackPins;
    }
}
