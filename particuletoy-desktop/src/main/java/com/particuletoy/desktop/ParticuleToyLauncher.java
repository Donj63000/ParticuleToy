package com.particuletoy.desktop;

import javafx.application.Application;

/**
 * Dedicated launcher class (helps with some toolchains / packaging edge cases).
 */
public final class ParticuleToyLauncher {

    private ParticuleToyLauncher() {}

    public static void main(String[] args) {
        Application.launch(ParticuleToyApp.class, args);
    }
}
