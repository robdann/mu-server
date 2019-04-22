package io.muserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;

class Http2Response extends NettyResponseAdaptor {
    private final ChannelHandlerContext ctx;
    private final H2Headers headers;
    private final Http2ConnectionEncoder encoder;
    private final int streamId;

    Http2Response(ChannelHandlerContext ctx, NettyRequestAdapter request, H2Headers headers, Http2ConnectionEncoder encoder, int streamId) {
        super(request, headers);
        this.ctx = ctx;
        this.headers = headers;
        this.encoder = encoder;
        this.streamId = streamId;
    }

    @Override
    protected ChannelFuture writeToChannel(boolean isLast, ByteBuf content) {
        return writeToChannel(ctx, encoder, streamId, content, isLast);
    }

    static ChannelFuture writeToChannel(ChannelHandlerContext ctx, Http2ConnectionEncoder encoder, int streamId, ByteBuf content, boolean isLast) {
        ChannelPromise channelPromise = ctx.newPromise();
        if (ctx.executor().inEventLoop()) {
            encoder.writeData(ctx, streamId, content, 0, isLast, channelPromise);
        } else {
            ctx.executor().execute(() -> encoder.writeData(ctx, streamId, content, 0, isLast, channelPromise));
        }
        ctx.channel().flush();
        return channelPromise;
    }

    @Override
    protected boolean onBadRequestSent() {
        return false; // the stream is bad, but the connection is fine. Doesn't matter.
    }

    @Override
    protected void startStreaming() {
        super.startStreaming();
        writeHeaders(false);
    }

    private void writeHeaders(boolean isEnd) {
        headers.entries.status(httpStatus().codeAsText());
        encoder.writeHeaders(ctx, streamId, headers.entries, 0, isEnd, ctx.newPromise());
        if (isEnd) {
            ctx.channel().flush();
        }
    }

    @Override
    protected void writeFullResponse(ByteBuf body) {
        writeHeaders(false);
        writeToChannel(true, body);
    }

    @Override
    protected boolean connectionOpen() {
        return ctx.channel().isOpen();
    }

    @Override
    protected ChannelFuture closeConnection() {
        return ctx.channel().close();
    }

    @Override
    protected ChannelFuture writeLastContentMarker() {
        return writeToChannel(true, Unpooled.directBuffer(0));
    }

    @Override
    protected void sendEmptyResponse(boolean addContentLengthHeader) {
        if (addContentLengthHeader) {
            headers.set(HeaderNames.CONTENT_LENGTH, HeaderValues.ZERO);
        }
        writeHeaders(true);
    }


    @Override
    protected void writeRedirectResponse() {
        writeHeaders(true);
    }
}
