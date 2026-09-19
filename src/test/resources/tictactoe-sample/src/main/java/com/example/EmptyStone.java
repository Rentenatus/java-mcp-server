package com.example;

import java.awt.Color;

public class EmptyStone extends AbstractStone {
    public EmptyStone() {
        super(' ', Color.WHITE, true);
    }
    @Override
    public String getDisplayText() {
        return "";
    }
    @Override
    @Deprecated
    public String getMarker() {
        return "";
    }
}
