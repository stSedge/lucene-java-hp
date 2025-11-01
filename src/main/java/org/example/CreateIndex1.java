package org.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class CreateIndex1 {
    private static Document createDocument(Map<String, Object> page) {
        Document doc = new Document();

        String url = (String) page.get("url");
        String name = (String) page.get("name");
        String summary = (String) page.get("summary");
        String fullText = (String) page.get("full_text");

        doc.add(new StringField("url", url, Field.Store.YES));
        doc.add(new TextField("name", name, Field.Store.YES));
        doc.add(new TextField("summary", summary, Field.Store.YES));
        doc.add(new TextField("full_text", fullText, Field.Store.NO));

        Object infoboxObject = page.get("infobox");

        if (infoboxObject instanceof Map<?, ?> infobox) {
            String infoboxContent = String.join(", ", infobox.values().stream()
                    .map(Object::toString)
                    .toList());
            doc.add(new TextField("infobox_content", infoboxContent, Field.Store.NO));

            Object nicknamesObj = infobox.get("Прозвища");
            if (nicknamesObj != null) {
                String nicknamesStr = nicknamesObj.toString();
                String[] nicknames = nicknamesStr.split("\\s*,\\s*");
                for (String nickname : nicknames) {
                    doc.add(new TextField("synonyms", nickname, Field.Store.NO));
                }
            }
        }

        Object categoriesObject = page.get("categories");
        if (categoriesObject instanceof List<?> categories) {
            for (Object categoryObj : categories) {
                String category = categoryObj.toString();
                doc.add(new StringField("category", category, Field.Store.YES));
            }
        }

        return doc;
    }


    public static void createIndex(String jsonFilePath, String indexPath) throws Exception {
        Path indexDir = Paths.get(indexPath);
        Directory directory = FSDirectory.open(indexDir);

        MixedAnalyzer analyzer = new MixedAnalyzer();

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (IndexWriter writer = new IndexWriter(directory, config)) {
            Gson gson = new Gson();
            try (Reader reader = new FileReader(jsonFilePath)) {
                List<Map<String, Object>> pages = gson.fromJson(reader, new TypeToken<List<Map<String, Object>>>() {}.getType());
                for (Map<String, Object> page : pages) {
                    Document doc = createDocument(page);
                    writer.addDocument(doc);
                }
            }
        }
    }
}
