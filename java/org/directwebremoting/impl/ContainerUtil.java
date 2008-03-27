/*
 * Copyright 2005 Joe Walker
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
package org.directwebremoting.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.xml.parsers.ParserConfigurationException;

import org.directwebremoting.AccessControl;
import org.directwebremoting.AjaxFilterManager;
import org.directwebremoting.Configurator;
import org.directwebremoting.Container;
import org.directwebremoting.ConverterManager;
import org.directwebremoting.Creator;
import org.directwebremoting.CreatorManager;
import org.directwebremoting.DebugPageGenerator;
import org.directwebremoting.Remoter;
import org.directwebremoting.ScriptSessionManager;
import org.directwebremoting.ServerContextBuilder;
import org.directwebremoting.ServerLoadMonitor;
import org.directwebremoting.WebContextBuilder;
import org.directwebremoting.dwrp.DefaultConverterManager;
import org.directwebremoting.dwrp.DwrpHtmlJsMarshaller;
import org.directwebremoting.dwrp.DwrpPlainJsMarshaller;
import org.directwebremoting.servlet.UrlProcessor;
import org.directwebremoting.util.LocalUtil;
import org.directwebremoting.util.Logger;
import org.directwebremoting.util.VersionUtil;
import org.xml.sax.SAXException;

/**
 * An abstraction of all the common servlet operations that are required to host
 * a DWR service that depends on the servlet spec.
 * It would be good to have a base class for all servlet operations, however
 * lack of MI prevents us from doing this.
 * @author Joe Walker [joe at getahead dot ltd dot uk]
 */
