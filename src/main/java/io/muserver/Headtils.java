package io.muserver;

import io.netty.handler.codec.HeadersUtils;

import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

class Headtils {
    static List<ForwardedHeader> getForwardedHeaders(Headers headers) {
        List<String> all = headers.getAll(HeaderNames.FORWARDED);
        if (all.isEmpty()) {

            List<String> hosts = headers.getAll(HeaderNames.X_FORWARDED_HOST);
            List<String> ports = headers.getAll(HeaderNames.X_FORWARDED_PORT);
            List<String> protos = headers.getAll(HeaderNames.X_FORWARDED_PROTO);
            List<String> fors = headers.getAll(HeaderNames.X_FORWARDED_FOR);
            int max = Math.max(Math.max(Math.max(hosts.size(), protos.size()), fors.size()), ports.size());
            if (max == 0) {
                return emptyList();
            }
            List<ForwardedHeader> results = new ArrayList<>();

            boolean includeHost = hosts.size() == max;
            boolean includeProto = protos.size() == max;
            boolean includeFor = fors.size() == max;
            boolean includePort = ports.size() == max;
            String curHost = includePort && !includeHost ? headers.get(HeaderNames.HOST) : null;

            for (int i = 0; i < max; i++) {
                String host = includeHost ? hosts.get(i) : null;
                String port = includePort ? ports.get(i) : null;
                String proto = includeProto ? protos.get(i) : null;
                String forValue = includeFor ? fors.get(i) : null;
                boolean useDefaultPort = port == null || (proto != null &&
                    ((proto.equalsIgnoreCase("http") && "80".equals(port))
                    || proto.equalsIgnoreCase("https") && "443".equals(port)));
                String hostToUse =
                    includeHost ? host
                    : includePort ? curHost
                    : null;
                if (hostToUse != null && !useDefaultPort) {
                    hostToUse = hostToUse.replaceFirst(":[0-9]+$", "") + ":" + port;
                }
                results.add(new ForwardedHeader(null, forValue, hostToUse, proto, null));
            }

            return results;
        } else {
            List<ForwardedHeader> results = new ArrayList<>();
            for (String s : all) {
                results.addAll(ForwardedHeader.fromString(s));
            }
            return results;
        }
    }

    static List<ParameterizedHeaderWithValue> getParameterizedHeaderWithValues(Headers headers, CharSequence headerName) {
        String input = headers.get(headerName);
        if (input == null) {
            return emptyList();
        }
        return ParameterizedHeaderWithValue.fromString(input);
    }

    static MediaType getMediaType(Headers headers) {
        String value = headers.get(HeaderNames.CONTENT_TYPE);
        if (value == null) {
            return null;
        }
        return MediaTypeParser.fromString(value);
    }

    static String toString(Headers headers, Collection<String> toSuppress) {
        return HeadersUtils.toString(headers.getClass(), new RedactorIterator(headers.iterator(), toSuppress), headers.size());
    }

    private static class RedactorIterator implements Iterator<Map.Entry<String, String>> {
        static final List<String> sensitiveOnes = asList(HeaderNames.COOKIE.toString(), HeaderNames.SET_COOKIE.toString(), HeaderNames.AUTHORIZATION.toString());
        private final Iterator<Map.Entry<String, String>> iterator;
        private final Collection<String> toSuppress;

        public RedactorIterator(Iterator<Map.Entry<String, String>> iterator, Collection<String> toSuppress) {
            this.iterator = iterator;
            if (toSuppress == null) {
                this.toSuppress = sensitiveOnes;
            } else {
                this.toSuppress = toSuppress.stream().map(String::toLowerCase).collect(Collectors.toSet());
            }
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Map.Entry<String, String> next() {
            Map.Entry<String, String> next = iterator.next();
            if (toSuppress.contains(next.getKey().toLowerCase())) {
                return new AbstractMap.SimpleImmutableEntry<>(next.getKey(), "(hidden)");
            }
            return next;
        }
    }
}
