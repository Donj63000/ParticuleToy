package com.particuletoy.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple debug logger that appends to bug-log.txt in the working directory.
 */
public final class DebugLog {
    private static final Object LOCK = new Object();
    private static final String LOG_FILE_NAME = "bug-log.txt";
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final File LOG_FILE = resolveLogFile();

    private DebugLog() {}

    public static void log(String line) {
        if (line == null) return;
        String stamped = LocalDateTime.now().format(TS_FORMAT) + " " + line;
        writeLine(stamped);
    }

    private static void writeLine(String line) {
        synchronized (LOCK) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                System.err.println("Failed to write " + LOG_FILE.getAbsolutePath() + ": " + e.getMessage());
            }
        }
    }

    private static File resolveLogFile() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 4 && dir != null; i++) {
            File pom = new File(dir, "pom.xml");
            if (pom.isFile()) {
                return new File(dir, LOG_FILE_NAME);
            }
            dir = dir.getParentFile();
        }
        return new File(System.getProperty("user.dir"), LOG_FILE_NAME);
    }
}
