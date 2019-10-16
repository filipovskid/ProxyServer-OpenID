package com.filipovski.server;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import org.apache.http.client.utils.URIBuilder;


public class RequestUtils {
	
	static URL buildUrl(String base, String endpoint, Map<String, String> parameters) throws URISyntaxException, MalformedURLException {
		URIBuilder uriBuilder = new URIBuilder(base + endpoint);
		
		parameters.entrySet()
			.stream()
			.forEach(e -> uriBuilder.addParameter(e.getKey(), e.getValue()));
					
		return uriBuilder.build().toURL();
	}
}
