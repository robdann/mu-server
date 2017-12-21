package scaffolding;

import okhttp3.*;
import okio.BufferedSink;

import javax.net.ssl.*;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientUtils {

    public static final OkHttpClient client;
    public static X509TrustManager veryTrustingTrustManager = veryTrustingTrustManager();

    static {
        Logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINE);
        client = newClient()
            .build();
    }

    public static OkHttpClient.Builder newClient() {
        return new OkHttpClient.Builder()
            .hostnameVerifier((hostname, session) -> true)
            .sslSocketFactory(sslContextForTesting(veryTrustingTrustManager).getSocketFactory(), veryTrustingTrustManager);
    }

    public static RequestBody largeRequestBody(StringBuffer sentData) {
        return new RequestBody() {
            public MediaType contentType() {
                return MediaType.parse("text/plain");
            }

            public void writeTo(BufferedSink sink) throws IOException {
                write(sink, "Numbers\n");
                write(sink, "-------\n");
                for (int i = 2; i <= 997; i++) {
                    write(sink, String.format(" * %s\n", i));
                }
            }

            private void write(BufferedSink sink, String s) throws IOException {
                sentData.append(s);
                sink.writeUtf8(s);
            }
        };
    }

    public static Request.Builder request() {
        return new Request.Builder();
    }

    public static Response call(Request.Builder request) {
        try {
            return client.newCall(request.build()).execute();
        } catch (IOException e) {
            throw new RuntimeException("Error while calling " + request, e);
        }
    }

    public static SSLContext sslContextForTesting(TrustManager trustManager) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{trustManager}, null);
            return context;
        } catch (Exception e) {
            throw new RuntimeException("Cannot set up test SSLContext", e);
        }
    }

    public static X509TrustManager veryTrustingTrustManager() {
        return new X509TrustManager() {
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) { }

            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) { }

            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
        };
    }
}
