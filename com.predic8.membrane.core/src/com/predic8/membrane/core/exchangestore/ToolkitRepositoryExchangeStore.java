/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchangestore;

import gov.nist.hit.ds.initialization.installation.Installation;
import gov.nist.hit.ds.initialization.installation.PropertyServiceManager;
import gov.nist.hit.ds.repository.api.ArtifactId;
import gov.nist.hit.ds.repository.api.Asset;
import gov.nist.hit.ds.repository.api.PropertyKey;
import gov.nist.hit.ds.repository.api.Repository;
import gov.nist.hit.ds.repository.api.RepositoryException;
import gov.nist.hit.ds.repository.api.RepositoryFactory;
import gov.nist.hit.ds.repository.api.RepositorySource.Access;
import gov.nist.hit.ds.repository.api.Type;
import gov.nist.hit.ds.repository.simple.Configuration;
import gov.nist.hit.ds.repository.simple.SimpleId;
import gov.nist.hit.ds.repository.simple.SimpleType;
import gov.nist.hit.ds.utilities.datatypes.Hl7Date;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.QueueConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;

import net.timewalker.ffmq3.FFMQConstants;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangesUtil;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.startup.ApplicationCachePreLoader;
import com.predic8.membrane.core.startup.dto.GatewayTag;
import com.predic8.membrane.core.statistics.RuleStatistics;


public class ToolkitRepositoryExchangeStore extends AbstractExchangeStore {

	private static final String FORWARD_TO = "forwardTo";

	private static final String MESSAGE_FROM = "messageFrom";

	private static final String PROXY_HOST = "proxy";
	
	private static final String PROXY_PORT = "proxyPort";

	private static final String PROXY_RULE_MAPPING_NAME = "proxyRuleMappingName";

	private static Log log = LogFactory.getLog(ToolkitRepositoryExchangeStore.class
			.getName());

	private String dir;

	private boolean raw;

	private File directory;

	private static int counter = 0;

	private static final DateFormat dateFormat = new SimpleDateFormat(
			"'h'hh'm'mm's'ss'ms'ms");

	private static final String separator = System
			.getProperty("file.separator");

	public static final String MESSAGE_FILE_PATH = "message.file.path";

	private boolean saveBodyOnly = false;

	// Configuration
    private ApplicationCachePreLoader appCache;

    // Repository
    private Repository repos = null;
    private String toolkitInstallationPath;
        
    /* ActiveMQ based configuration - not used 
    private final String DEFAULT_BROKER_NAME = "tcp://localhost:61616";
    private final String DEFAULT_USER_NAME = ActiveMQConnection.DEFAULT_USER;
    private final String DEFAULT_PASSWORD = ActiveMQConnection.DEFAULT_PASSWORD;
    */
    private final String DEFAULT_QUEUE = "txmon";

    public void init() throws RepositoryException  {
        log.info("Enter init() for ToolkitRepositoryExchangeStore");

        // Toolkit access bootstrap
        Installation.installation();
		try {
			if (Installation.installation().getExternalCache()==null) {
				File tpPath = new File(getToolkitInstallationPath(),  "WEB-INF"+ File.separator + Installation.TOOLKIT_PROPERTIES);
				
				Properties props = new Properties();
				
				FileReader fr = new FileReader(tpPath);
				props.load(fr);
				fr.close();
				String ecDir = props.getProperty(PropertyServiceManager.EXTERNAL_CACHE);
				
				if (ecDir!=null) {
					Installation.installation().setExternalCache(new File(ecDir));
				} else {
					throw new RepositoryException("Undefined "+PropertyServiceManager.EXTERNAL_CACHE + " property in " + Installation.TOOLKIT_PROPERTIES);
				}
									
				Installation.installation().initialize();					
			}
			
		} catch (Exception ex) {
			log.info("Init failed. " + ex.toString());
			return;
		}
		
        // Make sure external repository is accessible
        // set the repository variable here
			Configuration.configuration();
    		repos = getProxyRepos();		        
    }
    
