package com.example;

import java.awt.Color;

public class XStone extends AbstractStone {
    public XStone() {
        super('X', Color.RED, false);
    }
    @Override
    public String getDisplayText() {
        return "X";
    }
    @Override
    @Deprecated
    public String getMarker() {
        return "X-MARK";
    }
}
