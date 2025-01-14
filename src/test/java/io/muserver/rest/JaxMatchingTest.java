package io.muserver.rest;

import io.muserver.MuServer;
import okhttp3.Response;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Test;
import scaffolding.MuAssert;
import scaffolding.ServerUtils;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.io.IOException;
import java.net.URI;

import static io.muserver.rest.RestHandlerBuilder.restHandler;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static scaffolding.ClientUtils.call;
import static scaffolding.ClientUtils.request;
import static scaffolding.ServerUtils.httpsServerForTest;

public class JaxMatchingTest {
    private MuServer server;

    @Test
    public void canAccessClassPathParamsInMethod() throws IOException {
        @Path("/{thing : [a-z]+}")
        class Thing {
            @GET
            public String get(@PathParam("thing") String thing) {
                return thing;
            }
        }
        server = ServerUtils.httpsServerForTest()
            .addHandler(RestHandlerBuilder.restHandler(new Thing()).build())
            .start();
        try (Response resp = call(request().url(server.uri().resolve("/tiger").toString()))) {
            assertThat(resp.body().string(), is("tiger"));
        }
        try (Response resp = call(request().url(server.uri().resolve("/TIGER").toString()))) {
            assertThat(resp.code(), is(404));
        }
    }

    @Test
    public void pathsCanRepeatParameters() throws IOException {
        @Path("/{thing : [a-z]+}/{thing}")
        class Thing {
            @GET
            @Path("/{thing}/{thing}")
            public String get(@PathParam("thing") String thing) {
                return thing;
            }
        }
        server = ServerUtils.httpsServerForTest()
            .addHandler(RestHandlerBuilder.restHandler(new Thing()).build())
            .start();
        try (Response resp = call(request(server.uri().resolve("/tiger/tiger/tiger/tiger")))) {
            assertThat(resp.body().string(), is("tiger")); // uppercut
        }
        try (Response resp = call(request(server.uri().resolve("/TIGER/TIGER/TIGER/TIGER")))) {
            assertThat(resp.code(), is(404));
        }
        try (Response resp = call(request(server.uri().resolve("/tiger/TIGER/TIGER/TIGER")))) {
            assertThat(resp.code(), is(404));
        }
        try (Response resp = call(request(server.uri().resolve("/dog/tiger/tiger/tiger")))) {
            assertThat(resp.code(), is(404));
        }
        try (Response resp = call(request(server.uri().resolve("/tiger/tiger/tiger/dog")))) {
            assertThat(resp.code(), is(404));
        }
    }

    @Test
    public void differentMethodsCanHaveDifferentRegexes() throws IOException {
        @Path("/api")
        class Thing {
            @GET
            @Path("/{id : \\d+}")
            public String get(@PathParam("id") int id) {
                return "got " + id;
            }
            @DELETE
            @Path("{id}")
            public String delete(@PathParam("id") String id) {
                return "deleted " + id;
            }
        }
        server = ServerUtils.httpsServerForTest()
            .addHandler(RestHandlerBuilder.restHandler(new Thing()).build())
            .start();
        try (Response resp = call(request(server.uri().resolve("/api/123")))) {
            assertThat(resp.body().string(), is("got 123"));
        }
        try (Response resp = call(request(server.uri().resolve("/api/hello")))) {
            assertThat(resp.code(), is(405)); // because DELETE is matched
        }

        try (Response resp = call(request(server.uri().resolve("/api/hmmm")).delete())) {
            assertThat(resp.body().string(), is("deleted hmmm"));
        }
        try (Response resp = call(request(server.uri().resolve("/api/123")).delete())) {
            // TODO is this actually expected?
            assertThat(resp.code(), is(405)); // because it matches the more specific integer regex which only has GET
        }
    }

    @Test
    public void partialMatchesAreNotIncluded() {
        @Path("/runners")
        class Runners {
            @GET
            @Path("/{id}")
            public void id() {}
        }
        server = ServerUtils.httpsServerForTest()
            .addHandler(RestHandlerBuilder.restHandler(new Runners()))
            .start();
        try (Response resp = call(request(server.uri().resolve("/runners/myrunner/system")))) {
            assertThat(resp.code(), is(404));
        }
    }


    @Test
    public void matrixParametersMatchDefaultPathParamRegex() throws IOException {
        @Path("/blah/{thing}")
        class Thing {
            @GET
            @Path("/something")
            public String get(@PathParam("thing") String thing) {
                return thing;
            }
        }
        server = ServerUtils.httpsServerForTest()
            .addHandler(RestHandlerBuilder.restHandler(new Thing()).build())
            .start();
        URI matrixUri = server.uri().resolve("/blah;ignored=true/tiger;color=red;type=cat/something;ignored=true");
        try (Response resp = call(request().url(matrixUri.toString()))) {
            assertThat(resp.body().string(), is("tiger"));
        }
    }

    @Test
    public void pathParamsUseTheFinalPathParam() throws Exception {
        @Path("/customers/{id}")
        class CustomerResource {
            @GET
            @Path("/address/{id}")
            public String getAddress(@PathParam("id") String addressId) {return addressId;}
        }
        this.server = httpsServerForTest().addHandler(restHandler(new CustomerResource()).build()).start();
        try (Response resp = call(request(server.uri().resolve("/customers/123/address/456")))) {
            assertThat(resp.code(), Matchers.is(200));
            assertThat(resp.body().string(), containsString("456"));
        }
    }

    @After
    public void stopIt() {
        MuAssert.stopAndCheck(server);
    }


}