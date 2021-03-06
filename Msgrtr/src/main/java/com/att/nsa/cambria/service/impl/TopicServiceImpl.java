/*******************************************************************************
 * BSD License
 *  
 * Copyright (c) 2016, AT&T Intellectual Property.  All other rights reserved.
 *  
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *  
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 *    and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 * 3. All advertising materials mentioning features or use of this software must display the
 *    following acknowledgement:  This product includes software developed by the AT&T.
 * 4. Neither the name of AT&T nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *  
 * THIS SOFTWARE IS PROVIDED BY AT&T INTELLECTUAL PROPERTY ''AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL AT&T INTELLECTUAL PROPERTY BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;  LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 *******************************************************************************/
/**
 * 
 */
package com.att.nsa.cambria.service.impl;

import java.io.IOException;

import org.apache.http.HttpStatus;
import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.att.ajsc.filemonitor.AJSCPropertiesMap;
import com.att.nsa.cambria.CambriaApiException;
import com.att.nsa.cambria.beans.DMaaPContext;
import com.att.nsa.cambria.beans.DMaaPKafkaMetaBroker;
import com.att.nsa.cambria.beans.TopicBean;
import com.att.nsa.cambria.constants.CambriaConstants;
import com.att.nsa.cambria.exception.DMaaPAccessDeniedException;
import com.att.nsa.cambria.exception.DMaaPErrorMessages;
import com.att.nsa.cambria.exception.DMaaPResponseCode;
import com.att.nsa.cambria.exception.ErrorResponse;
import com.att.nsa.cambria.metabroker.Broker;
import com.att.nsa.cambria.metabroker.Broker.TopicExistsException;
import com.att.nsa.cambria.metabroker.Topic;
import com.att.nsa.cambria.security.DMaaPAAFAuthenticator;
import com.att.nsa.cambria.security.DMaaPAAFAuthenticatorImpl;
import com.att.nsa.cambria.security.DMaaPAuthenticatorImpl;
import com.att.nsa.cambria.service.TopicService;
import com.att.nsa.cambria.utils.DMaaPResponseBuilder;
import com.att.nsa.configs.ConfigDbException;
import com.att.nsa.security.NsaAcl;
import com.att.nsa.security.NsaApiKey;
import com.att.nsa.security.ReadWriteSecuredResource.AccessDeniedException;

/**
 * @author author
 *
 */
@Service
public class TopicServiceImpl implements TopicService {

	//private static final Logger LOGGER = Logger.getLogger(TopicServiceImpl.class);
	private static final EELFLogger LOGGER = EELFManager.getInstance().getLogger(TopicServiceImpl.class);
	@Autowired
	private DMaaPErrorMessages errorMessages;
	
	//@Value("${msgRtr.topicfactory.aaf}")
	//private String mrFactory;
	
	
	/**
	 * @param dmaapContext
	 * @throws JSONException
	 * @throws ConfigDbException
	 * @throws IOException
	 * 
	 */
	@Override
	public void getTopics(DMaaPContext dmaapContext) throws JSONException, ConfigDbException, IOException {

		LOGGER.info("Fetching list of all the topics.");
		JSONObject json = new JSONObject();

		JSONArray topicsList = new JSONArray();

		for (Topic topic : getMetaBroker(dmaapContext).getAllTopics()) {
			topicsList.put(topic.getName());
		}

		json.put("topics", topicsList);

		LOGGER.info("Returning list of all the topics.");
		DMaaPResponseBuilder.respondOk(dmaapContext, json);

	}

