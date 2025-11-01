package org.example;

import java.util.Scanner;

import static org.example.SpellIndexer.buildSpellIndex;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws Exception {
        final String JSON_FILE_PATH = "all_pages.json";
        final String INDEX_PATH = "index";

        //CreateIndex1.createIndex(JSON_FILE_PATH, INDEX_PATH);
        //final String SPELL_INDEX_PATH = "spellIndex";
        //buildSpellIndex(INDEX_PATH, SPELL_INDEX_PATH);

        Search searcher = new Search(INDEX_PATH);
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\nВведите запрос или 0 для выхода");
            String userInput = scanner.nextLine();
            if (userInput.equals("0")) {
                break;
            }
            if (userInput.isBlank()) {
                continue;
            }
            searcher.search(userInput, 10);
        }

        scanner.close();
    }
}