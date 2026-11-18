package nl.logicai.wiki.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.Tag;
import nl.logicai.wiki.repositories.PageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search on title and content (spec F-13). The database does the matching; this class shapes the
 * result: path for each hit, a short text fragment with the hits marked, tag counts and paging.
 * Known limitations (MySQL full-text index): whole words only, no stemming or prefix matching; words are
 * runs of letters, digits and underscores, so "node.js" is searched as "node". The index holds no words
 * shorter than {@value #MIN_TOKEN_LENGTH} characters ({@code innodb_ft_min_token_size}) and none of MySQL's
 * built-in stopwords, so those are left out of the query; a query made of only such words ("AI", "C#")
 * falls back to a plain substring match on title and text, newest first.
 */
@Service
@Transactional(readOnly = true)
public class SearchService {

	public static final int PAGE_SIZE = 20;
	private static final int SNIPPET_LENGTH = 160;

	/** MySQL defaults: innodb_ft_min_token_size and innodb_ft_max_token_size. */
	static final int MIN_TOKEN_LENGTH = 3;
	private static final int MAX_TOKEN_LENGTH = 84;

	/** The words InnoDB never indexes (INFORMATION_SCHEMA.INNODB_FT_DEFAULT_STOPWORD). */
	private static final Set<String> STOPWORDS = Set.of("a", "about", "an", "are", "as", "at", "be", "by", "com", "de",
		"en", "for", "from", "how", "i", "in", "is", "it", "la", "of", "on", "or", "that", "the", "this", "to", "was",
		"what", "when", "where", "who", "will", "with", "und", "www");

	/** A word as the MySQL full-text parser sees it: letters, digits, underscores, single apostrophes inside. */
	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_]+(?:'[\\p{L}\\p{N}_]+)*");

	/** A piece of the snippet; {@code hit} marks a matched search term. */
	public record Segment(String text, boolean hit) {
	}

	public record Hit(Page page, String path, List<Segment> snippet, List<Tag> tags) {

		public Hit(Page page, String path, List<Segment> snippet) {
			this(page, path, snippet, List.of());
		}
	}

	public record TagCount(String name, long count) {
	}

	public record Result(String query, String tag, int page, int totalPages, long total,
			List<Hit> hits, List<TagCount> tags) {

		public boolean hasPrevious() {
			return page > 1;
		}

		public boolean hasNext() {
			return page < totalPages;
		}

	}

	private final PageRepository pages;
	private final PageService pageService;
	private final TagService tagService;

	public SearchService(PageRepository pages, PageService pageService, TagService tagService) {
		this.pages = pages;
		this.pageService = pageService;
		this.tagService = tagService;
	}

	public Result search(String rawQuery, String rawTag, int pageNumber) {
		String query = rawQuery == null ? "" : rawQuery.trim();
		String tag = rawTag == null || rawTag.isBlank() ? null : rawTag.trim();
		int page = Math.max(1, pageNumber);
		if (query.isEmpty()) {
			return new Result(query, tag, 1, 0, 0, List.of(), List.of());
		}

		String expression = booleanExpression(query);
		String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
		var pageable = PageRequest.of(page - 1, PAGE_SIZE);
		var found = expression.isEmpty()
			? pages.searchSubstring(pattern, tag, pageable)
			: pages.search(expression, tag, pageable);
		List<String> terms = terms(query);
		List<Hit> hits = new ArrayList<>();
		for (Page hit : found.getContent()) {
			String path = pageService.ancestors(hit).stream().map(Page::getTitle).reduce((a, b) -> a + " / " + b).orElse("");
			hits.add(new Hit(hit, path, snippet(hit.getSearchText(), terms), tagService.tagsOf(hit.getId())));
		}
		List<Object[]> tagRows = expression.isEmpty()
			? pages.countTagsInSubstringSearch(pattern)
			: pages.countTagsInSearch(expression);
		List<TagCount> tags = tagRows.stream()
			.map(row -> new TagCount((String) row[0], ((Number) row[1]).longValue()))
			.toList();
		return new Result(query, tag, page, found.getTotalPages(), found.getTotalElements(), hits, tags);
	}

	/**
	 * The query as a MySQL boolean-mode expression in which every indexed word is required ("+spring +boot").
	 * Only word characters reach the database, so the boolean operators a user may type (+ - @ * " and
	 * friends) cannot change the meaning or break the query. Empty when the query holds no word the index knows.
	 */
	static String booleanExpression(String query) {
		List<String> words = new ArrayList<>();
		Matcher matcher = WORD.matcher(query.toLowerCase(Locale.ROOT));
		while (matcher.find()) {
			String word = matcher.group();
			if (word.length() >= MIN_TOKEN_LENGTH && word.length() <= MAX_TOKEN_LENGTH && !STOPWORDS.contains(word)
					&& !words.contains(word)) {
				words.add(word);
			}
		}
		StringBuilder expression = new StringBuilder();
		for (String word : words) {
			expression.append(expression.isEmpty() ? "+" : " +").append(word);
		}
		return expression.toString();
	}

	private static List<String> terms(String query) {
		List<String> terms = new ArrayList<>();
		for (String term : query.split("\\s+")) {
			if (!term.isBlank()) {
				terms.add(term.toLowerCase(Locale.ROOT));
			}
		}
		return terms;
	}

	/** A window of text around the first matched term, with every term occurrence marked as a hit. */
	static List<Segment> snippet(String text, List<String> terms) {
		String plain = text == null ? "" : text.replaceAll("\\s+", " ").trim();
		if (plain.isEmpty()) {
			return List.of();
		}
		String lower = plain.toLowerCase(Locale.ROOT);
		int first = -1;
		for (String term : terms) {
			int at = lower.indexOf(term);
			if (at >= 0 && (first < 0 || at < first)) {
				first = at;
			}
		}
		int start = first < 0 ? 0 : Math.max(0, first - SNIPPET_LENGTH / 3);
		int end = Math.min(plain.length(), start + SNIPPET_LENGTH);
		String window = plain.substring(start, end);

		List<Segment> segments = new ArrayList<>();
		if (start > 0) {
			segments.add(new Segment("…", false));
		}
		if (terms.isEmpty()) {
			segments.add(new Segment(window, false));
		}
		else {
			Pattern pattern = Pattern.compile(terms.stream().map(Pattern::quote).reduce((a, b) -> a + "|" + b).orElse(""),
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			Matcher matcher = pattern.matcher(window);
			int last = 0;
			while (matcher.find()) {
				if (matcher.start() > last) {
					segments.add(new Segment(window.substring(last, matcher.start()), false));
				}
				segments.add(new Segment(matcher.group(), true));
				last = matcher.end();
			}
			if (last < window.length()) {
				segments.add(new Segment(window.substring(last), false));
			}
		}
		if (end < plain.length()) {
			segments.add(new Segment("…", false));
		}
		return segments;
	}

}
