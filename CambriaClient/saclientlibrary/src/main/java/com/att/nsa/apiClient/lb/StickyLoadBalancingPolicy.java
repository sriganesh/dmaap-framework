/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.nsa.apiClient.lb;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.LoggerFactory;

/**
 * This load balancing policy selects one out of a set of possible elements to use. It continually
 * selects the same element until the client calls {@code onSuspend} or {@code onDown}. In that case,
 * the next {@code select} call may return a new element. If the element is suspended, it won't be
 * returned for {@code suspensionPeriodInSecs} if set, otherwise {@code DEFAULT_SUSPENSION_PERIOD_IN_SECS}.
 * If the element is marked as down then {@code select} will never return it.
 * 
 */
public class StickyLoadBalancingPolicy<C, E> implements LoadBalancingPolicy<C, E> {
	
	private static final long DEFAULT_SUSPENSION_PERIOD_IN_SECS = 30;
	
	private final ConcurrentHashMap<C, E> stickyHosts;
	private final CopyOnWriteArrayList<E> activeHosts;
	private final CopyOnWriteArrayList<E> allHosts;
	private final ScheduledExecutorService scheduler;
	
	private AtomicInteger theBest;
	private AtomicInteger theNotSoBest;

	private AtomicLong suspensionPeriodInSecs;
	
	public StickyLoadBalancingPolicy(Collection<E> elements) {
		
		if (elements == null || elements.isEmpty())
			throw new IllegalArgumentException("At least one element must be provided to the round robin load balancing policy");
		
		stickyHosts = new ConcurrentHashMap<C, E> ();
		activeHosts = new CopyOnWriteArrayList<E> (elements);
		allHosts = new CopyOnWriteArrayList<E> (elements);
		
		scheduler = Executors.newSingleThreadScheduledExecutor();
		
		theBest = new AtomicInteger(0);
		theNotSoBest = new AtomicInteger(0);
		
		suspensionPeriodInSecs = new AtomicLong(-1L);
	}

	@Override
	public E select() {
		return select(null);
	}
	
	@Override
	public E select(C criteria) {
		
		E stickyHost = getStickyHost(criteria);
		
		if (stickyHost != null)
			return stickyHost;
		
		@SuppressWarnings("unchecked")
		final CopyOnWriteArrayList<E> activeHostsSnapshot = (CopyOnWriteArrayList<E>) activeHosts.clone();
		
		if (activeHostsSnapshot.isEmpty()) {
			log.warn("No active hosts were available.  Returning inactive hosts and hoping for the best");
			
			//Overflow protection
			theNotSoBest.compareAndSet(allHosts.size(), 0);
			
			return allHosts.get(theNotSoBest.getAndIncrement());
		} else {
			theBest.compareAndSet(activeHostsSnapshot.size(), 0);
			final E selectedElement = activeHostsSnapshot.get(theBest.getAndIncrement());
			stickyHosts.putIfAbsent(criteria, selectedElement);
			return selectedElement;
		}
	}

	@Override
	public void onUp(final E element) {
		activeHosts.addIfAbsent(element);
	}

	@Override
	public void onDown(final E element) {
		if (activeHosts.remove(element)) {
			theBest.getAndDecrement();
			removeStickyHost(element);
		}
	}

	@Override
	public void onSuspend(final E element) {
		if (activeHosts.remove(element)) {
			theBest.getAndDecrement();
			removeStickyHost(element);
			
			scheduler.schedule(new Runnable() {
				public void run() {
					activeHosts.addIfAbsent(element);
				}
			}, (suspensionPeriodInSecs.get() > 0) ? suspensionPeriodInSecs.get() : DEFAULT_SUSPENSION_PERIOD_IN_SECS, TimeUnit.SECONDS);
		}
	}
	
	@Override
	public void setSuspensionPeriod(int suspensionPeriodInSecs) {
		if (suspensionPeriodInSecs <= 0)
			throw new IllegalArgumentException("Suspension period must be > 0");
		
		this.suspensionPeriodInSecs.set(suspensionPeriodInSecs);
	}

	@Override
	public void close() {
		scheduler.shutdownNow();
	}
	
	private void removeStickyHost(E element) {
		for (Entry<C, E> entry : stickyHosts.entrySet()) {
			if (entry.getValue().equals(element))
				stickyHosts.remove(entry.getKey());
		}
	}
	
	private E getStickyHost(C criteria) {
		if (stickyHosts.containsKey(criteria)) {
			return stickyHosts.get(criteria);
		}
		
		return null;
	}
	
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(StickyLoadBalancingPolicy.class);
}
