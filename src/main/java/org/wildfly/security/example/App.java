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

import static org.wildfly.security.password.interfaces.ClearPassword.ALGORITHM_CLEAR;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.wildfly.elytron.web.undertow.server.ElytronContextAssociationHandler;
import org.wildfly.elytron.web.undertow.server.ElytronRunAsHandler;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.realm.SimpleRealmEntry;
import org.wildfly.security.auth.server.HttpAuthenticationFactory;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.MechanismConfigurationSelector;
import org.wildfly.security.auth.server.MechanismRealmConfiguration;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.http.HttpAuthenticationException;
import org.wildfly.security.http.HttpServerAuthenticationMechanismFactory;
import org.wildfly.security.http.impl.ServerMechanismFactoryImpl;
import org.wildfly.security.http.util.FilterServerMechanismFactory;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.permission.PermissionVerifier;
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
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.OpenListener;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.protocol.http.HttpOpenListener;

/**
 * Hello world!
 *
 */
public class App
{
    public static void main( String[] args ) throws Exception
    {
        Security.addProvider(new WildFlyElytronProvider());

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
        openListener.setRootHandler(new BlockingHandler(createRootHttpHandler()));
        ChannelListener acceptListener = ChannelListeners.openListenerAdapter(openListener);

        AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(new InetSocketAddress("localhost", 8181), acceptListener, serverOptions);

        server.resumeAccepts();
    }

    private static HttpHandler createRootHttpHandler() throws Exception {
        final SecurityDomain securityDomain = createSecurityDomain();
        final HttpAuthenticationFactory httpAuthenticationFactory = createHttpAuthenticationFactory(securityDomain);

        HttpHandler rootHandler = new ElytronRunAsHandler((new ResponseHandler(securityDomain)));

        rootHandler = new BlockingHandler(rootHandler);
        rootHandler = new AuthenticationCallHandler(rootHandler);
        rootHandler = new AuthenticationConstraintHandler(rootHandler);
        ElytronContextAssociationHandler.Builder elytronContextHandlerBuilder = ElytronContextAssociationHandler.builder()
                .setNext(rootHandler)
                .setMechanismSupplier(() -> httpAuthenticationFactory.getMechanismNames().stream()
                        .map(mechanismName -> {
                            try {
                                return httpAuthenticationFactory.createMechanism(mechanismName);
                            } catch (HttpAuthenticationException e) {
                                throw new RuntimeException("Failed to create mechanism.", e);
                            }
                        })
                        .filter(m -> m != null)
                        .collect(Collectors.toList()));
        rootHandler = elytronContextHandlerBuilder.build();

        return rootHandler;
    }

    private static SecurityDomain createSecurityDomain() throws GeneralSecurityException {
        PasswordFactory passwordFactory = PasswordFactory.getInstance(ALGORITHM_CLEAR);

        Map<String, SimpleRealmEntry> passwordMap = new HashMap<>();
        passwordMap.put("elytron", new SimpleRealmEntry(Collections.singletonList(new PasswordCredential(passwordFactory.generatePassword(new ClearPasswordSpec("Coleoptera".toCharArray()))))));

        SimpleMapBackedSecurityRealm simpleRealm = new SimpleMapBackedSecurityRealm();
        simpleRealm.setPasswordMap(passwordMap);

        SecurityDomain.Builder builder = SecurityDomain.builder()
                .setDefaultRealmName("TestRealm");

        builder.addRealm("TestRealm", simpleRealm).build();
        builder.setPermissionMapper((principal, roles) -> PermissionVerifier.from(new LoginPermission()));

       return builder.build();
    }

    private static HttpAuthenticationFactory createHttpAuthenticationFactory(final SecurityDomain securityDomain) throws Exception {
        HttpServerAuthenticationMechanismFactory factory = new FilterServerMechanismFactory(new ServerMechanismFactoryImpl(), true, "BASIC");

        return HttpAuthenticationFactory.builder()
                .setSecurityDomain(securityDomain)
                .setMechanismConfigurationSelector(MechanismConfigurationSelector.constantSelector(
                        MechanismConfiguration.builder()
                                .addMechanismRealm(MechanismRealmConfiguration.builder().setRealmName("Elytron Realm").build())
                                .build()))
                .setFactory(factory)
                .build();
    }

}