	private static Repository getProxyRepos() throws RepositoryException {
		RepositoryFactory reposFact = new RepositoryFactory(Configuration.getRepositorySrc(Access.RW_EXTERNAL));
		ArtifactId id = new SimpleId("transactions-cap");
		
		Repository repos = null;
		try {
			repos = reposFact.getRepository(id);	
		} catch (RepositoryException re) {
			repos = reposFact.createNamedRepository(
					id.getIdString(),
					"captured transactions",
					new SimpleType("simpleRepos"),
					id.getIdString()
					);			
		}
		return repos;
	}
	
	private synchronized StringBuffer getTxDetailCsv(String parentName, Asset msg, boolean isReq) {
		StringBuffer buf = new StringBuffer();
		// TODO: Refactor to allow common reference
		// final String[] columns = {"Timestamp","Status","Artifact","Message From","Proxy","Forwarded To","Path","ContentType","Method","Length","Response Time"};
        
		try {
			buf.append(msg.getProperty(JDBCUtil.TIME)); // parentName 
			buf.append(",\"");    buf.append(msg.getProperty(JDBCUtil.STATUS_CODE));
	        buf.append("\",\"");  buf.append(msg.getProperty(JDBCUtil.MSG_TYPE));
	        
			buf.append("\",\"" ); buf.append(msg.getProperty(MESSAGE_FROM));
	        buf.append("\",\"" ); buf.append(msg.getProperty(PROXY_PORT));			
	        buf.append("\",\"" ); buf.append(msg.getProperty(FORWARD_TO));
	        	        			
	        buf.append("\", \""); buf.append(((isReq)?msg.getProperty(JDBCUtil.PATH):""));	        	        
	        
	        buf.append("\",\"" ); buf.append( msg.getProperty(JDBCUtil.CONTENT_TYPE));
	        buf.append("\",\"" ); buf.append(((isReq)?msg.getProperty(JDBCUtil.METHOD):""));
	        buf.append("\",\"" ); buf.append(msg.getProperty(JDBCUtil.CONTENT_LENGTH));
	        buf.append("\",\"" ); buf.append(((isReq)?"":msg.getProperty(JDBCUtil.DURATION)));
	        buf.append("\"");
		
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
 
		return buf;
	}
	
	    private void setTxProperties(Asset asset, AbstractExchange exc, boolean isReq)  {

			Message msg = exc.getResponse() == null ? exc.getRequest() : exc
					.getResponse();
	    	try {
	            
	    		SimpleDateFormat sdf2 = new SimpleDateFormat(Hl7Date.parseFmt);		
	    		asset.setProperty(JDBCUtil.TIME, sdf2.format(ExchangesUtil.getDate(exc)));
	    		
	            asset.setProperty(JDBCUtil.MSG_TYPE, (isReq)?"REQUEST":"RESPONSE");

	            asset.setProperty(JDBCUtil.STATUS_CODE, "" + ((isReq)?200: exc.getResponse().getStatusCode()));
	    		asset.setProperty(JDBCUtil.RULE, ""+exc.getRule().toString());
	    		
	    		if (exc.getRequest()!=null) {
	    			asset.setProperty(JDBCUtil.METHOD, ""+exc.getRequest().getMethod());
	    			asset.setProperty(JDBCUtil.PATH, ""+exc.getRequest().getUri());			
	    		}

	    		// asset.setProperty(JDBCUtil.CLIENT,(gatewayHCIDs!=null && !"".equals(gatewayHCIDs[2]))?gatewayHCIDs[2]:exc.getSourceHostname());
	    		
	    		// asset.setProperty(JDBCUtil.SERVER ,(gatewayHCIDs!=null && !"".equals(gatewayHCIDs[3]))?gatewayHCIDs[3]:exc.getServer());
	    		
	    		log.debug("*** " + exc.getTimeReqReceived() + " -- " + exc.getTimeReqSent() + " -- " +  exc.getTimeResReceived() + " -- " + exc.getTimeResSent() );
	    		
	    		String initiatingHost = exc.getServer();
	    		
	    		String respondingHost = ((ForwardingRule)exc.getRule()).getTargetHost();
	    		
	    		String[] hostDetails = getHostDetail(initiatingHost, respondingHost);
	    		
	    		
	    		String messageFrom = ((hostDetails!=null && hostDetails.length==3)?hostDetails[2]:initiatingHost);
	    		String forwardTo = ((ForwardingRule)exc.getRule()).getTargetHost() + ":" + ((ForwardingRule)exc.getRule()).getTargetPort(); // Real
	    		if (!isReq) {
	    			String swapDirectionTemp = forwardTo;
	    			
	    			forwardTo = messageFrom;
	    			messageFrom = swapDirectionTemp;	    			
	    		}
	    		
	    		// Sender
	    		asset.setProperty(MESSAGE_FROM , messageFrom);
	    		
	    		// Receiver
	    		if (exc.getRule() instanceof ForwardingRule) {
	    			asset.setProperty(FORWARD_TO, forwardTo); 
	    								  	
	    		}
	    		asset.setProperty(PROXY_HOST , exc.getRule().getKey().getHost());
	    		asset.setProperty(PROXY_PORT, ""+exc.getRule().getKey().getPort());
	    		asset.setProperty(PROXY_RULE_MAPPING_NAME , exc.getRule().getName()); 
	    			            
	            
	            if (isReq) {
	            	asset.setProperty(JDBCUtil.CONTENT_TYPE ,""+exc.getRequestContentType());
	            	asset.setProperty(JDBCUtil.CONTENT_LENGTH , ""+ ((msg.getBody()!=null)?msg.getBody().getLength():"")); // exc.getRequestContentLength()        	
	            } else {
	            	asset.setProperty(JDBCUtil.CONTENT_TYPE ,""+exc.getResponseContentType());
	            	asset.setProperty(JDBCUtil.CONTENT_LENGTH , ""+ ((msg.getBody()!=null)?msg.getBody().getLength():"")); //exc.getResponseContentLength()        	        	
	            	asset.setProperty(JDBCUtil.DURATION, "" + (exc.getTimeResReceived() - exc.getTimeReqSent())); // (exc.getTimeResReceived() - exc.getTimeReqSent()
	            }

	            Object o = JDBCUtil.getExProperty(exc, FileExchangeStore.MESSAGE_FILE_PATH);
	            if (o!=null)
	            	asset.setProperty(JDBCUtil.MSG_FILE_PATH, "" + (String)o);
	    		
	    	} catch (Exception ex) {
	    		log.error(ex.toString());
	    	}
	    		    	

	    }
  

	public void add(AbstractExchange exc)  {
		if (exc.getResponse() == null)
			counter++;

		if (repos == null) {
        	log.error("Repository not initialized -- transaction capture escaped!");
            return;        	
        }


		Message msg = exc.getResponse() == null ? exc.getRequest() : exc
				.getResponse();
		//
		
		try {
			boolean isReq = exc.getResponse() == null;

	        SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_kk_mm_ss_SS");        
	        String parentName = sdf.format(exc.getTime().getTime()) + "_" + exc.getNanoTime();
	        String ioHeaderId = parentName+"_io";
			Asset ioHeader = null;
			
			if (isReq) {
		        Type simpleType = new SimpleType("simpleType");
		        //set id+nao, create asset, create new child asset with id as parent+"In_Out", set displayOrder
		                
		        Asset txRecord = repos.createNamedAsset(parentName/*displayName*/, null, simpleType, parentName);
		        
		        ioHeader = repos.createNamedAsset("Input/Output Messages", null, simpleType, ioHeaderId);		        
		        ioHeader = txRecord.addAsset(ioHeader);
			} else {
				// Deep scan: ioHeader = repos.getAsset(new SimpleId(ioHeaderId));
				ioHeader = repos.getAssetByRelativePath(new File((String)exc.getProperty(ioHeaderId)));
			}
			

	        Asset msgType = null;
	        if (isReq) {
	            msgType = repos.createNamedAsset("Request", null, new SimpleType("reqType"), ioHeaderId+"_Request");
	            msgType.setProperty(PropertyKey.DISPLAY_ORDER, "1");
	        } else {
	        	msgType = repos.createNamedAsset("Response", null, new SimpleType("resType"), ioHeaderId+"_Response");
	            msgType.setProperty(PropertyKey.DISPLAY_ORDER, "2");
	        }
	        setTxProperties(msgType, exc, isReq);
	        String txDetailCsv = getTxDetailCsv(parentName, msgType, isReq).toString();
	        
	        msgType = ioHeader.addAsset(msgType);
	        if (isReq) {
		    	exc.setProperty(ioHeaderId, ioHeader.getPropFileRelativePart());	
	        }

			String contentType = (isReq)?exc.getRequestContentType():exc.getResponseContentType();
			saveArtifacts(msgType, msg, isReq, null, contentType, txDetailCsv);
						
		} catch (Exception ex) {
			log.error(ex.toString());
			ex.printStackTrace();
		}		
	}

    private void saveArtifacts(Asset msgType, Message msg , boolean isReq, String[] gatewayHCIDs, String bodyContentType, String txDetailCsv) throws Exception {
    	
		Asset msgHeader = null;
		Asset msgBody = null;		
		String hdrType = (isReq)?"reqHdrType":"resHdrType";
		String bodyType = (isReq)?"reqBodyType":"resBodyType";
		
        msgHeader = repos.createNamedAsset("Header", null, new SimpleType(hdrType), msgType.getId().getIdString()+"_Header");
        msgHeader.setProperty(PropertyKey.DISPLAY_ORDER, "1");
       	msgHeader.setMimeType("text/plain");


		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			msg.writeStartLine(os);
			msg.getHeader().write(os);
			os.write((Constants.CRLF).getBytes());
			msgHeader.updateContent(os.toByteArray());
			msgHeader = msgType.addAsset(msgHeader);
			
			if (!msg.isBodyEmpty()) {						
		        msgBody = repos.createNamedAsset("Message", null, new SimpleType(bodyType), msgType.getId().getIdString()+"_Message");
		        msgBody.setProperty(PropertyKey.DISPLAY_ORDER, "2");
		        if (bodyContentType!=null) {
		        	msgBody.setMimeType(bodyContentType);
		        }
	
		        msgBody.updateContent(msg.getBody().getRaw());
		        msgBody = msgType.addAsset(msgBody);
			}			
		} catch (Exception ex) {
	        log.warn(ex.toString());
		} finally {
			os.close();
		}
		
		log.debug("Tx detail info: " + txDetailCsv);
		
		try {
    		sendMessage( 
    				 txDetailCsv
    				, (isReq?"REQUEST":"RESPONSE")
    				, msgType.getProperty(PropertyKey.PARENT_ID)
    				, msgHeader.getRepository().getIdString()
    				, msgHeader.getSource().getAccess().name()    			
    				, msgHeader.getPropFileRelativePart(), (msgBody!=null?msgBody.getPropFileRelativePart():null)
    				, (PROXY_HOST + ": " + msgType.getProperty(PROXY_HOST) + ":" + msgType.getProperty(PROXY_PORT) + ", " + PROXY_RULE_MAPPING_NAME + ": " + msgType.getProperty(PROXY_RULE_MAPPING_NAME))
    		        );
    	} catch (Exception ex) {
    		log.warn(ex.toString());
    	}
    }

