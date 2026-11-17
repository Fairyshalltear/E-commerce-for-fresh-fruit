package com.fairyshalltear.freshfruit.ai.model;

import java.net.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static com.fairyshalltear.freshfruit.ai.AiErrors.invalid;

@Component
public class AiEndpointPolicy {
    private final List<String> hosts;
    private final boolean local;
    public AiEndpointPolicy(@Value("${freshfruit.ai.allowed-hosts:}") String hosts,
            @Value("${freshfruit.ai.allow-local-endpoints:false}") boolean local) {
        this.hosts = Arrays.stream(hosts.split(",")).map(String::trim).filter(s->!s.isBlank()).map(s->s.toLowerCase(Locale.ROOT)).toList();
        this.local = local;
    }
    public URI validate(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            boolean localhost = host != null && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]"));
            if (host == null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                    || (!"https".equals(uri.getScheme()) && !(local && localhost && "http".equals(uri.getScheme())))) throw new IllegalArgumentException();
            host = host.toLowerCase(Locale.ROOT);
            final String checkedHost=host;
            if (!(local && localhost) && hosts.stream().noneMatch(pattern->pattern.startsWith("*.")
                    ? checkedHost.endsWith(pattern.substring(1)) && checkedHost.length()>pattern.length()-1 : checkedHost.equals(pattern))) throw new IllegalArgumentException();
            for (InetAddress address : InetAddress.getAllByName(host)) validateAddress(address);
            return uri;
        } catch (Exception error) { throw invalid("模型地址需使用HTTPS、已允许的供应商域名和公开网络地址"); }
    }
    public void validateAddress(InetAddress address) {
        if (local && address.isLoopbackAddress()) return;
        byte[] bytes=address.getAddress();
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()
                || (bytes.length==16 && (bytes[0]&0xfe)==0xfc)
                || (bytes.length==4 && ((bytes[0]&255)==0 || (bytes[0]&255)>=224
                    || ((bytes[0]&255)==100 && (bytes[1]&255)>=64 && (bytes[1]&255)<=127)
                    || ((bytes[0]&255)==198 && ((bytes[1]&255)==18 || (bytes[1]&255)==19)))))
            throw invalid("模型连接目标不是允许的公开网络地址");
    }
}
