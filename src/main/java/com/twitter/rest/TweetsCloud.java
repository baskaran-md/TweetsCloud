package com.twitter.rest;


import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import javax.ws.rs.core.Response;


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

@Path("/buildcloud")
public class TweetsCloud {

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
    private static final String ERROR_INVALID_ARGUMENTS = "Invalid Arguments. Please check to it.";

    private static final int HTTP_OK = 200;
    private static final int HTTP_BADREQUEST = 400;
    private static final int HTTP_NOTFOUND = 404;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;

	@GET
	@Path("/test/{twitterHandle}")
	public Response getMsg(@PathParam("twitterHandle") String twitterHandle) {
		String output = "TwitterHandle Passed : " + twitterHandle;
		return Response.status(200).entity(output).build();
	}

    @GET
    @Path("/{twitterHandle}")
    public  Response getCloud(@PathParam("twitterHandle") String twitterHandle,
                              @QueryParam("keys") boolean hashKey,
                              @QueryParam("include_rts") boolean include_rts,
                              @QueryParam("exclude_replies") boolean exclude_replies,
                              @QueryParam("sample") int tweetsToCloud) {

        final String TWITTER_HANDLE = twitterHandle;
        final int TWEETS_TO_CLOUD = (tweetsToCloud<=0)?200:tweetsToCloud;
        final boolean INCLUDE_RETWEETS = include_rts;
        final boolean EXCLUDE_REPLIES = exclude_replies;

        String userTimelineAPI = TWITTER_USER_TIMELINE_BASE_API +
                "?"	+	QUERY_PARAM_SCREEN_NAME		+	"="		+	TWITTER_HANDLE 		+
                "&"	+	QUERY_PARAM_INCLUDE_RETWEET	+	"="		+	INCLUDE_RETWEETS 	+
                "&"	+	QUERY_PARAM_EXCLUDE_REPLIES	+	"="		+	EXCLUDE_REPLIES 	+
                "&"	+	QUERY_PARAM_TWEET_COUNT		+	"="	 	+	MAX_TWEETS_PER_PAGE ;

        HashMap<Integer, Integer> hm = new HashMap<Integer, Integer>();
        String twitterHashMap = new String();
        String twitterHashKey = new String();

        try {
            if(TWEETS_TO_CLOUD>TWEETS_COUNT_LIMIT) throw new Exception(ERROR_TWEET_COUNT_LIMIT_EXCEEDED);
            hm = getTimeline(userTimelineAPI, TWEETS_TO_CLOUD);
             twitterHashMap = printHashMap(hm);
             twitterHashKey = printHashKey(hm);
             if(hashKey)
                 return Response.status(HTTP_OK).entity(twitterHashKey).build();
             else
                return Response.status(HTTP_OK).entity(twitterHashMap).build();

        } catch (IllegalArgumentException ex) {
            return Response.status(HTTP_BADREQUEST).entity(ERROR_INVALID_ARGUMENTS).build();
        } catch (Exception ex) {
            return Response.status(HTTP_BADREQUEST).entity(ex.getMessage()).build();
        }
    }

    /**
     * printHashMap Function to print the HashMap of Tweets
     * @param hp
     */
    private static String printHashKey(HashMap<Integer, Integer> hp){
        Map<Integer, Integer> map = new HashMap<Integer, Integer>(hp);
        String twitterHashKey = new String();
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            twitterHashKey +=   "'"+ entry.getKey() +"',";
        }
        twitterHashKey = twitterHashKey.substring(0,twitterHashKey.lastIndexOf(","));
        return "[" + twitterHashKey + "]";
    }

    /**
     * printHashMap Function to print the HashMap of Tweets
     * @param hp
     */
    private static String printHashMap(HashMap<Integer, Integer> hp){
        Map<Integer, Integer> map = new HashMap<Integer, Integer>(hp);
        String twitterHash = new String();
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            twitterHash +=   "{ \"name\": "+ entry.getKey() +", \"data\": "+ entry.getValue() +" },";
        }
        twitterHash = twitterHash.substring(0,twitterHash.lastIndexOf(","));
        return "[" + twitterHash + "]";
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
            Document doc = XMLUnit.buildControlDocument(xml);
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