    private String[] getHostDetail(String initiatingHost, String respondingHost) {
    	log.debug("i: " + initiatingHost + ",r: " + respondingHost);
    	
        if (getAppCache() != null) {            

            String[] excProvider = new String[] { initiatingHost, respondingHost, "", "" };
            GatewayTag gTagInitiator = null; 
            GatewayTag gTagResponder = null;
                        

            gTagInitiator = getAppCache().getGatewayTagMap(initiatingHost);
            gTagResponder = getAppCache().getGatewayTagMap(respondingHost);
            
            if (gTagInitiator != null && gTagInitiator.getHCID() != null) {
                excProvider[0] = gTagInitiator.getHCID();
            }
            if (gTagResponder != null && gTagResponder.getHCID() != null) {
                excProvider[1] = gTagResponder.getHCID();
            }

            if (gTagInitiator != null && gTagResponder != null) {
                    excProvider[2] = gTagInitiator.getGatewayAddress();
                    excProvider[3] = gTagResponder.getGatewayAddress();
            }

            return excProvider;
        } else {
            log.error("Error in resolving HCIDs: appCache or exchange error!");
        }
        return null;
    }
    
    
    private void sendMessage(String txDetail, String msgType, String parentId, String repId, String acs, String headerLoc, String bodyLoc, String proxyDetail) {
        log.debug("\n enter sendMessage \n");
        
        if (txDetail==null) {
        	log.debug("\n empty txDetail, exit sendMessage\n");
        	return;
        }
        
        javax.jms.QueueConnection connection = null;
        javax.jms.QueueSession session = null;
        
        try {
	        Hashtable<String,String> env = new Hashtable<String, String>();
	        env.put(Context.INITIAL_CONTEXT_FACTORY, FFMQConstants.JNDI_CONTEXT_FACTORY);
	        env.put(Context.PROVIDER_URL, "tcp://localhost:10002"); // FFMQ server
	        Context context = new InitialContext(env);
	
	        // Lookup a connection factory in the context
	        javax.jms.QueueConnectionFactory factory = (QueueConnectionFactory) context.lookup(FFMQConstants.JNDI_QUEUE_CONNECTION_FACTORY_NAME);
	
	        connection = factory.createQueueConnection();
	                

	        javax.jms.QueueSender sender = null;
	
	        session = connection.createQueueSession(false,
	        javax.jms.Session.AUTO_ACKNOWLEDGE);
	        
        // Create the Queue and QueueSender for sending requests.
            javax.jms.Queue queue = null;
            queue = session.createQueue("txmon");
            sender = session.createSender(queue);

            // Now that all setup is complete, start the Connection and send the
            // message.
            connection.start();
        
        
            log.debug("\nBefore inserting into the queue for transactionId:"
                    + txDetail);

            javax.jms.MapMessage mapMsg = session.createMapMessage();
            mapMsg.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
            mapMsg.setObject("txDetail", txDetail);
            if (parentId!=null)
            	mapMsg.setObject("ioHeaderId", parentId);            
            if (repId!=null)
            	mapMsg.setObject("repId", repId);
            if (acs!=null)
            	mapMsg.setObject("acs", acs);
            if (headerLoc!=null)
            	mapMsg.setObject("headerLoc", headerLoc);
            if (bodyLoc!=null)
            	mapMsg.setObject("bodyLoc", bodyLoc);
            if (msgType!=null)
            	mapMsg.setObject("msgType", msgType);
            if (proxyDetail!=null)
            	mapMsg.setObject("proxyDetail", proxyDetail);            
	        

            sender.send(mapMsg);
                      
            
            log.debug("\n *************** sendMessage()\n");
        } catch (Exception ex) {
            log.error("\n *************** Error inserting into the queue \n");
            log.error(ex.toString());
            ex.printStackTrace();
        } finally {
        	// Clean up
        	if (session!=null) {
                try {
    				session.close();
    			} catch (Exception ex) {}        		
        	}
        	if (connection!=null) {
                try {
    				connection.close();
    			} catch (Exception e) {}        		
        	}
        }

    }

