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
package com.att.nsa.cambria.metrics.publisher.impl;

import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;
import com.att.nsa.apiClient.http.CacheUse;
import com.att.nsa.apiClient.http.HttpClient;
import com.att.nsa.cambria.constants.CambriaConstants;

/**
 * 
 * @author author
 *
 */
public class CambriaBaseClient extends HttpClient implements com.att.nsa.cambria.metrics.publisher.CambriaClient 
{
	protected CambriaBaseClient ( Collection<String> hosts ) throws MalformedURLException
	{
		this ( hosts, null );
	}

	protected CambriaBaseClient ( Collection<String> hosts, String clientSignature ) throws MalformedURLException
	{
//		super ( hosts, CambriaConstants.kStdCambriaServicePort, clientSignature,
//			CacheUse.NONE, 1, 1, TimeUnit.MILLISECONDS );
		super(ConnectionType.HTTP, hosts, CambriaConstants.kStdCambriaServicePort, clientSignature, CacheUse.NONE, 1, 1L, TimeUnit.MILLISECONDS, 32, 32, 600000);

		//fLog = LoggerFactory.getLogger ( this.getClass().getName () );
		fLog = EELFManager.getInstance().getLogger(this.getClass().getName());
		//( this.getClass().getName () );
	}

	@Override
	public void close ()
	{
	}

	protected Set<String> jsonArrayToSet ( JSONArray a ) throws JSONException
	{
		if ( a == null ) return null;

		final TreeSet<String> set = new TreeSet<String> ();
		for ( int i=0; i<a.length (); i++ )
		{
			set.add ( a.getString ( i ));
		}
		return set;
	}
	/**
	 * @param log
	 */
	public void logTo ( EELFLogger  log )
	{
		fLog = log; 
		
		//replaceLogger ( log );
	}

	protected EELFLogger  getLog ()
	{
		return fLog;
	}
	
	private EELFLogger  fLog;
	
	

}
