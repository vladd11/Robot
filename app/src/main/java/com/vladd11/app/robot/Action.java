package com.vladd11.app.robot;

public enum Action {
    LEFT((byte) 0x0),
    RIGHT((byte) 0x1),
    FORWARD((byte) 0x2),
    BACK((byte) 0x3),
    STOP((byte) 0x4);

    private final byte action;

    Action(byte action) {
        this.action = action;
    }

    public byte getAction() {
        return action;
    }
}
