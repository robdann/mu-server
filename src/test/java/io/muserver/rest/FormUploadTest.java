package io.muserver.rest;

import io.muserver.MuServer;
import io.muserver.UploadedFile;
import okhttp3.*;
import org.junit.After;
import org.junit.Test;
import scaffolding.ServerUtils;

import javax.ws.rs.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static io.muserver.UploadTest.*;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static scaffolding.ClientUtils.call;
import static scaffolding.ClientUtils.request;

public class FormUploadTest {

    private MuServer server;

    @Test
    public void formParamsCanBeUploads() throws IOException {

        @Path("/images")
        class ImageResource {

            @POST
            @Consumes(javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA)
            public String create(@FormParam("Hello") String hello, @FormParam("image") UploadedFile file, @FormParam("The name") String theName) {
                String filename = file == null ? "null" : file.filename();
                long size = file == null ? -1 : file.size();
                return "Hello " + hello + " and The name=" + theName + " and filename is " + filename + " and size is " + size;
            }

        }

        server = ServerUtils.httpsServerForTest()
            .addHandler(RestHandlerBuilder.restHandler(new ImageResource()))
            .start();

        try (Response resp = call(request(server.uri().resolve("/images"))
            .post(new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("Hello", "World")
                .addFormDataPart("The name", "the value / with / stuff")
                .build())
        )) {
            assertThat(resp.code(), is(200));
            assertThat(resp.body().string(), is("Hello World and The name=the value / with / stuff and filename is null and size is -1"));
        }

        try (Response resp = call(request(server.uri().resolve("/images"))
            .post(new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("Hello", "World")
                .addFormDataPart("The name", "the value / with / stuff")
                .addPart(Headers.of("Content-Disposition", "form-data; name=\"image\"; filename=\"guangzhou.jpeg\""),
                    RequestBody.create(guangzhou, MediaType.parse("image/jpeg")))
                .build())
        )) {
            assertThat(resp.code(), is(200));
            assertThat(resp.body().string(), is("Hello World and The name=the value / with / stuff and filename is guangzhou.jpeg and size is 372987"));
        }

    }

    @Test
    public void largeFilesCanBeUsed() throws IOException {

        File temp = new File("target/testoutput/upload" + UUID.randomUUID() + ".txt");
        temp.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(temp)) {
            for (int i = 1; i < 954000; i++) {
                fw.write("TEST TEST\r\n");
            }
            fw.write("TEST TEST");
        }

        @Path("/images")
        class ImageResource {

            @POST
            @Consumes(javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA)
            @Produces("text/plain")
            public String create(@FormParam("metadata") String metadata, @FormParam("file") File file) {
                long size = file == null ? -1 : file.length();
                return "Hello " + metadata + " and size is " + size;
            }

        }

        server = ServerUtils.httpsServerForTest()
            .withMaxRequestSize(1024 * 1024 * 1024L)
            .addHandler(RestHandlerBuilder.restHandler(new ImageResource()))
            .start();

        try (Response resp = call(request(server.uri().resolve("/images"))
            .post(new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata", "some-sample-data")
                .addFormDataPart("file", temp.getName(), RequestBody.create(temp, MediaType.parse(APPLICATION_OCTET_STREAM)))
                .build())
        )) {
            assertThat(resp.code(), is(200));
            assertThat(resp.body().string(), is("Hello some-sample-data and size is 10493998"));
        }
    }

    @Test
    public void collectionsOfFilesAreAllowed() throws IOException {

        @Path("/images")
        class ImageResource {

            @POST
            @Consumes(javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA)
            public String create(@FormParam("image") List<UploadedFile> files) {
                String v = "";
                for (UploadedFile file : files) {
                    String filename = file == null ? "null" : file.filename();
                    long size = file == null ? -1 : file.size();
                    v += "filename is " + filename + " and size is " + size + "\n";
                }
                return v;
            }

        }

        server = ServerUtils.httpsServerForTest()
            .addHandler(RestHandlerBuilder.restHandler(new ImageResource()))
            .start();

        try (Response resp = call(request(server.uri().resolve("/images"))
            .post(new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("Hello", "World")
                .build())
        )) {
            assertThat(resp.code(), is(200));
            assertThat(resp.body().string(), is(""));
        }

        try (Response resp = call(request(server.uri().resolve("/images"))
            .post(new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(Headers.of("Content-Disposition", "form-data; name=\"image\"; filename=\"guangzhou, china.jpeg\""),
                    RequestBody.create(MediaType.parse("image/jpeg"), guangzhouChina))
                .addPart(Headers.of("Content-Disposition", "form-data; name=\"image\"; filename=\"friends.jpg\""),
                    RequestBody.create(MediaType.parse("image/jpeg"), friends))
                .build())
        )) {
            assertThat(resp.code(), is(200));
            assertThat(resp.body().string(), is("filename is guangzhou  china.jpeg and size is 372987\n" +
                "filename is friends.jpg and size is 1712954\n"));
        }

    }

    @After
    public void stopIt() {
        scaffolding.MuAssert.stopAndCheck(server);
    }

}
