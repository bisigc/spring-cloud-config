/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.config.server.environment;

import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.client.HttpClient;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.apache.HttpClientConnection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.config.EnvironmentRepositoryConfiguration;
import org.springframework.cloud.config.server.proxy.ProxyHostProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;

/**
 * @author Dylan Roberts
 */
public class ConfigurableHttpConnectionFactoryIntegrationTests {
    private static final ProxyHostProperties AUTHENTICATED_HTTP_PROXY = new ProxyHostProperties();
    static {
        AUTHENTICATED_HTTP_PROXY.setHost("http://authenticated.http.proxy");
        AUTHENTICATED_HTTP_PROXY.setPort(8080);
        AUTHENTICATED_HTTP_PROXY.setUsername("username");
        AUTHENTICATED_HTTP_PROXY.setPassword("password");
    }
    private static final ProxyHostProperties AUTHENTICATED_HTTPS_PROXY = new ProxyHostProperties();
    static {
        AUTHENTICATED_HTTPS_PROXY.setHost("http://authenticated.https.proxy");
        AUTHENTICATED_HTTPS_PROXY.setPort(8081);
        AUTHENTICATED_HTTPS_PROXY.setUsername("username2");
        AUTHENTICATED_HTTPS_PROXY.setPassword("password2");
    }
    private static final ProxyHostProperties HTTP_PROXY = new ProxyHostProperties();
    static {
        HTTP_PROXY.setHost("http://http.proxy");
        HTTP_PROXY.setPort(8080);
    }
    private static final ProxyHostProperties HTTPS_PROXY = new ProxyHostProperties();
    static {
        HTTPS_PROXY.setHost("http://https.proxy");
        HTTPS_PROXY.setPort(8081);
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void authenticatedHttpsProxy() throws Exception {
        String repoUrl = "https://myrepo/repo.git";
        new SpringApplicationBuilder(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(gitProperties(repoUrl, null, AUTHENTICATED_HTTPS_PROXY))
                .run();
        HttpClient httpClient = getHttpClientForUrl(repoUrl);
        expectedException.expectCause(allOf(
                instanceOf(UnknownHostException.class),
                hasProperty("message", containsString(AUTHENTICATED_HTTPS_PROXY.getHost()))));

        makeRequest(httpClient, "https://somehost");
    }

    @Test
    public void httpsProxy() throws Exception {
        String repoUrl = "https://myrepo/repo.git";
        new SpringApplicationBuilder(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(gitProperties(repoUrl, null, HTTPS_PROXY))
                .run();
        HttpClient httpClient = getHttpClientForUrl(repoUrl);
        expectedException.expectCause(allOf(
                instanceOf(UnknownHostException.class),
                hasProperty("message", containsString(HTTPS_PROXY.getHost()))));

        makeRequest(httpClient, "https://somehost");
    }

    @Test
    public void httpsProxy_placeholderUrl() throws Exception {
        new SpringApplicationBuilder(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(gitProperties("https://myrepo/{placeholder1}/{placeholder2}-repo.git", null, HTTPS_PROXY))
                .run();
        HttpClient httpClient = getHttpClientForUrl("https://myrepo/someplaceholdervalue/anotherplaceholdervalue-repo.git");
        expectedException.expectCause(allOf(
                instanceOf(UnknownHostException.class),
                hasProperty("message", containsString(HTTPS_PROXY.getHost()))));

        makeRequest(httpClient, "https://somehost");
    }

    @Test
    public void httpsProxy_notCalled() throws Exception {
        String repoUrl = "https://myrepo/repo.git";
        new SpringApplicationBuilder(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(gitProperties(repoUrl, null, HTTPS_PROXY))
                .run();
        HttpClient httpClient = getHttpClientForUrl(repoUrl);
        expectedException.expectCause(allOf(
                instanceOf(UnknownHostException.class),
                hasProperty("message", containsString("somehost"))));

        makeRequest(httpClient, "http://somehost");
    }

    @Test
    public void authenticatedHttpProxy() throws Exception {
        String repoUrl = "https://myrepo/repo.git";
        new SpringApplicationBuilder(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(gitProperties(repoUrl, AUTHENTICATED_HTTP_PROXY, null))
                .run();
        HttpClient httpClient = getHttpClientForUrl(repoUrl);
        expectedException.expectCause(allOf(
                instanceOf(UnknownHostException.class),
                hasProperty("message", containsString(AUTHENTICATED_HTTP_PROXY.getHost()))));

        makeRequest(httpClient, "http://somehost");
    }

    @Test
    public void httpProxy() throws Exception {
        String repoUrl = "https://myrepo/repo.git";
        new SpringApplicationBuilder(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(gitProperties(repoUrl, HTTP_PROXY, null))
                .run();
        HttpClient httpClient = getHttpClientForUrl(repoUrl);
        expectedException.expectCause(allOf(
                instanceOf(UnknownHostException.class),
                hasProperty("message", containsString(HTTP_PROXY.getHost()))));

        makeRequest(httpClient, "http://somehost");
    }

    @Test
    public void httpProxy_placeholderUrl() throws Exception {
        new SpringApplicationBuilder(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(gitProperties("https://myrepo/{placeholder}-repo.git", HTTP_PROXY, null))
                .run();
        HttpClient httpClient = getHttpClientForUrl("https://myrepo/someplaceholdervalue-repo.git");
        expectedException.expectCause(allOf(
                instanceOf(UnknownHostException.class),
                hasProperty("message", containsString(HTTP_PROXY.getHost()))));

        makeRequest(httpClient, "http://somehost");
    }

    @Test
    public void httpProxy_notCalled() throws Exception {
        String repoUrl = "https://myrepo/repo.git";
        new SpringApplicationBuilder(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(gitProperties(repoUrl, HTTP_PROXY, null))
                .run();
        HttpClient httpClient = getHttpClientForUrl(repoUrl);
        expectedException.expectCause(allOf(
                instanceOf(UnknownHostException.class),
                hasProperty("message", containsString("somehost"))));

        makeRequest(httpClient, "https://somehost");
    }

    private String[] gitProperties(String repoUrl, ProxyHostProperties httpProxy, ProxyHostProperties httpsProxy) {
        List<String> result = new ArrayList<>();
        result.add("spring.cloud.config.server.git.uri=" + repoUrl);
        if (httpProxy != null) {
            result.addAll(Arrays.asList(
                    "spring.cloud.config.server.git.proxy.http.host=" + httpProxy.getHost(),
                    "spring.cloud.config.server.git.proxy.http.port=" + httpProxy.getPort()
            ));
            if (httpProxy.getUsername() != null && httpProxy.getPassword() != null) {
                result.addAll(Arrays.asList(
                        "spring.cloud.config.server.git.proxy.http.username=" + httpProxy.getUsername(),
                        "spring.cloud.config.server.git.proxy.http.password=" + httpProxy.getPassword()
                ));
            }
        }
        if (httpsProxy != null) {
            result.addAll(Arrays.asList(
                    "spring.cloud.config.server.git.proxy.https.host=" + httpsProxy.getHost(),
                    "spring.cloud.config.server.git.proxy.https.port=" + httpsProxy.getPort()
            ));
            if (httpsProxy.getUsername() != null && httpsProxy.getPassword() != null) {
                result.addAll(Arrays.asList(
                        "spring.cloud.config.server.git.proxy.https.username=" + httpsProxy.getUsername(),
                        "spring.cloud.config.server.git.proxy.https.password=" + httpsProxy.getPassword()
                ));
            }
        }
        return result.toArray(new String[0]);
    }

    private void makeRequest(HttpClient httpClient, String url) {
        RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
        restTemplate.getForObject(url, String.class);
    }

    private HttpClient getHttpClientForUrl(String repoUrl) throws IOException {
        HttpConnectionFactory connectionFactory = HttpTransport.getConnectionFactory();
        HttpConnection httpConnection = connectionFactory.create(new URL(repoUrl));
        assertThat(httpConnection).isInstanceOf(HttpClientConnection.class);
        return (HttpClient) ReflectionTestUtils.getField(httpConnection, "client");
    }

    @Configuration
    @EnableConfigurationProperties(ConfigServerProperties.class)
    @Import({ PropertyPlaceholderAutoConfiguration.class, EnvironmentRepositoryConfiguration.class })
    protected static class TestConfiguration {
    }
}