    /*
     * ActiveMq based - not used
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

    private void sendMessage(String broker, String username, String password, String sQueue
    		, String txDetail, String msgType, String parentId, String repId, String acs, String headerLoc, String bodyLoc) {
        log.debug("\n enter sendMessage \n");
        
        if (txDetail==null) {
        	log.debug("\n empty txDetail, exit sendMessage\n");
        	return;
        }
        
        javax.jms.QueueConnection connect = null;
        javax.jms.QueueSession session = null;
        javax.jms.QueueSender sender = null;
        try {
            // Create a JMS connection
            javax.jms.QueueConnectionFactory factory;
            factory = new ActiveMQConnectionFactory(username, password, broker);
            connect = factory.createQueueConnection(username, password);
            session = connect.createQueueSession(false,
                    javax.jms.Session.AUTO_ACKNOWLEDGE);
            // Create the Queue and QueueSender for sending requests.
            javax.jms.Queue queue = null;
            queue = session.createQueue(sQueue);
            sender = session.createSender(queue);

            // Now that all setup is complete, start the Connection and send the
            // message.
            connect.start();
            log.debug("\nBefore inserting into the queue for transactionId:"
                    + txDetail);

            javax.jms.MapMessage mapMsg = session.createMapMessage();
            mapMsg.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
            mapMsg.setObject("txDetail", txDetail);
            if (parentId!=null)
            	mapMsg.setObject("ioHeaderId", parentId);            
            if (repId!=null)
            	mapMsg.setObject("repId", repId);
            if (acs!=null)
            	mapMsg.setObject("acs", acs);
            if (headerLoc!=null)
            	mapMsg.setObject("headerLoc", headerLoc);
            if (bodyLoc!=null)
            	mapMsg.setObject("bodyLoc", bodyLoc);
            if (msgType!=null)
            	mapMsg.setObject("msgType", msgType);

            sender.send(mapMsg);

            // Clean up
            session.close();
            connect.close();
            
            log.debug("\n *************** sendMessage()\n");
        } catch (Exception ex) {
            log.debug("\n *************** Error inserting into the queue \n");
            log.error(ex.toString());
        }
    }
    */

