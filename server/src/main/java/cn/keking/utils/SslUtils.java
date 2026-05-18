package cn.keking.utils;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * @author 高雄
 */
public class SslUtils {

    /**
     * 创建忽略SSL验证的HttpClient（适用于HttpClient 5.6）
     */
    public static CloseableHttpClient createHttpClientIgnoreSsl() throws Exception {
        return configureHttpClientBuilder(HttpClients.custom(), true, true).build();
    }

    /**
     * 配置HttpClientBuilder，支持SSL和重定向配置
     * @param builder HttpClientBuilder
     * @param ignoreSSL 是否忽略SSL验证
     * @param enableRedirect 是否启用重定向
     * @return 配置好的HttpClientBuilder
     */
    public static HttpClientBuilder configureHttpClientBuilder(HttpClientBuilder builder,
                                                               boolean ignoreSSL,
                                                               boolean enableRedirect) throws Exception {
        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultSocketConfig(SocketConfig.custom()
                                .setSoTimeout(Timeout.ofSeconds(10))
                                .build())
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(2))
                                .build());

        // 配置SSL
        if (ignoreSSL) {
            // 创建自定义的SSL上下文
            SSLContext sslContext = createIgnoreVerifySSL();

            // 使用SSLConnectionSocketFactoryBuilder构建SSL连接工厂
            DefaultClientTlsStrategy tlsStrategy = new DefaultClientTlsStrategy(
                    sslContext, NoopHostnameVerifier.INSTANCE);
            connectionManagerBuilder.setTlsSocketStrategy(tlsStrategy);
        }

        // 使用统一连接管理器配置连接池参数与连接超时
        PoolingHttpClientConnectionManager connectionManager = connectionManagerBuilder.build();
        connectionManager.setMaxTotal(200);
        connectionManager.setDefaultMaxPerRoute(20);
        builder.setConnectionManager(connectionManager);

        // 配置请求参数
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(72))
                .setRedirectsEnabled(enableRedirect)
                .setMaxRedirects(5)
                .build();
        builder.setDefaultRequestConfig(requestConfig);

        if (!enableRedirect) {
            builder.disableRedirectHandling();
        }

        return builder;
    }

    /**
     * 创建忽略SSL验证的SSLContext
     * 修改为public访问权限
     */
    public static SSLContext createIgnoreVerifySSL() throws Exception {
        // 使用TLSv1.2或TLSv1.3
        SSLContext sc = SSLContext.getInstance("TLSv1.2");

        // 实现一个X509TrustManager，忽略所有证书验证
        X509TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // 信任所有客户端证书
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // 信任所有服务器证书
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };

        sc.init(null, new javax.net.ssl.TrustManager[]{trustManager}, new java.security.SecureRandom());
        return sc;
    }
}
