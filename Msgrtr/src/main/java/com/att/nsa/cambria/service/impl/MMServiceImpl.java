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
package com.att.nsa.cambria.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;

import org.apache.http.HttpStatus;
import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.att.ajsc.filemonitor.AJSCPropertiesMap;
import com.att.nsa.cambria.CambriaApiException;
import com.att.nsa.cambria.backends.Consumer;
import com.att.nsa.cambria.backends.ConsumerFactory;
import com.att.nsa.cambria.backends.ConsumerFactory.UnavailableException;
import com.att.nsa.cambria.backends.Publisher.message;
import com.att.nsa.cambria.backends.MetricsSet;
import com.att.nsa.cambria.backends.Publisher;

import com.att.nsa.cambria.beans.DMaaPContext;
import com.att.nsa.cambria.beans.LogDetails;
import com.att.nsa.cambria.constants.CambriaConstants;
import com.att.nsa.cambria.exception.DMaaPAccessDeniedException;
import com.att.nsa.cambria.exception.DMaaPErrorMessages;
import com.att.nsa.cambria.exception.DMaaPResponseCode;
import com.att.nsa.cambria.exception.ErrorResponse;
import com.att.nsa.cambria.metabroker.Broker.TopicExistsException;
import com.att.nsa.cambria.metabroker.Topic;
import com.att.nsa.cambria.resources.CambriaEventSet;
import com.att.nsa.cambria.resources.CambriaOutboundEventStream;
import com.att.nsa.cambria.security.DMaaPAAFAuthenticator;
import com.att.nsa.cambria.security.DMaaPAAFAuthenticatorImpl;
import com.att.nsa.cambria.service.MMService;
import com.att.nsa.cambria.utils.ConfigurationReader;
import com.att.nsa.cambria.utils.DMaaPResponseBuilder;
import com.att.nsa.cambria.utils.Utils;
import com.att.nsa.configs.ConfigDbException;
import com.att.nsa.drumlin.service.standards.MimeTypes;
import com.att.nsa.drumlin.till.nv.rrNvReadable.missingReqdSetting;

import com.att.nsa.security.ReadWriteSecuredResource.AccessDeniedException;
import com.att.nsa.util.rrConvertor;

import kafka.producer.KeyedMessage;

@Service
public class MMServiceImpl implements MMService {
	private static final String BATCH_LENGTH = "event.batch.length";
	private static final String TRANSFER_ENCODING = "Transfer-Encoding";
	//private static final Logger LOG = Logger.getLogger(MMServiceImpl.class);
	private static final EELFLogger LOG = EELFManager.getInstance().getLogger(MMServiceImpl.class);
	@Autowired
	private DMaaPErrorMessages errorMessages;

	@Autowired
	@Qualifier("configurationReader")
	private ConfigurationReader configReader;

	// HttpServletRequest object
	@Context
	private HttpServletRequest request;

	// HttpServletResponse object
	@Context
	private HttpServletResponse response;

	@Override
	public void addWhiteList() {

	}

	@Override
	public void removeWhiteList() {

	}

	@Override
	public void listWhiteList() {

	}

	@Override
	public String subscribe(DMaaPContext ctx, String topic, String consumerGroup, String clientId)
			throws ConfigDbException, TopicExistsException, AccessDeniedException, UnavailableException,
			CambriaApiException, IOException {

		// final long startTime = System.currentTimeMillis();
		final HttpServletRequest req = ctx.getRequest();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		// was this host blacklisted?
		final String remoteAddr = Utils.getRemoteAddress(ctx);
		
		if (ctx.getConfigReader().getfIpBlackList().contains(remoteAddr)) {

			ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_FORBIDDEN,
					DMaaPResponseCode.ACCESS_NOT_PERMITTED.getResponseCode(),
					"Source address [" + remoteAddr + "] is blacklisted. Please contact the cluster management team.",
					null, Utils.getFormattedDate(new Date()), topic, Utils.getUserApiKey(ctx.getRequest()),
					ctx.getRequest().getRemoteHost(), null, null);
			LOG.info(errRes.toString());
			throw new CambriaApiException(errRes);
		}

		int limit = CambriaConstants.kNoLimit;

