package org.example;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
        Map<String, Float> weight = new HashMap<>();
        weight.put("name", 10.0f);
        weight.put("synonyms", 9.0f);
        weight.put("infobox_content", 4.0f);
        weight.put("summary", 9.0f);
        weight.put("full_text", 0.5f);

        this.queryParser = new MultiFieldQueryParser(fields, analyzer, weight);

        //this.queryParser.setDefaultOperator(QueryParser.Operator.AND);
    }

    public void search(String queryString, int maxResults) throws IOException, ParseException {
        Query query = queryParser.parse(queryString);

        if (queryString.split("\\s+").length > 1) {
            BooleanQuery.Builder combined = new BooleanQuery.Builder();
            combined.add(query, BooleanClause.Occur.SHOULD);

            String[] fields = {"name", "summary", "full_text"};
            for (String field : fields) {
                List<String> tokens = new ArrayList<>();
                try (TokenStream ts = queryParser.getAnalyzer().tokenStream(field, queryString)) {
                    ts.reset();
                    while (ts.incrementToken()) {
                        tokens.add(ts.getAttribute(CharTermAttribute.class).toString());
                    }
                    ts.end();
                }

                if (tokens.size() > 1) {
                    SpanQuery[] spanTerms = tokens.stream()
                            .map(t -> new SpanTermQuery(new org.apache.lucene.index.Term(field, t)))
                            .toArray(SpanQuery[]::new);

                    SpanNearQuery nearQuery = new SpanNearQuery(spanTerms, 5, true);
                    combined.add(new BoostQuery(nearQuery, 2f), BooleanClause.Occur.SHOULD);
                }
            }

            query = combined.build();
        }

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
