/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.peeco.showcase.proxyGeneration;

import org.apache.peeco.impl.PeecoExtension;
import org.apache.webbeans.component.ExtensionBean;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.service.ClassLoaderProxyService;
import org.apache.webbeans.spi.DefiningClassService;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.PassivationCapable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.stream.Collectors.joining;

public class ProxyGenerator
{
    public static void main(String[] args)
    {
        try
        {
            PeecoExtension.disable();
            final SeContainer container = SeContainerInitializer.newInstance()
                    .addProperty(DefiningClassService.class.getName(), ClassLoaderProxyService.Spy.class.getName()) // no unsafe usage
                    .addProperty("org.apache.webbeans.proxy.useStaticNames", "true") // a bit unsafe but otherwise no way to get pregenerated proxies
                    .initialize();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }

        ProxyGenerator proxyGenerator = new ProxyGenerator();
        proxyGenerator.run();
    }

    public void run()
    {
        //TODO: specify some working directory to output the proxy classes to
        final Path out = Paths.get("");
        if (!Files.exists(out))
        {
            throw new IllegalStateException("You should run compile before this task, missing: " + out);
        }

        BeanManager beanManager = CDI.current().getBeanManager();

        beanManager.getBeans(Object.class).stream()
                .filter(b -> beanManager.isNormalScope(b.getScope()) && !isIgnoredBean(b)) // todo: do it also for interception
                .forEach(it ->
                {
                    try
                    { // triggers the proxy creation
                        beanManager.getReference(it, Object.class, beanManager.createCreationalContext(null));
                    }
                    catch (final Exception ex)
                    {
                        ex.printStackTrace();
                    }
                });

        final String config = ClassLoaderProxyService.Spy.class.cast(WebBeansContext.currentInstance().getService(DefiningClassService.class))
                .getProxies().entrySet().stream()
                .map(e ->
                {
                    final Path target = out.resolve(e.getKey().replace('.', '/') + ".class");
                    try
                    {
                        Files.createDirectories(target.getParent());
                        Files.write(target, e.getValue());
                    }
                    catch (final IOException ex)
                    {
                        throw new IllegalStateException(ex);
                    }
                    System.out.println("Created proxy '{" + e.getKey() + "}'" );
                    return "<reflection>\n" +
                            "<name>" + e.getKey().replace("$$", "$$$$") + "</name>\n" +
                            "<allDeclaredConstructors>true</allDeclaredConstructors>\n" +
                            "<allDeclaredMethods>true</allDeclaredMethods>\n" +
                            "<allDeclaredFields>true</allDeclaredFields>\n" +
                            "</reflection>";
                })
                .collect(joining("\n"));
    }


    private boolean isIgnoredBean(final Bean<?> b)
    { // we don't want a proxy for java.util.Set
        return (PassivationCapable.class.isInstance(b) && "apache.openwebbeans.OwbInternalConversationStorageBean".equals(PassivationCapable.class.cast(b).getId())) ||
                ExtensionBean.class.isInstance(b) /*not needed*/;
    }
}
