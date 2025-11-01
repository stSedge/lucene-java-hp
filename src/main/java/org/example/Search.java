package org.example;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Search {
    private final IndexSearcher searcher;
    private final MultiFieldQueryParser queryParser;

    public Search(String indexPath) throws IOException {
        Path indexDir = Paths.get(indexPath);
        Directory directory = FSDirectory.open(indexDir);
        IndexReader reader = DirectoryReader.open(directory);
        this.searcher = new IndexSearcher(reader);

        MixedAnalyzer analyzer = new MixedAnalyzer();
        final String[] fields = {"name", "synonyms", "summary", "full_text", "infobox_content"};
        this.queryParser = new MultiFieldQueryParser(fields, analyzer);
    }

    public void search(String queryString, int maxResults) throws IOException, ParseException {
        Query query = queryParser.parse(queryString);
        TopDocs topDocs = searcher.search(query, maxResults);
        ScoreDoc[] hits = topDocs.scoreDocs;

        if (hits.length == 0) {
            String corrected = correctQuery(queryString);
            if (!corrected.equalsIgnoreCase(queryString)) {
                query = queryParser.parse(corrected);
                topDocs = searcher.search(query, maxResults);
                hits = topDocs.scoreDocs;
            }
        }

        if (hits.length == 0) {
            System.out.println("Совпадений не найдено.");
            return;
        }

        System.out.println("Найдено " + hits.length + " совпадений:");
        for (ScoreDoc hit : hits) {
            Document doc = searcher.doc(hit.doc);
            System.out.println(" - " + doc.get("name") + " (Ссылка: " + doc.get("url") + ")");
        }
    }

    private String correctQuery(String queryString) throws IOException {
        Path spellDir = Paths.get("spellIndex");
        try (Directory dir = FSDirectory.open(spellDir);
             SpellChecker spellChecker = new SpellChecker(dir)) {

            String[] tokens = queryString.split("\\s+");
            StringBuilder corrected = new StringBuilder();

            for (String token : tokens) {
                String[] suggestions = spellChecker.suggestSimilar(token, 1);
                if (suggestions.length > 0) {
                    corrected.append(suggestions[0]);
                } else {
                    corrected.append(token);
                }
                corrected.append(" ");
            }
            return corrected.toString().trim();
        }
    }
}
