package io.muserver;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scaffolding.MuAssert;
import scaffolding.ServerUtils;
import scaffolding.SlowBodySender;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static scaffolding.ClientUtils.*;
import static scaffolding.MuAssert.assertNotTimedOut;

public class RequestBodyReaderInputStreamAdapterTest {
    private static final Logger log = LoggerFactory.getLogger(RequestBodyReaderInputStreamAdapterTest.class);
    private MuServer server;

    @Test
    @Ignore("Why fail")
    public void hugeBodiesCanBeStreamed() throws IOException {
        int chunkSize = 64000;
        int loops = 100000;

        server = ServerUtils.httpsServerForTest()
            .withMaxRequestSize(loops * (long)chunkSize)
            .addHandler((request, response) -> {
                response.contentType("application/octet-stream");
                try (InputStream is = request.inputStream().orElseThrow(() -> new MuException("No input stream"))) {
                    byte[] buffer = new byte[chunkSize];
                    int read;
                    long received = 0;
                    while ((read = is.read(buffer)) > -1) {
                        received += read;
                    }
                    response.write("Got " + received + " bytes");
                }
                return true;
            })
            .start();

        Request.Builder request = request()
            .url(server.uri().toString())
            .post(new RequestBody() {
                @Override
                public MediaType contentType() {
                    return MediaType.get("application/octet-stream");
                }

                @Override
                public void writeTo(BufferedSink bufferedSink) throws IOException {
                    byte[] ones = new byte[chunkSize];
                    Arrays.fill(ones, (byte) 1);
                    for (int i = 0; i < loops; i++) {
                        ByteBuffer src = ByteBuffer.wrap(ones);
                        bufferedSink.write(src);
                    }
                }
            });

        try (Response resp = call(request)) {
            assertThat(resp.code(), is(200));
            assertThat(resp.body().string(), equalTo("Got " + (chunkSize * loops) + " bytes"));
        }
    }

    @Test
    public void hugeBodiesCanBeStreamedWithJetty() throws Exception {

        long loops = 1000;
        int chunkSize = 64000;

        server = ServerUtils.httpsServerForTest()
            .withMaxRequestSize(loops * (long)chunkSize)
            .addHandler((request, response) -> {
                log.info("Got " + request + " with " + request.headers());
                response.contentType("application/octet-stream");
                try (InputStream is = request.inputStream().orElseThrow(() -> new MuException("No input stream"));
                     OutputStream out = response.outputStream(chunkSize)) {
                    try {
                        Mutils.copy(is, out, chunkSize);
                    } catch (Throwable e) {
                        log.error("ARGH", e);
                    }
                }
                return true;
            })
            .start();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Result> clientResult = new AtomicReference<>();
        AtomicLong bytesReceived = new AtomicLong();

        try (PipedOutputStream uploadOutStream = new PipedOutputStream();
             InputStreamContentProvider inputProvider = new InputStreamContentProvider(new PipedInputStream(uploadOutStream, chunkSize), chunkSize)) {

            jettyClient().newRequest(server.uri())
                .method("POST")
                .header("content-type", "application/octet-stream")
                .content(inputProvider)
                .send(new org.eclipse.jetty.client.api.Response.Listener() {
                    @Override
                    public void onBegin(org.eclipse.jetty.client.api.Response response) {
                        log.info("Client response starting");
                    }

                    @Override
                    public void onContent(org.eclipse.jetty.client.api.Response response, ByteBuffer content) {
                        bytesReceived.addAndGet(content.remaining());
                    }

                    @Override
                    public void onComplete(Result result) {
                        log.info("Client response complete " + result);
                        if (result.getFailure() != null) {
                            log.warn("Client response failure", result.getFailure());
                        }
                        log.info("Got " + (bytesReceived.get() / 1_000_000) + "mb");
                        clientResult.set(result);
                        latch.countDown();
                    }
                });


            byte[] ones = new byte[chunkSize];
            Arrays.fill(ones, (byte) 1);
            for (int i = 0; i < loops; i++) {
                uploadOutStream.write(ones);
                uploadOutStream.flush();
            }
            uploadOutStream.close();
            assertNotTimedOut("Response complete", latch);
            assertThat(bytesReceived.get(), equalTo(loops * (long)chunkSize));
            assertThat(clientResult.get().getFailure(), nullValue());
            assertThat(clientResult.get().getResponse().getStatus(), is(200));
        }


    }


    @Test
    public void requestBodiesCanBeReadAsInputStreams() throws IOException {
        server = ServerUtils.httpsServerForTest()
            .addHandler((request, response) -> {
                try (InputStream is = request.inputStream().orElseThrow(() -> new MuException("No input stream"))) {
                    Mutils.copy(is, response.outputStream(), 8192);
                }
                return true;
            })
            .start();

        Request.Builder request = request()
            .url(server.uri().toString())
            .post(new SlowBodySender(10));

        try (Response resp = call(request)) {
            assertThat(resp.code(), equalTo(200));
            assertThat(resp.body().string(), equalTo("Loop 0\nLoop 1\nLoop 2\nLoop 3\nLoop 4\nLoop 5\nLoop 6\nLoop 7\nLoop 8\nLoop 9\n"));
        }
    }


    @Test
    public void canReadByteByByte() throws IOException {
        server = ServerUtils.httpsServerForTest()
            .addHandler((request, response) -> {
                try (InputStream is = request.inputStream().orElseThrow(() -> new MuException("No input stream"))) {
                    int val;
                    while ((val = is.read()) > -1) {
                        response.sendChunk("" + ((char) val));
                    }
                }
                return true;
            })
            .start();

        Request.Builder request = request()
            .url(server.uri().toString())
            .post(new SlowBodySender(10));

        try (Response resp = call(request)) {
            assertThat(resp.code(), equalTo(200));
            assertThat(resp.body().string(), equalTo("Loop 0\nLoop 1\nLoop 2\nLoop 3\nLoop 4\nLoop 5\nLoop 6\nLoop 7\nLoop 8\nLoop 9\n"));
        }
    }

    @Test
    public void closingTheStreamEarlyCancelsRequest() throws IOException {
        server = ServerUtils.httpsServerForTest()
            .addHandler((request, response) -> {
                try (InputStream is = request.inputStream().orElseThrow(() -> new MuException("No input stream"))) {
                    is.read();
                    is.read();
                }
                return true;
            })
            .start();

        Request.Builder request = request()
            .url(server.uri().toString())
            .post(new SlowBodySender(10));

        try (Response resp = call(request)) {
            resp.body().bytes();
            Assert.fail("What's supposed to happen?");
        }
    }

    @Test
    public void readingAfterFinishedThrows() throws IOException {
        server = ServerUtils.httpsServerForTest()
            .addHandler((request, response) -> {
                try (InputStream is = request.inputStream().orElseThrow(() -> new MuException("No input stream"))) {
                    while (is.read() > -1) {
                    }
                    response.write("After it was already closed it returned " + is.read());
                }
                return true;
            })
            .start();

        Request.Builder request = request()
            .url(server.uri().toString())
            .post(new SlowBodySender(10));

        try (Response resp = call(request)) {
            assertThat(resp.body().string(), equalTo("After it was already closed it returned -1"));
        }
    }


    @After
    public void destroy() {
        MuAssert.stopAndCheck(server);
    }

}