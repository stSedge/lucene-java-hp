package org.example;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SpellIndexer {

    public static void buildSpellIndex(String mainIndexPath, String spellIndexPath) throws IOException {
        Path indexDir = Paths.get(mainIndexPath);
        Path spellDir = Paths.get(spellIndexPath);

        try (Directory mainIndex = FSDirectory.open(indexDir);
             Directory spellIndex = FSDirectory.open(spellDir);
             IndexReader reader = DirectoryReader.open(mainIndex)) {

            SpellChecker spellChecker = new SpellChecker(spellIndex);

            LuceneDictionary dictName = new LuceneDictionary(reader, "name");
            LuceneDictionary dictSyn = new LuceneDictionary(reader, "synonyms");
            LuceneDictionary dictInfo = new LuceneDictionary(reader, "infobox_content");

            MixedAnalyzer analyzer = new MixedAnalyzer();

            spellChecker.indexDictionary(dictName, new IndexWriterConfig(analyzer), true);
            spellChecker.indexDictionary(dictSyn, new IndexWriterConfig(analyzer), false);
            spellChecker.indexDictionary(dictInfo, new IndexWriterConfig(analyzer), false);
            spellChecker.close();
        }
    }
}