		if (req.getParameter("limit") != null) {
			limit = Integer.parseInt(req.getParameter("limit"));
		}
		limit = 1;
		// int timeoutMs = 60000;
		int timeoutMs = CambriaConstants.kNoTimeout;
		String strtimeoutMS = AJSCPropertiesMap.getProperty(CambriaConstants.msgRtr_prop, "timeout");
		if (strtimeoutMS != null)
			timeoutMs = Integer.parseInt(strtimeoutMS);
		// int timeoutMs = ctx.getConfigReader().getSettings().getInt("timeout",
		// CambriaConstants.kNoTimeout);
		if (req.getParameter("timeout") != null) {
			timeoutMs = Integer.parseInt(req.getParameter("timeout"));
		}

		// By default no filter is applied if filter is not passed as a
		// parameter in the request URI
		String topicFilter = CambriaConstants.kNoFilter;
		if (null != req.getParameter("filter")) {
			topicFilter = req.getParameter("filter");
		}
		// pretty to print the messaages in new line
		String prettyval = "0";
		String strPretty = AJSCPropertiesMap.getProperty(CambriaConstants.msgRtr_prop, "pretty");
		if (null != strPretty)
			prettyval = strPretty;

		String metaval = "0";
		String strmeta = AJSCPropertiesMap.getProperty(CambriaConstants.msgRtr_prop, "meta");
		if (null != strmeta)
			metaval = strmeta;

		final boolean pretty = rrConvertor.convertToBooleanBroad(prettyval);
		// withMeta to print offset along with message
		final boolean withMeta = rrConvertor.convertToBooleanBroad(metaval);

		// is this user allowed to read this topic?
		//final NsaApiKey user = DMaaPAuthenticatorImpl.getAuthenticatedUser(ctx);
		final Topic metatopic = ctx.getConfigReader().getfMetaBroker().getTopic(topic);

