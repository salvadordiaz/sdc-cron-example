package fr.salvadordiaz.gae.sdc;

import static com.google.common.collect.Iterables.*;
import static com.google.common.collect.Maps.*;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Joiner.MapJoiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

public class RemoteApiTask extends HttpServlet {
	private static final long serialVersionUID = -3443525668037129412L;

	private static final String NEW_LINE = "\n++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n";

	private final Logger log = Logger.getLogger(getClass().getName());

	private final URLFetchService urlFetchService = URLFetchServiceFactory.getURLFetchService();
	private static final MapJoiner joiner = Joiner.on("&").withKeyValueSeparator("=");
	private static final Splitter lineSplitter = Splitter.on("\n").omitEmptyStrings().trimResults();
	private static final Splitter keyValueSplitter = Splitter.on("=").omitEmptyStrings().trimResults();
	private final Function<Iterable<String>, String> getValue = new Function<Iterable<String>, String>() {
		@Override
		public String apply(Iterable<String> input) {
			return get(input, 1, null);
		}
	};
	private final Function<Iterable<String>, String> getKey = new Function<Iterable<String>, String>() {
		@Override
		public String apply(Iterable<String> input) {
			return getFirst(input, null).toLowerCase();
		}

	};
	private final Function<String, Iterable<String>> splitKeyValue = new Function<String, Iterable<String>>() {
		@Override
		public Iterable<String> apply(String input) {
			return keyValueSplitter.split(input);
		}
	};
	private final Function<HTTPHeader, String> getHeaderName = new Function<HTTPHeader, String>() {
		@Override
		public String apply(HTTPHeader input) {
			return input.getName().toLowerCase();
		}
	};

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		//sdc resource request: 'use_intranet_header' and auth cookie
		final HTTPRequest sdcReq = new HTTPRequest(new URL("https://sdc-example.appspot.com/sdc/sdc"));
		sdcReq.addHeader(new HTTPHeader("Cookie", getCookie()));
		//fetch sdc resource
		final HTTPResponse sdcResp = urlFetchService.fetch(sdcReq);
		final String respString = sdcResp.getResponseCode() + " : " + new String(sdcResp.getContent());
		resp.getWriter().println(respString);
		log.log(Level.WARNING, "Response : " + respString);
	}

	private String getCookie() throws IOException {
			//token request
			final HTTPRequest tokenReq = new HTTPRequest(new URL("https://www.google.com/accounts/ClientLogin"), HTTPMethod.POST,
					FetchOptions.Builder.doNotFollowRedirects());
			tokenReq.setPayload(joiner.join(ImmutableMap.<String, String> builder()//
				.put("Email", "sdcuser@mygoogleappsdomain.com")//
				.put("Passwd", "sdcuserpassword")//
					.put("accountType", "HOSTED")//
					.put("service", "ah")//
					.put("source", "sdc-example.appspot.com").build()).getBytes());
			//fetch token response
			final HTTPResponse tokenResp = urlFetchService.fetch(tokenReq);
			logResponse(tokenResp, "TokenResponse");
			//process token response
			final Iterable<Iterable<String>> split = transform(lineSplitter.split(new String(tokenResp.getContent())), splitKeyValue);
			final Map<String, String> tokenRespMap = ImmutableMap.copyOf(transformValues(uniqueIndex(split, getKey), getValue));
			//cookie request
			final HTTPRequest cookieReq = new HTTPRequest(new URL("https://sdc-example.appspot.com/_ah/login?auth=" + tokenRespMap.get("auth")),
					HTTPMethod.GET, FetchOptions.Builder.doNotFollowRedirects());
			//fetch cookie response
			final HTTPResponse cookieResp = urlFetchService.fetch(cookieReq);
			//process cookie response
			final Map<String, HTTPHeader> headersByName = uniqueIndex(cookieResp.getHeaders(), getHeaderName);
			logResponse(cookieResp, "CookieResponse");
		return headersByName.get("set-cookie").getValue();
	}

	private void logResponse(HTTPResponse resp, String responseName) {
		final StringBuffer buffer = new StringBuffer("Printing HTTPResponse : ").append(responseName).append(NEW_LINE);
		buffer.append("ResponseCode : ").append(resp.getResponseCode()).append(NEW_LINE);
		buffer.append("FinalUrl : ").append(resp.getFinalUrl() != null ? resp.getFinalUrl().toExternalForm() : "null").append(NEW_LINE);
		buffer.append("Printing response payload : ").append("\n");
		if (resp.getContent() != null) {
			try {
				final String content = new String(resp.getContent());
				final Iterable<Iterable<String>> contentLines = transform(lineSplitter.split(content), splitKeyValue);
				final Map<String, String> tokenRespMap = ImmutableMap.copyOf(transformValues(uniqueIndex(contentLines, getKey), getValue));
				for (String tokenName : tokenRespMap.keySet()) {
					buffer.append(tokenName).append(" : ").append(tokenRespMap.get(tokenName)).append("\n");
				}
			} catch (Exception e) {
				buffer.append("Content printing raised exception : ").append(e.getMessage());
			}
		}
		buffer.append(NEW_LINE);
		buffer.append("Printing response headers : ").append("\n");
		final Map<String, HTTPHeader> headersByName = uniqueIndex(resp.getHeaders(), getHeaderName);
		for (String headerName : headersByName.keySet()) {
			buffer.append(headerName).append(" : ").append(headersByName.get(headerName).getValue()).append("\n");
		}
		buffer.append(NEW_LINE);
		log.log(Level.WARNING, buffer.toString());
	}
}