	/**
	 * @param dmaapContext
	 * @throws JSONException
	 * @throws ConfigDbException
	 * @throws IOException
	 * 
	 */
	public void getAllTopics(DMaaPContext dmaapContext) throws JSONException, ConfigDbException, IOException {

		LOGGER.info("Fetching list of all the topics.");
		JSONObject json = new JSONObject();

		JSONArray topicsList = new JSONArray();

		for (Topic topic : getMetaBroker(dmaapContext).getAllTopics()) {
			JSONObject obj = new JSONObject();
			obj.put("topicName", topic.getName());
			//obj.put("description", topic.getDescription());
			obj.put("owner", topic.getOwner());
			obj.put("txenabled", topic.isTransactionEnabled());
			topicsList.put(obj);
		}

		json.put("topics", topicsList);

		LOGGER.info("Returning list of all the topics.");
		DMaaPResponseBuilder.respondOk(dmaapContext, json);

	}

	
	/**
	 * @param dmaapContext
	 * @param topicName
	 * @throws ConfigDbException
	 * @throws IOException
	 * @throws TopicExistsException
	 */
	@Override
	public void getTopic(DMaaPContext dmaapContext, String topicName)
			throws ConfigDbException, IOException, TopicExistsException {

		LOGGER.info("Fetching details of topic " + topicName);
		Topic t = getMetaBroker(dmaapContext).getTopic(topicName);

		if (null == t) {
			LOGGER.error("Topic [" + topicName + "] does not exist.");
			throw new TopicExistsException("Topic [" + topicName + "] does not exist.");
		}

		JSONObject o = new JSONObject();
		o.put ( "name", t.getName () );
		o.put ( "description", t.getDescription () );
		
		if (null!=t.getOwners ())
		o.put ( "owner", t.getOwners ().iterator ().next () );
		if(null!=t.getReaderAcl ())
		o.put ( "readerAcl", aclToJson ( t.getReaderAcl () ) );
		if(null!=t.getWriterAcl ())
		o.put ( "writerAcl", aclToJson ( t.getWriterAcl () ) );
	
		LOGGER.info("Returning details of topic " + topicName);
		DMaaPResponseBuilder.respondOk(dmaapContext, o);

	}

	
	/**
	 * @param dmaapContext
	 * @param topicBean
	 * @throws CambriaApiException
	 * @throws AccessDeniedException
	 * @throws IOException
	 * @throws TopicExistsException
	 * @throws JSONException
	 * 
	 * 
	 * 
	 */
	@Override
	public void createTopic(DMaaPContext dmaapContext, TopicBean topicBean)
			throws CambriaApiException, DMaaPAccessDeniedException,IOException, TopicExistsException {

		LOGGER.info("Creating topic " + topicBean.getTopicName());
		
		final NsaApiKey user = DMaaPAuthenticatorImpl.getAuthenticatedUser(dmaapContext);
		String key = null;
		String appName=dmaapContext.getRequest().getHeader("AppName");
		String enfTopicName= com.att.ajsc.beans.PropertiesMapBean.getProperty(CambriaConstants.msgRtr_prop,"enforced.topic.name.AAF");
	
		if(user != null)
		{
			key = user.getKey();
			
			if(  enfTopicName != null && topicBean.getTopicName().indexOf(enfTopicName) >=0 ) {
				
				LOGGER.error("Failed to create topic"+topicBean.getTopicName()+", Authentication failed.");
				
				ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_UNAUTHORIZED, 
						DMaaPResponseCode.ACCESS_NOT_PERMITTED.getResponseCode(), 
						errorMessages.getCreateTopicFail()+" "+errorMessages.getNotPermitted1()+" create "+errorMessages.getNotPermitted2());
				LOGGER.info(errRes.toString());
				throw new DMaaPAccessDeniedException(errRes);
				
			}
		}
				
