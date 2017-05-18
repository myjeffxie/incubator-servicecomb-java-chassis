/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.huawei.paas.cse.transport.rest.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.huawei.paas.cse.common.rest.RestConst;
import com.huawei.paas.cse.common.rest.definition.RestOperationMeta;
import com.huawei.paas.cse.core.AsyncResponse;
import com.huawei.paas.cse.core.Invocation;
import com.huawei.paas.cse.core.definition.OperationMeta;
import com.huawei.paas.cse.transport.rest.client.http.HttpMethodFactory;
import com.huawei.paas.cse.transport.rest.client.http.VertxHttpMethod;
import com.huawei.paas.foundation.ssl.SSLCustom;
import com.huawei.paas.foundation.ssl.SSLOption;
import com.huawei.paas.foundation.ssl.SSLOptionFactory;
import com.huawei.paas.foundation.vertx.VertxTLSBuilder;
import com.huawei.paas.foundation.vertx.VertxUtils;
import com.huawei.paas.foundation.vertx.client.ClientPoolManager;
import com.huawei.paas.foundation.vertx.client.http.HttpClientVerticle;
import com.huawei.paas.foundation.vertx.client.http.HttpClientWithContext;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;

/**
 * <一句话功能简述>
 * <功能详细描述>
 * @author   
 * @version  [版本号, 2017年1月16日]
 * @see  [相关类/方法]
 * @since  [产品/模块版本]
 */
public final class RestTransportClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestTransportClient.class);

    private static final String SSL_KEY = "rest.consumer";

    private ClientPoolManager<HttpClientWithContext> clientMgr = new ClientPoolManager<>();

    private final boolean sslEnabled;

    public RestTransportClient(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public void init(Vertx vertx) throws Exception {
        HttpClientOptions httpClientOptions = createHttpClientOptions();
        DeploymentOptions deployOptions = VertxUtils.createClientDeployOptions(clientMgr,
                TransportClientConfig.getThreadCount(),
                TransportClientConfig.getConnectionPoolPerThread(),
                httpClientOptions);
        VertxUtils.blockDeploy(vertx, HttpClientVerticle.class, deployOptions);
    }

    /**
     * 创建http client配置项
     * @return
     */
    private HttpClientOptions createHttpClientOptions() {
        HttpClientOptions httpClientOptions = new HttpClientOptions();

        if (this.sslEnabled) {
            SSLOptionFactory factory =
                SSLOptionFactory.createSSLOptionFactory(SSL_KEY,
                        null);
            SSLOption sslOption;
            if (factory == null) {
                sslOption = SSLOption.buildFromYaml(SSL_KEY);
            } else {
                sslOption = factory.createSSLOption();
            }
            SSLCustom sslCustom = SSLCustom.createSSLCustom(sslOption.getSslCustomClass());
            VertxTLSBuilder.buildHttpClientOptions(sslOption, sslCustom, httpClientOptions);
        }
        return httpClientOptions;
    }

    /**
     * <一句话功能简述>
     * <功能详细描述>
     * @param invocation
     * @param asyncResp
     * @throws Exception
     */
    public void send(Invocation invocation, AsyncResponse asyncResp) throws Exception {
        HttpClientWithContext httpClientWithContext = clientMgr.findThreadBindClientPool();

        OperationMeta operationMeta = invocation.getOperationMeta();
        RestOperationMeta swaggerRestOperation = operationMeta.getExtData(RestConst.SWAGGER_REST_OPERATION);
        String method = swaggerRestOperation.getHttpMethod();
        try {
            VertxHttpMethod httpMethod = HttpMethodFactory.findHttpMethodInstance(method);
            httpMethod.doMethod(httpClientWithContext, invocation, asyncResp);
        } catch (Exception e) {
            asyncResp.fail(invocation.getInvocationType(), e);
            LOGGER.error("vertx rest transport send error.", e);
        }
    }

}