package com.zs.wordecho.data;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One vocabulary entry. The source files use a compact "word<TAB>meaning" format;
 * levelMask records whether the word belongs to CET-4 (1), CET-6 (2), or both (3).
 */
public final class Word {
    private static final Pattern LEADING_POS = Pattern.compile("^(?:[A-Za-z&]+\\.\\s*)+");
    private static final Pattern FIRST_POS = Pattern.compile("^([A-Za-z&]+\\.)\\s*");
    private static final Pattern BRACKETS = Pattern.compile("\\[[^\\]]*]");
    private static final Pattern POS_ANYWHERE = Pattern.compile(
            "(?i)(?:^|\\s)(?:n|v|vt|vi|adj|adv|prep|conj|pron|num|art|int|aux|modal)\\s*\\.");
    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z]{2,}");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");

    private final int levelMask;
    private final String text;
    private final String meaning;
    private final String key;
    private final String posAbbrev;
    private final String posLabel;
    private final String meaningBody;
    private final String spokenMeaning;

    public Word(int levelMask, String text, String meaning) {
        this.levelMask = levelMask;
        this.text = text.trim();
        this.meaning = meaning.trim();
        this.key = this.text.toLowerCase(Locale.ROOT);
        this.posAbbrev = extractPos(this.meaning);
        this.posLabel = toChinesePos(this.posAbbrev);
        this.meaningBody = buildMeaningBody(this.meaning);
        this.spokenMeaning = buildSpokenMeaning(this.meaning);
    }

    private static String extractPos(String meaning) {
        Matcher matcher = FIRST_POS.matcher(meaning);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String toChinesePos(String abbrev) {
        if (abbrev == null || abbrev.isEmpty()) {
            return "";
        }
        String cleaned = abbrev.replace(".", "");
        String[] tokens = cleaned.split("&");
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            String label;
            switch (token.toLowerCase(Locale.ROOT)) {
                case "n": label = "名词"; break;
                case "v": label = "动词"; break;
                case "vt": label = "及物动词"; break;
                case "vi": label = "不及物动词"; break;
                case "adj": label = "形容词"; break;
                case "adv": label = "副词"; break;
                case "prep": label = "介词"; break;
                case "conj": label = "连词"; break;
                case "pron": label = "代词"; break;
                case "num": label = "数词"; break;
                case "art": label = "冠词"; break;
                case "int": label = "感叹词"; break;
                case "aux": label = "助动词"; break;
                case "modal": label = "情态动词"; break;
                default: label = token; break;
            }
            if (out.length() > 0) {
                out.append(" / ");
            }
            out.append(label);
        }
        return out.toString();
    }

    private static String buildMeaningBody(String meaning) {
        String body = LEADING_POS.matcher(meaning).replaceFirst("").trim();
        return body.isEmpty() ? meaning : body;
    }

    private static String buildSpokenMeaning(String meaning) {
        String spoken = BRACKETS.matcher(meaning).replaceAll(" ");
        spoken = POS_ANYWHERE.matcher(spoken).replaceAll(" ");
        spoken = ENGLISH_WORD.matcher(spoken).replaceAll(" ");
        spoken = spoken.replace('；', '，')
                .replace(';', '，')
                .replace('：', '，')
                .replace(':', '，')
                .replace("……", "，")
                .replace("——", "，");
        spoken = SPACES.matcher(spoken).replaceAll(" ").trim();
        spoken = spoken.replaceAll("^[，,、\\s]+", "").replaceAll("[，,、\\s]+$", "");
        if (!CJK.matcher(spoken).find()) {
            return meaning;
        }
        if (!spoken.endsWith("。") && !spoken.endsWith("！") && !spoken.endsWith("？")) {
            spoken += "。";
        }
        return spoken;
    }

    public int getLevelMask() {
        return levelMask;
    }

    public boolean hasLevel(int level) {
        return level == 0 || (levelMask & level) != 0;
    }

    public String getText() {
        return text;
    }

    public String getMeaning() {
        return meaning;
    }

    public String getMeaningBody() {
        return meaningBody;
    }

    public String getSpokenMeaning() {
        return spokenMeaning;
    }

    public String getKey() {
        return key;
    }

    public String getPosAbbrev() {
        return posAbbrev;
    }

    public String getPosLabel() {
        return posLabel;
    }

    @Override
    public String toString() {
        return text + " — " + meaning;
    }
}
