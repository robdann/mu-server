package ronin.muserver.rest;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.After;
import org.junit.Test;
import ronin.muserver.MuServer;
import ronin.muserver.Mutils;
import scaffolding.ClientUtils;
import scaffolding.StringUtils;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.util.concurrent.CountDownLatch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static ronin.muserver.MuServerBuilder.httpsServer;
import static scaffolding.ClientUtils.call;

public class BinaryEntityProvidersTest {
    private MuServer server;

    @Test
    public void byteArraysSupported() throws Exception {
        @Path("samples")
        class Sample {
            @POST
            @Produces("application/octet-stream")
            public byte[] echo(byte[] value) {
                return value;
            }
        }
        startServer(new Sample());
        check(StringUtils.randomBytes(64 * 1024));
        checkNoBody();
    }
    @Test
    public void inputStreamsSupported() throws Exception {
        @Path("samples")
        class Sample {
            @POST
            @Produces("application/octet-stream")
            public byte[] echo(InputStream body) throws IOException {
                return Mutils.toByteArray(body, 2048);
            }
        }
        startServer(new Sample());
        check(StringUtils.randomBytes(64 * 1024));
        checkNoBody();
    }
    @Test
    public void filesSupported() throws Exception {
        File sample = new File("src/test/resources/sample-static/images/friends.jpg");
        if (!sample.isFile()) {
            throw new RuntimeException("Expected " + sample.getCanonicalPath() + " to be a file");
        }
        @Path("samples")
        class Sample {
            @POST
            @Produces("image/jpeg")
            public File echo(File value) {
                return value;
            }
        }
        startServer(new Sample());
        check(Mutils.toByteArray(new FileInputStream(sample), 8192), "image/jpeg");
        checkNoBody();
    }

    @Test
    public void streamingOutputSupported() throws IOException {
        @Path("samples")
        class Sample {
            @POST
            @Produces("application/octet-stream")
            public StreamingOutput echo(byte[] input) {
                return output -> {
                    System.out.println("I am seeing " + input.length + " bytes: " + new String(input, UTF_8));
                    output.write(input);
                };
            }
        }
        startServer(new Sample());
        check(StringUtils.randomStringOfLength(32 * 1024).getBytes(UTF_8));
        checkNoBody();
    }

    @Test
    public void streamingOutputReallyStreams() throws IOException {
        StringBuilder errors = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        @Path("samples")
        class Sample {
            @GET
            @Produces("text/plain")
            public StreamingOutput streamStuff() {
                return output -> {
                    try {
                        output.write("This is message one...".getBytes(UTF_8));
                        output.flush();
                        latch.await();
                        output.write("... and this is message two".getBytes(UTF_8));
                    } catch (Exception e) {
                        errors.append(e.toString());
                    }

                };
            }
        }
        startServer(new Sample());

        try (Response resp = call(ClientUtils.request().url(server.uri().resolve("/samples").toString()));
             Reader reader = resp.body().charStream()) {

            StringBuilder output = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) > -1) {
                output.append(buffer, 0, read);
                if (output.toString().equals("This is message one...")) {
                    latch.countDown();
                }
            }
            assertThat(output.toString(), equalTo("This is message one..." + "... and this is message two"));
        }


        assertThat(errors.toString(), equalTo(""));
    }


    private void check(byte[] value) throws IOException {
        check(value, "application/octet-stream");
    }

    private void check(byte[] value, String mimeType) throws IOException {
        Response resp = call(ClientUtils.request()
            .post(RequestBody.create(MediaType.parse(mimeType), value))
            .url(server.uri().resolve("/samples").toString())
        );
        assertThat(resp.code(), equalTo(200));
        assertThat(resp.header("Content-Type"), equalTo(mimeType));
        byte[] actual = resp.body().bytes();
        assertThat("Expected " + value.length + " bytes; got " + actual.length, new String(actual, UTF_8), equalTo(new String(value, UTF_8)));
    }

    private void checkNoBody() throws IOException {
        Response resp = call(ClientUtils.request()
            .post(RequestBody.create(MediaType.parse("text/plain"), ""))
            .url(server.uri().resolve("/samples").toString())
        );
        assertThat(resp.code(), equalTo(400));
        assertThat(resp.body().string(), equalTo("400 Bad Request"));

    }


    private void startServer(Object restResource) {
        this.server = httpsServer().addHandler(new RestHandler(restResource)).start();
    }

    @After
    public void stop() {
        if (server != null) server.stop();
    }

}