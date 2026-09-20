package com.example;

interface Zeile {
    char getSymbol();
    boolean isEmpty();
    String getDisplayText();
    java.awt.Color getColor();
    @Deprecated
    String getMarker();
}
