package org.builder.session.jackson.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.URL;
import java.net.UnknownHostException;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.NONE)
public class HostnameUtils {

    private static final String IP_ADDRESS_SERVER = "http://bot.whatismyipaddress.com";

    public static String resolveIpAddress(AddressType type) {
        try {
            switch(type) {
                case PRIVATE:
                    return Inet4Address.getLocalHost().getHostAddress();
                case PUBLIC:
                    URL url_name = new URL(IP_ADDRESS_SERVER);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(url_name.openStream()));
                    return reader.readLine().trim();
                    default:
                        throw new IllegalArgumentException("Unexpected type of IP Address: " + type);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Could not find IP address: " + type, e);
        }
    }

    public static String resolveHostname() {
        try {
            return Inet4Address.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not find local hostname.", e);
        }
    }

    public enum AddressType {
        PUBLIC, PRIVATE
    }

}
