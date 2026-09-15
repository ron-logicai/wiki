package nl.logicai.wiki.models;

/**
 * A validated BlockNote document ready for storage.
 *
 * @param json          normalized JSON of the block array
 * @param searchText    plain text derived from the blocks, for the search index
 * @param schemaVersion structure version of the document format
 */
public record WikiDocument(String json, String searchText, int schemaVersion) {

	public static final String EMPTY_JSON = "[]";

}
