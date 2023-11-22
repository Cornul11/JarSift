package nl.tudelft.cornul11.thesis.packaging.util;

import java.net.InetAddress;

public class HostNameUtility {
    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
    }
}
