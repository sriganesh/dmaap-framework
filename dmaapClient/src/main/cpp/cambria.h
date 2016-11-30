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

#ifndef _CABMRIA_H_
#define _CABMRIA_H_

/*
 * This is a client library for the AT&T Cambria Event Routing Service.
 * 
 * Cambria clients post string messages to the broker on a topic, optionally
 * with a partition name.
 */

/* An opaque type for the client instance. */
typedef void* CAMBRIA_CLIENT;

/* Cambria has two formats. CAMBRIA_NATIVE_FORMAT is preferred. */
#define CAMBRIA_NATIVE_FORMAT "application/cambria"
#define CAMBRIA_JSON_FORMAT "application/json"

/* pseudo-HTTP client-side status codes */
#define CAMBRIA_NO_HOST 470
#define CAMBRIA_CANT_CONNECT 471
#define CAMBRIA_UNRECOGNIZED_FORMAT 472
#define CAMBRIA_BAD_RESPONSE 570

/*
 * Send response structure. Be sure to call cambriaDestroySendResponse() after receiving this.
 */
struct cambriaSendResponse
{
	int statusCode;
	char* statusMessage;
	char* responseBody;
};

/*
 * Get response structure. Be sure to call cambriaDestroyGetResponse() after receiving this.
 */
struct cambriaGetResponse
{
	int statusCode;
	char* statusMessage;

	int messageCount;
	char** messageSet;
};

/*
 *	Send a message in a single call. Returns the number sent (1 or 0).
 */ 
extern "C" int cambriaSimpleSend ( const char* host, int port, const char* topic, const char* streamName, const char* msg );

/*
 *	Send multiple messages in a single call. Returns the number sent.
 */ 
extern "C" int cambriaSimpleSendMultiple ( const char* host, int port, const char* topic, const char* streamName, const char** messages, unsigned int msgCount );

/*
 *	Create a client instance to post messages to the given host:port, topic, and
 *	either the CAMBRIA_NATIVE_FORMAT or CAMBRIA_JSON_FORMAT.
 */
extern "C" CAMBRIA_CLIENT cambriaCreateClient ( const char* host, int port, const char* topic, const char* format );

/*
 *	Cleanup a client instance.
 */ 
extern "C" void cambriaDestroyClient ( CAMBRIA_CLIENT client );

/*
 *	Send a single message to the broker using the stream name provided. (If null, no stream name is used.)
 */
extern "C" cambriaSendResponse* cambriaSendMessage ( CAMBRIA_CLIENT client, const char* streamName, const char* message );

/*
 *	Send a batch of messages to the broker using the stream name provided. (If null, no stream name is used.)
 */
extern "C" cambriaSendResponse* cambriaSendMessages ( CAMBRIA_CLIENT client, const char* streamName, const char** messages, unsigned int count );

/*
 *	Retrieve messages from the broker. If a timeout value is 0 (or lower), the broker returns a response
 * 	immediately. Otherwise, the server holds the connection open up to the given timeout. Likewise, if limit
 * 	is 0 (or lower), the server sends as many messages as it cares to. Otherwise, at most 'limit' messages are
 * 	returned.
 */
extern "C" cambriaGetResponse* cambriaGetMessages ( CAMBRIA_CLIENT client, unsigned long timeoutMs, unsigned int limit );

/*
 *	After processing a response, pass it back to the library for cleanup. 
 */
extern "C" void cambriaDestroySendResponse ( CAMBRIA_CLIENT client, const cambriaSendResponse* response );

extern "C" void cambriaDestroyGetResponse ( CAMBRIA_CLIENT client, const cambriaGetResponse* response );

#endif
