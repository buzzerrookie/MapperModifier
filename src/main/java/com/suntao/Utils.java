package com.suntao;

public class Utils {

    public static String camelCaseToUnderscore(String var) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < var.length(); i++) {
            char ch = var.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                sb.append('_').append((char) (ch + 0x20));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static String rtrim(String str) {
        int i = 0;
        for (i = str.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(str.charAt(i))) {
                continue;
            }
            break;
        }
        return str.substring(0, i + 1);
    }
}
