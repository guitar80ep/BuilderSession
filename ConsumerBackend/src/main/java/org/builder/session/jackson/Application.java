package org.builder.session.jackson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.build.session.jackson.proto.Resource;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistry;
import org.builder.session.jackson.client.loadbalancing.ServiceRegistryImpl;
import org.builder.session.jackson.server.Server;
import org.builder.session.jackson.server.ServerImpl;
import org.builder.session.jackson.system.Profiler;
import org.builder.session.jackson.system.SystemUtil;
import org.builder.session.jackson.system.TaskSystemUtil;
import org.builder.session.jackson.utils.CommandLineArguments;
import org.builder.session.jackson.utils.LoggingInitializer;
import org.builder.session.jackson.workflow.utilize.Consumer;
import org.builder.session.jackson.workflow.utilize.PIDConfig;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application
{
    public static void main(String[] args) {

        initializeLogging();

        //Gather input information...
        Preconditions.checkArgument(args.length >= 6,
                                    "Expected at least 6 arguments to this application " +
                                            "[--port, --pidConfig, --serviceDiscoveryId].");
        final int port = parsePort(args);
        final PIDConfig pidConfig = parsePidConfig(args);
        final String dnsName = parseServiceDiscoveryId(args);
        final SystemUtil systemUtil = parseProfiling(args);
        final Set<Resource> resources = parseConsumerConfig(args);
        final ServiceRegistry registry = new ServiceRegistryImpl(dnsName);
        final Map<Resource, Consumer> consumers = Consumer.buildDefaultConsumers(resources,
                                                                                 systemUtil,
                                                                                 pidConfig,
                                                                                 registry);

        log.info("Starting a server on port {}, with consumers {}, with PID {}, and DNS \"{}\".",
                 new Object[]{ port,
                               resources,
                               pidConfig,
                               dnsName });

        try (Server server = new ServerImpl(port, consumers, registry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            server.start();
            while(!shouldStop(reader)) {
                // Do nothing while the loop awaits completion...
            }
        } catch (Throwable t) {
            log.error("Found unexpected error while running server. Shutting down.", t);
            System.exit(1);
        }

        log.info( "Server shutdown is complete." );
        System.exit(0);
    }

    protected static boolean shouldStop(BufferedReader reader) throws IOException {
        return reader.ready();
    }

    protected static SystemUtil parseProfiling(final @NonNull String[] args) {
        SystemUtil systemUtil = new TaskSystemUtil();
        Optional<Integer> secondsToProfile = CommandLineArguments.parseArg(args,
                                                                           false,
                                                                           "--runProfiling",
                                                                           Integer::parseInt);
        secondsToProfile.ifPresent(s -> {
            try (Profiler profiler = new Profiler(Duration.ofSeconds(s))) {
                profiler.profile(systemUtil);
            } catch (Throwable t) {
                log.error("System profiling failed due to: {}", t);
                System.exit(1);
            }
        });

        return systemUtil;
    }

    protected static int parsePort(final @NonNull String[] args) {
        return CommandLineArguments.parseArg(args, true, "--port", s -> {
            int port = Integer.parseInt(s);
            Range<Integer> range = Range.openClosed(0, Short.MAX_VALUE * 2);
            Preconditions.checkArgument(range.contains(port), "Expected valid port within range " + range);
            return port;
        }).get();
    }

    protected static PIDConfig parsePidConfig(final @NonNull String[] args) {
        return CommandLineArguments.parseArg(args, true, "--pid", s -> {
            s = s.trim();
            Preconditions.checkArgument(s.startsWith("["), "List should start with \"[\"");
            Preconditions.checkArgument(s.endsWith("]"), "List should ends with \"[\"");
            s = s.substring(1, s.length() - 1);
            String[] listArgs = s.split(",");
            Preconditions.checkArgument(listArgs.length == 5, "Expected exactly 5 value for PIDConfig list, but got " + listArgs);
            long paceInMillis = Integer.parseInt(listArgs[0].trim());
            double p = Double.parseDouble(listArgs[1].trim());
            double d = Double.parseDouble(listArgs[2].trim());
            double i = Double.parseDouble(listArgs[3].trim());
            double decay = Double.parseDouble(listArgs[4].trim());
            Range<Double> range = Range.open(0.0, 10.0);
            Preconditions.checkArgument(range.contains(p), "Expected valid P-Value within range " + range);
            Preconditions.checkArgument(range.contains(d), "Expected valid D-Value within range " + range);
            Preconditions.checkArgument(range.contains(i), "Expected valid I-Value within range " + range);
            Preconditions.checkArgument(range.contains(decay), "Expected valid Decay within range " + range);
            return PIDConfig.builder()
                            .pace(Duration.ofMillis(paceInMillis))
                            .proportionFactor(p)
                            .derivativeFactor(d)
                            .integralFactor(i)
                            .integralDecay(decay)
                            .build();
        }).get();
    }

    protected static Set<Resource> parseConsumerConfig(final @NonNull String[] args) {
        return CommandLineArguments.parseArg(args, true, "--consumers", s -> {
            s = s.trim();
            Preconditions.checkArgument(s.startsWith("["), "List should start with \"[\"");
            Preconditions.checkArgument(s.endsWith("]"), "List should ends with \"[\"");
            s = s.substring(1, s.length() - 1);
            String[] listArgs = s.split(",");
            return Arrays.asList(listArgs)
                         .stream()
                         .map(r -> Resource.valueOf(r.toUpperCase()))
                         .collect(Collectors.toSet());
        }).get();
    }

    protected static String parseServiceDiscoveryId (final @NonNull String[] args) {
        return CommandLineArguments.parseArg(args,
                                             true,
                                             "--serviceDiscoveryId",
                                             Function.identity()).get();
    }

    private static void initializeLogging () {
        //Initialize logging...
        LoggingInitializer logger = new LoggingInitializer();
        logger.addPatternLayout("MainLayout", "%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n")
              .addConsoleAppender("ConsoleAppender", "MainLayout")
              .addFileAppender("FileAppender", "logs/application.log", "MainLayout")
              .addRootLogger(Level.WARN)
              .addLogger("org.builder.session", Level.INFO,
                         false,
                         "ConsoleAppender",
                         "FileAppender")
              .addLogger("org.builder.session.jackson.system",
                         Level.INFO,
                         false,
                         "ConsoleAppender",
                         "FileAppender")
              .addLogger("org.builder.session.jackson.client",
                         Level.DEBUG,
                         false,
                         "ConsoleAppender",
                         "FileAppender")
              .addLogger("org.builder.session.jackson.workflow",
                         Level.DEBUG,
                         false,
                         "ConsoleAppender",
                         "FileAppender")
              .build();
        log.info("Logging is initialized!");
    }
}
