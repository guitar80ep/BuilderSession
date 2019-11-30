package org.builder.session.jackson.utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.LoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import lombok.NonNull;

public class LoggingInitializer {

    ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
    Map<String, LayoutComponentBuilder> layouts = new HashMap<>();

    public void build() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = builder.build();
        ctx.updateLoggers(config);
        ctx.start(config);
        while(!ctx.isStarted()) {
            try {
                Thread.sleep(1000);
                System.out.println("Awaiting logger start: " + ctx);
            } catch (Throwable t) {
                System.err.println("Failed to start logger: " + ctx);
            }
        }
    }

    public LoggingInitializer addPatternLayout(@NonNull final String name,
                                               @NonNull final String pattern) {
        LayoutComponentBuilder layout  = builder.newLayout("PatternLayout");
        layout.addAttribute("pattern", pattern);
        layouts.put(name, layout);
        return this;
    }

    public LoggingInitializer addConsoleAppender(@NonNull final String name,
                                                 @NonNull final String layoutName) {
        LayoutComponentBuilder layout = layouts.get(layoutName);
        builder.add(builder.newAppender(name, "Console")
                           .add(layout));
        return this;
    }

    public LoggingInitializer addFileAppender(@NonNull final String name,
                                              @NonNull final String filePath,
                                              @NonNull final String layoutName) {
        LayoutComponentBuilder layout = layouts.get(layoutName);
        builder.add(builder.newAppender(name, "File")
                           .addAttribute("fileName", filePath)
                           .add(layout));
        return this;
    }

    @SafeVarargs
    public final LoggingInitializer addLogger(@NonNull final String packagePath,
                                              @NonNull final Level level,
                                              @NonNull final boolean additivity,
                                              @NonNull final String... appenderNames) {
        LoggerComponentBuilder logger = builder.newLogger(packagePath, level);
        logger.addAttribute("additivity", additivity);
        for(String appender : appenderNames) {
            logger.add(builder.newAppenderRef(appender));
        }
        builder.add(logger);
        return this;
    }

    @SafeVarargs
    public final LoggingInitializer addRootLogger(@NonNull final Level level,
                                                  @NonNull final String... appenderNames) {
        RootLoggerComponentBuilder logger = builder.newRootLogger(level);
        for(String appender : appenderNames) {
            logger.add(builder.newAppenderRef(appender));
        }
        builder.add(logger);
        return this;
    }

}
