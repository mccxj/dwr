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
package org.directwebremoting.servlet;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.directwebremoting.util.FakeHttpServletRequestFactory;
import org.directwebremoting.util.FakeHttpServletResponse;
import org.directwebremoting.util.FakeServletConfig;
import org.directwebremoting.util.FakeServletContextFactory;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Joe Walker [joe at getahead dot ltd dot uk]
 */
public class DwrServletTest
{
    @Test
    @Ignore // TODO: need to adjust dirs (which WEB-INF dir is supposed to be used here???)
    public void doGet() throws Exception
    {
        DwrServlet servlet = new DwrServlet();

        ServletContext servletContext = FakeServletContextFactory.create("test/web");
        FakeServletConfig config = new FakeServletConfig("dwr-invoker", servletContext);
        servlet.init(config);

        HttpServletRequest request = FakeHttpServletRequestFactory.create();
        HttpServletResponse response = new FakeHttpServletResponse();
        servlet.doPost(request, response);
    }
}
