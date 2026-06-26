package com.example;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class BossLogUser {
    private String name;

    public void greet() {
        log.info("hello from " + name);
    }
}
