package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Node;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;

	// the index where the results go
	private JedisIndex index;

	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();

	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	String baseURL = "https://en.wikipedia.org";

	/**
	 * Constructor.
	 *
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 *
	 * @return
	 */
	public int queueSize() {
		return queue.size();
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b
	 *
	 * @return URL of the page that was indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
		if (queue.isEmpty()) return null;
		String url = queue.poll();
		if (!testing && index.isIndexed(url)) return null;
		Elements paragraphs;
        if (testing) {
			paragraphs = wf.readWikipedia(url);
			queueInternalLinks(paragraphs);
		} else {
			paragraphs = wf.fetchWikipedia(url);
			queueInternalLinks(paragraphs);
		}
		index.indexPage(url, paragraphs);
		return url;
	}

	/**
	 * Parses paragraphs and adds internal links to the queue.
	 *
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {
		for (int i = 0; i < paragraphs.size(); i++) {
			Element paragraph = paragraphs.get(i);
			Iterable<Node> iter = new WikiNodeIterable(paragraph);
			getURLs(iter);
		}
	}

	// Add URLs to queue
	private void getURLs(Iterable<Node> iter) {
		for (Node node : iter) {
			if (node instanceof Element) {
				Element curr = (Element)node;
				if (isValid(curr)) {
					String url = baseURL + node.attr("href");
					queue.offer(url);
				}
			}
		}
	}

	private boolean isValid(Element e) {
		String name = e.nodeName();
		if (name == "a" && e.attr("href").startsWith("/wiki/")) return true;
		return false;
	}

	public static void main(String[] args) throws IOException {

		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis);
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);

		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);
		} while (res == null);

		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
