package com.example;

abstract class AbstractZeile implements Zeile {
    private char symbol;
    private java.awt.Color color;
    private boolean empty;
    protected AbstractZeile(char symbol, java.awt.Color color, boolean empty) {
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
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getSymbol() + "]";
    }
}
