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
        weight.put("name", 100.0f);
        weight.put("synonyms", 50.0f);
        weight.put("infobox_content", 4.0f);
        weight.put("summary", 9.0f);
        weight.put("full_text", 0.5f);

        this.queryParser = new MultiFieldQueryParser(fields, analyzer, weight);

        //this.queryParser.setDefaultOperator(QueryParser.Operator.AND);
    }

    public static double cosine(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.size() != b.size()) return 0.0;
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0.0 || nb == 0.0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private List<Double> relEmb(String query) {
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            String json = "{ \"a\": \"" + query.replace("\"","\\\"") + "\", \"b\": \"" + query.replace("\"","\\\"") + "\" }";
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    json,
                    okhttp3.MediaType.parse("application/json")
            );
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url("http://localhost:8000/emb")
                    .post(body)
                    .build();
            okhttp3.Response response = client.newCall(request).execute();
            assert response.body() != null;
            String resp = response.body().string();
            org.json.JSONObject obj = new org.json.JSONObject(resp);
            org.json.JSONArray arr = obj.getJSONArray("emb");

            List<Double> result = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getDouble(i));
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }



    public void search(String queryString, int maxResults, KG kg) throws Exception {
        String normQuery = KG.normalize(queryString);
        List<Double> r_emb = relEmb(queryString);

        class RelScore {
            String rel;
            double score;
            RelScore(String r, double s) { rel = r; score = s; }
        }

        List<RelScore> scores = new ArrayList<>();

        for (String rel : kg.rel_emb.keySet()) {
            double score = cosine(r_emb , kg.rel_emb.get(rel));
            if (score > 0.3)
                scores.add(new RelScore(KG.normalize(rel), score));
        }
        scores.sort((a, b) -> Double.compare(b.score, a.score));
        List<String> topRels = new ArrayList<>();
        for (int i = 0; i < Math.min(10, scores.size()); i++) {
            topRels.add(scores.get(i).rel);
        }

        Set<String> resultDocs = new HashSet<>();

        List<ScoredDocument> subjects = search_sbj(queryString, 2);

        for (var foundRel : topRels) {
            for (var sbj : subjects) {
                String docName = sbj.doc.get("name");
                String normDocName = KG.normalize(docName);
                Map<String, List<String>> preds = kg.data.get(normDocName);
                if (preds != null) {
                    List<String> objs = preds.get(foundRel);
                    if (objs != null) {
                        for (String o : objs) {
                            var luceneDoc = search_sbj(KG.normalize(o), 1).getFirst().doc;
                            resultDocs.add(luceneDoc.get("name"));
                        }
                    }
                }
                Map<String, List<String>> rev = kg.rev_data.get(foundRel);
                if (rev != null) {
                    List<String> subs = rev.get(normDocName);
                    if (subs != null) {
                        for (String s : subs) {
                            var luceneDoc = search_sbj(KG.normalize(s), 1).getFirst().doc;
                            resultDocs.add(luceneDoc.get("name"));
                        }
                    }
                }
            }
        }

        for (var d : resultDocs) {
            System.out.println(" ! " + d);
        }

        List<ScoredDocument> reranked = search_sbj(queryString, maxResults);
        for (ScoredDocument sd : reranked) {
            System.out.println(" - " + sd.doc.get("name"));
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

    private double mlScore(String q, String a) {
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

            String json = "{ \"a\": \"" + q.replace("\"","\\\"") + "\", \"b\": \"" + a.replace("\"","\\\"") + "\" }";

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

    public List<ScoredDocument> search_sbj(String queryString, int maxResults) throws IOException, ParseException {
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
        List<ScoredDocument> reranked = new ArrayList<>();
        for (ScoreDoc hit : hits) {
            Document doc = searcher.doc(hit.doc);
            String content = doc.get("name");
            double score = mlScore(queryString, content) * 100;
            double score1 = hit.score;
            double score2 = 0.8 * score1 + 0.2 * score;
            reranked.add(new ScoredDocument(doc, score2, score, score1));
        }
        reranked.sort((a, b) -> Double.compare(b.score, a.score));
        return reranked;
    }

}

