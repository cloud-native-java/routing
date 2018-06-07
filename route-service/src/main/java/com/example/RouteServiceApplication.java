package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
	* @author Ben Hale
	*/
@SpringBootApplication
public class RouteServiceApplication {

		public static void main(String[] args) {
				SpringApplication.run(RouteServiceApplication.class, args);
		}

		// <1>
		@Bean
		RestTemplate restOperations() {
				RestTemplate restTemplate = new RestTemplate(new TrustEverythingClientHttpRequestFactory()); // <2>
				restTemplate.setErrorHandler(new NoErrorsResponseErrorHandler()); // <3>
				return restTemplate;
		}

		private static class NoErrorsResponseErrorHandler extends DefaultResponseErrorHandler {

				@Override
				public boolean hasError(ClientHttpResponse response) throws IOException {
						return false;
				}
		}

		private static final class TrustEverythingClientHttpRequestFactory extends SimpleClientHttpRequestFactory {

				private static SSLContext getSslContext(TrustManager trustManager) {
						try {
								SSLContext sslContext = SSLContext.getInstance("SSL");
								sslContext.init(null, new TrustManager[]{trustManager}, null);
								return sslContext;
						}
						catch (KeyManagementException | NoSuchAlgorithmException e) {
								throw new RuntimeException(e);
						}
				}

				@Override
				protected HttpURLConnection openConnection(URL url, Proxy proxy) throws IOException {

						HttpURLConnection connection = super.openConnection(url, proxy);

						if (connection instanceof HttpsURLConnection) {
								HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
								SSLContext sslContext = getSslContext(new TrustEverythingTrustManager());
								httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
								httpsConnection.setHostnameVerifier((s, session) -> true);
						}
						return connection;
				}

		}

		private static final class TrustEverythingTrustManager implements
			X509TrustManager {

				@Override
				public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
					throws CertificateException {
				}

				@Override
				public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
					throws CertificateException {
				}

				@Override
				public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
				}
		}
}
