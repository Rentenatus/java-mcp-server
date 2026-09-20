package com.example;

@Deprecated
class CodeZeile extends AbstractZeile {
    public CodeZeile() {
        super('C', java.awt.Color.GREEN, false);
    }
    @Override
    public String getDisplayText() {
        return "C";
    }
    @Override
    @Deprecated
    public String getMarker() {
        return "C-MARK";
    }
    @Deprecated
    public String getHint() {
        return "Secret code";
    }
}
