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
    @Override
    public char getSymbol() {
        return symbol;
    }
    @Override
    public boolean isEmpty() {
        return empty;
    }
    @Override
    public java.awt.Color getColor() {
        return color;
    }
    public String toString() {
        return getClass().getSimpleName() + "[" + getSymbol() + "]";
    }
}
