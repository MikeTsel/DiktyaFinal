package server;

import java.util.logging.*;

public class LoggingConfig {
    public static void configureLogging() {
        try {
            Logger rootLogger = Logger.getLogger("");

            for (Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }

            ConsoleHandler consoleHandler = new ConsoleHandler();
            SimpleFormatter formatter = new SimpleFormatter() {
                @Override
                public synchronized String format(LogRecord record) {
                    return String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %2$s: %3$s%n",
                            new java.util.Date(record.getMillis()),
                            record.getLevel().getName(),
                            record.getMessage());
                }
            };
            consoleHandler.setFormatter(formatter);

            consoleHandler.setLevel(Level.INFO);
            rootLogger.setLevel(Level.INFO);      // Only show INFO level and above

            rootLogger.addHandler(consoleHandler);

            Logger.getLogger("server").setLevel(Level.INFO);

            Logger.getLogger("sun.rmi").setLevel(Level.OFF);
            Logger.getLogger("java.rmi").setLevel(Level.OFF);
            Logger.getLogger("javax.management").setLevel(Level.OFF);
            Logger.getLogger("com.sun.jmx").setLevel(Level.OFF);
            Logger.getLogger("java.io").setLevel(Level.OFF);

            System.out.println("Logging system initialized - showing only application logs");
        } catch (Exception e) {
            System.err.println("Failed to setup logger: " + e.getMessage());
        }
    }
}