public class ContainerUtil
{
    /**
     * Take a DefaultContainer and setup the default beans
     * @param defaultContainer The container to configure
     * @throws InstantiationException If we can't instantiate a bean
     * @throws IllegalAccessException If we have access problems creating a bean
     */
    public static void setupDefaults(DefaultContainer defaultContainer) throws InstantiationException, IllegalAccessException
    {
        defaultContainer.addParameter(AccessControl.class.getName(), DefaultAccessControl.class.getName());
        defaultContainer.addParameter(ConverterManager.class.getName(), DefaultConverterManager.class.getName());
        defaultContainer.addParameter(CreatorManager.class.getName(), DefaultCreatorManager.class.getName());
        defaultContainer.addParameter(UrlProcessor.class.getName(), UrlProcessor.class.getName());
        defaultContainer.addParameter(WebContextBuilder.class.getName(), DefaultWebContextBuilder.class.getName());
        defaultContainer.addParameter(ServerContextBuilder.class.getName(), DefaultServerContextBuilder.class.getName());
        defaultContainer.addParameter(AjaxFilterManager.class.getName(), DefaultAjaxFilterManager.class.getName());
        defaultContainer.addParameter(Remoter.class.getName(), DefaultRemoter.class.getName());
        defaultContainer.addParameter(DebugPageGenerator.class.getName(), DefaultDebugPageGenerator.class.getName());
        defaultContainer.addParameter(DwrpHtmlJsMarshaller.class.getName(), DwrpHtmlJsMarshaller.class.getName());
        defaultContainer.addParameter(DwrpPlainJsMarshaller.class.getName(), DwrpPlainJsMarshaller.class.getName());
        defaultContainer.addParameter(ScriptSessionManager.class.getName(), DefaultScriptSessionManager.class.getName());
        defaultContainer.addParameter(ServerLoadMonitor.class.getName(), DefaultServerLoadMonitor.class.getName());

        defaultContainer.addParameter("debug", "false"); //$NON-NLS-1$ //$NON-NLS-2$
        defaultContainer.addParameter("allowImpossibleTests", "false"); //$NON-NLS-1$ //$NON-NLS-2$
        defaultContainer.addParameter("scriptCompressed", "false"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Take a DefaultContainer and setup the default beans
     * @param defaultContainer The container to configure
     * @param servletConfig The servlet configuration (null to ignore)
     * @throws InstantiationException If we can't instantiate a bean
     * @throws IllegalAccessException If we have access problems creating a bean
     */
    public static void setupFromServletConfig(DefaultContainer defaultContainer, ServletConfig servletConfig) throws InstantiationException, IllegalAccessException
    {
        Enumeration en = servletConfig.getInitParameterNames();
        while (en.hasMoreElements())
        {
            String name = (String) en.nextElement();
            String value = servletConfig.getInitParameter(name);
            defaultContainer.addParameter(name, value);
        }
    }

    /**
     * 
     * @param config
     * @param container
     * @param webContextBuilder
     * @param servlet
     */
    public static void prepareForWebContextFilter(ServletConfig config, Container container, WebContextBuilder webContextBuilder, HttpServlet servlet)
    {
        ServletContext context = config.getServletContext();

        context.setAttribute(Container.class.getName(), container);
        context.setAttribute(WebContextBuilder.class.getName(), webContextBuilder);
        context.setAttribute(ServletConfig.class.getName(), config);
        context.setAttribute(HttpServlet.class.getName(), servlet);
    }

    /**
     * Add configurators from init params to the end of the list of
     * configurators.
     * @param container The container to configure
     * @param servletConfig The source of init parameters
     * @return true if any Configurators were read
     * @throws SAXException If the config file parse fails
     * @throws ParserConfigurationException If the config file parse fails
     * @throws IOException If the config file read fails
     */
    public static boolean configureUsingInitParams(Container container, ServletConfig servletConfig) throws IOException, ParserConfigurationException, SAXException
    {
        Enumeration en = servletConfig.getInitParameterNames();
        boolean foundConfig = false;
        while (en.hasMoreElements())
        {
            String name = (String) en.nextElement();
            String value = servletConfig.getInitParameter(name);

            // if the init param starts with "config" then try to load it
            if (name.startsWith(INIT_CONFIG))
            {
                foundConfig = true;

                StringTokenizer st = new StringTokenizer(value, "\n,"); //$NON-NLS-1$
                while (st.hasMoreTokens())
                {
                    String fileName = st.nextToken().trim();
                    DwrXmlConfigurator local = new DwrXmlConfigurator();
                    local.setServletResourceName(fileName);
                    local.configure(container);
                }
            }
            else if (name.equals(INIT_CUSTOM_CONFIGURATOR))
            {
                foundConfig = true;

                try
                {
                    Configurator configurator = (Configurator) LocalUtil.classNewInstance(INIT_CUSTOM_CONFIGURATOR, value, Configurator.class);
                    configurator.configure(container);
                    log.debug("Loaded config from: " + value); //$NON-NLS-1$
                }
                catch (Exception ex)
                {
                    log.warn("Failed to start custom configurator", ex); //$NON-NLS-1$
                }
            }
        }

        return foundConfig;
    }

    /**
     * Allow all the configurators to have a go at the container in turn
     * @param container The container to configure
     * @param configurators A list of configurators to run against the container
     */
    public static void configure(Container container, List configurators)
    {
        // Allow all the configurators to have a go
        for (Iterator it = configurators.iterator(); it.hasNext();)
        {
            Configurator configurator = (Configurator) it.next();

            log.debug("** Adding config from " + configurator); //$NON-NLS-1$
            configurator.configure(container);
        }
    }

    /**
     * If helps some situations if people can get at the container by looking
     * in the servlet context, under some name.
     * The name is specified in an initParameter. 
     * @param container The container to publish
     * @param config Source of initParams to dictate publishing and contexts to publish to
     */
    public static void publishContainer(Container container, ServletConfig config)
    {
        String publishName = config.getInitParameter(INIT_PUBLISH_CONTAINER);
        if (publishName != null)
        {
            config.getServletContext().setAttribute(publishName, container);
        }
    }

    /**
     * Some logging so we have a good clue what we are working with.
     * @param config The servlet config
     */
    public static void logStartup(ServletConfig config)
    {
        log.info("DWR Version " + VersionUtil.getVersion() + " starting."); //$NON-NLS-1$ //$NON-NLS-2$
        log.info("- Servlet Engine: " + config.getServletContext().getServerInfo()); //$NON-NLS-1$
        log.info("- Java Version:   " + System.getProperty("java.version"));  //$NON-NLS-1$//$NON-NLS-2$
        log.info("- Java Vendor:    " + System.getProperty("java.vendor"));  //$NON-NLS-1$//$NON-NLS-2$
    }

    /**
     * Create a bunch of debug information about a container
     * @param container The container to print debug information about
     */
    public static void debugConfig(Container container)
    {
        if (log.isDebugEnabled())
        {
            // Container level debug
            log.debug("Container"); //$NON-NLS-1$
            log.debug("  Type: " + container.getClass().getName()); //$NON-NLS-1$
            Collection beanNames = container.getBeanNames();
            for (Iterator it = beanNames.iterator(); it.hasNext();)
            {
                String name = (String) it.next();
                Object object = container.getBean(name);

                if (object instanceof String)
                {
                    log.debug("  Param: " + name + " = " + object + " (" + object.getClass().getName() + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                }
                else
                {
                    log.debug("  Bean: " + name + " = " + object + " (" + object.getClass().getName() + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                }
            }

            // AccessControl debugging
            AccessControl accessControl = (AccessControl) container.getBean(AccessControl.class.getName());
            log.debug("AccessControl"); //$NON-NLS-1$
            log.debug("  Type: " + accessControl.getClass().getName()); //$NON-NLS-1$

            // AjaxFilterManager debugging
            AjaxFilterManager ajaxFilterManager = (AjaxFilterManager) container.getBean(AjaxFilterManager.class.getName());
            log.debug("AjaxFilterManager"); //$NON-NLS-1$
            log.debug("  Type: " + ajaxFilterManager.getClass().getName()); //$NON-NLS-1$

            // ConverterManager debugging
            ConverterManager converterManager = (ConverterManager) container.getBean(ConverterManager.class.getName());
            log.debug("ConverterManager"); //$NON-NLS-1$
            log.debug("  Type: " + converterManager.getClass().getName()); //$NON-NLS-1$

            // CreatorManager debugging
            CreatorManager creatorManager = (CreatorManager) container.getBean(CreatorManager.class.getName());
            log.debug("CreatorManager"); //$NON-NLS-1$
            log.debug("  Type: " + creatorManager.getClass().getName()); //$NON-NLS-1$
            Collection creatorNames = creatorManager.getCreatorNames();
            for (Iterator it = creatorNames.iterator(); it.hasNext();)
            {
                String creatorName = (String) it.next();
                Creator creator = creatorManager.getCreator(creatorName);
                log.debug("  Creator: " + creatorName + " = " + creator + " (" + creator.getClass().getName() + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            }
        }
    }

    /**
     * Init parameter: Set a dwr.xml config file.
     * This is only a prefix since we might have more than 1 config file.
     */
    public static final String INIT_CONFIG = "config"; //$NON-NLS-1$

    /**
     * Init parameter: Skip reading the default config file if none are specified.
     */
    public static final String INIT_SKIP_DEFAULT = "skipDefaultConfig"; //$NON-NLS-1$

    /**
     * Init parameter: If we are doing Servlet.log logging, to what level?
     */
    public static final String INIT_LOGLEVEL = "logLevel"; //$NON-NLS-1$

    /**
     * Init parameter: Should we publish the container to the servlet context,
     * and if so, under what name?
     */
    public static final String INIT_PUBLISH_CONTAINER = "publishContainerAs"; //$NON-NLS-1$

    /**
     * Init parameter: If you wish to use a custom configurator, place its
     * class name here
     */
    public static final String INIT_CUSTOM_CONFIGURATOR = "customConfigurator"; //$NON-NLS-1$

    /**
     * The log stream
     */
    private static final Logger log = Logger.getLogger(ContainerUtil.class);
}
