package com.example;

import java.awt.Color;

public abstract class AbstractStone implements Stone {
    private final char symbol;
    private final Color color;
    private final boolean empty;

    protected AbstractStone(char symbol, Color color, boolean empty) {
        this.symbol = symbol;
        this.color = color;
        this.empty = empty;
    }
    public char getSymbol() {
        return symbol;
    }
    public boolean isEmpty() {
        return empty;
    }
    public java.awt.Color getColor() {
        return color;
    }
    public String toString() {
        return getClass().getSimpleName() + "[" + getSymbol() + "]";
    }
}
