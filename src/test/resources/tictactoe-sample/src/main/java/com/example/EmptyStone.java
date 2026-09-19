package com.example;

import java.awt.Color;

public class EmptyStone extends AbstractStone {
    public EmptyStone() {
        super(' ', Color.WHITE, true);
    }
    public String getDisplayText() {
        return "";
    }
}