		if (metatopic == null) {
			// no such topic.
			ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_NOT_FOUND,
					DMaaPResponseCode.RESOURCE_NOT_FOUND.getResponseCode(),
					errorMessages.getTopicNotExist() + "-[" + topic + "]", null, Utils.getFormattedDate(new Date()),
					topic, null, null, clientId, ctx.getRequest().getRemoteHost());
			LOG.info(errRes.toString());
			throw new CambriaApiException(errRes);
		}
		//String metricTopicname = com.att.ajsc.filemonitor.AJSCPropertiesMap.getProperty(CambriaConstants.msgRtr_prop,	"metrics.send.cambria.topic");
		/*
		 * if (null==metricTopicname)
		 * metricTopicname="msgrtr.apinode.metrics.dmaap"; //else if(user!=null)
		 * if(null==ctx.getRequest().getHeader("Authorization")&&
		 * !topic.equalsIgnoreCase(metricTopicname)) { if (null !=
		 * metatopic.getOwner() && !("".equals(metatopic.getOwner()))){ // check
		 * permissions metatopic.checkUserRead(user); } }
		 */

		Consumer c = null;
		try {
			final MetricsSet metricsSet = ctx.getConfigReader().getfMetrics();

			c = ctx.getConfigReader().getfConsumerFactory().getConsumerFor(topic, consumerGroup, clientId, timeoutMs);

			final CambriaOutboundEventStream coes = new CambriaOutboundEventStream.Builder(c).timeout(timeoutMs)
					.limit(limit).filter(topicFilter).pretty(pretty).withMeta(withMeta).build();
			coes.setDmaapContext(ctx);
			coes.setTopic(metatopic);

			DMaaPResponseBuilder.setNoCacheHeadings(ctx);

			try {
				coes.write(baos);
			} catch (Exception ex) {

			}

			c.commitOffsets();
			final int sent = coes.getSentCount();

			metricsSet.consumeTick(sent);

		} catch (UnavailableException excp) {

			ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE,
					DMaaPResponseCode.SERVER_UNAVAILABLE.getResponseCode(),
					errorMessages.getServerUnav() + excp.getMessage(), null, Utils.getFormattedDate(new Date()), topic,
					null, null, clientId, ctx.getRequest().getRemoteHost());
			LOG.info(errRes.toString());
			throw new CambriaApiException(errRes);

		} catch (CambriaApiException excp) {

			throw excp;
		} catch (Exception excp) {

			ctx.getConfigReader().getfConsumerFactory().destroyConsumer(topic, consumerGroup, clientId);

			ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE,
					DMaaPResponseCode.SERVER_UNAVAILABLE.getResponseCode(),
					"Couldn't respond to client, closing cambria consumer" + excp.getMessage(), null,
					Utils.getFormattedDate(new Date()), topic, null, null, clientId, ctx.getRequest().getRemoteHost());
			LOG.info(errRes.toString());
			throw new CambriaApiException(errRes);
		} finally {

			boolean kSetting_EnableCache = ConsumerFactory.kDefault_IsCacheEnabled;
			String strkSetting_EnableCache = AJSCPropertiesMap.getProperty(CambriaConstants.msgRtr_prop,
					ConsumerFactory.kSetting_EnableCache);
			if (null != strkSetting_EnableCache)
				kSetting_EnableCache = Boolean.parseBoolean(strkSetting_EnableCache);

			if (!kSetting_EnableCache && (c != null)) {
				c.close();

			}
		}
		return baos.toString();
	}

	@Override
	public void pushEvents(DMaaPContext ctx, final String topic, InputStream msg, final String defaultPartition,
			final String requestTime) throws ConfigDbException, AccessDeniedException, TopicExistsException,
					CambriaApiException, IOException, missingReqdSetting {

		//final NsaApiKey user = DMaaPAuthenticatorImpl.getAuthenticatedUser(ctx);
		//final Topic metatopic = ctx.getConfigReader().getfMetaBroker().getTopic(topic);

		final String remoteAddr = Utils.getRemoteAddress(ctx);

		if (ctx.getConfigReader().getfIpBlackList().contains(remoteAddr)) {

			ErrorResponse errRes = new ErrorResponse(HttpStatus.SC_FORBIDDEN,
					DMaaPResponseCode.ACCESS_NOT_PERMITTED.getResponseCode(),
					"Source address [" + remoteAddr + "] is blacklisted. Please contact the cluster management team.",
					null, Utils.getFormattedDate(new Date()), topic, Utils.getUserApiKey(ctx.getRequest()),
					ctx.getRequest().getRemoteHost(), null, null);
			LOG.info(errRes.toString());
			throw new CambriaApiException(errRes);
		}

		String topicNameStd = null;

		topicNameStd = com.att.ajsc.beans.PropertiesMapBean.getProperty(CambriaConstants.msgRtr_prop,
				"enforced.topic.name.AAF");
		String metricTopicname = com.att.ajsc.filemonitor.AJSCPropertiesMap.getProperty(CambriaConstants.msgRtr_prop,
				"metrics.send.cambria.topic");
		if (null == metricTopicname)
			metricTopicname = "msgrtr.apinode.metrics.dmaap";
		boolean topicNameEnforced = false;
		if (null != topicNameStd && topic.startsWith(topicNameStd)) {
			topicNameEnforced = true;
		}

		final HttpServletRequest req = ctx.getRequest();

		boolean chunked = false;
		if (null != req.getHeader(TRANSFER_ENCODING)) {
			chunked = req.getHeader(TRANSFER_ENCODING).contains("chunked");
		}

		String mediaType = req.getContentType();
		if (mediaType == null || mediaType.length() == 0) {
			mediaType = MimeTypes.kAppGenericBinary;
		}

		if (mediaType.contains("charset=UTF-8")) {
			mediaType = mediaType.replace("; charset=UTF-8", "").trim();
		}

		if (!topic.equalsIgnoreCase(metricTopicname)) {
			pushEventsWithTransaction(ctx, msg, topic, defaultPartition, requestTime, chunked, mediaType);
		} else {
			pushEvents(ctx, topic, msg, defaultPartition, chunked, mediaType);
		}
	}

	private static void addTransactionDetailsToMessage(message msg, final String topic, HttpServletRequest request,
			final String messageCreationTime, final int messageSequence, final Long batchId,
			final boolean transactionEnabled) {
		LogDetails logDetails = generateLogDetails(topic, request, messageCreationTime, messageSequence, batchId,
				transactionEnabled);
		logDetails.setMessageLengthInBytes(Utils.messageLengthInBytes(msg.getMessage()));
		msg.setTransactionEnabled(transactionEnabled);
		msg.setLogDetails(logDetails);
	}

	private static LogDetails generateLogDetails(final String topicName, HttpServletRequest request,
			final String messageTimestamp, int messageSequence, Long batchId, final boolean transactionEnabled) {
		LogDetails logDetails = new LogDetails();
		logDetails.setTopicId(topicName);
		logDetails.setMessageTimestamp(messageTimestamp);
		logDetails.setPublisherId(Utils.getUserApiKey(request));
		logDetails.setPublisherIp(request.getRemoteHost());
		logDetails.setMessageBatchId(batchId);
		logDetails.setMessageSequence(String.valueOf(messageSequence));
		logDetails.setTransactionEnabled(transactionEnabled);
		logDetails.setTransactionIdTs(Utils.getFormattedDate(new Date()));
		logDetails.setServerIp(request.getLocalAddr());
		return logDetails;
	}

	private void pushEvents(DMaaPContext ctx, String topic, InputStream msg, String defaultPartition, boolean chunked,
			String mediaType) throws ConfigDbException, AccessDeniedException, TopicExistsException,
					CambriaApiException, IOException {
		final MetricsSet metricsSet = ctx.getConfigReader().getfMetrics();

		// setup the event set
		final CambriaEventSet events = new CambriaEventSet(mediaType, msg, chunked, defaultPartition);

		// start processing, building a batch to push to the backend
		final long startMs = System.currentTimeMillis();
		long count = 0;

		long maxEventBatch = 1024 * 16;
		String batchlen = AJSCPropertiesMap.getProperty(CambriaConstants.msgRtr_prop, BATCH_LENGTH);
		if (null != batchlen)
			maxEventBatch = Long.parseLong(batchlen);

		// long maxEventBatch =
		// ctx.getConfigReader().getSettings().getLong(BATCH_LENGTH, 1024 * 16);
		final LinkedList<Publisher.message> batch = new LinkedList<Publisher.message>();
		final ArrayList<KeyedMessage<String, String>> kms = new ArrayList<KeyedMessage<String, String>>();

		try {
			// for each message...
			Publisher.message m = null;
			while ((m = events.next()) != null) {
				// add the message to the batch
				batch.add(m);
				final KeyedMessage<String, String> data = new KeyedMessage<String, String>(topic, m.getKey(),
						m.getMessage());
				kms.add(data);
				// check if the batch is full
				final int sizeNow = batch.size();
				if (sizeNow > maxEventBatch) {
					ctx.getConfigReader().getfPublisher().sendBatchMessage(topic, kms);
					kms.clear();
					batch.clear();
					metricsSet.publishTick(sizeNow);
					count += sizeNow;
				}
			}

			// send the pending batch
			final int sizeNow = batch.size();
			if (sizeNow > 0) {
				ctx.getConfigReader().getfPublisher().sendBatchMessage(topic, kms);
				kms.clear();
				batch.clear();
				metricsSet.publishTick(sizeNow);
				count += sizeNow;
			}

			final long endMs = System.currentTimeMillis();
			final long totalMs = endMs - startMs;

			LOG.info("Published " + count + " msgs in " + totalMs + "ms for topic " + topic);

			// build a responseP
			final JSONObject response = new JSONObject();
			response.put("count", count);
			response.put("serverTimeMs", totalMs);
			// DMaaPResponseBuilder.respondOk(ctx, response);

		} catch (Exception excp) {

			int status = HttpStatus.SC_NOT_FOUND;
			String errorMsg = null;
			if (excp instanceof CambriaApiException) {
				status = ((CambriaApiException) excp).getStatus();
				JSONTokener jsonTokener = new JSONTokener(((CambriaApiException) excp).getBody());
				JSONObject errObject = new JSONObject(jsonTokener);
				errorMsg = (String) errObject.get("message");

			}
			ErrorResponse errRes = new ErrorResponse(status, DMaaPResponseCode.PARTIAL_PUBLISH_MSGS.getResponseCode(),
					errorMessages.getPublishMsgError() + ":" + topic + "." + errorMessages.getPublishMsgCount() + count
							+ "." + errorMsg,
					null, Utils.getFormattedDate(new Date()), topic, null, ctx.getRequest().getRemoteHost(), null,
					null);
			LOG.info(errRes.toString());
			throw new CambriaApiException(errRes);

		}
	}

	private void pushEventsWithTransaction(DMaaPContext ctx, InputStream inputStream, final String topic,
			final String partitionKey, final String requestTime, final boolean chunked, final String mediaType)
					throws ConfigDbException, AccessDeniedException, TopicExistsException, IOException,
					CambriaApiException {

		final MetricsSet metricsSet = ctx.getConfigReader().getfMetrics();

		// setup the event set
		final CambriaEventSet events = new CambriaEventSet(mediaType, inputStream, chunked, partitionKey);

		// start processing, building a batch to push to the backend
		final long startMs = System.currentTimeMillis();
		long count = 0;
		long maxEventBatch = 1024 * 16;
		String evenlen = AJSCPropertiesMap.getProperty(CambriaConstants.msgRtr_prop, BATCH_LENGTH);
		if (null != evenlen)
			maxEventBatch = Long.parseLong(evenlen);

		final LinkedList<Publisher.message> batch = new LinkedList<Publisher.message>();
		final ArrayList<KeyedMessage<String, String>> kms = new ArrayList<KeyedMessage<String, String>>();

		Publisher.message m = null;
		int messageSequence = 1;
		Long batchId = 1L;
		final boolean transactionEnabled = true;
		int publishBatchCount = 0;
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SS");

		// LOG.warn("Batch Start Id: " +
		// Utils.getFromattedBatchSequenceId(batchId));
		try {
			// for each message...
			batchId = DMaaPContext.getBatchID();

			String responseTransactionId = null;

			while ((m = events.next()) != null) {

				// LOG.warn("Batch Start Id: " +
				// Utils.getFromattedBatchSequenceId(batchId));

				addTransactionDetailsToMessage(m, topic, ctx.getRequest(), requestTime, messageSequence, batchId,
						transactionEnabled);
				messageSequence++;

				// add the message to the batch
				batch.add(m);

				responseTransactionId = m.getLogDetails().getTransactionId();

				JSONObject jsonObject = new JSONObject();
				jsonObject.put("message", m.getMessage());
				jsonObject.put("transactionId", responseTransactionId);
				final KeyedMessage<String, String> data = new KeyedMessage<String, String>(topic, m.getKey(),
						jsonObject.toString());
				kms.add(data);

				// check if the batch is full
				final int sizeNow = batch.size();
				if (sizeNow >= maxEventBatch) {
					String startTime = sdf.format(new Date());
					LOG.info("Batch Start Details:[serverIp=" + ctx.getRequest().getLocalAddr() + ",Batch Start Id="
							+ batchId + "]");
					try {
						ctx.getConfigReader().getfPublisher().sendBatchMessage(topic, kms);
						// transactionLogs(batch);
						for (message msg : batch) {
							LogDetails logDetails = msg.getLogDetails();
							LOG.info("Publisher Log Details : " + logDetails.getPublisherLogDetails());
						}
					} catch (Exception excp) {

						int status = HttpStatus.SC_NOT_FOUND;
						String errorMsg = null;
						if (excp instanceof CambriaApiException) {
							status = ((CambriaApiException) excp).getStatus();
							JSONTokener jsonTokener = new JSONTokener(((CambriaApiException) excp).getBody());
							JSONObject errObject = new JSONObject(jsonTokener);
							errorMsg = (String) errObject.get("message");
						}
						ErrorResponse errRes = new ErrorResponse(status,
								DMaaPResponseCode.PARTIAL_PUBLISH_MSGS.getResponseCode(),
								"Transaction-" + errorMessages.getPublishMsgError() + ":" + topic + "."
										+ errorMessages.getPublishMsgCount() + count + "." + errorMsg,
								null, Utils.getFormattedDate(new Date()), topic, Utils.getUserApiKey(ctx.getRequest()),
								ctx.getRequest().getRemoteHost(), null, null);
						LOG.info(errRes.toString());
						throw new CambriaApiException(errRes);
					}
					kms.clear();
					batch.clear();
					metricsSet.publishTick(sizeNow);
					publishBatchCount = sizeNow;
					count += sizeNow;
					// batchId++;
					String endTime = sdf.format(new Date());
					LOG.info("Batch End Details:[serverIp=" + ctx.getRequest().getLocalAddr() + ",Batch End Id="
							+ batchId + ",Batch Total=" + publishBatchCount + ",Batch Start Time=" + startTime
							+ ",Batch End Time=" + endTime + "]");
					batchId = DMaaPContext.getBatchID();
				}
			}

			// send the pending batch
			final int sizeNow = batch.size();
			if (sizeNow > 0) {
				String startTime = sdf.format(new Date());
				LOG.info("Batch Start Details:[serverIp=" + ctx.getRequest().getLocalAddr() + ",Batch Start Id="
						+ batchId + "]");
				try {
					ctx.getConfigReader().getfPublisher().sendBatchMessage(topic, kms);
					// transactionLogs(batch);
					for (message msg : batch) {
						LogDetails logDetails = msg.getLogDetails();
						LOG.info("Publisher Log Details : " + logDetails.getPublisherLogDetails());
					}
				} catch (Exception excp) {
					int status = HttpStatus.SC_NOT_FOUND;
					String errorMsg = null;
					if (excp instanceof CambriaApiException) {
						status = ((CambriaApiException) excp).getStatus();
						JSONTokener jsonTokener = new JSONTokener(((CambriaApiException) excp).getBody());
						JSONObject errObject = new JSONObject(jsonTokener);
						errorMsg = (String) errObject.get("message");
					}

					ErrorResponse errRes = new ErrorResponse(status,
							DMaaPResponseCode.PARTIAL_PUBLISH_MSGS.getResponseCode(),
							"Transaction-" + errorMessages.getPublishMsgError() + ":" + topic + "."
									+ errorMessages.getPublishMsgCount() + count + "." + errorMsg,
							null, Utils.getFormattedDate(new Date()), topic, Utils.getUserApiKey(ctx.getRequest()),
							ctx.getRequest().getRemoteHost(), null, null);
					LOG.info(errRes.toString());
					throw new CambriaApiException(errRes);
				}
				kms.clear();
				metricsSet.publishTick(sizeNow);
				count += sizeNow;
				// batchId++;
				String endTime = sdf.format(new Date());
				publishBatchCount = sizeNow;
				LOG.info("Batch End Details:[serverIp=" + ctx.getRequest().getLocalAddr() + ",Batch End Id=" + batchId
						+ ",Batch Total=" + publishBatchCount + ",Batch Start Time=" + startTime + ",Batch End Time="
						+ endTime + "]");
			}

			final long endMs = System.currentTimeMillis();
			final long totalMs = endMs - startMs;

			LOG.info("Published " + count + " msgs in " + totalMs + "ms for topic " + topic);

			// build a response
			final JSONObject response = new JSONObject();
			response.put("count", count);
			response.put("serverTimeMs", totalMs);

		} catch (Exception excp) {
			int status = HttpStatus.SC_NOT_FOUND;
			String errorMsg = null;
			if (excp instanceof CambriaApiException) {
				status = ((CambriaApiException) excp).getStatus();
				JSONTokener jsonTokener = new JSONTokener(((CambriaApiException) excp).getBody());
				JSONObject errObject = new JSONObject(jsonTokener);
				errorMsg = (String) errObject.get("message");
			}

			ErrorResponse errRes = new ErrorResponse(status, DMaaPResponseCode.PARTIAL_PUBLISH_MSGS.getResponseCode(),
					"Transaction-" + errorMessages.getPublishMsgError() + ":" + topic + "."
							+ errorMessages.getPublishMsgCount() + count + "." + errorMsg,
					null, Utils.getFormattedDate(new Date()), topic, Utils.getUserApiKey(ctx.getRequest()),
					ctx.getRequest().getRemoteHost(), null, null);
			LOG.info(errRes.toString());
			throw new CambriaApiException(errRes);
		}
	}
}
