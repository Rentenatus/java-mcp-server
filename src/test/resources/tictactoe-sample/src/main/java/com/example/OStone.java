package com.example;

import java.awt.Color;

public class OStone extends AbstractStone {
    public OStone() {
        super('O', Color.BLUE, false);
    }
    public String getDisplayText() {
        return "O";
    }
}
