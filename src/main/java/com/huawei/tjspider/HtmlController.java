package com.huawei.tjspider;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.jsoup.Connection;
import java.nio.charset.Charset;

@Controller
public class HtmlController {

	private static final Logger logger = LoggerFactory.getLogger(HtmlController.class);
	
	@RequestMapping(value ="/htm", method = {RequestMethod.GET,RequestMethod.POST}, produces = "text/html; charset=UTF-8")
	public @ResponseBody String getHtmlContent(@RequestParam("url") String url,HttpServletResponse response)  {
		logger.info("/htm getHtml url link:"+url);
		return getHtmlWithJsoup(url,response);
	}

	@RequestMapping(value ="/post", method = {RequestMethod.GET,RequestMethod.POST}, produces = "text/html; charset=UTF-8")
	public @ResponseBody String getHtmlCont(@RequestParam("url") String url,HttpServletResponse response)  {
		logger.info("/post getHtml url link:"+url);
		return getHtmlWithJsoup(url,response);
	}

	private String getHtmlWithJsoup(String url,HttpServletResponse response){
		Connection conn = Jsoup.connect(url).timeout(30*1000);
		conn.header("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.106 Safari/537.36");
		Document doc = null;
		try {
			doc = conn.get();
			doc.charset(Charset.forName("UTF-8"));
			return doc.html();
		} catch (IOException e) {
			try {
				response.sendError(404, "Original response IS 200, but FAILED to PARSE or SERVE the html.");
			} catch (IOException e1) {
				logger.error(e1.getMessage());
			}
			logger.error(e.getMessage());
		}
		return null;
	}

	@RequestMapping(value = "/htm/{charset}", method = RequestMethod.GET, produces = "text/html; charset=UTF-8")
	public @ResponseBody String getHtm(@PathVariable String charset, @RequestParam("url") String url,
			HttpServletResponse response) throws IOException {
		logger.info(url + " ===== STARTS " + url);

		HttpGet httpget = new HttpGet(url);
		httpget.setHeader("User-Agent",
				"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.103 Safari/537.36");
		CloseableHttpClient httpclient = HttpClients.createDefault();
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(30 * 1000).setSocketTimeout(90 * 1000)
				.setConnectionRequestTimeout(10 * 1000).build();
		httpget.setConfig(requestConfig);
		CloseableHttpResponse srcResponse = null;
		InputStream isContent = null;
		try {
			srcResponse = httpclient.execute(httpget);
			if (srcResponse.getStatusLine().getStatusCode() == 200) {

				isContent = srcResponse.getEntity().getContent();
				byte[] byteContent = IOUtils.toByteArray(isContent);
				isContent.close();
				logger.info(url + " Length: " + byteContent.length);
				Document doc = Jsoup.parse(new String(byteContent, charset), url);

				String charsetHtml = getHtmlCharset(doc);
				String charsetResponse = getResponseCharset(srcResponse.getHeaders("Content-Type"));

				String finalCharset = getFinalCharset(charsetHtml, charsetResponse, charset);
				logger.info(url + " Charset: html:" + charsetHtml + "," + "response:" + charsetResponse + ","
						+ "given:" + charset + "," + "final:" + finalCharset);
				if (charset.equalsIgnoreCase(finalCharset)) {
					return doc.html();
				} else {
					doc = Jsoup.parse(new String(byteContent, finalCharset), url);
					return doc.html();
				}
			} else {
				response.sendError(404, "Original response IS NOT 200.");
				logger.warn(url + " Response 404: Original response: " + srcResponse.getStatusLine().toString());
				return null;
			}
		} catch (Exception e) {
			response.sendError(404, "Original response IS 200, but FAILED to PARSE or SERVE the html.");
			logger.error(url + " Response 404: Original response IS 200, but FAILED to PARSE or SERVE the html.");
			logger.error(url + " " + e.getMessage());
			e.printStackTrace();
			return null;
		} finally {
			if (isContent != null) {
				isContent.close();
			}
			if (srcResponse != null) {
				srcResponse.close();
			}
			if (httpclient != null) {
				httpclient.close();
			}
			logger.info(url + " Finally: get and serve html.");
		}
	}

	private String getFinalCharset(String charsetHtml, String charsetResponse, String charset) {
		String result = charset;
		if (charsetHtml != null && !"".equalsIgnoreCase(charsetHtml)) {
			result = charsetHtml;
		} else if (charsetResponse != null && !"".equalsIgnoreCase(charsetResponse)) {
			result = charsetResponse;
		}
		if ("gb2312".equalsIgnoreCase(result)) {
			result = "gbk";
		}
		return result;
	}

	private String getResponseCharset(Header[] ContentTypeHeaders) {
		if (ContentTypeHeaders.length > 0) {
			String s = ContentTypeHeaders[0].getValue();
			Pattern patternCharset = Pattern.compile("charset=([A-Za-z0-9\\-]+)");
			Matcher matcher = patternCharset.matcher(s);
			while (matcher.find()) {
				return matcher.group(0).split("=")[1].toLowerCase().trim();
			}
		}
		return null;
	}

	private String getHtmlCharset(Document doc) {
		/*
		 * facebook html5 <meta charset="utf-8" />
		 * 
		 * csdn html4 <meta http-equiv="Content-Type"
		 * content="text/html; charset=utf-8" />
		 */
		String result = null;
		Elements heads = doc.getElementsByTag("head");
		if (heads.size() > 0) {
			Element head = heads.get(0);
			Pattern patternCharset = Pattern.compile("charset=([A-Za-z0-9\\-]+)");
			Elements elementCharsets = head.getElementsByAttributeValueMatching("content", patternCharset);
			if (elementCharsets.size() > 0) {
				Element elementCharset = elementCharsets.get(0);
				Matcher matcher = patternCharset.matcher(elementCharset.attr("content"));
				while (matcher.find()) {
					result = matcher.group(0).split("=")[1].toLowerCase().trim();
				}
			} else {
				elementCharsets = head.getElementsByAttribute("charset");
				if (elementCharsets.size() > 0) {
					Element elementCharset = elementCharsets.get(0);
					result = elementCharset.attr("charset");
				}
			}
		}
		return result;
	}
}
