package nl.logicai.wiki.services;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.exceptions.PageNotFoundException;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.PageFavorite;
import nl.logicai.wiki.repositories.PageFavoriteRepository;
import nl.logicai.wiki.repositories.PageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Personal favorites (spec U-03): every signed-in user may star active pages. */
@Service
@Transactional
public class FavoriteService {

	private final PageFavoriteRepository favorites;
	private final PageRepository pages;
	private final Clock clock;

	public FavoriteService(PageFavoriteRepository favorites, PageRepository pages, Clock clock) {
		this.favorites = favorites;
		this.pages = pages;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Page> favoritesOf(String username) {
		return favorites.findActivePages(username);
	}

	/** Stars or unstars the page; returns the new state. A trashed or unknown page gives 404. */
	public boolean toggle(UUID pageId, String username) {
		pages.findByIdAndDeletedAtIsNull(pageId).orElseThrow(() -> new PageNotFoundException(pageId));
		if (favorites.existsByPageIdAndUsername(pageId, username)) {
			favorites.deleteByPageIdAndUsername(pageId, username);
			return false;
		}
		favorites.save(new PageFavorite(pageId, username, clock.instant()));
		return true;
	}
}
