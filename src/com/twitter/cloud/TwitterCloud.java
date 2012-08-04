package com.twitter.cloud;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.custommonkey.xmlunit.XMLUnit;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/**
 * Program to generate the hash table having the count of twitter vs number of each words in it.
 * @author baskar
 * 
 */
public class TwitterCloud {

	private static final String TWITTER_USER_TIMELINE_BASE_API = "https://api.twitter.com/1/statuses/user_timeline.xml";

	private static final String QUERY_PARAM_SCREEN_NAME = "screen_name";
	private static final String QUERY_PARAM_INCLUDE_RETWEET = "include_rts";
	private static final String QUERY_PARAM_EXCLUDE_REPLIES = "exclude_replies";
	private static final String QUERY_PARAM_TWEET_COUNT = "count";
	private static final String QUERY_PARAM_PAGE = "page";
	
	private static final String TIMELINE_TWEET_XPATH = "//text";
	private static final int TWEETS_COUNT_LIMIT = 1000;
	private static final int MAX_TWEETS_PER_PAGE = 200;

	private static final String ERROR_TWITTER_HANDLE_NOTFOUND = "Error from twitter: \"Not found\"";
	private static final String ERROR_NOT_AUTHORIZED = "Error from twitter: \"Not authorized\"";
	private static final String ERROR_NO_TWEETS_DONE = "The user hasn't tweeted yet";
	private static final String ERROR_MESSAGE = "Oops! Something went wrong. Check your inputs and try again.";
	private static final String ERROR_INCORECT_RESPONSE = "Response is not well-formed";
	private static final String ERROR_TWEET_COUNT_LIMIT_EXCEEDED = "Tweets to Cloud limited to "+TWEETS_COUNT_LIMIT;
	
	private static final int HTTP_OK = 200;
	private static final int HTTP_NOTFOUND = 404;
	private static final int HTTP_UNAUTHORIZED = 401;

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) {		
		final String TWITTER_HANDLE = "iambaskar";
		final int TWEETS_TO_CLOUD = 200;
		final boolean INCLUDE_RETWEETS = false;
		final boolean EXCLUDE_REPLIES = false;
		
		String userTimelineAPI = TWITTER_USER_TIMELINE_BASE_API +
					"?"	+	QUERY_PARAM_SCREEN_NAME		+	"="		+	TWITTER_HANDLE 		+ 
					"&"	+	QUERY_PARAM_INCLUDE_RETWEET	+	"="		+	INCLUDE_RETWEETS 	+ 
					"&"	+	QUERY_PARAM_EXCLUDE_REPLIES	+	"="		+	EXCLUDE_REPLIES 	+ 
					"&"	+	QUERY_PARAM_TWEET_COUNT		+	"="	 	+	MAX_TWEETS_PER_PAGE ; 
		
		HashMap<Integer, Integer> hm = new HashMap<Integer, Integer>();
		try {
			if(TWEETS_TO_CLOUD>TWEETS_COUNT_LIMIT) throw new Exception(ERROR_TWEET_COUNT_LIMIT_EXCEEDED);
			hm = getTimeline(userTimelineAPI, TWEETS_TO_CLOUD);
			printMap(hm);
		} catch (IllegalArgumentException ex) {
			System.out.println("Invalid Arguments. Please check to it.");
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
	}
	
	/**
	 * printMap Function to print the HashMap of Tweets
	 * @param hp
	 */
	private static void printMap(HashMap<Integer, Integer> hp){	    
	    Map<Integer, Integer> map = new HashMap<Integer, Integer>(hp);
	    for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
	        System.out.println(entry.getKey() +":"+ entry.getValue());
	    }
	}
	
	/**
	 * getTimeline method which take the timeline url as input, generate hashmap and return it 
	 * @param apiUrl
	 * @param maxCount
	 * @return
	 * @throws Exception
	 */
	private static HashMap<Integer, Integer> getTimeline(String apiUrl, int maxCount) throws Exception {
		int totalTimelinePages = maxCount/MAX_TWEETS_PER_PAGE;
		int tweetsOnFinalPage = maxCount % MAX_TWEETS_PER_PAGE;
		if(tweetsOnFinalPage!=0) 
			totalTimelinePages++;
		else 
			tweetsOnFinalPage=MAX_TWEETS_PER_PAGE;
		
		Client client = Client.create();
		ClientResponse clientResponse = null;
		
		String responseBody = "";
		HashMap<Integer, Integer> tweetsHashMap = new HashMap<Integer, Integer>();
		try {
			while(totalTimelinePages !=0 ){
				WebResource webResource = client.resource(apiUrl+"&"+QUERY_PARAM_PAGE+"="+totalTimelinePages);
				WebResource.Builder webResourceRequestBuilder = webResource.getRequestBuilder();
				clientResponse = webResourceRequestBuilder.get(ClientResponse.class);		
				responseBody = clientResponse.getEntity(String.class);
				if(clientResponse.getStatus()==HTTP_OK) {
					tweetsHashMap = buildHashOnTweets(TIMELINE_TWEET_XPATH, responseBody, tweetsHashMap, tweetsOnFinalPage, totalTimelinePages);
					tweetsOnFinalPage=MAX_TWEETS_PER_PAGE;
				} else if(clientResponse.getStatus()==HTTP_NOTFOUND) {
					throw new Exception(ERROR_TWITTER_HANDLE_NOTFOUND);
				} else if(clientResponse.getStatus()==HTTP_UNAUTHORIZED) {
					throw new Exception(ERROR_NOT_AUTHORIZED);
				} else {
					throw new Exception(ERROR_MESSAGE);
				}
				totalTimelinePages--;
			}
		}catch (IOException ex) {
			System.out.println(ex.getMessage());
		}
		return tweetsHashMap;
	}
	
	/**
	 * 
	 * @param xpathToFetch
	 * @param xml
	 * @param tweetsHashMap
	 * @param tweetCount
	 * @return
	 * @throws Exception
	 */
	private static HashMap<Integer, Integer> buildHashOnTweets(String xpathToFetch, String xml, HashMap<Integer, Integer> tweetsHashMap, int tweetCount, int page) throws Exception {
		try {
//			Converting XML into Document Object..."
			Document doc = XMLUnit.buildControlDocument(xml);
//			Fetching the given XPath Value from the XML..."
			XPath xpath = XPathFactory.newInstance().newXPath();
			xpathToFetch=xpathToFetch+"/text()";			  
			XPathExpression expr = xpath.compile(xpathToFetch);
			Object result = expr.evaluate(doc, XPathConstants.NODESET);
			NodeList nodes = (NodeList) result;
			int nodeCount = (nodes.getLength()>tweetCount)?tweetCount:nodes.getLength(); 
			if(nodeCount == 0 && page==1) throw new Exception(ERROR_NO_TWEETS_DONE); 
			for (int i = 0; i < nodeCount; i++) {
			  int length = nodes.item(i).getNodeValue().split(" +").length;
			  if (!tweetsHashMap.containsKey(length))
					  tweetsHashMap.put(length, 1);
			  else 
				  tweetsHashMap.put(length,tweetsHashMap.get(length)+1);
			}
		}catch (SAXException sax) {
			System.out.println(ERROR_INCORECT_RESPONSE);
		}
		return tweetsHashMap;
	}


}
