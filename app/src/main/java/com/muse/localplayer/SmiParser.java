package com.muse.localplayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SAMI (.smi) 歌词解析：提取 <SYNC Start=毫秒> 时间轴 */
public class SmiParser {

    public static class Line {
        public int time;   // 毫秒
        public String text;
        Line(int t, String s) { time = t; text = s; }
    }

    private final List<Line> lines = new ArrayList<Line>();

    public boolean parseFile(String path) {
        lines.clear();
        try {
            File f = new File(path);
            if (!f.exists()) return false;
            // 先按 UTF-8 读，失败按 GBK（中文 smi 常见）
            String content = readAll(f, "UTF-8");
            if (!content.toUpperCase().contains("<SYNC")) {
                content = readAll(f, "GBK");
            }
            parse(content);
            return !lines.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private String readAll(File f, String enc) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), enc));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private void parse(String html) {
        // 找所有 <SYNC Start=12345> ... 下一个 SYNC 之前的内容
        Pattern p = Pattern.compile("<sync\\s+start\\s*=\\s*(\\d+)[^>]*>(.*?)(?=<sync\\s+start|</body)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        while (m.find()) {
            int t = Integer.parseInt(m.group(1));
            String txt = clean(m.group(2));
            if (!txt.isEmpty()) lines.add(new Line(t, txt));
        }
    }

    /** 去 HTML 标签，&nbsp; 转空格 */
    private String clean(String s) {
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&")
             .replace("&lt;", "<").replace("&gt;", ">");
        // 取第一行非空
        String[] parts = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(part);
            }
        }
        return sb.toString().trim();
    }

    public int size() { return lines.size(); }

    /** 给定播放进度（毫秒），返回当前歌词行号 */
    public int indexAt(int posMs) {
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).time <= posMs) idx = i;
            else break;
        }
        return idx;
    }

    public Line get(int i) { return lines.get(i); }
}
