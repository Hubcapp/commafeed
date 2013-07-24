package com.commafeed.backend.services;

import java.util.List;
import java.util.Map;

import javax.ejb.ApplicationException;
import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.commafeed.backend.cache.CacheService;
import com.commafeed.backend.dao.FeedEntryDAO;
import com.commafeed.backend.dao.FeedEntryStatusDAO;
import com.commafeed.backend.dao.FeedSubscriptionDAO;
import com.commafeed.backend.feeds.FeedRefreshTaskGiver;
import com.commafeed.backend.feeds.FeedUtils;
import com.commafeed.backend.model.Feed;
import com.commafeed.backend.model.FeedCategory;
import com.commafeed.backend.model.FeedSubscription;
import com.commafeed.backend.model.Models;
import com.commafeed.backend.model.User;
import com.google.api.client.util.Maps;

public class FeedSubscriptionService {

	private static Logger log = LoggerFactory
			.getLogger(FeedSubscriptionService.class);

	@SuppressWarnings("serial")
	@ApplicationException
	public static class FeedSubscriptionException extends RuntimeException {
		public FeedSubscriptionException(String msg) {
			super(msg);
		}
	}

	@Inject
	FeedService feedService;

	@Inject
	FeedEntryDAO feedEntryDAO;

	@Inject
	FeedEntryStatusDAO feedEntryStatusDAO;

	@Inject
	FeedSubscriptionDAO feedSubscriptionDAO;

	@Inject
	ApplicationSettingsService applicationSettingsService;

	@Inject
	FeedRefreshTaskGiver taskGiver;

	@Inject
	CacheService cache;

	public Feed subscribe(User user, String url, String title,
			FeedCategory category) {

		final String pubUrl = applicationSettingsService.get().getPublicUrl();
		if (StringUtils.isBlank(pubUrl)) {
			throw new FeedSubscriptionException(
					"Public URL of this CommaFeed instance is not set");
		}
		if (url.startsWith(pubUrl)) {
			throw new FeedSubscriptionException(
					"Could not subscribe to a feed from this CommaFeed instance");
		}

		Feed feed = feedService.findOrCreate(url);

		FeedSubscription sub = feedSubscriptionDAO.findByFeed(user, feed);
		if (sub == null) {
			sub = new FeedSubscription();
			sub.setFeed(feed);
			sub.setUser(user);
		}
		sub.setCategory(category);
		sub.setPosition(0);
		sub.setTitle(FeedUtils.truncate(title, 128));
		feedSubscriptionDAO.saveOrUpdate(sub);

		taskGiver.add(feed);
		return feed;
	}

	public Map<Long, Long> getUnreadCount(User user) {
		Map<Long, Long> map = cache.getUnreadCounts(user);
		if (map == null) {
			log.debug("unread count cache miss for {}", Models.getId(user));
			List<FeedSubscription> subs = feedSubscriptionDAO.findAll(user);
			map = Maps.newHashMap();
			for (FeedSubscription sub : subs) {
				map.put(sub.getId(), feedEntryStatusDAO.getUnreadCount(sub));
			}
			cache.setUnreadCounts(user, map);
		}
		return map;
	}
}