	public AbstractExchange[] getExchanges(RuleKey ruleKey) {
		throw new RuntimeException(
				"Method getExchanges() is not supported by FileExchangeStore");
	}

	public int getNumberOfExchanges(RuleKey ruleKey) {
		throw new RuntimeException(
				"Method getNumberOfExchanges() is not supported by FileExchangeStore");
	}

	public void remove(AbstractExchange exchange) {
		throw new RuntimeException(
				"Method remove() is not supported by FileExchangeStore");
	}

	public void removeAllExchanges(Rule rule) {
		throw new RuntimeException(
				"Method removeAllExchanges() is not supported by FileExchangeStore");
	}

	public String getDir() {
		return dir;
	}

	public void setDir(String dir) {
		this.dir = dir;
	}

	public boolean isRaw() {
		return raw;
	}

	public void setRaw(boolean raw) {
		this.raw = raw;
	}

	public RuleStatistics getStatistics(RuleKey ruleKey) {

		return null;
	}

	public Object[] getAllExchanges() {
		return null;
	}

	public Object[] getLatExchanges(int count) {
		return null;
	}

	public List<AbstractExchange> getAllExchangesAsList() {
		return null;
	}

	public void removeAllExchanges(AbstractExchange[] exchanges) {
		// ignore
	}

	public boolean isSaveBodyOnly() {
		return saveBodyOnly;
	}

	public void setSaveBodyOnly(boolean saveBodyOnly) {
		this.saveBodyOnly = saveBodyOnly;
	}

	public String getToolkitInstallationPath() {
		return toolkitInstallationPath;
	}

	public void setToolkitInstallationPath(String toolkitInstallationPath) {
		this.toolkitInstallationPath = toolkitInstallationPath;
	}

	/**
	 * @return the appCache
	 */
	public ApplicationCachePreLoader getAppCache() {
		return appCache;
	}

	/**
	 * @param appCache the appCache to set
	 */
	public void setAppCache(ApplicationCachePreLoader appCache) {
		this.appCache = appCache;
	}

}
