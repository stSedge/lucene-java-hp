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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        /* for (ScoreDoc hit : hits) {
            Document doc = searcher.doc(hit.doc);
            System.out.println(" - " + doc.get("name") + " (Ссылка: " + doc.get("url") + ")"); }
         */
        List<ScoredDocument> reranked = new ArrayList<>();
        for (ScoreDoc hit : hits) {
            Document doc = searcher.doc(hit.doc);
            String content = doc.get("name");
            double score = mlScore(queryString, content);
            double score1 = hit.score;
            double score2 = 0.3 * score1 + 0.7 * score;
            reranked.add(new ScoredDocument(doc, score2, score1, score));
        }
        reranked.sort((a, b) -> Double.compare(b.score, a.score));
        for (ScoredDocument sd : reranked) {
            System.out.println(" - " + sd.doc.get("name") + "\n"
                    + " Score " + sd.score + "\n"
                    + " ML score " + sd.mlscore + "\n"
                    + " Lucene score " + sd.lucenecscore + "\n");
        }
        /*try (BufferedWriter writer = new BufferedWriter(
                new FileWriter("results.txt", StandardCharsets.UTF_8, true))) {
            writer.write("Запрос: " + queryString + "\n");
            if (hits.length == 0) {
                writer.write("Совпадений не найдено.\n\n");
                return;
            }
            for (ScoreDoc hit : hits) {
                Document doc = searcher.doc(hit.doc);
                String line = " - " + doc.get("name");
                writer.write(line + "\n");
            }
            writer.write("\n");
        }*/
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

    private double mlScore(String q, String a) {
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

            String json = "{ \"q\": \"" + q.replace("\"","\\\"") + "\", \"a\": \"" + a.replace("\"","\\\"") + "\" }";

            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    json,
                    okhttp3.MediaType.parse("application/json")
            );

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url("http://localhost:8000/score")
                    .post(body)
                    .build();

            okhttp3.Response response = client.newCall(request).execute();
            assert response.body() != null;
            String resp = response.body().string();

            org.json.JSONObject obj = new org.json.JSONObject(resp);
            return obj.getDouble("score");

        } catch (Exception e) {
            return 0.0;
        }
    }

    private static class ScoredDocument {
        Document doc;
        double score;
        double mlscore;
        double lucenecscore;

        ScoredDocument(Document doc, double score, double mlscore, double lucenecscore) {
            this.doc = doc;
            this.score = score;
            this.lucenecscore = lucenecscore;
            this.mlscore = mlscore;
        }
    }

}

