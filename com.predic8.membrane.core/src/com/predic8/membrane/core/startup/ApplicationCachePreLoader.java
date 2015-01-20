/**
 * 
 */
package com.predic8.membrane.core.startup;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Connection;

import java.util.HashMap;
import java.util.Map;

import com.predic8.membrane.core.util.IP;
import com.predic8.membrane.core.startup.dto.GatewayTag;

/**
 * @author Sunil.Bhaskarla
 * see ILT-365 WBS reference code I.b.
 * 
 */
public class ApplicationCachePreLoader {

	private static final String SPLITTER_DOT = "\\.";


	private enum PreLoaderState {
		 ERROR, UNINITIALIZED, INITIALIZED
	}
	private static Log log = LogFactory.getLog(ApplicationCachePreLoader.class.getName());
	private static final Map<String, GatewayTag> gatewayList = new HashMap<String,GatewayTag>();
	private static String definitionSource;
	private static String[] hostNames;
	private static DataSource dataSource;	
	private static String gatewayTagDataSetDef;
	private static PreLoaderState preLoaderState = PreLoaderState.UNINITIALIZED;

    /**
     * Matches provided IP address against a known hostname list
     * @param ipAddress
     * @return
     */
	public String getGatewayHostName(String ipAddress) {
		
		if (ipAddress==null) {
			log.error("getGatewayHostName: improper ip lookup request:"+ipAddress);
			return null;
		}
		
		IP ip = new IP();
		for (String key : gatewayList.keySet()) {
			
			InetAddress[] iaArray = ip.getAllIPs(key);
			if (iaArray!=null) {
				for (InetAddress ia : iaArray) {
					log.debug(ipAddress + ">key<" + key +  "> --- ia: <" + ia.getHostAddress() + "><" + ip.getIP(key)+ ">");
					if (ipAddress.equals(ia.getHostAddress())) {
						return key;
					}				
				}				
			} else {
				log.warn("Hostname cannot be resolved for this address: " + ipAddress);
				return ipAddress;
			}

			
//			log.info(ip.getIP(key));
//			if (ipAddress.equals(ip.getIP(key))) {
//				return key;
//			}
		}
		
		log.error("getGatewayHostName: ip lookup request not found:"+ipAddress);
		return null;
	}
	
	public GatewayTag getGatewayTagMap(String gatewayAddress) {
		if (gatewayAddress!=null)
			gatewayAddress = gatewayAddress.toLowerCase();
		
		if (preLoaderState==PreLoaderState.INITIALIZED) {
			GatewayTag gTag = gatewayList.get(gatewayAddress);
			
			if (gTag==null) {//DNSCache.java: canonical returned short name
				for (String key : gatewayList.keySet()) {
					String[] fqdn = key.split(SPLITTER_DOT);
					if ("localhost".equalsIgnoreCase(gatewayAddress) || "0.0.0.0.0.0.0.1".equals(gatewayAddress) || "127.0.0.1".equals(gatewayAddress) ) {
						 return gatewayList.get(key);
					} else if (fqdn.length>1) {
						if (fqdn[0].equalsIgnoreCase(gatewayAddress)) {
							return gatewayList.get(key);
						} 
					} else {
						log.error("Setup error: Gateway view does not have a FQDN host name:"+key);
					}					
				}
					
			} else 
				return gTag;
		} 
		
		log.error("getGatewayTagMap lookup failed for "+gatewayAddress);
		return null;
    }
    
	
	
	
	/*
	 * init() method
	 * see ILT-365 WBS reference code I.b.i.
	 */
	public static void init() {
		log.info("Enter init() for ApplicationCachePreLoader");
		Connection con = null;
		
		if (preLoaderState==PreLoaderState.UNINITIALIZED) {
			
			if ("local".equals(getDefinitionSource()) && getHostNames()!=null) {			
				
				for (String hostName : getHostNames()) {
		        	GatewayTag gTag = new GatewayTag();
		        	gTag.setGatewayAddress(hostName);
			    	gTag.setHostedBy("");
			        gTag.setHCID("");
		        	gatewayList.put(hostName, gTag);			        	
		        	log.debug("Loaded gatewayTagDataSetDef for hostName:"+ hostName);					
				}
				preLoaderState = PreLoaderState.INITIALIZED;

			} else if ("db".equals(getDefinitionSource())) {

				try {
					con = dataSource.getConnection();
		
					 Statement stmt = con.createStatement();
					    ResultSet rs = stmt.executeQuery(getGatewayTagDataSetDef());
					    while (rs.next()) {
					    	String gatewayAddress = rs.getString("gatewayAddress");
					        if (gatewayAddress!=null && !"".equals(gatewayAddress)){
					        	GatewayTag gTag = new GatewayTag();
					        	gTag.setGatewayAddress(gatewayAddress);
						    	gTag.setHostedBy(rs.getString("hostedBy"));
						        gTag.setHCID(rs.getString("HCID"));
					        	gatewayList.put(gatewayAddress, gTag);			        	
					        	log.debug("Loaded gatewayTagDataSetDef for gatewayAddress:"+ gatewayAddress);		        		
					        }			        
					    }
					
				} catch (Exception ex) {
					preLoaderState = PreLoaderState.ERROR;
					throw new RuntimeException("Init for GatewayTagMap@"+ ApplicationCachePreLoader.class.getName() +" failed: " + ex.getMessage());
				} finally {
					if (preLoaderState!=PreLoaderState.ERROR && preLoaderState==PreLoaderState.UNINITIALIZED)
						preLoaderState = PreLoaderState.INITIALIZED;
					closeConnection(con);
				}
			}
			

			
		}
		log.info("preLoader state is:"+preLoaderState);
		log.info("Exit init() for ApplicationCachePreLoader");
    }

	protected DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
	private static void closeConnection(Connection con) {
		try {
			con.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected static String getGatewayTagDataSetDef() {
		return gatewayTagDataSetDef;
	}


	public void setGatewayTagDataSetDef(String gatewayTagDataSetDef) {
		this.gatewayTagDataSetDef = gatewayTagDataSetDef;
	}


	/**
	 * @return the definitionSource
	 */
	protected static String getDefinitionSource() {
		return definitionSource;
	}

	/**
	 * @param definitionSource the definitionSource to set
	 */
	public void setDefinitionSource(String definitionSource) {
		this.definitionSource = definitionSource;
	}

	/**
	 * @return the hostNames
	 */
	protected static String[] getHostNames() {
		return hostNames;
	}

	/**
	 * @param hostNames the hostNames to set
	 */
	public void setHostNames(String[] hostNames) {
		this.hostNames = hostNames;
	}






}
