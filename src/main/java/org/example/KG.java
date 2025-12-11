package org.example;

import org.apache.lucene.analysis.TokenStream;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.nio.file.*;
import java.util.*;

public class KG {
    public Map<String, Map<String, List<String>>> data = new HashMap<>();
    public Map<String, Map<String, List<String>>> rev_data = new HashMap<>();
    public Map<String, List<Double>> rel_emb = new HashMap<>();
    public Set<String> relations = new HashSet<>();
    private static final MixedAnalyzer analyzer = new MixedAnalyzer();

    public static String normalize(String text) {
        try (TokenStream ts = analyzer.tokenStream("field", text)) {
            ts.reset();
            StringBuilder sb = new StringBuilder();
            while (ts.incrementToken()) {
                sb.append(ts.getAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class).toString());
                sb.append(" ");
            }
            ts.end();
            return sb.toString().trim();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void loadEmbeddings(String path) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> list = mapper.readValue(
                Files.readAllBytes(Paths.get(path)),
                new TypeReference<List<Map<String, Object>>>() {}
        );
        for (Map<String, Object> item : list) {
            String text = (String) item.get("text");
            List<Double> arr = (List<Double>) item.get("embedding");
            rel_emb.put(normalize(text), arr);
        }
    }

    public static KG load(String filePath) throws Exception {
        KG kg = new KG();
        List<String> lines = Files.readAllLines(Paths.get(filePath));

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            String[] parts = line.split(",", 3);
            if (parts.length < 3) continue;

            String subj = normalize(parts[0].trim());
            String pred = normalize(parts[1].trim());
            String obj  = normalize(parts[2].trim());
            if (pred.isEmpty())
                continue;
            kg.relations.add(pred);

            kg.data.computeIfAbsent(subj, k -> new HashMap<>())
                    .computeIfAbsent(pred, k -> new ArrayList<>())
                    .add(obj);

            kg.rev_data.computeIfAbsent(pred, k -> new HashMap<>())
                    .computeIfAbsent(obj, k -> new ArrayList<>())
                    .add(subj);
        }
        return kg;
    }

}
