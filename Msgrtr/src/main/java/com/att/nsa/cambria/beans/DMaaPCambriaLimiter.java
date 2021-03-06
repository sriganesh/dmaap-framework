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
package com.att.nsa.cambria.beans;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.att.nsa.cambria.CambriaApiException;
import com.att.nsa.cambria.constants.CambriaConstants;
import com.att.nsa.cambria.exception.DMaaPResponseCode;
import com.att.nsa.cambria.exception.ErrorResponse;
import com.att.nsa.drumlin.service.standards.HttpStatusCodes;
import com.att.nsa.drumlin.till.nv.rrNvReadable;
import com.att.nsa.drumlin.till.nv.rrNvReadable.invalidSettingValue;
import com.att.nsa.drumlin.till.nv.rrNvReadable.missingReqdSetting;
import com.att.nsa.metrics.impl.CdmRateTicker;

/**
 * class provide rate information
 * 
 * @author author
 *
 */
@Component
public class DMaaPCambriaLimiter {
	/**
	 * constructor initializes
	 * 
	 * @param settings
	 * @throws missingReqdSetting
	 * @throws invalidSettingValue
	 */
	@Autowired
	public DMaaPCambriaLimiter(@Qualifier("propertyReader") rrNvReadable settings)
			throws missingReqdSetting, invalidSettingValue {
		fRateInfo = new HashMap<String, RateInfo>();
		fMaxEmptyPollsPerMinute = settings.getDouble(CambriaConstants.kSetting_MaxEmptyPollsPerMinute,
				CambriaConstants.kDefault_MaxEmptyPollsPerMinute);
		fWindowLengthMins = settings.getInt(CambriaConstants.kSetting_RateLimitWindowLength,
				CambriaConstants.kDefault_RateLimitWindowLength);
		fSleepMs = settings.getLong(CambriaConstants.kSetting_MaxEmptyPollsPerMinute,
				CambriaConstants.kDefault_SleepMsOnRateLimit);
	}

	/**
	 * static method provide the sleep time
	 * 
	 * @param ratePerMinute
	 * @return
	 */
	public static long getSleepMsForRate(double ratePerMinute) {
		if (ratePerMinute <= 0.0)
			return 0;
		return Math.max(1000, Math.round(60 * 1000 / ratePerMinute));
	}

	/**
	 * Construct a rate limiter.
	 * 
	 * @param maxEmptyPollsPerMinute
	 *            Pass <= 0 to deactivate rate limiting.
	 *            @param windowLengthMins
	 */
	public DMaaPCambriaLimiter(double maxEmptyPollsPerMinute, int windowLengthMins) {
		this(maxEmptyPollsPerMinute, windowLengthMins, getSleepMsForRate(maxEmptyPollsPerMinute));
	}

	/**
	 * Construct a rate limiter
	 * 
	 * @param maxEmptyPollsPerMinute
	 *            Pass <= 0 to deactivate rate limiting.
	 * @param sleepMs
	 * @param windowLengthMins
	 */
	public DMaaPCambriaLimiter(double maxEmptyPollsPerMinute, int windowLengthMins, long sleepMs) {
		fRateInfo = new HashMap<String, RateInfo>();
		fMaxEmptyPollsPerMinute = Math.max(0, maxEmptyPollsPerMinute);
		fWindowLengthMins = windowLengthMins;
		fSleepMs = Math.max(0, sleepMs);
	}

	/**
	 * Tell the rate limiter about a call to a topic/group/id. If the rate is
	 * too high, this call delays its return and throws an exception.
	 * 
	 * @param topic
	 * @param consumerGroup
	 * @param clientId
	 * @throws CambriaApiException
	 */
	public void onCall(String topic, String consumerGroup, String clientId) throws CambriaApiException {
		// do nothing if rate is configured 0 or less
		if (fMaxEmptyPollsPerMinute <= 0) {
			return;
		}

		// setup rate info for this tuple
		final RateInfo ri = getRateInfo(topic, consumerGroup, clientId);

		final double rate = ri.onCall();
		log.info(ri.getLabel() + ": " + rate + " empty replies/minute.");

		if (rate > fMaxEmptyPollsPerMinute) {
			try {
				log.warn(ri.getLabel() + ": " + rate + " empty replies/minute, limit is " + fMaxEmptyPollsPerMinute
						+ ".");
				if (fSleepMs > 0) {
					log.warn(ri.getLabel() + ": " + "Slowing response with " + fSleepMs
							+ " ms sleep, then responding in error.");
					Thread.sleep(fSleepMs);
				} else {
					log.info(ri.getLabel() + ": " + "No sleep configured, just throwing error.");
				}
			} catch (InterruptedException e) {
				// ignore
			}
			ErrorResponse errRes = new ErrorResponse(HttpStatusCodes.k429_tooManyRequests, 
					DMaaPResponseCode.TOO_MANY_REQUESTS.getResponseCode(), 
					"This client is making too many requests. Please use a long poll "
							+ "setting to decrease the number of requests that result in empty responses. ");
			log.info(errRes.toString());
			throw new CambriaApiException(errRes);
		}
	}

	/**
	 * 
	 * @param topic
	 * @param consumerGroup
	 * @param clientId
	 * @param sentCount
	 */
	public void onSend(String topic, String consumerGroup, String clientId, long sentCount) {
		// check for good replies
		if (sentCount > 0) {
			// that was a good send, reset the metric
			getRateInfo(topic, consumerGroup, clientId).reset();
		}
	}

	private static class RateInfo {
		/**
		 * constructor initialzes
		 * 
		 * @param label
		 * @param windowLengthMinutes
		 */
		public RateInfo(String label, int windowLengthMinutes) {
			fLabel = label;
			fCallRateSinceLastMsgSend = new CdmRateTicker("Call rate since last msg send", 1, TimeUnit.MINUTES,
					windowLengthMinutes, TimeUnit.MINUTES);
		}

		public String getLabel() {
			return fLabel;
		}

		/**
		 * CdmRateTicker is reset
		 */
		public void reset() {
			fCallRateSinceLastMsgSend.reset();
		}

		/**
		 * 
		 * @return
		 */
		public double onCall() {
			fCallRateSinceLastMsgSend.tick();
			return fCallRateSinceLastMsgSend.getRate();
		}

		private final String fLabel;
		private final CdmRateTicker fCallRateSinceLastMsgSend;
	}

	private final HashMap<String, RateInfo> fRateInfo;
	private final double fMaxEmptyPollsPerMinute;
	private final int fWindowLengthMins;
	private final long fSleepMs;
	//private static final Logger log = LoggerFactory.getLogger(DMaaPCambriaLimiter.class);
	private static final EELFLogger log = EELFManager.getInstance().getLogger(DMaaPCambriaLimiter.class);
	private RateInfo getRateInfo(String topic, String consumerGroup, String clientId) {
		final String key = makeKey(topic, consumerGroup, clientId);
		RateInfo ri = fRateInfo.get(key);
		if (ri == null) {
			ri = new RateInfo(key, fWindowLengthMins);
			fRateInfo.put(key, ri);
		}
		return ri;
	}

	private String makeKey(String topic, String group, String id) {
		return topic + "::" + group + "::" + id;
	}
}
