package nl.logicai.wiki.services;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class FavoriteService {
    private final PageFavoriteRepository favorites;
    private final PageRepository pages;
    private final Clock clock;

    public FavoriteService(PageFavoriteRepository favorites, PageRepository pages, Clock clock){
    }
    @Transactional(readOnly = true)
    public List<Page>favoriteOf(String username){
        return favorites.findActivePages(username);
    }
    public boolean toggle(UUID pageId, String username){
      pages.findByidAndDeleteAtIsNull(page.id).orElseThrow(() ->new PageNotFoundException(pageID));
      if(favorites.exitsByPageIdAndUsername(pageId, username)){
          favorite.deleteByPageIdAndUsername(pageId, username);
          return false;
      }
      favorites.save(new PageFavorite(pageId, username, clock.instant()));
      return true;
    }
}
