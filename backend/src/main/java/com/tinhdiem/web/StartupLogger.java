package com.tinhdiem.web;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** In địa chỉ để mở trên máy này và trên điện thoại cùng Wi-Fi ngay khi ứng dụng sẵn sàng. */
@Component
public class StartupLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    private final Environment environment;

    public StartupLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printAddresses() {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        StringBuilder message = new StringBuilder("\n\n  Mở trình duyệt tại:\n");
        message.append("    http://localhost:").append(port).append('\n');
        for (String ip : lanAddresses()) {
            message.append("    http://").append(ip).append(':').append(port).append("   (điện thoại cùng Wi-Fi)\n");
        }
        message.append("\n  Nếu điện thoại không vào được: cho phép Java qua tường lửa Windows (mạng riêng).\n");
        log.info(message.toString());
    }

    private static List<String> lanAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        result.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("Không liệt kê được địa chỉ mạng", e);
        }
        return result;
    }
}
