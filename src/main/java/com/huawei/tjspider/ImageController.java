package com.huawei.tjspider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = "/img")
public class ImageController {

	private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

	@RequestMapping(value = "", method = RequestMethod.GET)
	public void getImg(@RequestParam("url") String url, @RequestParam("referer") String referer,
			HttpServletResponse response) throws IOException {
		logger.info("");
		String shortUrl = url.substring(url.lastIndexOf("/"));
		logger.info(shortUrl + " STARTS ===== " + url);

		DateTime now = new DateTime();
		DateTimeFormatter dtDtf = DateTimeFormat.forPattern("yyyy-MM-dd");

		File imgFile = new File(Thread.currentThread().getContextClassLoader().getResource("").getPath() + "/image/"
				+ dtDtf.print(now) + "/" + URLEncoder.encode(url, "UTF-8"));
		File imgFolder = imgFile.getParentFile();
		if (!imgFolder.exists()) {
			imgFolder.mkdirs();
		}
		if (!imgFile.exists()) {
			logger.info(shortUrl + " Start getting image.");
			CloseableHttpResponse srcResponse = null;
			BufferedOutputStream bosFile = null;
			CloseableHttpClient httpclient = null;
			try {
				HttpGet httpget = new HttpGet(url);
				httpget.setHeader("Referer", referer);
				httpget.setHeader("User-Agent",
						"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.103 Safari/537.36");

				httpget.setConfig(RequestConfig.custom().setConnectTimeout(30 * 1000).setSocketTimeout(90 * 1000)
						.setConnectionRequestTimeout(10 * 1000).build());

				httpclient = HttpClients.createDefault();
				srcResponse = httpclient.execute(httpget);
				if (srcResponse.getStatusLine().getStatusCode() == 200) {
					bosFile = new BufferedOutputStream(new FileOutputStream(imgFile));
					srcResponse.getEntity().writeTo(bosFile);
					bosFile.close();
					srcResponse.close();
					httpclient.close();
					logger.info(shortUrl + " Writed to " + imgFile.getAbsolutePath() + " size=" + imgFile.length());
				} else {
					response.sendError(404, "Original response IS NOT 200.");
					logger.info(shortUrl + " Response 404: Original response is not 200.");
					return;
				}
				srcResponse.close();
				httpclient.close();
			} catch (Exception e) {
				logger.info(shortUrl + " Exception: Failed to get image or save image.");
				e.printStackTrace();
				if (imgFile.exists()) {
					imgFile.delete();
				}
			} finally {
				if (bosFile != null) {
					bosFile.close();
				}
				if (srcResponse != null) {
					srcResponse.close();
				}
				if (httpclient != null) {
					httpclient.close();
				}
				logger.info(shortUrl + " Finally: getting and saving image.");
			}
		} else {
			logger.info(shortUrl + " Skip getting image.");
		}
		
		if (!imgFile.exists()) {
			response.sendError(404, "Original response IS 200, but FAILED to write image to file.");
			logger.info(shortUrl + " Response 404: Original response is not 200.");
			return;
		} else {
			logger.info(shortUrl + " Start serving image.");
			BufferedInputStream bisFile = null;
			BufferedOutputStream bosResponse = null;
			try {
				bisFile = new BufferedInputStream(new FileInputStream(imgFile));
				response.setContentLength((int) imgFile.length());
				bosResponse = new BufferedOutputStream(response.getOutputStream());
				byte[] buff = new byte[4096];
				int bytesRead;
				while (-1 != (bytesRead = bisFile.read(buff, 0, buff.length))) {
					bosResponse.write(buff, 0, bytesRead);
				}
				bosResponse.close();
				response.flushBuffer();
				bisFile.close();
			} catch (Exception e) {
				logger.info(shortUrl + " Exception: FAILED to serve image to client.");
				e.printStackTrace();
			} finally {
				if (bosResponse != null) {
					bosResponse.close();
				}
				if (bisFile != null) {
					bisFile.close();
				}
				logger.info(shortUrl + " Finally: serving image.");
			}
		}
		logger.info(shortUrl + " ENDS =======");
	}
}
