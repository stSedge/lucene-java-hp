package org.example;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.pattern.PatternReplaceCharFilter;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.ru.RussianLightStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

import java.io.Reader;
import java.util.regex.Pattern;

public class MixedAnalyzer extends Analyzer {

    private final CharArraySet stopWords = RussianAnalyzer.getDefaultStopSet();

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new StandardTokenizer();

        TokenStream tokenStream = new WordDelimiterGraphFilter(
                tokenizer,
                WordDelimiterGraphFilter.GENERATE_WORD_PARTS |
                        WordDelimiterGraphFilter.CATENATE_WORDS |
                        WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE |
                        WordDelimiterGraphFilter.SPLIT_ON_NUMERICS |
                        WordDelimiterGraphFilter.PRESERVE_ORIGINAL,
                null
        );

        tokenStream = new LowerCaseFilter(tokenStream);
        tokenStream = new StopFilter(tokenStream, stopWords);
        tokenStream = new RussianLightStemFilter(tokenStream);

        return new TokenStreamComponents(tokenizer, tokenStream);
    }

    @Override
    protected Reader initReader(String fieldName, Reader reader) {
        return new PatternReplaceCharFilter(Pattern.compile("-"), "", reader);
    }
}
