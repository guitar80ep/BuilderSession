package org.builder.session.jackson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import org.apache.logging.log4j.Level;
import org.builder.session.jackson.server.Server;
import org.builder.session.jackson.server.ServerImpl;
import org.builder.session.jackson.utils.JavaSystemUtil;
import org.builder.session.jackson.utils.LoggingInitializer;
import org.builder.session.jackson.utils.PIDConfig;
import org.builder.session.jackson.utils.Profiler;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class App 
{
    public static void main(String[] args) {

        //Initialize logging...
        LoggingInitializer logger = new LoggingInitializer();
        logger.addPatternLayout("MainLayout", "%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n")
              .addConsoleAppender("ConsoleAppender", "MainLayout")
              .addFileAppender("FileAppender", "logs/application.log", "MainLayout")
              .addRootLogger(Level.WARN)
              .addLogger("org.builder.session",
                         Level.DEBUG,
                         false,
                         "ConsoleAppender",
                         "FileAppender")
              .build();
        log.info("Logging is initialized!");

        //Gather port information...
        parseProfiling(args);
        final int port = parsePort(args);
        final PIDConfig pidConfig = parsePidConfig(args);
        final String dnsName = parseServiceDiscoveryId(args);

        log.info("Starting a server on port {}, with PID {}, and DNS \"{}\".",
                 new Object[]{ port,
                               pidConfig,
                               dnsName });

        try (Server server = new ServerImpl(port, pidConfig, dnsName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            server.start();
            while(!shouldStop(reader)) {
                // Do nothing while the loop awaits completion...
            }
        } catch (Throwable t) {
            log.error("Found unexpected error while running server.", t);
        }

        log.info( "Server shutdown is complete." );
        System.exit(0);
    }

    protected static boolean shouldStop(BufferedReader reader) throws IOException {
        return reader.ready();
    }

    protected static void parseProfiling(final @NonNull String[] args) {
        Preconditions.checkArgument(args.length >= 2, "Expected at least 2 arguments to this application.");

        Optional<Integer> secondsToProfile = parseArg(args,
                                                      false,
                                                      "--runProfiling",
                                                      Integer::parseInt);
        secondsToProfile.ifPresent(s -> {
            try (Profiler profiler = new Profiler(Duration.ofSeconds(s))) {
                log.warn(profiler.getProfileInfo(new JavaSystemUtil()));
            } catch (Throwable t) {
                log.error("System profiling failed due to: {}", t);
                System.exit(1);
            }
        });
    }

    protected static int parsePort(final @NonNull String[] args) {
        return parseArg(args, true, "--port", s -> {
            int port = Integer.parseInt(s);
            Range<Integer> range = Range.openClosed(0, Short.MAX_VALUE * 2);
            Preconditions.checkArgument(range.contains(port), "Expected valid port within range " + range);
            return port;
        }).get();
    }

    protected static PIDConfig parsePidConfig(final @NonNull String[] args) {
        return parseArg(args, true, "--pid", s -> {
            s = s.trim();
            Preconditions.checkArgument(s.startsWith("["), "List should start with \"[\"");
            Preconditions.checkArgument(s.endsWith("]"), "List should ends with \"[\"");
            s = s.substring(1, s.length() - 1);
            String[] listArgs = s.split(",");
            Preconditions.checkArgument(listArgs.length == 4, "Expected exactly 4 value for PIDConfig list, but got " + listArgs);
            long paceInMillis = Integer.parseInt(listArgs[0].trim());
            double p = Double.parseDouble(listArgs[1].trim());
            double d = Double.parseDouble(listArgs[2].trim());
            double i = Double.parseDouble(listArgs[3].trim());
            Range<Double> range = Range.open(0.0, 10.0);
            Preconditions.checkArgument(range.contains(p), "Expected valid P-Value within range " + range);
            Preconditions.checkArgument(range.contains(d), "Expected valid D-Value within range " + range);
            Preconditions.checkArgument(range.contains(i), "Expected valid I-Value within range " + range);
            return PIDConfig.builder()
                            .pace(Duration.ofMillis(paceInMillis))
                            .proportionFactor(p)
                            .derivativeFactor(d)
                            .integralFactor(i)
                            .build();
        }).get();
    }

    protected static String parseServiceDiscoveryId (final @NonNull String[] args) {
        return parseArg(args,
                        true,
                        "--serviceDiscoveryId",
                        Function.identity()).get();
    }

    protected static <T> Optional<T> parseArg(final @NonNull String[] args,
                                              final boolean required,
                                              final @NonNull String parameterName,
                                              final @NonNull Function<String, T> func) {
        Preconditions.checkArgument(args.length % 2 == 0, "Expected an even number of arguments, but got " + args);
        for(int i = 0; i < args.length; i += 2) {
            String currentParamName = args[i];
            String currentParamValue = args[i+1];
            if(parameterName.equals(currentParamName)) {
                return Optional.of(func.apply(currentParamValue));
            }
        }

        if(required) {
            throw new IllegalArgumentException("Could not find appropriate parameter " + parameterName);
        } else {
            return Optional.empty();
        }
    }
}
