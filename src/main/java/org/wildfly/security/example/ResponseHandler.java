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

import java.io.OutputStream;
import java.io.PrintWriter;

import org.wildfly.security.auth.server.SecurityDomain;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * A simple {@link HttpHandler} to return a response to the calling client.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ResponseHandler implements HttpHandler {

    private final SecurityDomain securityDomain;

    ResponseHandler(final SecurityDomain securityDomain) {
        this.securityDomain = securityDomain;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        OutputStream outputStream = exchange.getOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);
        printWriter.println("<html>");
        printWriter.println("    <head><title>Programatic WildFly Elytron with Undertow.</title></head>");
        printWriter.println("    <body>");
        printWriter.println("        <h1>Programatic WildFly Elytron with Undertow.</h1>");
        printWriter.println(String.format("<p>Current Identity = '%s'</p>", securityDomain.getCurrentSecurityIdentity().getPrincipal().getName()));
        printWriter.println("    </body>");
        printWriter.println("</html>");
        printWriter.close();

        exchange.endExchange();
    }

}