		//else if (user==null && (null==dmaapContext.getRequest().getHeader("Authorization") && null == dmaapContext.getRequest().getHeader("cookie")) ) {
			else if (user == null &&  null==dmaapContext.getRequest().getHeader("Authorization")     && 
					 (null == appName  &&  null == dmaapContext.getRequest().getHeader("cookie"))) {
			LOGGER.error("Failed to create topic"+topicBean.getTopicName()+", Authentication failed.");
			
			ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_UNAUTHORIZED, 
					DMaaPResponseCode.ACCESS_NOT_PERMITTED.getResponseCode(), 
					errorMessages.getCreateTopicFail()+" "+errorMessages.getNotPermitted1()+" create "+errorMessages.getNotPermitted2());
			LOGGER.info(errRes.toString());
			throw new DMaaPAccessDeniedException(errRes);
		}
		
		if (user == null &&  (null!=dmaapContext.getRequest().getHeader("Authorization") ||
					 null != dmaapContext.getRequest().getHeader("cookie"))) {
			//if (user == null && (null!=dmaapContext.getRequest().getHeader("Authorization") || null != dmaapContext.getRequest().getHeader("cookie"))) {
			 // ACL authentication is not provided so we will use the aaf authentication
			LOGGER.info("Authorization the topic");
		
			String permission = "";
			String nameSpace="";
			if(topicBean.getTopicName().indexOf(".")>1)
			 nameSpace = topicBean.getTopicName().substring(0,topicBean.getTopicName().lastIndexOf("."));
		
			 String mrFactoryVal=AJSCPropertiesMap.getProperty(CambriaConstants.msgRtr_prop,"msgRtr.topicfactory.aaf");
		
			//AJSCPropertiesMap.getProperty(CambriaConstants.msgRtr_prop,kSettings_KafkaZookeeper);
			
			permission = mrFactoryVal+nameSpace+"|create";
			DMaaPAAFAuthenticator aaf = new DMaaPAAFAuthenticatorImpl();
			
			if(!aaf.aafAuthentication(dmaapContext.getRequest(), permission))
			{
				
				LOGGER.error("Failed to create topic"+topicBean.getTopicName()+", Authentication failed.");
				
				ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_UNAUTHORIZED, 
						DMaaPResponseCode.ACCESS_NOT_PERMITTED.getResponseCode(), 
						errorMessages.getCreateTopicFail()+" "+errorMessages.getNotPermitted1()+" create "+errorMessages.getNotPermitted2());
				LOGGER.info(errRes.toString());
				throw new DMaaPAccessDeniedException(errRes);
				
			}else{
				// if user is null and aaf authentication is ok then key should be ""
				//key = "";
				/**
				 * Added as part of AAF user it should return username
				 */
				
				key = dmaapContext.getRequest().getUserPrincipal().getName().toString();
				LOGGER.info("key ==================== "+key);
				
			}
		}

		try {
			final String topicName = topicBean.getTopicName();
			final String desc = topicBean.getTopicDescription();

			final  int partitions = topicBean.getPartitionCount();
		
			final int replicas = topicBean.getReplicationCount();
			boolean transactionEnabled = topicBean.isTransactionEnabled();
			

			final Broker metabroker = getMetaBroker(dmaapContext);
			final Topic t = metabroker.createTopic(topicName, desc, key, partitions, replicas,
					transactionEnabled);

			LOGGER.info("Topic created successfully. Sending response");
			DMaaPResponseBuilder.respondOk(dmaapContext, topicToJson(t));
		} catch (JSONException excp) {
			
			LOGGER.error("Failed to create topic. Couldn't parse JSON data.", excp);
			ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_BAD_REQUEST, 
					DMaaPResponseCode.INCORRECT_JSON.getResponseCode(), 
					errorMessages.getIncorrectJson());
			LOGGER.info(errRes.toString());
			throw new CambriaApiException(errRes);
			
		}
	}

	/**
	 * @param dmaapContext
	 * @param topicName
	 * @throws ConfigDbException
	 * @throws IOException
	 * @throws TopicExistsException
	 * @throws CambriaApiException
	 * @throws AccessDeniedException
	 */
	@Override
	public void deleteTopic(DMaaPContext dmaapContext, String topicName)
			throws IOException, ConfigDbException, CambriaApiException, TopicExistsException, DMaaPAccessDeniedException, AccessDeniedException {

		LOGGER.info("Deleting topic " + topicName);
		final NsaApiKey user = DMaaPAuthenticatorImpl.getAuthenticatedUser(dmaapContext);

		if (user == null && null!=dmaapContext.getRequest().getHeader("Authorization")) {
			LOGGER.info("Authenticating the user, as ACL authentication is not provided");
//			String permission = "com.att.dmaap.mr.topic"+"|"+topicName+"|"+"manage";
			String permission = "";
			String nameSpace = topicName.substring(0,topicName.lastIndexOf("."));
			 String mrFactoryVal=AJSCPropertiesMap.getProperty(CambriaConstants.msgRtr_prop,"msgRtr.topicfactory.aaf");
//			String tokens[] = topicName.split(".mr.topic.");
			permission = mrFactoryVal+nameSpace+"|destroy";
			DMaaPAAFAuthenticator aaf = new DMaaPAAFAuthenticatorImpl();
			if(!aaf.aafAuthentication(dmaapContext.getRequest(), permission))
			{
				LOGGER.error("Failed to delete topi"+topicName+". Authentication failed.");
				ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_FORBIDDEN, 
						DMaaPResponseCode.ACCESS_NOT_PERMITTED.getResponseCode(), 
						errorMessages.getCreateTopicFail()+" "+errorMessages.getNotPermitted1()+" delete "+errorMessages.getNotPermitted2());
				LOGGER.info(errRes.toString());
				throw new DMaaPAccessDeniedException(errRes);
			}
			
			
		}

		final Broker metabroker = getMetaBroker(dmaapContext);
		final Topic topic = metabroker.getTopic(topicName);

		if (topic == null) {
			LOGGER.error("Failed to delete topic. Topic [" + topicName + "] does not exist.");
			throw new TopicExistsException("Failed to delete topic. Topic [" + topicName + "] does not exist.");
		}

		metabroker.deleteTopic(topicName);

		LOGGER.info("Topic [" + topicName + "] deleted successfully. Sending response.");
		DMaaPResponseBuilder.respondOkWithHtml(dmaapContext, "Topic [" + topicName + "] deleted successfully");

	}

	/**
	 * 
	 * @param dmaapContext
	 * @return
	 */
	private DMaaPKafkaMetaBroker getMetaBroker(DMaaPContext dmaapContext) {
		return (DMaaPKafkaMetaBroker) dmaapContext.getConfigReader().getfMetaBroker();
	}

	/**
	 * @param dmaapContext
	 * @param topicName
	 * @throws ConfigDbException
	 * @throws IOException
	 * @throws TopicExistsException
	 * 
	 */
	@Override
	public void getPublishersByTopicName(DMaaPContext dmaapContext, String topicName)
			throws ConfigDbException, IOException, TopicExistsException {
		LOGGER.info("Retrieving list of all the publishers for topic " + topicName);
		Topic topic = getMetaBroker(dmaapContext).getTopic(topicName);

		if (topic == null) {
			LOGGER.error("Failed to retrieve publishers list for topic. Topic [" + topicName + "] does not exist.");
			throw new TopicExistsException(
					"Failed to retrieve publishers list for topic. Topic [" + topicName + "] does not exist.");
		}
		
		

		final NsaAcl acl = topic.getWriterAcl();

		LOGGER.info("Returning list of all the publishers for topic " + topicName + ". Sending response.");
		DMaaPResponseBuilder.respondOk(dmaapContext, aclToJson(acl));

	}

	/**
	 * 
	 * @param acl
	 * @return
	 */
	private static JSONObject aclToJson(NsaAcl acl) {
		final JSONObject o = new JSONObject();
		if (acl == null) {
			o.put("enabled", false);
			o.put("users", new JSONArray());
		} else {
			o.put("enabled", acl.isActive());

			final JSONArray a = new JSONArray();
			for (String user : acl.getUsers()) {
				a.put(user);
			}
			o.put("users", a);
		}
		return o;
	}

	/**
	 * @param dmaapContext
	 * @param topicName
	 */
	@Override
	public void getConsumersByTopicName(DMaaPContext dmaapContext, String topicName)
			throws IOException, ConfigDbException, TopicExistsException {
		LOGGER.info("Retrieving list of all the consumers for topic " + topicName);
		Topic topic = getMetaBroker(dmaapContext).getTopic(topicName);

		if (topic == null) {
			LOGGER.error("Failed to retrieve consumers list for topic. Topic [" + topicName + "] does not exist.");
			throw new TopicExistsException(
					"Failed to retrieve consumers list for topic. Topic [" + topicName + "] does not exist.");
		}

		final NsaAcl acl = topic.getReaderAcl();

		LOGGER.info("Returning list of all the consumers for topic " + topicName + ". Sending response.");
		DMaaPResponseBuilder.respondOk(dmaapContext, aclToJson(acl));

	}

	/**
	 * 
	 * @param t
	 * @return
	 */
	private static JSONObject topicToJson(Topic t) {
		final JSONObject o = new JSONObject();

		o.put("name", t.getName());
		o.put("description", t.getDescription());
		o.put("owner", t.getOwner());
		o.put("readerAcl", aclToJson(t.getReaderAcl()));
		o.put("writerAcl", aclToJson(t.getWriterAcl()));

		return o;
	}

	/**
	 * @param dmaapContext
	 * @param topicName
	 * @param producerId
	 * @throws ConfigDbException
	 * @throws IOException
	 * @throws TopicExistsException
	 * @throws AccessDeniedException
	 * @throws  
	 * 
	 */
	@Override
	public void permitPublisherForTopic(DMaaPContext dmaapContext, String topicName, String producerId)
			throws AccessDeniedException, ConfigDbException, IOException, TopicExistsException,CambriaApiException {

		LOGGER.info("Granting write access to producer [" + producerId + "] for topic " + topicName);
		final NsaApiKey user = DMaaPAuthenticatorImpl.getAuthenticatedUser(dmaapContext);
		
//		if (user == null) {
//			
//			LOGGER.info("Authenticating the user, as ACL authentication is not provided");
////			String permission = "com.att.dmaap.mr.topic"+"|"+topicName+"|"+"manage";
//			
//			DMaaPAAFAuthenticator aaf = new DMaaPAAFAuthenticatorImpl();
//			String permission = aaf.aafPermissionString(topicName, "manage");
//			if(!aaf.aafAuthentication(dmaapContext.getRequest(), permission))
//			{
//				LOGGER.error("Failed to permit write access to producer [" + producerId + "] for topic " + topicName
//									+ ". Authentication failed.");
//				ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_FORBIDDEN, 
//						DMaaPResponseCode.ACCESS_NOT_PERMITTED.getResponseCode(), 
//						errorMessages.getNotPermitted1()+" <Grant publish permissions> "+errorMessages.getNotPermitted2()+ topicName);
//				LOGGER.info(errRes);
//				throw new DMaaPAccessDeniedException(errRes);
//			}
//		}

		Topic topic = getMetaBroker(dmaapContext).getTopic(topicName);

		if (null == topic) {
			LOGGER.error("Failed to permit write access to producer [" + producerId + "] for topic. Topic [" + topicName
					+ "] does not exist.");
			throw new TopicExistsException("Failed to permit write access to producer [" + producerId
					+ "] for topic. Topic [" + topicName + "] does not exist.");
		}

		topic.permitWritesFromUser(producerId, user);

		LOGGER.info("Write access has been granted to producer [" + producerId + "] for topic [" + topicName
				+ "]. Sending response.");
		DMaaPResponseBuilder.respondOkWithHtml(dmaapContext, "Write access has been granted to publisher.");

	}

	/**
	 * @param dmaapContext
	 * @param topicName
	 * @param producerId
	 * @throws ConfigDbException
	 * @throws IOException
	 * @throws TopicExistsException
	 * @throws AccessDeniedException
	 * @throws DMaaPAccessDeniedException 
	 * 
	 */
	@Override
	public void denyPublisherForTopic(DMaaPContext dmaapContext, String topicName, String producerId)
			throws AccessDeniedException, ConfigDbException, IOException, TopicExistsException, DMaaPAccessDeniedException {

		LOGGER.info("Revoking write access to producer [" + producerId + "] for topic " + topicName);
		final NsaApiKey user = DMaaPAuthenticatorImpl.getAuthenticatedUser(dmaapContext);
//		if (user == null) {
//			
////			String permission = "com.att.dmaap.mr.topic"+"|"+topicName+"|"+"manage";
//			DMaaPAAFAuthenticator aaf = new DMaaPAAFAuthenticatorImpl();
//			String permission = aaf.aafPermissionString(topicName, "manage");
//			if(!aaf.aafAuthentication(dmaapContext.getRequest(), permission))
//			{
//				LOGGER.error("Failed to revoke write access to producer [" + producerId + "] for topic " + topicName
//						+ ". Authentication failed.");
//				ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_FORBIDDEN, 
//						DMaaPResponseCode.ACCESS_NOT_PERMITTED.getResponseCode(), 
//						errorMessages.getNotPermitted1()+" <Revoke publish permissions> "+errorMessages.getNotPermitted2()+ topicName);
//				LOGGER.info(errRes);
//				throw new DMaaPAccessDeniedException(errRes);
//				
//			}
//		}

		Topic topic = getMetaBroker(dmaapContext).getTopic(topicName);

		if (null == topic) {
			LOGGER.error("Failed to revoke write access to producer [" + producerId + "] for topic. Topic [" + topicName
					+ "] does not exist.");
			throw new TopicExistsException("Failed to revoke write access to producer [" + producerId
					+ "] for topic. Topic [" + topicName + "] does not exist.");
		}

		topic.denyWritesFromUser(producerId, user);

		LOGGER.info("Write access has been revoked to producer [" + producerId + "] for topic [" + topicName
				+ "]. Sending response.");
		DMaaPResponseBuilder.respondOkWithHtml(dmaapContext, "Write access has been revoked for publisher.");

	}

	/**
	 * @param dmaapContext
	 * @param topicName
	 * @param consumerId
	 * @throws DMaaPAccessDeniedException 
	 */
	@Override
	public void permitConsumerForTopic(DMaaPContext dmaapContext, String topicName, String consumerId)
			throws AccessDeniedException, ConfigDbException, IOException, TopicExistsException, DMaaPAccessDeniedException {

		LOGGER.info("Granting read access to consumer [" + consumerId + "] for topic " + topicName);
		final NsaApiKey user = DMaaPAuthenticatorImpl.getAuthenticatedUser(dmaapContext);
//		if (user == null) {
//			
////			String permission = "com.att.dmaap.mr.topic"+"|"+topicName+"|"+"manage";
//			DMaaPAAFAuthenticator aaf = new DMaaPAAFAuthenticatorImpl();
//			String permission = aaf.aafPermissionString(topicName, "manage");
//			if(!aaf.aafAuthentication(dmaapContext.getRequest(), permission))
//			{
//				LOGGER.error("Failed to permit read access to consumer [" + consumerId + "] for topic " + topicName
//						+ ". Authentication failed.");
//				ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_FORBIDDEN, 
//						DMaaPResponseCode.ACCESS_NOT_PERMITTED.getResponseCode(), 
//						errorMessages.getNotPermitted1()+" <Grant consume permissions> "+errorMessages.getNotPermitted2()+ topicName);
//				LOGGER.info(errRes);
//				throw new DMaaPAccessDeniedException(errRes);
//			}
//		}

		Topic topic = getMetaBroker(dmaapContext).getTopic(topicName);

		if (null == topic) {
			LOGGER.error("Failed to permit read access to consumer [" + consumerId + "] for topic. Topic [" + topicName
					+ "] does not exist.");
			throw new TopicExistsException("Failed to permit read access to consumer [" + consumerId
					+ "] for topic. Topic [" + topicName + "] does not exist.");
		}

		topic.permitReadsByUser(consumerId, user);

		LOGGER.info("Read access has been granted to consumer [" + consumerId + "] for topic [" + topicName
				+ "]. Sending response.");
		DMaaPResponseBuilder.respondOkWithHtml(dmaapContext,
				"Read access has been granted for consumer [" + consumerId + "] for topic [" + topicName + "].");
	}

	/**
	 * @param dmaapContext
	 * @param topicName
	 * @param consumerId
	 * @throws DMaaPAccessDeniedException 
	 */
	@Override
	public void denyConsumerForTopic(DMaaPContext dmaapContext, String topicName, String consumerId)
			throws AccessDeniedException, ConfigDbException, IOException, TopicExistsException, DMaaPAccessDeniedException {

		LOGGER.info("Revoking read access to consumer [" + consumerId + "] for topic " + topicName);
		final NsaApiKey user = DMaaPAuthenticatorImpl.getAuthenticatedUser(dmaapContext);
//		if (user == null) {
////			String permission = "com.att.dmaap.mr.topic"+"|"+topicName+"|"+"manage";
//			DMaaPAAFAuthenticator aaf = new DMaaPAAFAuthenticatorImpl();
//			String permission = aaf.aafPermissionString(topicName, "manage");
//			if(!aaf.aafAuthentication(dmaapContext.getRequest(), permission))
//			{
//				LOGGER.error("Failed to revoke read access to consumer [" + consumerId + "] for topic " + topicName
//						+ ". Authentication failed.");
//				ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_FORBIDDEN, 
//						DMaaPResponseCode.ACCESS_NOT_PERMITTED.getResponseCode(), 
//						errorMessages.getNotPermitted1()+" <Grant consume permissions> "+errorMessages.getNotPermitted2()+ topicName);
//				LOGGER.info(errRes);
//				throw new DMaaPAccessDeniedException(errRes);
//			}
//			
//			
//		}

		Topic topic = getMetaBroker(dmaapContext).getTopic(topicName);

		if (null == topic) {
			LOGGER.error("Failed to revoke read access to consumer [" + consumerId + "] for topic. Topic [" + topicName
					+ "] does not exist.");
			throw new TopicExistsException("Failed to permit read access to consumer [" + consumerId
					+ "] for topic. Topic [" + topicName + "] does not exist.");
		}

		topic.denyReadsByUser(consumerId, user);

		LOGGER.info("Read access has been revoked to consumer [" + consumerId + "] for topic [" + topicName
				+ "]. Sending response.");
		DMaaPResponseBuilder.respondOkWithHtml(dmaapContext,
				"Read access has been revoked for consumer [" + consumerId + "] for topic [" + topicName + "].");

	}


	
	
	
}
