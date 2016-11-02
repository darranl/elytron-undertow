/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.security.example;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;

import io.undertow.UndertowOptions;
import io.undertow.server.OpenListener;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.protocol.http.HttpOpenListener;

/**
 * Hello world!
 *
 */
public class App
{
    public static void main( String[] args ) throws IOException
    {
        System.out.println("Starting Server");
        // Stolen directly from Undertow ;-)
        Xnio xnio = Xnio.getInstance("nio", App.class.getClassLoader());
        XnioWorker worker = xnio.createWorker(OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 8)
                .set(Options.CONNECTION_HIGH_WATER, 1000000)
                .set(Options.CONNECTION_LOW_WATER, 1000000)
                .set(Options.WORKER_TASK_CORE_THREADS, 30)
                .set(Options.WORKER_TASK_MAX_THREADS, 30)
                .set(Options.TCP_NODELAY, true)
                .set(Options.CORK, true)
                .getMap());

        OptionMap serverOptions = OptionMap.builder()
                .set(Options.TCP_NODELAY, true)
                .set(Options.BACKLOG, 1000)
                .set(Options.REUSE_ADDRESSES, true)
                .set(Options.BALANCING_TOKENS, 1)
                .set(Options.BALANCING_CONNECTIONS, 2)
                .getMap();

        ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 8192, 8192 * 8192);

        OpenListener openListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true,
                UndertowOptions.ENABLE_CONNECTOR_STATISTICS, true));
        openListener.setRootHandler(new BlockingHandler(new ResponseHandler()));
        ChannelListener acceptListener = ChannelListeners.openListenerAdapter(openListener);

        AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(new InetSocketAddress("localhost", 8181), acceptListener, serverOptions);

        server.resumeAccepts();
    }


}
