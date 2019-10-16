package org.builder.session.jackson.utils;

import java.net.Inet4Address;
import java.net.UnknownHostException;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.NONE)
public class HostnameUtils {

    public static String resolveIpAddress() {
        try {
            return Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not find local hostname.", e);
        }
    }

    public static String resolveHostname() {
        try {
            return Inet4Address.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not find local hostname.", e);
        }
    }

}
