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
package com.att.nsa.cambria.metrics.publisher;

import java.io.IOException;
import java.util.Collection;

/**
 * A Cambria publishing interface.
 * 
 * @author author
 *
 */
public interface CambriaPublisher extends CambriaClient {
	/**
	 * A simple message container
	 */
	public static class message {
		/**
		 * 
		 * @param partition
		 * @param msg
		 */
		public message(String partition, String msg) {
			fPartition = partition == null ? "" : partition;
			fMsg = msg;
			if (fMsg == null) {
				throw new IllegalArgumentException("Can't send a null message.");
			}
		}

		/**
		 * 
		 * @param msg
		 */
		public message(message msg) {
			this(msg.fPartition, msg.fMsg);
		}

		/**
		 *  declaring partition string
		 */
		public final String fPartition;
		/**
		 * declaring fMsg String
		 */
		public final String fMsg;
	}

	/**
	 * Send the given message using the given partition.
	 * 
	 * @param partition
	 * @param msg
	 * @return the number of pending messages
	 * @throws IOException
	 */
	int send(String partition, String msg) throws IOException;

	/**
	 * Send the given message using its partition.
	 * 
	 * @param msg
	 * @return the number of pending messages
	 * @throws IOException
	 */
	int send(message msg) throws IOException;

	/**
	 * Send the given messages using their partitions.
	 * 
	 * @param msgs
	 * @return the number of pending messages
	 * @throws IOException
	 */
	int send(Collection<message> msgs) throws IOException;

	/**
	 * Close this publisher. It's an error to call send() after close()
	 */
	void close();
}
